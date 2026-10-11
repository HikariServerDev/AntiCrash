# Configuration

Userが編集するファイルは`config/anticrash.toml`だけです。内部Runtimeは`.anticrash/`へ分離されます。初回起動時に下記と同じ全キーと、各項目の用途・危険性を説明するコメントを生成します。値は実装の既定値です。

## 完全な既定ファイル

```toml
# AntiCrash 1.0.0 - complete user configuration
# Internal state is stored only under .anticrash/.
config_version = 1
language = "ja"

[checkpoint]
enabled = true
interval_seconds = 30
create_on_manual_command = true
create_on_clean_shutdown = true
shutdown_timeout_seconds = 5
retention_count = 20
retention_days = 7
retention_max_gb = 0
save_batch_chunks = 256

[checkpoint.performance]
target_barrier_ms = 15
warn_barrier_ms = 25
critical_barrier_ms = 50
warn_checkpoint_ms = 5000
rolling_enabled = true
rolling_budget_ms_per_tick = 1.0
rolling_max_budget_ms_per_tick = 3.0
adaptive_budget = true
warn_finalize_ms = 1000

[checkpoint.pipeline]
max_inflight_generations = 2

[checkpoint.incremental]
base_interval = 0
max_chain_depth = 0

[checkpoint.io]
background_threads = 1
max_mb_per_second = 0

[checkpoint.storage]
minimum_free_mb = 256

[checkpoint.durability]
mode = "SAFE"

[checkpoint.engine]
# LOGICAL_DELTA is the low-pause Exact-Epoch semantic path.
mode = "LOGICAL_DELTA"
# Used only when the logical cut cannot be proven exact.
fallback = "PORTABLE_GENERATION"
single_inflight = true
# true permits carrying POI/data files without a generic semantic capture.
allow_unsupported_persistent_state = false

[checkpoint.cache]
# Bounded server-thread ChunkSerializer candidate cache.
max_mb = 256
budget_ms_per_tick = 1.0

[checkpoint.mirror]
# Observation only. Vanilla queues/tickets are never replaced.
enabled = true
audit_interval_seconds = 300
trace_limit = 64

[commands]
help_permission_level = 4
status_permission_level = 4
start_permission_level = 0
start_force_permission_level = 4
freeze_permission_level = 4
checkpoint_permission_level = 4
recovery_permission_level = 4
fakeplayers_permission_level = 4
journal_permission_level = 4
config_permission_level = 4
debug_permission_level = 4

[snapshot]
backend = "AUTO"
verify_checksums = true
keep_crash_raw_world = true
crash_raw_retention_count = 10
io_threads = 0

[recovery]
policy = "AUTO"
freeze_after_restore = true
require_manual_start = true
start_command = "/ac start"
restore_latest_valid_checkpoint = true
allow_restore_older_checkpoint = true
fallback_to_previous_checkpoint = true
ask_timeout_seconds = 60
ask_default_action = "ABORT"
allow_start_with_failed_validation = false
spawn_restart_helper = false
temporary_ticket_release_ticks = 100
# ALL: every chunk loaded at the checkpoint, at its observed level
preload_mode = "ALL"
max_preload_chunks = 100000
chunk_ready_timeout_seconds = 120

[recovery.lock]
allow_login = true
allow_chat = true
allow_commands = true
allow_player_movement = true
allow_player_look = true
allow_sneak = true
allow_block_break = false
allow_block_place = false
allow_item_use = false
allow_entity_attack = false
allow_entity_interact = false
allow_container_interaction = false
allow_item_drop = false
allow_item_pickup = false

[player_snapshot]
enabled = true
dimension = true
position = true
rotation = true
gamemode = false
abilities = false
inventory_main = false
inventory_armor = false
inventory_offhand = false
selected_hotbar_slot = false
experience_level = false
experience_progress = false
experience_total = false
ender_chest = false
health = false
absorption = false
food_level = false
food_saturation = false
food_exhaustion = false
potion_effects = false
velocity = false
fall_distance = false
fire_ticks = false
air = false
spawn_point = false

[player_snapshot.vanilla_files]
restore_playerdata = false
restore_stats = false
restore_advancements = false

[fakeplayer]
enabled = true
auto_rejoin = true
restore_before_unfreeze = true
require_all_ready_before_start = true

[fakeplayer.snapshot]
dimension = true
position = true
rotation = true
gamemode = true
flying = true
inventory = true
selected_slot = true
health = true
hunger = true
effects = true
sneaking = true
sprinting = true
movement = true
actions = true
action_internal_timing = true
item_use_cooldown = true
block_break_progress = true

[login]
retry_timeout_seconds = 5

[notifications]
frozen_action_bar = true
checkpoint_action_bar_delay_ticks = 10
ask_prompt_in_game = true

[consistency]
mode = "IGNORE"

[logging]
level = "NORMAL"
console = true
file = true
max_file_mb = 50
retained_files = 10
show_checkpoint_contents = false
show_player_details = false
show_fakeplayer_details = true
show_block_events = false
show_file_operations = false
show_transaction_details = false
```

