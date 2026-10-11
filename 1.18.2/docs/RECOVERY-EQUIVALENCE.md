# Recovery Equivalence Contract

## 目的

AntiCrashの`STRICT` Recoveryは、TMC装置の形やChunk数を推測して保護する機能ではありません。Checkpoint境界でMinecraft 1.18.2が実際に持っていた、観測・意味的に復元可能な実行状態を記録し、Worldを再読込した後に同じ状態を再構成できたことを検証してからGame Tickを再開します。

この文書でいう等価性は、Java processのheap、CPU register、worker thread stack、OS scheduler、lighting workerの内部queueまで同一にするという意味ではありません。それらは安全な公開restore APIがなく、無理にserializeするとVanilla invariantを壊します。復元できないものは隠さず`UNSUPPORTED`として記録し、既定ではFail Closedにします。

## 実装基準と調査結果

対象はMinecraft 1.18.2、Yarn `1.18.2+build.4`、Fabric Loader 0.17.3、Java 17です。実際のFabric Loom merged JARと、実サーバーに存在するCarpet 1.4.69 JARのbytecodeを確認した上で、次の境界を採用しています。

- `ServerChunkManager.getChunkFutureSyncOnMainThread(int, int, ChunkStatus, boolean)`および内部のFutureは、実際のmapped bytecode上では`CompletableFuture<Either<WorldChunk, Unloaded>>`です。RecoveryはFutureを保持し、次のserver-loopで完了をpollします。Main Threadで`.join()`したり、同期`getChunk`へ戻ったりしません。
- `ServerChunkManager.addTicket`の引数はlevelではなくradiusです。Vanilla内部のlevelは`33 - radius`で計算されるため、RecoveryはFULL `33 -> radius 0`、block-ticking `32 -> radius 1`、entity-ticking/forced `31 -> radius 2`へ変換します。
- `ChunkHolder.LevelType`、accessible/ticking/entity-ticking Future、`ServerChunkManager.isTickingFutureReady`を組み合わせて、FULL、block-ticking、entity-ticking readinessを判定します。
- `WorldTickScheduler`はblock-ticking readiness predicateでscheduled tickを実行します。したがってscheduled tickをentity-tickingだけの集合として切り捨てず、v6ではaccessibleなFULL Chunkのblock/fluid queueを対象にします。
- `ChunkTicketManager.ticketsByPosition`はread-only観測だけに使います。自然に再生成されるPlayer/FakePlayer/portal等のTicketと二重にしないため、内部ledgerの丸ごと書き戻しは行いません。
- `EntityList`の`forEach`で観測できる実際のiteration orderを保存しますが、`ServerEntityManager`や内部Mapをreflectionで置換するrestoreは行いません。
- `World`では`lcgBlockSeed`と次回`tickOrder`をAccessorで扱います。`World.random`は1.18.2の`java.util.Random`内部stateをJDK `--add-opens`なしに安全取得できないため、強制公開しません。
- `RedstoneTorchBlock.BURNOUT_MAP`は古いWorld objectを保存せず、Dimensionごとの新しい`ServerWorld` keyへ`BlockPos`、時刻、entry順を再構成します。
- Carpet 1.4.69では`TickSpeed`のtickrate、MSPT、`process_entities`、pause/freeze、time-warp scalarと、FakePlayer ActionPackのtiming fieldを確認しています。time warpの実行者・callback参照は新しいServerへ安全に結び直せないためUnsupportedです。

調査対象に存在しないfield/methodは推測で実装しません。lighting、Future内部のworker状態、CPU schedulingはこのContractのExact Runtime対象外です。

## Fidelity

```text
STRICT:
Goal = checkpoint tick-boundary equivalence

OPTIMIZED:
Goal = best-effort TMC recovery with lower memory/load cost
```

`STRICT`ではRuntime Capsule v6が必要です。Checkpoint時点のExpected domainを上限で切り捨てず、全Expected Chunkが必要なreadinessへ到達しなければ`/ac start`を拒否します。Mismatch、破損、restore失敗、旧形式で欠落した値は、Unsupported許可設定や`--force`で迂回できません。

