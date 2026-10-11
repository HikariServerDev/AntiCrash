# Compatibility

| Component | Target / policy |
|---|---|
| Minecraft | 1.18.2 |
| Fabric Loader | 0.17.3以上。`fabricloader >= 0.17.3`、不必要な上限なし |
| Java | 17。Main modとSupervisorともJava 17 release |
| Fabric API | 0.77.0+1.18.2でbuild確認。Runtime dependencyではなくoptional compile dependency |
| Carpet | optional。基準はFabric Carpet 1.4.69。無い場合はFakePlayerだけ無効 |
| Client | Client Mod不要。Server-side Mod |
| Velocity | hard dependencyなし。対象Server一つだけを処理 |
| Linux | Portable generationを標準使用。Btrfs/Reflinkは明示LEGACY_SAFE |
| Windows/macOS | Portable generationを使用。Linux commandは呼ばない |
| Filesystem | regular file、no symlinkを前提。checkpoint commitは同一親directory内でatomic moveを試し、非対応時も同一Filesystem内moveへfallback |

## Recovery Equivalence compatibility

Runtime Capsule v6とSTRICTの内部state対応は、Minecraft 1.18.2 Yarn `1.18.2+build.4`のmapped APIおよび実際のmerged bytecodeを基準にしています。確認済みの境界は次のとおりです。

- 公開`ServerChunkManager.getChunkFutureSyncOnMainThread(int, int, ChunkStatus, boolean)`はserver threadから呼ぶと内部executorをFuture完了まで駆動します。Recoveryは実バイトコードで確認したprivate `getChunkFuture`をMixin invokerで呼び、Futureを`.join()`せず保持・pollします。`ChunkHolder.LevelType`と`isTickingFutureReady`でFULL/block-ticking/entity-ticking readinessを判定します。
- `ServerChunkManager.addTicket`はticket levelではなくradiusを受け取ります。RecoveryはFULL `33 -> 0`、block-ticking `32 -> 1`、entity-ticking `31 -> 2`へ変換します。
- `ChunkTicketManager.ticketsByPosition`はread-only ledger観測にだけ使います。内部Mapの丸ごとrestoreはしません。
- `EntityList`は実際のiteration順、UUID、type、network IDを観測しますが、`ServerEntityManager`の内部Mapを置換する安全な公開APIがないため順序restoreはしません。
- `World`の`lcgBlockSeed`と次回`tickOrder`、BlockEntityのcurrent/pending ticker、`RedstoneTorchBlock.BURNOUT_MAP`は意味的に扱います。古いWorld objectやJava object参照はserializeしません。
- `World.random`は1.18.2で`java.util.Random`の内部stateを安全に公開するAPIがなく、JDK `--add-opens`を追加しません。STRICTでは`WORLD_RNG=UNSUPPORTED`です。

Carpet 1.4.69は実JARで`TickSpeed`のtickrate、MSPT、`process_entities`、pause/freeze、time-warp scalar、FakePlayer ActionPackのtiming fieldを確認しています。time warpの実行者・callback参照は新しいServerへ安全に再接続できないためUnsupportedです。派生Carpet版でcapture/restoreが失敗した場合、STRICTは既定で停止します。

## Fidelity modes

`STRICT`はcheckpoint tick-boundary equivalenceを目的とし、v6のExpected domainとRuntimeカテゴリを比較してからstartします。`OPTIMIZED`は旧来のCRITICAL/ENTITY_TICKING/ALLによる低負荷のbest-effort recoveryです。`OPTIMIZED does not guarantee tick-boundary equivalence`。

Runtime Capsule v1〜v5は読み込めますが、v6のexact domainやfingerprintを欠くためSTRICTでは`MISSING_FROM_OLD_FORMAT`として利用できません。Carpet未導入は`NOT_APPLICABLE`です。

## Backend details