## 型と共通ルール

| 型 | 値 |
|---|---|
| boolean | `true` / `false` |
| integer | 0以上の整数。intervalとretention countは正数、command permission levelは0〜4 |
| string | quoted string。backend、policy、mode、levelは許可値のみ |

型として解析できる範囲外の値はwarningを出して安全な既定値へ戻します。一方、TOML構文エラー、unsafe file、未知の将来`config_version`など設定全体を信頼できない場合は既存ファイルを保存したまま`RECOVERY_FAILED`で停止し、defaultsだけで勝手に起動しません。`config_version`は内部形式と独立したUser Config versionです。現在schemaの省略キーはdefaultsで補いますが、利用者のコメントを守るためファイルを自動書換えしません。

## Language

| Key | Default | Values | Meaning |
|---|---|---|---|
| `language` | `ja` | `ja` / `en` | AntiCrash自身のコマンド応答、復旧メッセージ、checkpoint・snapshot・内部ログの表示言語 |

`ja`が既定値です。`/ac config reload`で設定を再読み込みすると、以後のAntiCrashメッセージへ反映されます。Minecraft本体、Fabric、Java、OS、外部Modが出力するログはこの設定の対象外です。

## Checkpoint / Snapshot

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `checkpoint.enabled` | `true` | bool | 定期checkpoint全体。falseでは手動も作らない |
| `checkpoint.interval_seconds` | `30` | int 1〜86400 | monotonic wall-clock間隔。短くするとIO/barrier頻度が上がる。例`60` |
| `checkpoint.create_on_manual_command` | `true` | bool | `/ac checkpoint`を受付 |
| `checkpoint.create_on_clean_shutdown` | `true` | bool | normal shutdown開始時のfinal checkpoint |
| `checkpoint.shutdown_timeout_seconds` | `5` | int >=0 | normal shutdown時に進行中またはfinal checkpointを待つ上限。超過時は未確定分を破棄して停止を続行。0は待機しない |
| `checkpoint.retention_count` | `20` | int >=1 | Valid checkpointの最大個数。最低1個を維持 |
| `checkpoint.retention_days` | `7` | int >=0 | 古いcheckpointの保持日数。0は無期限 |
| `checkpoint.retention_max_gb` | `0` | int >=0 | 容量上限。0は無制限、最新は誤削除しない |
| `checkpoint.save_batch_chunks` | `256` | int 1〜4096 | 切り出しの中でIO queueに載せてよいchunk保存の件数。通常は変更不要です。`PORTABLE_GENERATION`の切り出しはqueueに残った書き込みをqueueごと取り込むので、diskへの書き込みを待つ必要があるのはheapを守るときだけです。上限はheapに合わせて決まり（heapの1/4を1 chunkあたり512 KiBで割った件数。16 GiBで8,192、1 GiBで512）、heapの残りが少ないときはそれより早く書き出します。この値を**16未満**にした場合だけ、その件数ごとに書き込みを待ちます（同期書き込みでは1件ごとにfsyncが入るため、切り出しが長くなります）。性能計測行の`chunk_save` / `chunk_drain`と`counts={savedChunks, ioWaits}`で確認できます |
| `checkpoint.performance.target_barrier_ms` | `20` | int 1〜60000 | Logical Cutの目標値。超過しても途中中断しない |
| `checkpoint.performance.warn_barrier_ms` | `50` | int >=0 | barrier警告閾値 |
| `checkpoint.performance.critical_barrier_ms` | `100` | int >= warn | 重大遅延の診断閾値 |
| `checkpoint.performance.warn_checkpoint_ms` | `5000` | int >=0 | snapshot警告閾値 |
| `checkpoint.performance.rolling_budget_ms_per_tick` / `rolling_max_budget_ms_per_tick` | `1.0` / `3.0` | decimal | RUNNING中のoptional dirty-chunk準備予算。未完了分を除外しない |
| `checkpoint.pipeline.max_inflight_generations` | `2` | int 1〜8 | background finalizerのbackpressure上限 |
| `checkpoint.incremental.base_interval` / `max_chain_depth` | `0` / `0` | int 0〜10000 | BASE（World全体をcopyし、前の世代とfileを共有しない新しい系列）を作る世代間隔/最大chain深度。`0`は定期的に作らない。各世代は完全でchecksum付きのfile集合なので、BASEは同じWorldのcopyを1つ増やすだけで、19 GBのWorldでは1回ごとに19 GBを書き込みます |
| `checkpoint.io.background_threads` | `1` | int 1〜16 | manifest/commit/retention worker数 |
| `checkpoint.io.max_mb_per_second` | `0` | int >=0 | background finalizer throttle。portable detachをresume後へ移さない |
| `checkpoint.storage.minimum_free_mb` | `256` | int >=0 | seal前の最低空き容量 |
| `checkpoint.durability.mode` | `SAFE` | `SAFE` / `BALANCED` / `FAST` | force範囲。FASTは電源断耐性を下げる |