`OPTIMIZED`は既存のCRITICAL/ENTITY_TICKING/ALL plannerを使います。既定の`ALL`はcheckpoint時のloaded集合を当時のレベルまで準備します。`CRITICAL`はforced、Player/FakePlayer、Block Event、transient state等のanchorだけを準備し、小さいheapでのロード量を抑えます。OPTIMIZEDはRuntime stateの全カテゴリの等価性までは約束しません。

`OPTIMIZED does not guarantee tick-boundary equivalence`。

設定例：

```toml
[recovery]
fidelity = "STRICT"
max_preload_chunks = 2048

[recovery.chunks]
max_concurrent_loads = 4

[recovery.strict]
allow_unsupported_runtime_state = false
allow_unsupported_carpet_state = false
require_entity_order = true
require_block_entity_ticker_order = true
require_ticket_ledger = true

[recovery.strict.players]
require_checkpoint_players = false
```

現行1.18.2では`WORLD_RNG`がUnsupportedとして常に記録されます。完全なStrict checkpointを成立させるには、Java Randomの内部stateを同値復元できないことを受け入れ、`allow_unsupported_runtime_state = true`を明示する必要があります。その場合も、他のカテゴリのMismatchや復元失敗は許可されません。Carpetの失敗やtime warpは`allow_unsupported_carpet_state`で別に判断します。

## Exact Checkpoint Tick Domain

各Dimensionの`ChunkStateSnapshot`へ、次の集合とrowを保存します。

| 状態 | 内容 |
|---|---|
| `loaded` | Checkpoint境界でstorageのloaded setに存在したか |
| `full` | `ChunkStatus.FULL` futureが実体を返せる状態だったか |
| `blockTicking` | Block/Fluid schedulerがtick対象とするticking readinessだったか |
| `entityTicking` | Entity-ticking futureとlevel typeがreadyだったか |
| `forced` | Vanilla forced chunk setに存在したか |
| metadata | ChunkPos、ChunkHolder level、completed level、level type、capture source |

Propagationだけの`BORDER`/`INACCESSIBLE` halo holderは、loaded/readiness/forcedの実状態でない限りdomainから除外します。これはAntiCrash temporary ticketが作る周辺bookkeepingを、Checkpointの実行可能domainと誤認しないためです。Holderが一時的に観測できないloaded/forced位置は、readiness=falseのrowとして残します。欠落をready扱いしません。

STRICTのChunk planはv6 domain rowのrequired集合を全て選びます。例えばExpected 654、`max_preload_chunks = 512`なら、512だけをロードして開始することはありません。

```text
Expected: 654
Configured maximum: 512
Ready: 0..512
STRICT RECOVERY FAILED / RECOVERY_FROZEN
reason: configured maximum is below the expected domain
```

## Future-based Chunk Recovery

復旧の各server-loopは次の状態遷移を行います。

```text
REQUEST up to max_concurrent_loads
  -> return to server loop
  -> Vanilla ticket/storage lifecycle advances
  -> future completion is observed without join
  -> FULL/block/entity readiness is checked
  -> mark READY or FAILED
  -> request the next batch
```

`recovery.chunks.max_concurrent_loads`は1〜64、既定4です。これはExpected Chunk数を減らす設定ではなく、FutureとChunk objectが同時に生きる数を制限するOOM対策です。復旧中は通常のWorld/Entity simulationをtickせず、Ticket・storage lifecycleだけを進めます。進捗が一定時間なくなれば`chunk_ready_timeout_seconds`でFail Closedし、失敗時はRecovery ticketを解放します。

旧実装のlevelをradiusとして渡す誤用（FULL 33をradius 33、entity-ticking 31をradius 31等）は行いません。これがlevel 0/2相当の広範囲ロードを発生させるOOM原因になっていました。

## Equivalence categories

各カテゴリは、期待値、復旧後の実値、canonical fingerprint、件数、状態、blocking flag、必要なら詳細差分を持ちます。

