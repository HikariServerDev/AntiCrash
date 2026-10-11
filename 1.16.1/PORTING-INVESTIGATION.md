# 1.16.1 legacy adapter 調査・実装状況

Minecraft 1.16.1 / Yarn `1.16.1+build.21`の実mapped JARをjavap/ASMで確認した。
Minecraft/World/Serverのクラスを起動・生成していない。coreのテストはJava 17以上の開発用であり、
Java 8のMinecraftへ現行Modをそのまま導入できるという意味ではない。

## 実在する状態と意味

| 対象 | 確認した実State/API | 新版との差 |
|---|---|---|
| ServerTickScheduler | scheduledTickActions Set、scheduledTickActionsInOrder TreeSet | World全体の予定tick。chunk-local schedulerではない |
| 同上 | currentTickActions Queue、consumedTickActions List | 通常tickのreturn時にclearされる。非空ならcutは延期すべき |
| ScheduledTick | global static idCounter、private final id、time、priority | world別tickOrderではなく、全Dimension/block/fluidで同じsequenceを使う |
| 同上 | Comparator = time / priority / id | 同時刻・同priorityの発火順にglobal idが必要 |
| isScheduled / isTicking | 検索用にnew ScheduledTickを作る | コンストラクターがidCounterを消費する。captureに使うとlive stateを変える |
| addScheduledTick | Set.containsの後、Set.add + TreeSet.add | 両方を一貫して更新するnative insertion。Map/TreeSet全置換は避ける |
| native scheduled NBT | i/x/y/z/t/pだけ保存 | idを保存しない。相対timeをlongからintへnarrowする |
| ServerWorld | entitiesById、entitiesByUuid、entitiesToLoad | modern ServerEntityManager/EntityListは存在しない |
| World | blockEntities / tickingBlockEntities / pendingBlockEntities | ticker wrapper objectではなくBlockEntityのList |
| World runtime | java.util.Random / lcgBlockSeed | world tickOrder fieldは存在しない |
| ChunkSerializer | Level.Entities / TileEntities / TileTicks / LiquidTicks | entityは別entities/IOではなくchunk payloadと一緒に保存される |
| ChunkHolder | borderFuture / tickingFuture / entityTickingFuture | FULL/block/entityのFuture readinessを観測可能 |
| ServerChunkManager | private getChunkFuture、public getChunkFutureSyncOnMainThread | public sync版はrunTasksで待つ。private futureをpollする構成が必要 |

getScheduledTicksInChunkはchunk境界の外側も検索するため、chunkごとの結果を単純に連結して
exact ledgerにする方式は使わない。全worldのordered setを境界で一度だけ観測し、
dimension/kind/type/positionとglobal sequenceから意味的に保存する設計にする。

## 実装済みcore

`adapter-core`は次のvalue-only部分を実装する。read-only native capture adapterも作成したが、
本体checkpoint engineへはまだ接続していない。

- Dimensionとblock/fluidを区別する予定tick ledger。
- 絶対時刻long、priority、global sequence、global next counterを保持。
- 重複scheduler identity、重複global sequence、counter矛盾、mid-tick queueを拒否。
- native comparatorによる予定順と、global sequenceによる復元構築順を分離。
- bounded entry count、streaming binary serialization、streaming SHA-256、immutable cut。
- 実JARのfield/API/insertion/query side effect/native NBT/Future waitのbytecode contractテスト。

このcoreはFabric entrypointを持たず、JAR生成taskも無効。
coreのテスト成功を既存の本体baselineテストやMod build成功として扱わない。
targetはPORT_IN_PROGRESS、共通buildへの収集は禁止のまま。

`src/main/java`には1.18.2からYarn targetへmigrateした調査用本体もある。
本体compileではmodern scheduler/entity manager/sculk/ticker等の非存在classによる104 errorsを確認した。
未接続の処理をempty fallbackへ変更して成功を装わない。
一方`LegacyScheduledTickAccessor`、`LegacyServerTickSchedulerAccessor`、`LegacySchedulerCapture`は
実1.16.1 compile classpathで独立compileが成功した。
captureはserver threadを要求し、全worldのblock/fluid ordered setを直接読み、
in-flight queue・Set/TreeSet不一致・memory上限を検査する。native queryやnew ScheduledTickは使わず、
実行順/sequenceを記録してもglobal counterを消費しない。
このcompile成功はAccessorのruntime適用や実capture実行の確認ではない。
coreテスト14件はfailure/error/skip 0。

## 未実装で残る接続

1. 作成済みread-only Accessor/captureの本体epoch境界への接続と、復元前のregistry/type検証。
2. idを挿入前に再構築するsafe factory + native addScheduledTickの接続とglobal counter復元。
3. chunk内Entity保存scope、dirty tracking、Low-Pause generationのIO seal/owner boundary。
4. native block entity orderingとentity iteration観測。safe restore不能な部分のSTRICT fail closed。
5. chunk readiness / ticket ledger / 非同期batch / freeze loopのlegacy adapter。
6. network/permission/FakePlayer/Carpet/player snapshot等の全機能接続。
7. field matrix・Mixin signature・保存/復元baseline tests・実Mod/Supervisor build。

実機Mixin適用とTMCのdifferential recoveryは、完成後にユーザーの隔離環境で確認する必要がある。
本番world、checkpoint、Server/Proxy/MySQL/systemdは操作していない。

Production server modified: NO