### Checkpoint engine / cache / mirror

| Key | Default | Type / Values | Meaning / safety boundary |
|---|---:|---|---|
| `checkpoint.engine.mode` | `LOGICAL_DELTA` | `LOGICAL_DELTA` / `PORTABLE_GENERATION` / `LEGACY_SAFE` | 通常のExact-Epoch経路。candidate不足・mutation uncertainty・未対応stateでは成功扱いせずfallback |
| `checkpoint.engine.fallback` | `PORTABLE_GENERATION` | `PORTABLE_GENERATION` / `LEGACY_SAFE` | Logical Deltaを証明できない場合の安全経路 |
| `checkpoint.engine.single_inflight` | `true` | bool | 複数のlogical materializationを重ねず、親viewの順序を維持 |
| `checkpoint.engine.allow_unsupported_persistent_state` | `false` | bool | `poi/`や`data/`にgeneric semantic captureできないregular fileがある場合の許可。trueは完全等価性を弱めるため、内容を理解した場合だけ使用 |
| `checkpoint.cache.max_mb` | `256` | int 1〜4096 | server thread candidate cacheのbounded heap。超過はevictionであり、変更なしの証拠にはならない |
| `checkpoint.cache.budget_ms_per_tick` | `1.0` | decimal 0〜20 | RUNNING中の`ChunkSerializer`候補作成予算。小さすぎるとcutで安全なfallbackが増える |
| `checkpoint.mirror.enabled` | `true` | bool | audit/diagnosticを有効化。Vanilla queue/ticketを置換しない |
| `checkpoint.mirror.audit_interval_seconds` | `300` | int 0〜86400 | mirror診断の最短audit間隔。0は毎回許可 |
| `checkpoint.mirror.trace_limit` | `64` | int 0〜1024 | scheduled mutation diagnosticのbounded件数。queueを保存する設定ではない |

Logical Deltaは親checkpointが必要です。初回または意図的な完全rootは`/ac checkpoint base`で作成します。`/ac checkpoint compact`は確定済みviewをLive Worldなしで新しいBASEへまとめるoffline operationです。`checkpoint.mirror`が`UNSUPPORTED`を返すのは1.18.2のprivate scheduler/ticket stateを安全に置換できないためで、`MATCH`と解釈してはいけません。

`LOGICAL_DELTA`のworkerはimmutable NBT bytes、file index、認証済みparentだけを使用します。candidate cacheのサイズを増やすことは正確性を改善しません。候補が間に合わない場合は低負荷を優先して黙って一部だけ保存せず、設定されたfallbackへ移ります。

## Commands