| Category | capture / restore | 既定STRICTでの扱い |
|---|---|---|
| `CHUNK_DOMAIN` | v6のDimension別domain set/readiness。Futureで必要状態へ準備。Holder level/type/sourceは診断用にも保存 | 必須。実行可能な集合・readinessのMismatchで停止。再起動で変わり得るHolder metadataだけでは停止しない |
| `BLOCK_EVENTS` | `syncedBlockEventQueue`/`blockEventQueue`のDimension、Pos、type、data、queue、順序 | 必須。事前検証後にclearして保存順へ復元 |
| `SCHEDULED_TICKS` | block/fluid type、Pos、絶対trigger、priority、`subTickOrder`、queue順。v6ではFULL accessible Chunkも空queueを含めて置換 | 必須。未知type、対象外Chunk、件数/順序差で停止 |
| `MOVING_PISTONS` | Piston BlockEntity NBTはVanilla Chunk save、tickerの位置/orderは観測。Piston mixinがtick後dirty化 | 必須。v6はgeneric ticker restoreでcurrent/pendingを扱い、存在を確認 |
| `BLOCK_ENTITY_TICKERS` | `blockEntityTickers`/`pendingBlockEntityTickers`のPos、BlockEntity type/name、queue、order。Java invokerはserializeしない | `require_block_entity_ticker_order`で必須性を選択。安全な意味的再構成が不能なら停止 |
| `ENTITIES` | EntityListのUUID、type、network ID、iteration order、removed flag | `require_entity_order`で必須性を選択。内部Mapの直接置換はしない。`require_checkpoint_players=false`なら未接続のReal Playerを比較から除外し、FakePlayerは含める |
| `REDSTONE_RUNTIME` | Dimension、Torch BlockPos、burnout time、entry order。新World keyへ復元 | 必須。旧World参照は持ち越さない |
| `WORLD_RUNTIME` | game time、time of day、`lcgBlockSeed`、次回`tickOrder`、各domain count | 必須。RNGは別カテゴリ |
| `WORLD_RNG` | `java.util.Random`内部stateを強制取得しない | `UNSUPPORTED`。runtime許可がなければ停止 |
| `TICKET_LEDGER` | 非AntiCrash Ticketのtype、level、argument、tickCreated、expiryを観測 | `require_ticket_ledger`で必須性を選択。restoreはobserve-only |
| `CARPET` | TickSpeed、process_entities、pause/freeze、warp scalar。active time warpはUnsupported | CarpetなしはN/A。導入済みのcapture/restore失敗は既定停止 |
| `FAKEPLAYERS` | Position、Rotation、Dimension、Gamemode、Flying、Sneak、Sprint、Movement、Inventory/config、USE/ATTACK、ActionPack timing、break progress | 必須。`next`等の保存値を再発行コマンドで上書きしない |
| `PLAYERS` | UUID、name、Dimension。`require_checkpoint_players`がtrueのときのみ必須。選択的な`players.json`とは別にRuntime Capsuleへ保存 | 既定N/A。trueなら同じPlayerが揃うまで停止。`player_snapshot.enabled=false`でもこの同一性検査は有効 |
| `CHECKPOINT_INTEGRITY` | capsule内fingerprintと再計算値、manifestのv6 counters/hash | 必須。認証済みpayloadの自己不一致で停止 |

### Statusの意味

- `MATCH`: 保存したcanonical stateと現在のstateが一致。
- `MISMATCH`: 両方を観測できたが、件数・順序・値が違う。
- `UNSUPPORTED`: 安全なcapture/restoreまたは比較対象の公開境界がない。
- `MISSING_FROM_OLD_FORMAT`: v1〜v5などに今回の値が存在しない。旧値をMATCH扱いしない。
- `INCOMPLETE`: capture/restoreが途中で失敗した、または必須payloadが欠落した。
- `NOT_APPLICABLE`: Carpet未導入、または任意Player比較を無効にしているなど、対象外。

## Runtime restoreの詳細

### Scheduled tickとWorld順序

1.18.2のVanilla Chunk NBTだけでは、同じtrigger/priority内の実行順を完全に表現できません。v6はmarkerが記録した`subTickOrder`と各Dimensionの次回`tickOrder`をRuntime Capsuleへ追加します。復元前にcounterを確認し、restored tickへ新規tickが割り込む初期値へ戻さないようにします。復元対象Chunkはscheduled tickが0件でもqueueをclear/reconstructし、World load中に作られた余分なtickを黙って残しません。