- `JAVA_INCREMENTAL`: 全OSのfallback。SHA-256で変更を判定し、旧checkpointからのみHardlinkを試します。
- `REFLINK`: LinuxのReflink対応filesystemで`cp --reflink=always`を使用します。ext系filesystemはAUTO判定で除外し、明らかな非対応環境で毎起動時に失敗を繰り返しません。
- `BTRFS`: Linux + Btrfs FileStore + `btrfs` commandが必要です。除外Player fileを削除してcommit metadataを作るため、意図的にwritable subvolume snapshotを作成します。
- `CRIU_EXPERIMENTAL`: 明示指定のみ。現在は安全側のbarrier copy実装で、プロセスmemory checkpointを保証しません。
- `PORTABLE_GENERATION`: 全OSの標準generation seal。CoW filesystemを要求せず、mutable fileのdetachをLogical Cut中に行います。
- `AUTO`: `PORTABLE_GENERATION`を選択します。BTRFS/REFLINK/CRIUは自動選択しません。

## Compatibility risks

Minecraft 1.18.2のprivate field、Block Event queue、Chunk storage、Server Network packetにMixin/Accessorを使います。別Modが同じ入口をcancelした場合、Recovery lockや接続時復元順が変わり得ます。通常運転時のTick順序へ介入する設計ではありませんが、他ModとのMixin競合は実構成で確認してください。

CarpetのFactory/派生版互換にはreflectionを使いますが、設定で選択したActionPack fieldは欠落時にfail closedにします。Fabric Carpet 1.4.69の`mobcaps` HUDは、Worldの最初のTickより前にSpawn情報がnullだと例外になるため、AntiCrashのRecovery Freeze中だけVanilla getterへ空の観測値を補います。Spawn情報の実体やWorld Tickは変更しません。

Minecraft 1.18.2のChunk ticketは数字が小さいほど強く広い範囲を保持します。Recoveryは内部level FULL=`33`、entity-ticking/forced=`31`を固定使用しますが、`ServerChunkManager.addTicket`へ渡すときはradius FULL `0`、entity-ticking/forced `2`へ変換します。既定の`ALL`ではcheckpoint時にloadedだった全Chunkを当時のレベルまで準備してから再開します。`CRITICAL`ではforcedと正確な復元anchorだけをロードします。どのmodeでも`max_preload_chunks`を超える要求は拒否します。level `2`をradiusとして誤用する旧buildは大規模Worldと小さいheapでOOMを起こし得るため互換対象外です。Fabric APIなし、Carpetなし、Velocityありの構成を優先していますが、実ServerでのLoader、Java、Filesystem権限、外部Player Data Modの動作はこのbuildだけでは証明できません。

## Logical Delta v2のportable境界

1.18.2 mapped bytecodeで`RegionFile` constructorが`(regionFilePath, regionDirectory, dsync)`であることを確認し、worker materializerもこの順序を使用します。親checkpointのhard linkを変更差分へ使う前にcopy-on-writeし、親とsourceが同じ認証済みviewの場合だけ自己変更によるctime/link-count差分を外部writerとして扱いません。Live Worldとcheckpoint間のhard linkは作りません。

`WorldTickScheduler`は自身からWorld/Dimensionを逆引きできないため、server threadが現存するscheduler object identityをDimensionへ登録します。未登録schedulerのmutationは推測せずlogical cutをblockingにします。Ticket ledger、lighting queue、worker Future、EntityList内部Mapはread-only観測または`UNSUPPORTED`であり、reflectionによる丸ごと置換はしません。

## Chunk/Entityデータへの追加キー

AntiCrashは、Chunkのblock entityとEntityのNBTに`AntiCrash`というcompoundを追加することがあります（読み込まれたまま保存されるときだけ）。Vanillaや他のModはこのキーを無視し、AntiCrashを外してもWorldはそのまま開けます。Vanillaが保存する値は変更しません。Runtime Capsule v7のcheckpointは、v6までしか知らない古いAntiCrashでは読めません。

## 実機検証済みの構成

Minecraft 1.18.2 / Fabric Loader 0.17.3 / Fabric API 0.77.0 / Carpet 1.4.69 / Java 21の専用Serverで、中断しなかった場合と復旧した場合をtick単位で比較しています。検証した装置・結果・既知の限界は[RUNTIME-QUALIFICATION.md](RUNTIME-QUALIFICATION.md)を参照してください。ほかのModが追加するBlock EntityやEntityの非保存状態は検証対象外です。