| Key | Default | Type / Values | Meaning / safety boundary |
|---|---:|---|---|
| `commands.help_permission_level` | `4` | int 0-4 | `/ac help`の必要権限レベル |
| `commands.status_permission_level` | `4` | int 0-4 | `/ac status`の必要権限レベル |
| `commands.start_permission_level` | `0` | int 0-4 | `/ac start`の必要権限レベル。既定0はOP不要 |
| `commands.start_force_permission_level` | `4` | int 0-4 | `/ac start --force`の必要権限レベル。安全のため既定4 |
| `commands.freeze_permission_level` | `4` | int 0-4 | `/ac freeze`の必要権限レベル |
| `commands.checkpoint_permission_level` | `4` | int 0-4 | checkpoint系（`checkpoint`、`list`、`inspect`）の必要権限レベル |
| `commands.recovery_permission_level` | `4` | int 0-4 | `recovery`系コマンドの必要権限レベル |
| `commands.fakeplayers_permission_level` | `4` | int 0-4 | `fakeplayers`系コマンドの必要権限レベル |
| `commands.journal_permission_level` | `4` | int 0-4 | `journal`系コマンドの必要権限レベル |
| `commands.config_permission_level` | `4` | int 0-4 | 設定reload系（`reload`、`config reload`）の必要権限レベル |
| `commands.debug_permission_level` | `4` | int 0-4 | `debug dump`の必要権限レベル |

## Snapshot

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `snapshot.backend` | `AUTO` | `AUTO` / `PORTABLE_GENERATION` / `JAVA_INCREMENTAL` / `REFLINK` / `BTRFS` / `CRIU_EXPERIMENTAL` | AUTO/PORTABLE_GENERATIONはportable seal。REFLINK/BTRFS/CRIUは明示LEGACY_SAFE |
| `snapshot.verify_checksums` | `true` | bool | manifest/checksumを検証。falseでは構造・path検証だけになり、内容破損を検出できない |
| `snapshot.keep_crash_raw_world` | `true` | bool | rollback前Worldを保存。前回のcrash-rawと内容が同じfileはhash照合のうえhard linkで共有し、変わったfileだけを新たに保存します |
| `snapshot.crash_raw_retention_count` | `10` | int >=1 | crash-raw保持個数 |
| `snapshot.io_threads` | `0` | int 0〜64 | 凍結中のpoint-in-time copyを行うthread数。処理はper-file statとhard link、つまりCPUではなくI/O latency待ちなので、SSDでは数threadで凍結時間が目に見えて縮みます。`0`は`min(4, コア数)`、`1`は従来の逐次copy。計測では10,175 fileのall-reused captureが1→125 ms、2→87 ms、4→75 ms、8→79 msでした |