### Moving PistonとBlockEntity ticker

`PistonBlockEntity`の`progress`、`pushedBlock`、`facing`、`extending`、`source`等はVanilla Chunk serializationに委ねます。Piston tick後に`World.markDirty`相当だけを行い、比較器更新や通常tick順序への介入はしません。全BlockEntityについてはcurrent/pending queueの意味的順序を保存し、restore時は位置・type・nameと現在のJava invokerを照合します。Java object自体や内部listの無検証置換はしません。照合できない場合は`UNSUPPORTED_RESTORE`相当のblocking failureです。

### Entity order

EntityListの観測は、UUID、Entity type、network ID、iteration orderをfingerprintへ保存します。安全なServerEntityManager再構築APIを確認できないため、内部Mapを書き換えて見かけの順序だけを合わせる処理はありません。Strictでこの観測を必須にした場合、復元後にEntityの再登録順が違えば`MISMATCH`になります。`require_checkpoint_players=false`（既定）では、再起動後にまだloginしていないReal Playerをこの順序比較から除外します。FakePlayerはActionPack復元対象なので除外せず、Real Playerの存在・相対順まで要求する場合は`require_checkpoint_players=true`にします。この設定の期待一覧は選択的な`players.json`の有効/無効に依存せず、Runtime Capsuleの軽量なUUID/name/Dimension情報から比較します。これは「一致しないまま動かす」より安全な挙動です。

### Torch burnoutとWorld runtime

`RedstoneTorchBlock.BURNOUT_MAP`のKeyが旧`World` objectの場合も、objectをJSONへ渡しません。Dimension IDとPos/time/orderへ変換し、復元時に新しい`ServerWorld` objectをKeyとして登録します。`lcgBlockSeed`と次回`tickOrder`はAccessorで復元します。Java Randomの内部seedは取得せず、`WORLD_RNG`へ明示します。

### Carpet / FakePlayer

Carpet未導入は正常な`NOT_APPLICABLE`です。Carpet導入済みでTickSpeed captureに失敗、FakePlayer ActionPackの必須fieldが欠落、restore後のtickrate/process/freezeが一致しない場合は、既定STRICTでは`INCOMPLETE`/`MISMATCH`で止めます。time warpのcallbackや実行者を別Serverへ移植することはしません。FakePlayerはWorld準備後・unfreeze前に復元し、`/player BB use interval 4`のような元コマンドを再発行して`next`をresetすることはありません。

### Ticket ledger

Ticketは診断上重要ですが、1.18.2の内部managerには安全な永続restore APIがありません。Ledgerはtype、level、argument、tickCreated、expiryをcaptureし、自然再生成TicketとAntiCrash temporary Ticketを区別します。Recoveryはその一覧を内部Mapへ戻しません。非空ledgerの比較は`UNSUPPORTED`として報告し、`require_ticket_ledger=false`はこの制限を受け入れる明示的な設定です。これはTicketを復元したことも、旧expiry/identity graphと等価であることも意味しません。

## `/ac start`直前の再検証

Chunk Futureが全て完了して`READY_TO_START`になった後も、Player login、自然Ticket、FakePlayer再作成、Carpet状態変化が起こり得ます。そのため`/ac start`の受付時に次を再取得します。

- Expected/actual Chunk domainとreadiness
- Block Event、scheduled tick、moving piston、BlockEntity ticker、Entity、Torch、World runtime、Ticket fingerprint
- Carpet TickSpeed/FakePlayerと、必要ならReal Player
- Runtime Capsuleの自己整合性とmanifest/capsule counters

一つでもblocking mismatchがあれば、`/ac start`は実行せず`RECOVERY_FROZEN`へ留めます。`--force`はSTRICT Contractを迂回しません。`allow_unsupported_*`で許可できるのは明示されたUnsupported categoryだけです。

## Commands / status

```text
/ac recovery verify
/ac recovery verify --verbose
/ac debug equivalence
/ac debug equivalence --verbose
/ac status
```