## Recovery

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `recovery.policy` | `AUTO` | `AUTO` / `ASK` / `MANUAL` | AUTOはcleanで通常起動・dirty時だけpre-worldで自動復元。ASKは終了のたびにWorld load前に候補metadataを表示してY/N確認（異常終了後の無回答はY、通常の停止後の無回答はN。通常の停止後のYは停止時のcheckpointから再開）。MANUALはdirty時にpre-worldで明示判断を待ち、未解決ならWorldを開かない |
| `recovery.fidelity` | `OPTIMIZED` | `STRICT` / `OPTIMIZED` | STRICTはCheckpoint時点の実際のtick domainと観測可能なRuntime stateを比較し、Mismatch/UnsupportedでFail Closed。OPTIMIZEDは軽量な従来型best-effort復旧 |
| `recovery.freeze_after_restore` | `true` | bool | restore後にTickを凍結 |
| `recovery.require_manual_start` | `true` | bool | `/ac start`まで再開しない |
| `recovery.start_command` | `/ac start` | string | 表示用の再開案内 |
| `recovery.restore_latest_valid_checkpoint` | `true` | bool | AUTOで最新Validを選ぶ。falseならdirtyでもcheckpointを自動復元せず、現在のWorldを変更せず通常起動する |
| `recovery.allow_restore_older_checkpoint` | `true` | bool | 最新失敗時に旧Validを試す |
| `recovery.fallback_to_previous_checkpoint` | `true` | bool | restore I/O失敗時の旧checkpoint fallback |
| `recovery.ask_timeout_seconds` | `60` | int 0〜86400 | ASKのpre-world Y/N待機時間。設定値をそのまま使用します。0はtimeoutなし。dirty ASKの無回答はY、MANUALの無回答はWorld load拒否 |
| `recovery.ask_default_action` | `ABORT` | `RESTORE` / `CONTINUE` / `ABORT` | 旧設定との互換用。pre-world ASKのtimeout判定には使用しない |
| `recovery.allow_start_with_failed_validation` | `false` | bool | 復元済みWorldのRuntime/Chunk/FakePlayer等のsemantic validation失敗に限りforce startを許可。破損したconfig/state、永続化失敗、未完了restore、判断待ちは迂回不可 |
| `recovery.spawn_restart_helper` | `false` | bool | 復元予約後の自動再起動用に、detachedなrestart helperプロセスを起動するかどうか。helperは実行中プロセスから起動commandを再構成するため、start scriptの環境（wrapper、環境変数、classpath）を再現できません。既定の`false`では**プロセスを一切起動せず**、markerも書かずに停止します。復元予約は`recovery-state.json`に残るので、手動で起動すれば次回起動時に適用されます。Supervisorを前面起動している場合はhandshakeが優先されるため、この設定は無視されます |
| `recovery.temporary_ticket_release_ticks` | `100` | int 1〜1200000 | `/ac start`後にRecovery Ticketを解除するtick数 |
| `recovery.preload_mode` | `ALL` | `CRITICAL` / `ENTITY_TICKING` / `ALL` | 復旧開始前に準備するChunkの範囲。ALLはcheckpoint時にloadedだった全Chunkを当時のレベル（entity-ticking `31`、block-ticking `32`、loaded `33`）まで準備してから再開します。checkpoint直前にServerが常駐させていたのと同じ集合です。ENTITY_TICKINGはforced・anchorとentity-ticking集合だけ、CRITICALはforcedとPlayer/FakePlayer/Block Event・予定tick等の正確な位置だけを準備し、残りは再開後に順次読み込まれます（その間、Chunkをまたぐ装置は一部だけが動きます） |
| `recovery.max_preload_chunks` | `100000` | int 1〜100000 | 選択したpreload Chunkのハード上限。超過時はticketを追加せず復旧を失敗扱いにします |
| `recovery.chunk_ready_timeout_seconds` | `120` | int 0〜86400 | 必要Chunkの準備が**まったく進まなくなってから**この秒数を超えると、凍結を続けずrecoveryをfail closedにします。進捗がある限り発動しないため、大きなpreload計画でも途中で打ち切られません。`0`で無効（従来どおり無期限に待機） |
| `recovery.chunks.max_concurrent_loads` | `4` | int 1〜64 | Future-based Chunk loadを同時に保持する数。ピークメモリを制限するだけで、STRICTのExpected Chunk数は減らしません。Main Threadで`.join()`しません |
| `recovery.strict.allow_unsupported_runtime_state` | `false` | bool | `WORLD_RNG`など安全な完全restore APIがないRuntime stateを許可する明示設定。既定はSTRICT checkpoint/startを拒否 |
| `recovery.strict.allow_unsupported_carpet_state` | `false` | bool | Carpet capture/restore failure、time warp等のUnsupportedを許可する明示設定。既定はFail Closed |
| `recovery.strict.require_entity_order` | `true` | bool | EntityListの観測順、UUID/type/network IDを必須比較。内部Mapの直接置換は行わない |
| `recovery.strict.require_block_entity_ticker_order` | `true` | bool | current/pending BlockEntity tickerの意味的順序を必須比較 |
| `recovery.strict.require_ticket_ledger` | `true` | bool | Ticket type/level/argument/tickCreated/expiryを観測・比較。安全な丸ごと復元は行わず、非空ledgerは`UNSUPPORTED`として扱う。trueならblocking、falseは制限を受け入れる |
| `recovery.strict.players.require_checkpoint_players` | `false` | bool | trueならCheckpoint時のReal Player UUID/name/Dimensionが揃うまで`/ac start`を拒否。期待一覧は選択的な`player_snapshot.enabled`/`players.json`とは独立したRuntime Capsuleへ保存し、Inventory方針は変更しない |

必要ChunkはDimensionとpositionで重複排除します。`OPTIMIZED`はCRITICAL/ENTITY_TICKING/ALLの選択範囲を使い、STRICTはv6で保存したdomain rowをExpectedとして一つも切り捨てません。Vanilla 1.18.2のFULL level `33`、block-ticking `32`、entity-ticking/forced `31`は、`ServerChunkManager.addTicket`が受け取るradius `0`、`1`、`2`へ変換して追加・解除します。`max_concurrent_loads`件のFutureだけを保持し、server loopで完了をpollするため、同時数の制限は正確性ではなくピークメモリの制御です。STRICTのExpected数が`max_preload_chunks`を超えた場合はticketを追加せず、上限不足の理由を表示して凍結します。

### Strict設定の注意

STRICTは未対応状態を黙って無視しません。現行1.18.2ではJDK `java.util.Random`の内部state、EntityList内部Mapの安全な書換え、Chunk Ticket Managerの丸ごと復元、Carpet time warpの実行者/callback再接続をUnsupportedとして記録します。既定の`allow_unsupported_runtime_state=false`、`allow_unsupported_carpet_state=false`では、該当するcheckpointを完全扱いにせず、`/ac start`も許可しません。明示的に許可しても、破損・Mismatch・restore失敗・旧形式の欠落を許可する設定にはなりません。

ASK/MANUALでValid checkpointがない場合は、Crash時点のWorldを凍結し、`/ac recovery continue`または利用可能なcrash-rawへの`/ac recovery abort`を待ちます。AUTO dirtyで自動復元候補がない場合は開始しません。

## Recovery lock

| Key | Default | Type | Meaning / risk / example |
|---|---:|---|---|
| `recovery.lock.allow_login` | true | bool | falseならRecovery中の新規Loginを拒否 |
| `recovery.lock.allow_chat` | true | bool | 通常chatを許可 |
| `recovery.lock.allow_commands` | true | bool | network commandを許可。falseでもconsole経路は別 |
| `recovery.lock.allow_player_movement` | true | bool | movement packetを許可するがWorld Tickは進まない |
| `recovery.lock.allow_player_look` | true | bool | look packetを許可 |
| `recovery.lock.allow_sneak` | true | bool | sneak inputを許可 |
| `recovery.lock.allow_block_break` | false | bool | Block Break mutation。trueは復旧中Worldを変える |
| `recovery.lock.allow_block_place` | false | bool | Block Place mutation |
| `recovery.lock.allow_item_use` | false | bool | Item Use/Release |
| `recovery.lock.allow_entity_attack` | false | bool | Entity Attack |
| `recovery.lock.allow_entity_interact` | false | bool | Entity Interaction |
| `recovery.lock.allow_container_interaction` | false | bool | chest/container/craft等 |
| `recovery.lock.allow_item_drop` | false | bool | Item Drop |
| `recovery.lock.allow_item_pickup` | false | bool | PickupはWorld Tick停止中は発生しにくいが、明示制御用 |

## Real Player snapshot

`player_snapshot.enabled`がfalseなら全項目を保存・適用しません。下記はすべて個別booleanです。既定でtrueなのは`dimension`、`position`、`rotation`だけです。`gamemode`、`abilities`、`inventory_main`、`inventory_armor`、`inventory_offhand`、`selected_hotbar_slot`、`experience_level`、`experience_progress`、`experience_total`、`ender_chest`、`health`、`absorption`、`food_level`、`food_saturation`、`food_exhaustion`、`potion_effects`、`velocity`、`fall_distance`、`fire_ticks`、`air`、`spawn_point`は既定falseです。

| Key group | Default | Type | Meaning / risk / example |
|---|---:|---|---|
| `player_snapshot.dimension` / `position` / `rotation` | true | bool | 接続時に場所だけ戻す。例:外部Inventory Modを上書きせず位置を戻す |
| `player_snapshot.gamemode` / `abilities` | false | bool | GameMode/Abilityを戻す。権限や飛行状態に影響 |
| `player_snapshot.inventory_main` / `inventory_armor` / `inventory_offhand` / `selected_hotbar_slot` | false | bool | 各inventory領域を個別復元。外部同期とのdup/lossに注意 |
| `player_snapshot.experience_level` / `experience_progress` / `experience_total` | false | bool | XPを個別復元 |
| `player_snapshot.ender_chest` | false | bool | Ender Chestを復元 |
| `player_snapshot.health` / `absorption` | false | bool | Health/Absorptionを復元 |
| `player_snapshot.food_level` / `food_saturation` / `food_exhaustion` | false | bool | Hunger系を復元 |
| `player_snapshot.potion_effects` | false | bool | Effectsを復元 |
| `player_snapshot.velocity` / `fall_distance` / `fire_ticks` / `air` | false | bool | 移動・落下・火・空気状態 |
| `player_snapshot.spawn_point` | false | bool | Spawn位置/Dimension/angle/forced |
| `player_snapshot.vanilla_files.restore_playerdata` | false | bool | World `playerdata`を別snapshot。外部Player Dataと衝突し得る |
| `player_snapshot.vanilla_files.restore_stats` | false | bool | `stats`を別snapshot |
| `player_snapshot.vanilla_files.restore_advancements` | false | bool | `advancements`を別snapshot |