`verify`と`debug equivalence`は同じReportを表示します。`--verbose`ではMismatchの先頭20件まで、Chunk座標・queue・index・expected/actualを表示します。`/ac status`ではFidelity、Expected/READY Chunk、current concurrent loads、pending Future、Equivalence結果、blocking category、Carpet completeness、Unsupported categoryが見えます。Checkpoint commit時の`checkpoint.diagnostic`ログには同じカテゴリの件数・hash、`WORLD_RNG`の対応可否、Runtime Capsuleサイズ、推定ヒープ使用量も記録されます。

## Old Capsule

Runtime Capsule v1〜v5は既存の後方互換ローダーで読み込みます。ただし、v6のexact domain、fingerprint、ticker/entity/torch/ticket/world runtime fieldがない場合、STRICTでは`MISSING_FROM_OLD_FORMAT`として扱います。欠落値を空集合、Vanilla初期値、現在値で補ってMATCHにすることはありません。旧Capsuleを使う必要がある場合は`OPTIMIZED`でbest-effort復旧し、等価性保証がないことを受け入れてください。

## Async TMCの境界

CPU scheduling、thread stack、worker Future、lighting queueなどをserializeして「同じ実行」を作ろうとはしません。将来の非同期TMC向けには、試行前のSafety Checkpointを基準とする次のCritical Section APIを設計対象とします。

```text
/ac guard begin <name> --crash rollback
/ac guard begin <name> --crash keep
/ac guard begin <name> --crash ask
/ac guard commit
/ac guard abort
/ac guard status
```

これは今回のRuntime Capsule v6のExact stateとは別機能です。今回のTrencher問題を、lighting/Future/CPU状態の危険なserializationで解決しません。

## 実機確認手順

本実装では実Minecraft Serverを起動しません。利用者が実構成で確認するときは、次の順でログとReportを保全してください。

1. `fidelity = "STRICT"`、十分な`max_preload_chunks`、低い`max_concurrent_loads`で起動する。
2. 装置を停止したいtick境界でcheckpointを作成し、ログのcheckpoint ID、各Dimensionのdomain count/hash、Runtime Capsule version 6、Unsupported一覧を保存する。
3. Supervisorまたは管理ツールから異常終了・復旧を行い、通常のログだけでなく`/ac status`を取得する。
4. `/ac recovery verify --verbose`で`CHUNK_DOMAIN`、`SCHEDULED_TICKS`、`BLOCK_ENTITY_TICKERS`、`ENTITIES`、`CARPET`の状態を確認する。
5. `READY`でない限り`/ac start`は実行しない。実行した場合も直前Reportと`/ac debug equivalence --verbose`を保存する。
6. Trencher、Flying Machine、TNT Duper等で途中停止・穴あきがある場合は、推測で設定を広げず、最初のMismatch categoryと座標、Expected/actual countを共有する。

Unit Test、serialization、temporary filesystem、mock Future以外のゲーム内挙動は、この文書だけではVerifiedになりません。

## Logical Delta v2との関係

Runtime Capsule v6のEquivalenceは、復元後の検証契約です。Checkpointの保存側では、これとは別に`LOGICAL_DELTA`がExact Epochを作ります。通常tick中に作ったChunk candidateはmutation generationとcapture epochが一致した場合だけ採用し、変更されていないChunk/entity regionは認証済みparent viewをauthorityとして扱います。workerはimmutable NBT bytesとparent pathだけを受け取り、Live `ServerWorld`やVanilla queueを参照しません。

初回起動、再起動後にmutation baselineが失われた場合、candidate eviction、schedulerの未登録、Chunkの変更後unload、`poi/`・`data/`の未対応persistent fileなど、Exactを証明できない場合は、変更を推測してLogical Deltaを公開しません。`checkpoint.engine.fallback`へ移り、fallback自体も失敗した場合はcheckpointを確定しません。既定fallbackは`PORTABLE_GENERATION`です。