Inventory、Ender Chest、Potion、FakePlayer payloadで使う各NBTは任意root型を扱い、復元時のallocationを1要素16 MiBに制限します。上限超過や不正なNBTはlive Player/FakePlayerを変更する前にRecovery validation failureとして拒否します。

## FakePlayer snapshot

`fakeplayer.enabled`、Carpet検出、個別snapshot項目が有効な場合だけ処理します。FakePlayerはTMC装置の一部としてReal Playerと別の既定値です。

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `fakeplayer.enabled` | true | bool | FakePlayer機能全体 |
| `fakeplayer.auto_rejoin` | true | bool | 不在FakePlayerをCarpet factory/commandで再作成 |
| `fakeplayer.restore_before_unfreeze` | true | bool | Freeze解除前に復元 |
| `fakeplayer.require_all_ready_before_start` | true | bool | 1体でも失敗ならstart拒否 |
| `fakeplayer.snapshot.dimension` / `position` / `rotation` | true | bool | 場所・向き |
| `fakeplayer.snapshot.gamemode` / `flying` | true | bool | GameMode/flying |
| `fakeplayer.snapshot.inventory` / `selected_slot` | true | bool | inventoryとselected slot |
| `fakeplayer.snapshot.health` / `hunger` / `effects` | true | bool | Health、Food/Saturation/Exhaustion、Effects |
| `fakeplayer.snapshot.sneaking` / `sprinting` / `movement` | true | bool | sneak/sprint/forward/strafing |
| `fakeplayer.snapshot.actions` | true | bool | ActionPack、actions map、currentBlock等 |
| `fakeplayer.snapshot.action_internal_timing` | true | bool | `next`、`interval`、`limit`、`done`等内部phase |
| `fakeplayer.snapshot.item_use_cooldown` | true | bool | cooldownフィールド |
| `fakeplayer.snapshot.block_break_progress` | true | bool | block break progress/delay |

## Login retry

checkpoint barrierは1秒未満で終わりますが、その瞬間にhandshakeを終えたPlayerは従来そのままkickされていました。Vanillaは`READY_TO_ACCEPT`のときだけ`ServerLoginNetworkHandler.tick`から受け入れ処理を呼び、そこでstateを変えません。そのためtickを1回見送るだけで次のtickに再試行されます。Vanillaのslow-login timeout（600 tick）を数える処理は同じmethodの末尾にあるため、保留中は停止します。

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `login.retry_timeout_seconds` | `5` | int 0〜20 | AntiCrashがloginを拒否する状況（checkpoint barrier中、または`allow_login=false`のRecovery Freeze中）で、接続を切らずにこの秒数まで保留し、毎tick受け入れを再試行します。時間内に凍結が解ければそのまま参加できます。**再試行の対象はAntiCrashの拒否だけ**で、ban・whitelist・満員などVanillaの理由は保留せず即座に従来どおり処理します。`0`で従来の即kick。上限20はClientが30秒無通信の接続を切るためです |

## Notifications

AntiCrashはcheckpoint barrierとrecoveryでWorldを意図的に止めます。表示が何もないと、Playerには「Serverが応答しなくなった」ようにしか見えません。以下はその理由を伝えるためだけの設定で、freeze条件・復旧判断・checkpoint内容には一切影響しません。

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `notifications.frozen_action_bar` | `true` | bool | World凍結中、現在のphase（checkpoint作成中／必要Chunk n/m／`/ac start`待ち／復旧失敗など）をaction barへ表示します。凍結が解けると即座に消去します。FakePlayerへは送りません |
| `notifications.checkpoint_action_bar_delay_ticks` | `10` | int 0〜1200 | 旧設定との互換性のため読み込みますが、Checkpoint作成中のActionbarは常に非表示です。 |
| `notifications.ask_prompt_in_game` | `true` | bool | ASKの質問をconsoleだけでなく、回答権限（`commands.recovery_permission_level`）を持つPlayerへも表示します。復旧判断待ちのServerへ参加した時点でも表示するため、凍結理由が分からないまま放置されることを防ぎます |

## Consistency / Logging

| Key | Default | Type / Values | Meaning / risk / example |
|---|---:|---|---|
| `consistency.mode` | `IGNORE` | `IGNORE` / `WARN` / `JOURNAL` | 外部Player Dataとの時点差。WARNは非機密の最小recordを永続化、JOURNALは詳細JSONL、両者ともSPIへ通知 |
| `logging.level` | `NORMAL` | `OFF` / `IMPORTANT` / `NORMAL` / `VERBOSE` / `TRACE` | 出力粒度 |
| `logging.console` / `logging.file` | true | bool | console/file出力 |
| `logging.console_color` | `AUTO` | `AUTO` / `ON` / `OFF` | Server consoleの色分け。AUTOは出力先が端末のときだけ色を付ける。`latest.log`と`anticrash.log`には色コードを書かない |
| `logging.chat_color` | `true` | bool | ゲーム内のコマンド返答・案内の色分け |
| `logging.max_file_mb` | 50 | int 1〜10240 | active logのrotation閾値 |
| `logging.retained_files` | 10 | int 1〜1000 | rotated log保持数。過大値によるrotation loopを拒否 |
| `logging.show_checkpoint_contents` | false | bool | checkpoint内容を詳細表示 |
| `logging.show_player_details` | false | bool | Real Player項目を詳細表示 |
| `logging.show_fakeplayer_details` | true | bool | FakePlayer概要を表示 |
| `logging.show_block_events` | false | bool | Block Eventを詳細表示 |
| `logging.show_file_operations` | false | bool | copy/hash/commit操作を表示 |
| `logging.show_transaction_details` | false | bool | Transaction詳細を表示 |

設定を変更した後は`/ac config reload`を実行できます。reloadの解析・検証が失敗した場合は変更を一切適用せず、現在の有効な設定を維持します。成功時は接続中Playerへコマンドtreeを再送し、権限とTab補完を更新します。Recovery中の安全判定に関わる変更は、次回起動または次の明確なstate boundaryで確認してください。

## Checkpoint engine settings

`[checkpoint.performance]`の`target_barrier_ms`は目標であり、正確性のためのhard deadlineではありません。`rolling_budget_ms_per_tick`は通常tick中の準備量だけを制限し、未準備chunkをcheckpointから除外しません。`[checkpoint.pipeline]`と`[checkpoint.io]`はfinalizerのbackpressureとworker数を制御しますが、1 GiB heapでは`background_threads = 1`を推奨します。

`[checkpoint.incremental]`のBASE/DELTAは認証済みparent chainを管理します。portable generationは各世代を完全なmaterialized viewとして保存し、`base_interval`または`max_chain_depth`（既定`0`は無効）を設定した場合に次の世代をBASEへ戻します。`[checkpoint.durability] mode = "FAST"`は電源断時に世代を失う可能性があるため、通常は`SAFE`を使用してください。詳細なEpoch/Fence、file-set、retention、計測は[CHECKPOINT-ENGINE.md](CHECKPOINT-ENGINE.md)と[CHECKPOINT-PERFORMANCE.md](CHECKPOINT-PERFORMANCE.md)にあります。

## セキュリティ警告とファイル権限

新規設定・内部state・restart marker・AntiCrashログはPOSIX filesystemで`0600`、新規`.anticrash/`ディレクトリは`0700`です。既存設定にgroup/other writeがある場合、または既存`.anticrash/`にgroup/other accessがある場合は警告しますが、共有運用との互換性のため既存permissionを自動変更しません。

次の明示設定は読み込みを拒否せず、利用者の選択を維持したまま起動時/reload時に警告します。

- `snapshot.verify_checksums = false`
- `commands.start_force`、`freeze`、`checkpoint`、`recovery`、`fakeplayers`、`config`、`debug`カテゴリのpermission level `0`
- 復旧凍結中のBlock/Item/Entity/Container/Drop/Pickup変更許可

TOML構文、将来version、symlink等で設定全体を信頼できない場合は従来どおりFail-closedです。警告だけの設定と、読み込みを拒否する破損状態を区別してください。