`CheckpointMirror`と`ScheduledTickMutationTracer`は、Chunk Ticket Manager、WorldTickScheduler、lighting queueを復元可能なsnapshotへ変換するものではありません。private queueを直接置換しないため、`UNSUPPORTED`は`MATCH`ではなくStrictのblocking候補です。`/ac debug mirror audit`で観測範囲とoverflowを確認し、`/ac debug checkpoint-performance last`でcandidate数・eviction・fallback理由を確認してください。

Entity unloadも同じ境界です。`ServerEntityManager`からのunload後に現在の`iterateEntities()`だけを見て親Entity regionを継承すると、unload直前のEntity stateを取り逃す可能性があります。現実装はunloadをmutation boundaryとして検出し、最終Entity regionをcaptureできない場合はLogical Deltaを確定せず、`PORTABLE_GENERATION`等へfallbackします。

## 再開直前のRuntime照合カテゴリ

最初のGame Tickの直前に、復旧用ticketを解除したうえでRuntime stateを取り直し、checkpointのfingerprintと比べます（`OPTIMIZED`でも行います）。上の表の`CHUNK_DOMAIN`、`SCHEDULED_TICKS`、`BLOCK_EVENTS`、`MOVING_PISTONS`、`BLOCK_ENTITY_TICKERS`、`REDSTONE_RUNTIME`、`WORLD_RUNTIME`に加えて、次を照合します。checkpointにはカテゴリごとの件数とdigestだけを保存するので、Entity数に比例してcapsuleが大きくなることはありません。

| Category | 内容 |
|---|---|
| `PISTON_RUNTIME` | 動作中ピストンの`progress` / `lastProgress` |
| `BREWING_RUNTIME` | 醸造中の残り時間と材料 |
| `END_GATEWAY_RUNTIME` | End Gatewayの`teleportCooldown` |
| `SCULK_RUNTIME` | スカルクセンサーの待機中の振動 |
| `PORTAL_ENTITY_RUNTIME` | ポータル転送待ちのEntity |
| `ENTITY_TRANSIENT_RUNTIME` | 無敵時間、再搭乗cooldown、`firstUpdate`、Item/オーブの`age`と`IdPhase` |
| `SHORT_LIVED_TICKETS` | 期限つきticketと残りtick |
| `DIMENSION_RUNTIME` | ディメンションの無人timer |
| `WEATHER_RUNTIME` | 天候の遷移値 |
| `CHUNK_RUNTIME` | 読み込み済みChunkのInhabitedTime |
| `ENTITY_ID_PHASE` | 次に生まれるEntityのnetwork id（40での余り） |
| `FAKEPLAYER_USE_RUNTIME` | FakePlayerの使用中アイテム、攻撃cooldown、Item cooldown |
| `CARPET_RULES` | Carpet本体と各拡張のruleのうち、組み込みのdefaultと違う値（`carpet.conf`由来と実行中の変更の両方） |
| `CONTAINER_SESSION` | 開いたままのコンテナ。復元できないため、あれば常に`UNSUPPORTED` |

結果は`MATCH` / `MISMATCH` / `UNSUPPORTED` / `NOT_APPLICABLE` / `MISSING_FROM_OLD_FORMAT` / `INCOMPLETE`のいずれかです。実機での検証結果は[RUNTIME-QUALIFICATION.md](RUNTIME-QUALIFICATION.md)にあります。

## v7で追加された状態と古いcheckpoint

Runtime Capsule v7は、動作中ピストンの進行度（照合用）、期限つきticketの残り時間、World無人timer、天候の遷移値、開いていたコンテナ（非対応として記録）、読み込み済みChunkのInhabitedTime、network idのcounter、照合カテゴリごとの件数とdigestを持ちます。v6以前のcheckpointにはこれらがありません。空値で埋めてMATCHにはせず、ピストンは`PISTON_RUNTIME: MISSING_FROM_OLD_FORMAT`としてDEGRADEDに表示し、ほかの新しいカテゴリは照合結果に`MISSING_FROM_OLD_FORMAT`と出ます。再開直前に出るカテゴリ: `PISTON_RUNTIME`、`BLOCK_ENTITY_TICKERS`、`ENTITIES`、`REDSTONE_RUNTIME`、`TICKET_RUNTIME`、`RUNTIME_ORDER`。
