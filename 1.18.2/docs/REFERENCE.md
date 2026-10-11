# AntiCrash 1.0.0 — 技術リファレンス（旧README）

Checkpoint作成のincident修正・検証範囲は[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)を参照してください。
OOM/親破損時は自動再試行を停止し、`/ac status`に原因を表示します。確認後、独立した保存は`/ac checkpoint base`で要求できます。
POI/PersistentStateがある場合の低停止時間pathは未対応で、安全なfile-backed fallbackを使用します。1GBで全Worldの動作を保証するものではありません。

Minecraft Java Edition 1.18.2向けのFabric Server Modです。Technical Minecraft（TMC）の装置を解析・修理するのではなく、正常なTick境界のWorldと必要なRuntime Stateをcheckpointとして保存し、異常終了後にその時点へ安全に戻します。

作者: AtsukiMC

## なぜ必要か

Piston、Moving Piston、Block Event、Scheduled Tick、Entity、Chunk、Portal、TNT、FallingBlock、Redstone、Carpet FakePlayerなどは、正しい順序とタイミングで進むことを前提にしています。JVM crash、Watchdog、OOM、kill、電源断などで途中状態だけが失われると、見た目が正常でも装置の次の動作を失うことがあります。

AntiCrashは「普通のVanillaから外れている状態」を修正しません。Headless Piston、Duper、特殊なPiston State、Update Order依存なども含め、checkpoint時点の状態をそのまま復元することを目的にします。約30秒のrollbackを、装置を推測修理するより安全な代償として扱います。

## 安全モデル

```text
Tick Nを完走
  -> Checkpoint Barrier
  -> Runtime / Player / FakePlayerを取得
  -> LOGICAL_DELTA: server threadで変更Chunk/Entityのimmutable NBT候補だけをExact Epochで確定
  -> Logical Cut: 親checkpointのimmutable viewへ論理差分を適用するhandoffをseal
  -> CHECKPOINT_SEALED -> Barrier解除 -> Tick N+1
  -> workerはLive Worldを読まず、親viewをcopy-on-write materializeし、Runtime JSON + manifest + checksum + READYを作成
  -> cp-XXXXXX.tmpをcp-XXXXXXへatomic renameして確定

候補不足、mutation observerの不確実性、未対応persistent stateなどで論理差分がExactにならない場合
  -> CHECKPOINT_BARRIERへ戻る
  -> bounded dirty Chunk save + Vanilla flush
  -> 設定されたPORTABLE_GENERATION/LEGACY_SAFEへ安全にfallback
```

既定の`LOGICAL_DELTA`は、毎回World全体をserver threadで走査・copyしません。通常tick中に`ChunkSerializer`で候補を少量ずつ作り、候補のmutation generationがcut時点と一致した場合だけimmutable bytesとして採用します。workerへMinecraft object、NBT object、queue、Live World pathは渡しません。親checkpointの変更されていないregular fileはcheckpoint間のhard link/copyで再利用し、変更先は必ずcopy-on-writeしてからRegionFileへ反映します。

初回は親となる完全なcheckpointが必要です。`/ac checkpoint base`で明示的にBASEを作成してください。論理差分の候補が追いつかない場合は、安全なfallbackへ移るため、低停止時間よりExact Epochの安全性を優先してcaptureを拒否・遅延します。PortableなfilesystemだけでEpoch Nを守るため、Worldを再開してからmutable fileを読む処理は行いません。

異常終了時は、破損・未完了checkpointを使わず、検証済みcheckpointのみを対象にします。RestoreはMinecraftがWorldを開く前にstagingしてから行います。復旧後は必要ChunkとFakePlayerを準備し、`/ac start`までWorld/Entity/Block Tickを止めます。

## 主な機能

- monotonic wall-clock基準の既定30秒checkpoint
- `PORTABLE_GENERATION`（`AUTO`の標準）、`JAVA_INCREMENTAL`、`REFLINK`、`BTRFS`、明示指定用`CRIU_EXPERIMENTAL`
- `LOGICAL_DELTA`（既定の低停止時間Exact-Epoch engine）、bounded candidate cache、offline compaction
- Exact-EpochのChunk/Entity mutation hookとscheduled-tick audit。未対応・観測不確実な状態は安全にfallback
- `.tmp`、manifest、SHA-256、READY、fsync、atomic commit
- dirty/clean run marker、AUTO/ASK/MANUAL recovery
- Recovery Freezeと最初のWorld Tick前のpre-world-load restore
- Block Event queue、Checkpoint時点のloaded/FULL/block-ticking/entity-ticking/forced chunk domain、一時状態の正確なchunk位置を持つRuntime Capsule
- moving piston、chunk-local scheduled tick、primed TNT、falling blockを欠落・部分再開させない保存と先行復旧
- Observer/Redstoneの同一tick順序を含むblock/fluid予定tickと、moving pistonのBlockEntity tick順序の厳密な保存・復元
- `STRICT` Recovery Equivalence（tick domain・Runtime fingerprint・直前再検証・Fail Closed）と、軽量な`OPTIMIZED` Recovery
- Real Playerは既定でDimension/Position/Rotationのみ。Inventory、XP、Health、Hunger等は個別設定
- Carpetがある場合のFakePlayerと、選択したActionPack内部値（`next`、`interval`、`currentBlock`等）の厳格な保存・復元
- `IGNORE` / `WARN` / `JOURNAL` と `ExternalPlayerDataBridge`
- `OFF`〜`TRACE`のconsole/file loggingとrotation
- AntiCrash独自のコマンド・復旧・checkpointログの日本語/英語切り替え（`language = "ja"`、既定は日本語）
- 管理者用`/ac` command
- 復元予約・OOM・異常終了を同じconsoleで監視する、Java 17 Supervisor（標準起動）

詳細は[アーキテクチャ](ARCHITECTURE.md)、[Checkpoint Engine](CHECKPOINT-ENGINE.md)、[Consistency](CHECKPOINT-CONSISTENCY.md)、[Performance](CHECKPOINT-PERFORMANCE.md)、[設定](CONFIGURATION.md)、[起動前復旧](STARTUP-RECOVERY.md)、[復旧手順](RECOVERY.md)、[Recovery Equivalence](RECOVERY-EQUIVALENCE.md)を参照してください。

## 導入

1. Minecraft 1.18.2、Fabric Loader 0.17.3以上、Java 17を用意します。
2. `build/libs/AntiCrash-1.0.0.jar`を、利用者が管理する対象Serverの`mods/`へコピーします。
3. 初回起動後に生成された`config/anticrash.toml`を確認します。AntiCrash独自メッセージは`language = "ja"`（`ja`/`en`、既定は`ja`）で切り替えられます。
4. Carpetを使う場合だけ、対応する1.18.2版（基準: Fabric Carpet 1.4.69）も同じServerへ導入します。
5. `supervisor/build/libs/AntiCrash-Supervisor-1.0.0.jar`をServer rootへ置き、付属または同等の`start.sh`からSupervisorを前面起動します。これにより管理ツールがSupervisorの1 PID・1 consoleだけを監視し、Minecraftのstdin/stdout/stderrを同じconsoleへ引き継ぎます。

標準起動の例（`SERVER_ROOT`は実際のServer rootへ置き換えます）。Supervisor JARは`start.sh`と同じディレクトリに置いてください。

```bash
exec taskset -c 0-18 java \
  -jar "$SERVER_ROOT/AntiCrash-Supervisor-1.0.0.jar" \
  --workdir "$SERVER_ROOT" --language ja \
  --max-restarts 3 --restart-delay-ms 5000 --stable-runtime-ms 60000 \
  -- java -Xms1G -Xmx1G -jar "$SERVER_ROOT/fabric-server-launch.jar" nogui
```

`--language en`にするとSupervisor自身の状態ログも英語になります。`-Xms1G -Xmx1G`は変更せず、OOM対策は復旧Chunkの選択範囲とticket指定の修正で行います。

`start.sh`はAntiCrash Modの必須APIではなく、上記のSupervisor起動を簡単にする入口です。Minecraftを直接起動してもcheckpoint、復旧、`/ac start`は動作します。ただし、管理ツールから同じPID・同じconsoleを監視し、復元後の再起動、OOM、異常終了を同じウィンドウで扱う要件では、Supervisorを前面プロセスとして起動してください。`start.sh`を使わない場合も、上記の`java -jar AntiCrash-Supervisor... -- java ...`を管理ツールの起動コマンドに設定すれば同じ動作になります。

AntiCrashはVelocity、別Server、MySQLへ接続せず、導入された一つのMinecraft Serverだけを対象にします。実Serverへのコピー・起動・停止・World Restoreはこの実装作業では行っていません。

## データ配置

```text
server-root/
├─ config/anticrash.toml              # User Configだけ
├─ .anticrash/                        # AntiCrash内部State
│  ├─ state/run-state.json
│  ├─ state/recovery-state.json
│  ├─ state/restart-request            # controlled restart時だけ存在する一時marker
│  ├─ state/debug-dump.json
│  ├─ checkpoints/cp-000001/
│  ├─ crash-raw/<run-id>/world/
│  ├─ journal/transactions-<run-id>.jsonl
│  └─ logs/anticrash.log*
└─ world/                             # 実Serverが選択したlevel-name
```

User Configへrun id、clean flag、Recovery State、journal position、FakePlayer Runtimeを保存しません。Worldの`playerdata`、`stats`、`advancements`は既定でsnapshot対象外です。必要な場合だけ`[player_snapshot.vanilla_files]`を有効化します。

## 設定（`config/anticrash.toml`）

AntiCrashが利用者向けに読み書きする設定ファイルは`config/anticrash.toml`だけです。初回起動時に全キーを含むテンプレートが生成され、内部状態、run marker、checkpoint、journal、ログは`.anticrash/`へ保存されます。設定ファイルを削除して再生成しても、`.anticrash/`のcheckpointや復旧状態は削除されません。

### 最小限の設定例

```toml
# AntiCrash独自メッセージ。ja（既定）または en
language = "ja"

[checkpoint]
enabled = true
interval_seconds = 30
create_on_manual_command = true
create_on_clean_shutdown = true
# normal shutdownでcheckpointを待つ上限。0は待機なし
shutdown_timeout_seconds = 5

[checkpoint.engine]
# LOGICAL_DELTA: 通常checkpointの低停止時間Exact-Epoch経路
mode = "LOGICAL_DELTA"
# Exactでない場合の安全なfallback
fallback = "PORTABLE_GENERATION"
single_inflight = true
allow_unsupported_persistent_state = false

[checkpoint.cache]
# server threadで作るimmutable Chunk NBT候補の上限
max_mb = 256
budget_ms_per_tick = 1.0

[checkpoint.mirror]
# Vanilla queue/ticketを置換しない観測専用diagnostic
enabled = true
audit_interval_seconds = 300
trace_limit = 64

[commands]
# Minecraft permission level: 0=全員、4=OP相当
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
# AUTO: portable generation seal。BTRFS/REFLINKは明示指定のLEGACY_SAFE
backend = "AUTO"
verify_checksums = true

[recovery]
# dirty exit時に最新Valid checkpointへ自動復元
policy = "AUTO"
# STRICT: checkpoint時点のtick domainと観測可能なRuntime stateを再構築してから再開
# OPTIMIZED: 軽量な従来型best-effort recovery（既定）
fidelity = "OPTIMIZED"
require_manual_start = true
# checkpoint時にloadedだった全Chunkを、当時のレベル（entity/block ticking・loaded）まで
# 準備してから再開する。複数Chunkにまたがる装置が一部だけ先に動き出すのを防ぐ
preload_mode = "ALL"
max_preload_chunks = 100000
chunk_ready_timeout_seconds = 120

[recovery.chunks]
# STRICTでもExpected Chunk数は減らさず、同時Future数だけを制限
max_concurrent_loads = 4

[recovery.strict]
# 1.18.2で安全に完全復元できない状態は既定でcheckpoint/startを止める
allow_unsupported_runtime_state = false
allow_unsupported_carpet_state = false
require_entity_order = true
require_block_entity_ticker_order = true
require_ticket_ledger = true

[recovery.strict.players]
# trueならcheckpoint時に存在したReal Player全員の再接続を待つ
require_checkpoint_players = false

[login]
# checkpoint中のloginを即kickせず、5秒まで保留して再試行（AntiCrashの拒否のみ）
retry_timeout_seconds = 5

[notifications]
# 凍結中の理由をaction barで通知し、ASKの質問を管理者へも表示
frozen_action_bar = true
ask_prompt_in_game = true

[logging]
level = "NORMAL"
console = true
file = true
```

値を省略した場合は実装の既定値が使われます。型として読める範囲外の値はwarningを出して安全側の既定値へ戻します。構文エラー、unsafe file、未知の将来`config_version`など設定全体を安全に読めない場合は、既存ファイルを上書きせず起動を`RECOVERY_FAILED`で停止します。reload失敗時は現在適用中の設定を維持します。`language`は`ja`または`en`のみで、既定値は`ja`です。現在のschemaで省略キーがあっても利用者のコメントを保つため自動書換えはせず、実際の旧schema migration時だけ検証成功後に更新します。

`fidelity = "STRICT"`を選ぶと、checkpoint作成時にも未対応状態（現在の1.18.2では、例えば`java.util.Random`の内部state）がある場合は、記録だけして完全checkpointとして公開せずFail Closedします。これを承知でRuntimeカテゴリを許可する場合だけ`allow_unsupported_runtime_state = true`を設定してください。Carpetのcapture/restore失敗、EntityList・BlockEntity tickerの順序を必須にするか、Ticket ledgerの観測を必須にするかは`[recovery.strict]`で個別に指定できます。許可はMismatchや破損を無視する`--force`ではなく、明示した`UNSUPPORTED`観測だけを対象にします。

### 設定項目の詳細

#### 言語

| キー | 既定値 | 説明 |
|---|---:|---|
| `language` | `ja` | AntiCrash独自のコマンド応答、checkpoint、復旧、snapshot、内部ログの言語。`ja` / `en`。Minecraft本体、Fabric、Java、OS、他Modのログは変わりません。 |

#### コマンド権限

Minecraftのpermission level（`0`〜`4`）をコマンドカテゴリごとに設定できます。`0`はOPでなくても実行でき、`4`は従来どおりOP level 4相当です。既定では復旧開始に必要な`/ac start`だけが`0`で、その他は`4`です。

| キー | 既定値 | 対象 |
|---|---:|---|
| `commands.help_permission_level` | `4` | `/ac help` |
| `commands.status_permission_level` | `4` | `/ac status` |
| `commands.start_permission_level` | `0` | `/ac start`。復旧後の通常startのみ |
| `commands.start_force_permission_level` | `4` | `/ac start --force`。validation失敗時の強制開始なので既定ではOP専用 |
| `commands.freeze_permission_level` | `4` | `/ac freeze` |
| `commands.checkpoint_permission_level` | `4` | checkpoint系、`/ac list`、`/ac inspect` |
| `commands.recovery_permission_level` | `4` | `/ac recovery ...`。`/ac recovery start`を含む |
| `commands.fakeplayers_permission_level` | `4` | `/ac fakeplayers ...` |
| `commands.journal_permission_level` | `4` | `/ac journal ...` |
| `commands.config_permission_level` | `4` | `/ac reload`、`/ac config reload` |
| `commands.debug_permission_level` | `4` | `/ac debug dump` |

設定値は`0`〜`4`だけが有効です。範囲外はwarningを出し、各項目の既定値へ戻します。`/ac start`を一般プレイヤーへ開放しても、復旧が開始可能な状態でなければ実行は拒否されます。`start_force_permission_level`などWorld・復旧状態を変更できるカテゴリを`0`へ下げると、起動時とreload時にセキュリティ警告を出します。`/ac start --force`には別途`recovery.allow_start_with_failed_validation = true`も必要です。

#### CheckpointとSnapshot

| キー | 既定値 | 説明・注意 |
|---|---:|---|
| `checkpoint.enabled` | `true` | 定期checkpoint全体。`false`にすると手動checkpointも作成しません。 |
| `checkpoint.interval_seconds` | `30` | `1`〜`86400`。GameTickではなくmonotonic wall-clockで定期実行します。短くすると保存頻度とI/O負荷が増えます。 |
| `checkpoint.create_on_manual_command` | `true` | `/ac checkpoint`を受け付けます。checkpoint中の要求は安全な次回境界まで保留されます。 |
| `checkpoint.create_on_clean_shutdown` | `true` | `/stop`など正常停止の開始時にfinal checkpointを試みます。次回の正常起動でrollbackは行いません。 |
| `checkpoint.shutdown_timeout_seconds` | `5` | 正常停止時に進行中またはfinal checkpointを待つ秒数。超過時は未確定処理を破棄してVanilla停止を続行します。`0`は待機しません。Watchdog停止では待機しません。 |
| `checkpoint.retention_count` | `20` | 完全なcheckpointディレクトリの最大個数。最新checkpointは削除しません。 |
| `checkpoint.retention_days` | `7` | 保存日数。`0`は日数による削除なし。 |
| `checkpoint.retention_max_gb` | `0` | checkpoint容量上限。`0`は無制限。最新checkpointを優先します。 |
| `checkpoint.save_batch_chunks` | `256` | 通常は変更不要。切り出しがIO queueに載せるchunk保存の上限はheapに合わせて決まり、16未満にした場合だけその件数ごとにdiskへの書き込みを待ちます。 |
| `checkpoint.performance.target_barrier_ms` | `20` | Logical Cut/Barrierの目標値。超過しても正確性のため途中中断しません。 |
| `checkpoint.performance.warn_barrier_ms` | `50` | barrierにこの時間以上かかった場合の警告閾値。処理を強制中断する値ではありません。 |
| `checkpoint.performance.critical_barrier_ms` | `100` | 重大な遅延として記録する閾値。Fail Closedを解除する値ではありません。 |
| `checkpoint.performance.warn_checkpoint_ms` | `5000` | snapshotにこの時間以上かかった場合の警告閾値。 |
| `checkpoint.performance.rolling_budget_ms_per_tick` / `rolling_max_budget_ms_per_tick` | `1.0` / `3.0` | 通常tick中に行うdirty chunk準備の予算。準備はcorrectness判定ではなく、未完了分をLogical Cutへ残します。 |
| `checkpoint.pipeline.max_inflight_generations` | `2` | Finalizerが詰まったときに新しい世代を無制限に作らない上限。現在のServer-thread pipelineは安全側に1世代ずつsealします。 |
| `checkpoint.incremental.base_interval` / `max_chain_depth` | `20` / `32` | ポータブル世代の親chainを定期的にBASEへ戻す基準。DELTAも完全materialized viewとして保存されます。BASEはLive Worldを凍結中に全copyせず、DELTAと同じ短い凍結で切り出した後、workerが検証済みの親checkpointからhard linkではなくcopyで作ります。 |
| `checkpoint.io.background_threads` | `1` | Runtime/Manifest/commitを処理するdaemon worker数。1 GiB heapでは1を推奨します。 |
| `checkpoint.io.max_mb_per_second` | `0` | 予約済みの最終化I/O throttle（0=無制限）。Portable pathで凍結中に行うのは変更fileのcopyだけで、この値でBarrier作業を遅くしません。 |
| `checkpoint.storage.minimum_free_mb` | `256` | seal前に確認するcheckpoint保存先の最低空き容量。不足時はCheckpointを公開せずFail Closedします。 |
| `checkpoint.durability.mode` | `SAFE` | `SAFE`はpayloadとcontrol metadataをforce。`BALANCED`はdirectory durabilityを一部省略、`FAST`はcontrol metadataのみforceするため、電源断時に世代が失われる可能性があります。破損はREADY検証で使用拒否します。 |
| `snapshot.backend` | `AUTO` | `AUTO`はportable generation sealを使用します。`BTRFS`/`REFLINK`/`CRIU_EXPERIMENTAL`は内部状態を完全に切り離す契約を持たない明示LEGACY_SAFE経路です。 |
| `snapshot.verify_checksums` | `true` | READY、checksum、manifest、各payloadを検証します。`false`は破損検出を弱めるため、通常は変更しません。 |
| `snapshot.keep_crash_raw_world` | `true` | dirty exit前のLive Worldをcrash-rawとして保持します。復旧調査に有用ですが容量を消費します。 |
| `snapshot.crash_raw_retention_count` | `10` | 保持するcrash-raw Worldの個数。 |
| `snapshot.io_threads` | `0` | 凍結後のworkerが変更fileのhash、親checkpointからのhard link・copyに使うthread数。`0`は2です。凍結中の変更file copyは常に最大4 threadで行います。 |

既定のLogical Deltaでは、Live Worldの全filesystemを凍結中に走査せず、通常tick中に作ったChunk candidateとmutation generationをExact Epochで検証します。変更Chunk・Entity regionだけをimmutable payloadとしてsealし、workerが認証済みparent viewへcopy-on-writeで適用します。`poi/`・`data/`のgeneric persistent file判定だけはbarrier前のpreflightで行い、対応できない場合は黙って古いfileを継承せずfallbackします。

Portable generationはLogical DeltaがExactにならない場合の安全なfallbackです。Minecraft 1.18.2の`flush=true`は全accessible ChunkHolderのfutureを1 server tick内でjoinし、dirty ChunkのNBTを一度に生成するため、低空きheapではWatchdog停止やcheckpoint固有のメモリ急増を起こします。AntiCrashはfallback時、保存境界を固定した後にdirty Chunkを設定batchずつ直列化し、batchごとにChunk IO queueが空になるまで次のserver tickへ処理を返します。未完了ChunkHolderとdirty backlogがなくなってからVanillaの最終flushを行い、seal journalと独立file setを確定してから通常tickを再開します。Runtime JSON、manifest、checksum、READY、retentionはその後のworkerで行うため、1 GiB設定でも最終化処理の同時メモリを抑えます。

Portable Generationの凍結中に行うのは、Vanillaと同じ保存（fsyncと全ChunkHolderの待機を除く）、Chunk・Entity・POIのIO queueの書き込み完了待ち、変更fileのcopyだけです。hash計算、変更のないfileのhard link、BASEのcopyはWorld再開後にworkerが検証済みの親checkpointだけを読んで行います。凍結中には全fileのsize・mtime・ctimeを親checkpointの記録と比較し、さらに`RegionBasedStorage#write`の記録（Chunk・Entity・POIの書き込み）と照合します。どちらか一方でも変更を示したfileはcopyし、両方が未変更を示したfileだけを親から引き継ぎます。切り出しは必ず1回の呼び出しで完結し、途中で他Modのtick処理は動きません。Entityの保存はRuntime Capsuleより前に行い、Capsuleとfileが同じ状態を指します。

変更のないfileは、検証済み前回checkpointのsize・mtime・ctime、checkpoint間Hardlinkのリンク数、またはSHA-256照合を使ってcheckpoint間で再利用します。Live Worldとcheckpointを直接Hardlinkすることはありません。各世代の`generation-seal.json`はLogical Cut時点のPRESENT/ABSENT/TOMBSTONE、BASE/DELTA、parent、cut tickを記録します。保持整理は親chainを先に解析し、読めない依存関係がある場合は削除を行いません。外部変更が検出されたfileは高速経路を使わず、SHA-256照合または新規copyへ切り替えます。

#### Recovery

| キー | 既定値 | 説明・注意 |
|---|---:|---|
| `recovery.policy` | `AUTO` | `AUTO`はcleanで現在のWorld、dirty時だけpre-worldで自動復元します。`ASK`は終了のたびにWorld load前に候補metadataを表示してY/N確認します（異常終了後の無回答はY、通常の停止後の無回答はN。通常の停止後のYは停止時のcheckpointから再開し、動作中だった装置が続きます）。`MANUAL`はdirty時にpre-worldで明示判断を待ちます。 |
| `recovery.fidelity` | `OPTIMIZED` | `STRICT`はCheckpoint時点の実際のtick domainと観測可能なRuntime stateの等価性を検証し、不一致・未対応状態でFail Closed。`OPTIMIZED`は従来の軽量best-effort復旧です。 |
| `recovery.freeze_after_restore` | `true` | 復元後もWorld Tickを凍結し、検証完了まで再開しません。 |
| `recovery.require_manual_start` | `true` | `/ac start`まで復旧後のWorldを開始しません。TMCでは安全確認のため推奨です。 |
| `recovery.start_command` | `/ac start` | 管理者へ表示する再開コマンド。実際の権限制御は変更しません。 |
| `recovery.restore_latest_valid_checkpoint` | `true` | `AUTO`で最新の整合性検証済みcheckpointを選びます。falseならdirtyでもcheckpointを自動復元せず、現在のWorldを変更せず通常起動します。 |
| `recovery.allow_restore_older_checkpoint` | `true` | 最新checkpointの復元に失敗した場合、古いValid checkpointを試します。 |
| `recovery.fallback_to_previous_checkpoint` | `true` | restore I/O失敗時に旧checkpointへfallbackします。 |
| `recovery.ask_timeout_seconds` | `60` | `ASK`のY/N回答を待つ秒数。設定値をそのまま使用します。`0`はtimeoutなし。通常の停止後の起動も、無回答ならこの秒数だけ待ってから通常起動します。 |
| `recovery.ask_default_action` | `ABORT` | 旧設定との互換用。pre-world ASKのtimeout判定には使用せず、dirty ASKはY、MANUALは未解決として扱います。 |
| `recovery.allow_start_with_failed_validation` | `false` | World復元後のRuntime/Chunk/FakePlayer等のsemantic validation失敗に限り`--force` startを許可します。破損した設定・内部state、永続化失敗、未完了のpre-world restore、復旧判断待ちは迂回できません。 |
| `recovery.spawn_restart_helper` | `false` | 復元予約後にrestart helperプロセスを起動するかどうか。既定の`false`ではプロセスを起動せず、停止後に手動で起動します（復元は次回起動時に適用）。Supervisor前面起動時はこの設定に関係なく自動再起動します。 |
| `recovery.temporary_ticket_release_ticks` | `100` | `/ac start`後に復旧用chunk ticketを解除するまでのtick数。 |
| `recovery.preload_mode` | `CRITICAL` | 復旧前に準備するChunk範囲。CRITICALはforced、正確なPlayer/FakePlayer/Block Event位置、checkpoint境界でmoving piston・予定tick・primed TNT・falling blockを持つChunkを対象にします。moving piston・scheduled tickの所在Chunkは、Checkpoint時に観測したtickレベル（entity-ticking/block-ticking/FULL）で準備し、それより上げません。ENTITY_TICKINGはentity-ticking集合も、ALLはloaded集合も加えます。 |
| `recovery.max_preload_chunks` | `256` | 選択したpreload Chunkのハード上限（1〜100000）。超過時はticketを追加せず復旧を止めます。大きくするとOOMリスクが上がります。 |
| `recovery.chunk_ready_timeout_seconds` | `120` | 必要Chunkの準備がまったく進まなくなってからこの秒数を超えると、凍結を続けずrecoveryをfail closedにします。進捗がある限り発動しません。`0`で無効。 |
| `recovery.chunks.max_concurrent_loads` | `4` | Future-based Chunk loadの同時実行数。STRICTのExpected Chunk数や正確性を減らさず、ピークメモリだけを制限します（1〜64）。Main ThreadでFutureを`join`しません。 |
| `recovery.strict.allow_unsupported_runtime_state` | `false` | `WORLD_RNG`など安全にrestoreできないRuntime observationを許可する明示設定。既定はSTRICT checkpoint/startを止めます。 |
| `recovery.strict.allow_unsupported_carpet_state` | `false` | Carpet capture/restore不能、time warpなどを許可する明示設定。既定はFail Closedです。 |
| `recovery.strict.require_entity_order` | `true` | EntityListのUUID/type/network ID/観測順の不一致・未対応をSTRICTのblocking条件にします。EntityList内部Mapは直接置換しません。 |
| `recovery.strict.require_block_entity_ticker_order` | `true` | current/pending BlockEntity tickerの意味的順序を必須にします。 |
| `recovery.strict.require_ticket_ledger` | `true` | Chunk Ticket ledgerを観測・比較します。1.18.2では安全な丸ごと復元を行わないため、非空ledgerは`UNSUPPORTED`として扱い、trueならblockingです。falseはこの観測制限を明示的に受け入れます。 |
| `recovery.strict.players.require_checkpoint_players` | `false` | trueならCheckpoint時に存在したReal PlayerのUUID/name/Dimensionが揃うまで`/ac start`を拒否します。Inventory方針は変更しません。 |

Recovery中の通信は`[recovery.lock]`で個別制御できます。`allow_login`、`allow_chat`、`allow_commands`、`allow_player_movement`、`allow_player_look`、`allow_sneak`は既定true、Block Place/Break、Item Use、Entity Attack/Interact、Container、Drop、Pickupは既定falseです。`allow_login=false`ではPlayer初期化前のConnection段階で安全に拒否します。`allow_player_movement=true`では通常移動に加え、Creative飛行状態の更新も許可します。これはCarpetの`creativeNoClip`がサーバー側の`abilities.flying`を参照するためです。許可してもWorld Tick自体が再開するわけではありません。checkpointのbarrierではRecovery lockを適用せず、Playerの入力を拒否もしません。Server thread上のtask queue（Playerのpacketとconsoleのcommand）をbarrierの間だけ保留し、barrier後に届いた順で処理します。barrierが複数tickに渡った場合はPlayer自身のtickも止めるため、barrier全体が1回の長いtickとして振る舞います。以前はbarrier中のpacketを破棄していたため、移動の引き戻し（ロールバック）、閉じたはずのコンテナが開いたまま、エリトラの滑空状態が戻らない、といった症状が出ていました。

復元時の必要ChunkはDimension+positionで重複排除し、STRICTではCheckpoint時点のdomain行（loaded/FULL/block-ticking/entity-ticking/forced）を1件も切り捨てずExpectedとして扱います。`recovery.chunks.max_concurrent_loads`（既定4）件だけFutureを同時に保持し、server loopへ戻って完了をpollします。Main ThreadでFutureを`join`しないため、同時ロード数の制限は正確性ではなくピークメモリ制御です。Minecraft 1.18.2の`ServerChunkManager.addTicket`はlevelではなくradiusを受け取るため、APIへはFULL `33 -> radius 0`、block-ticking `32 -> radius 1`、entity-ticking/forced `31 -> radius 2`へ変換して追加・解除します。

level 31/32 ticketは、Chunkが単にFULLになった時点では完了扱いにしません。復旧Freeze中はBlock/Entity simulationを実行せず、Vanillaのticket・storage lifecycleだけを進め、要求されたreadiness futureが完了してから準備完了とします。STRICTでExpectedが設定上限を超える場合は、例えば`Expected: 654 / Configured maximum: 512 / Ready: 512`のように切り捨てず、ticketを追加せず凍結します。旧実装のlevel `33`をradiusとして渡すことによるlevel `0`の広大な周辺Chunkの同期ロードは行いません。

Runtime Capsule version 6は、Checkpoint時点の各Dimensionについて、loaded、FULL、block-ticking、entity-ticking、forced、ChunkHolderのlevel/completed level/type/sourceを保存します。STRICTはこの実測domain全体をExpectedとして扱い、復旧後に同じ集合・readinessを再取得できなければ`/ac start`を拒否します。`max_preload_chunks`は正確性のための切り捨てに使わず、Expected数を超えた場合は未準備のままFail Closedします。

v6のEquivalence fingerprintは、`CHUNK_DOMAIN`、Block Event、block/fluid scheduled tick、Moving Piston、current/pending BlockEntity ticker、EntityList、Redstone Torch burnout、World runtime、`WORLD_RNG`の対応可否、Chunk Ticket ledger、Carpet、FakePlayer、設定時のみReal Player一覧をカテゴリ別に保存します。Hashは高速判定用で、`/ac recovery verify --verbose`または`/ac debug equivalence --verbose`では順序・座標・件数の差分を表示します。`/ac start`直前にも同じ検証を行うため、準備完了後のPlayer loginやTicket変化を見逃しません。通常のcheckpoint commitログにも各カテゴリの件数・hash・Unsupported一覧・Runtime Capsuleサイズ・ヒープ使用量を出力します。

1.18.2では、scheduled tickの`subTickOrder`とWorldの次回`tickOrder`、Block Event queue、moving pistonのChunk dirty化を保存・復元します。BlockEntity tickerはJava objectそのものではなく位置・type・名前・current/pendingの意味的順序を再構成します。EntityListはUUID/type/network ID/観測順をfingerprintへ含めますが、`ServerEntityManager`の内部Mapを安全な公開APIなしに置き換えないため、順序は観測のみです。`require_checkpoint_players=false`（既定）では未接続のReal PlayerをEntity order比較から除外し、FakePlayerは除外しません。Real Playerの存在・相対順まで要求する場合は`require_checkpoint_players=true`にしてください。この設定の期待一覧は、選択的な`player_snapshot.enabled`や`players.json`とは独立してRuntime Capsuleへ保存されます。

Redstone Torch burnoutは古いWorld objectを保存せず、Dimension、BlockPos、time、entry順を新しいServerWorld keyへ再構成します。`lcgBlockSeed`と`tickOrder`は保存しますが、1.18.2の`java.util.Random`内部stateをJDKへ`--add-opens`して強制取得することはせず、`WORLD_RNG=UNSUPPORTED`として扱います。Ticket ledgerもtype/level/argument/tickCreated/expiryを観測・診断しますが、Vanilla invariantを壊す内部Mapの丸ごと書き戻しや、自然再生成Ticketとの二重復元は行いません。

Carpetは未導入なら`NOT_APPLICABLE`、対応版でcapture/restoreに成功した場合だけ`CAPTURED`です。TickSpeed、`process_entities`、pause/freeze、FakePlayer ActionPack（`next`、`interval`、`count`、`continuous`等）を保持します。time warpの実行者・callback参照、取得/復元失敗、未対応runtime featureは`UNSUPPORTED`/`INCOMPLETE`として隠さず、既定STRICTではFail Closedです。

Runtime Capsule version 1〜5のcheckpointは後方互換で読み込めますが、v6にないexact domain/fingerprint/runtime fieldをSTRICTでMATCH扱いしません。STRICT選択時は`MISSING_FROM_OLD_FORMAT`としてstartを拒否します。通常の`OPTIMIZED`では旧CRITICAL/ENTITY_TICKING/ALL plannerを使えます。

有効な直前checkpointは、起動時の整合性検証結果をそのまま次のJava incrementalのコピー元へ引き継ぎます。これにより同じcheckpointを起動中に二重hashせず、再起動後も変更fileだけをコピーします。完全な初回checkpointだけはコピー元がないため全量copyになります。

#### Real Player snapshot

`player_snapshot.enabled = false`にするとReal Playerのsnapshotと適用を全体的に無効化します。既定で有効なのは`dimension`、`position`、`rotation`だけです。GameMode、Ability、Inventory（main/armor/offhand）、selected slot、XP、Ender Chest、Health、Absorption、Food、Saturation、Exhaustion、Potion、Velocity、Fall Distance、Fire、Air、Spawn Pointは個別に有効化できます。

`[player_snapshot.vanilla_files]`の`restore_playerdata`、`restore_stats`、`restore_advancements`は既定falseです。これらはWorldのVanillaファイルを別snapshotして復元するため、外部Player Data同期やInventory Modと競合する可能性があります。Real Playerの全NBTをcheckpoint目的で無条件にrollbackする設定ではありません。

#### Carpet FakePlayer snapshot

`fakeplayer.enabled`、Carpetの検出、個別項目が有効な場合に処理します。FakePlayerはTMC装置の一部になり得るため、Real Playerより広い既定値です。`dimension`、`position`、`rotation`、`gamemode`、`flying`、`inventory`、`selected_slot`、`health`、`hunger`、`effects`、`sneaking`、`sprinting`、`movement`、`actions`、`action_internal_timing`、`item_use_cooldown`、`block_break_progress`を個別に変更できます。

`auto_rejoin`は不在FakePlayerの再作成、`restore_before_unfreeze`は凍結解除前の復元、`require_all_ready_before_start`は1体でも準備失敗した場合のstart拒否を制御します。CarpetなしでもAntiCrash本体、World checkpoint、Recovery、Real Player処理は動作します。

#### Loginの再試行（`[login]`）

checkpoint barrierは短時間ですが、その瞬間に接続したPlayerは従来kickされていました。現在は接続を保留し、毎tick受け入れを再試行します。

| キー | 既定値 | 説明 |
|---|---:|---|
| `login.retry_timeout_seconds` | `5` | AntiCrashがloginを拒否する間、この秒数まで接続を保留して再試行します（0〜20）。再試行するのはAntiCrashの拒否だけで、ban・whitelist・満員などVanillaの理由は保留せず即座に処理します。`0`で従来の即kick。 |

#### 通知（`[notifications]`）

checkpoint barrierとrecoveryはWorldを意図的に停止します。何も表示しないとPlayerには不明な停止に見えるため、理由だけを伝えます。freeze条件や復旧判断そのものは変わりません。

| キー | 既定値 | 説明 |
|---|---:|---|
| `notifications.frozen_action_bar` | `true` | 凍結中のphase（checkpoint作成中、必要Chunk n/m、`/ac start`待ち、復旧失敗など）をaction barへ表示。解除時に消去します。 |
| `notifications.checkpoint_action_bar_delay_ticks` | `10` | 旧設定との互換性のため読み込みますが、Checkpoint作成中のActionbarは常に非表示です。 |
| `notifications.ask_prompt_in_game` | `true` | ASKの質問を、回答権限を持つPlayerへゲーム内でも表示。参加直後にも表示します。 |

#### ConsistencyとLogging

| キー | 既定値 | 説明 |
|---|---:|---|
| `consistency.mode` | `IGNORE` | Worldと外部Player Dataの時点差を`IGNORE`、`WARN`、`JOURNAL`で扱います。自動推測修正はしません。 |
| `logging.level` | `NORMAL` | `OFF`、`IMPORTANT`、`NORMAL`、`VERBOSE`、`TRACE`。 |
| `logging.console` / `logging.file` | `true` | consoleと`.anticrash/logs`への出力を個別に切り替えます。 |
| `logging.max_file_mb` | `50` | active logのrotation閾値。 |
| `logging.retained_files` | `10` | rotation後に保持するログ数。 |
| `logging.show_checkpoint_contents` | `false` | checkpoint内容の詳細表示。 |
| `logging.show_player_details` | `false` | Real Player snapshot詳細。個人データを含み得ます。 |
| `logging.show_fakeplayer_details` | `true` | FakePlayer概要。 |
| `logging.show_block_events` | `false` | Block Event詳細。 |
| `logging.show_file_operations` | `false` | copy、hash、commit操作。I/O調査時に有効です。 |
| `logging.show_transaction_details` | `false` | Transaction詳細。 |

### 設定変更の反映

ファイル編集後、サーバー内で`/ac config reload`を実行するとAntiCrash設定を再読み込みできます。`language`やloggingは以後のAntiCrash出力へ反映され、接続中Playerへコマンドtreeを再送するため、変更後の権限とTab補完も再ログインなしで更新されます。checkpoint・Recoveryの実行中に設定を変更した場合、既に開始済みの処理は途中で再構成せず、次のcheckpointまたは明確なRecovery状態境界から新しい値を使用します。reloadの解析・検証に失敗した場合は変更を一切適用せず、直前の有効な設定を維持します。

## コマンド

既定では`/ac start`のみpermission level 0（OP不要）で、それ以外はpermission level 4です。各カテゴリの必要レベルは`[commands]`で変更できます。一般Playerが`/ac`だけを入力すると安全な`/ac start`案内を表示し、管理者の`/ac help`は実行権限があるカテゴリだけを複数行で表示します。checkpoint ID引数はTab補完できます。

```text
/ac help
/ac status
/ac start [--force]
/ac freeze
/ac checkpoint [now]
/ac checkpoint base
/ac checkpoint compact
/ac checkpoint list
/ac checkpoint info <id>
/ac checkpoint restore <id|latest>
/ac recovery status
/ac recovery verify [--verbose]
/ac recovery restore <id|latest>
/ac recovery abort
/ac recovery continue
/ac y
/ac n
/ac fakeplayers
/ac fakeplayers restore
/ac fakeplayers info <name>
/ac journal status
/ac journal list
/ac config reload
/ac debug dump
/ac debug equivalence [--verbose]
/ac debug checkpoint-performance [verbose|last|reset]
/ac debug mirror [audit]
```

`/ac status`はstate、選択checkpoint、Chunk/Block Event/FakePlayerの進捗に加えて、次回checkpointまでの残り時間とASKの残り時間を表示します。Recovery中はFidelity、Expected/READY Chunk、実行中Future、Equivalence結果、blocking category、Carpet completeness、unsupported categoryも表示します。recovery中に開始できない場合は、その理由も1行で表示します。`/ac checkpoint list`と`/ac list`はcheckpoint IDにcommit時刻を添えて表示します。

`/ac recovery verify`は現在選択中のcheckpointとのRecovery Equivalenceを確認します。`/ac debug equivalence`は同じ比較を診断用途として表示します。どちらも`--verbose`を付けると、Mismatchの先頭20件まで座標・順序・期待値・実値を表示します。STRICTでは、表示が`READY`になる前に`/ac start`を実行しても拒否されます。

`/ac start`が拒否された場合は、原因（必要Chunk未準備、ASK回答待ち、FakePlayer未準備、`--force`が必要 など）を応答に含めます。

pre-world ASK中はServer consoleで単独の`Y`/`N`だけを使用できます。DedicatedServerのconsole readerが開始する前に一度だけ入力を読むため、入力用の別スレッドや2つ目の`BufferedReader`は作りません。World load後のゲーム内`/ac y`、`/ac n`は新しいpre-world判断には使用しません。

`/ac recovery abort`はrollback済みWorldをその場で上書きせず、保持しているcrash-raw Worldの次回起動前restore要求を保存してcontrolled restartします。

### 復元時のcontrolled restart

checkpointまたはcrash-rawの復元はMinecraftがWorldを開く前に行う必要があります。dirty ASKの`Y`とAUTOは、その場で復元して同じJVMのWorld loadへ進みます。`/ac checkpoint restore`、`/ac recovery restore`、`/ac recovery abort`の明示操作だけは、既存Worldを安全に閉じて次回起動前に適用するpending restoreとして扱います。

```text
明示的なpending restoreをatomic保存
  -> .anticrash/state/restart-requestを作成
  -> Vanillaの保存・停止処理を完了
  -> 旧Javaプロセスの終了を確認
  -> 同じJava起動commandをreplacement JVMとして再実行
  -> markerを消費
  -> World load前にrestore
  -> stale markerがあれば復元完了時にも除去
  -> 同じreplacement JVMで通常のServer起動を継続
```

通常の`/stop`ではrestart markerを作らないため、自動再起動しません。明示的なWorld復元後に2回目の再起動は必要なく、replacement JVMがそのままWorld loadと復旧準備を続けます。dirty ASKの`Y`とAUTOはそもそもreplacement JVMを作らず、現在の起動中JVMのWorld load前に復元します。標準のSupervisor起動では、Supervisorが前面に常駐し、childのPID・終了code・試行回数を同じconsoleへ記録します。controlled restartはmarkerを1回だけ消費して再起動し、OOM/異常終了は`--max-restarts`まで再試行します。Java childには`-XX:+ExitOnOutOfMemoryError`を自動付与するため、Server threadだけが死んでworkerが残る状態をSupervisorが無期限に待ちません。Supervisorはchildのstdin/stdout/stderrを継承するため、再起動後もASKの`Y`/`N`を同じconsoleで入力できます。直接起動も互換用に残していますが、直接起動のreplacementは`SIGTTIN`停止を避けるため標準入力が`/dev/null`（Windowsでは`NUL`）になります。環境flagとmarkerの完全一致をhandshakeとして使い、復元要求を一度だけ消費します。`--stable-runtime-ms`以上稼働した後は再試行回数をリセットするため、長時間稼働後の非0終了も再起動します。復元状態または再起動helperを安全に準備できない場合は停止せず、復元要求を拒否します。

復元が完了して`READY_TO_START`または`RECOVERY_FROZEN`になっている間にReal Playerがログインすると、`language = "ja"`では次を表示します。

```text
ゲーム内の時間が停止しています。
/ac startで再開できます。
```

`language = "en"`では`In-game time is paused.`と`Use /ac start to resume.`を表示します。`recovery.start_command`を変更した場合は2行目にもその値を使用します。ASKのY/N待ちや短時間のcheckpoint barrierでは、`/ac start`で再開できないためこの案内を表示しません。`/ac start`が実際に`RUNNING`へ遷移した時点では、接続中の全Real Playerへ`[AntiCrash] ゲーム内の時間を再開しました。`（英語では`In-game time has resumed.`）を表示します。

## Real Playerと外部Player Data

既定ではReal PlayerのDimension、X/Y/Z、Yaw/Pitchだけを保存・接続時に適用します。Inventory、Armor、Offhand、Selected Slot、XP、Ender Chest、Health、Hunger、Saturation、Exhaustion、Potion、その他状態は既定で変更しません。外部MySQL/同期Modのデータを推測してrollbackせず、s2、hub、c1、c2、Velocityも操作しません。

必要な項目を有効化した場合は、その項目だけが、復元後に各Real Playerが最初に接続した時点で1回だけ適用されます。`/ac start`より後に初参加したPlayerも対象です。Worldと外部Player Dataの時点差に起因するduplication/lossは、`IGNORE`、`WARN`、`JOURNAL`で可視化できますが、自動推測修正はしません。

## FakePlayerとCarpet

Carpetなしでも本体、World checkpoint、Recovery、Real Player処理は起動します。Carpetがある場合はFakePlayerの位置、Dimension、Inventory、Gamemode、flying、sneak、sprint、movement、Health/Hunger、effects、乗り物、ActionPackを復元します。ActionPackは文字列コマンドを再発行するだけでなく、反射的に内部フィールドを保存し、`next`や`interval`を可能な範囲で維持します。Recovery FreezeでVanillaの最初のWorld Tickを止めている間は、Carpetの`mobcaps` HUDが未初期化のSpawn情報を参照してもServerを落とさないよう、観測専用の空情報を返します。World Tickや実際のMob Spawn状態は進めません。

Carpetの内部クラスやFactoryのsignatureにはバージョン差があります。選択したActionPack項目はcheckpoint時と復元前に必須fieldを検証し、欠落時はbest-effortで進めずfail closedにします。FakePlayer再作成Factoryや派生Carpet版の互換性には反射fallbackが残るため、必要なFakePlayerがreadyにならない場合は既定で`/ac start`を拒否します。

## Snapshot Backend

`AUTO`は`PORTABLE_GENERATION`を選びます。一般Filesystem、Windows、macOSを含め、Btrfs/Reflink/ZFS/VSS/Linux固有機能に依存しません。Logical Cut中にmutableな変更fileを独立copyし、変更のないfileは検証済み親checkpointからだけ再利用します。`generation-seal.json`がfsyncされるまでWorldは再開せず、再開後のworkerはLive Worldを開きません。Btrfs/Reflink/CRIUは明示指定時だけ旧`LEGACY_SAFE`経路として使い、portable generationのEpoch契約を名乗りません。

## Recovery Policy

- `AUTO`: clean shutdownなら現在のWorldで通常起動し、dirty exitを検出した場合だけ最新候補を完全検証・restoreしてからWorldを開き、`RECOVERY_FROZEN`で待機します。
- `ASK`: clean shutdownは質問なしで通常起動します。dirty exitではWorld load前に終了直前の未検証checkpoint IDをServer consoleへ表示します。`Y`で完全検証・restore後に同じJVMがWorld loadへ進み、`N`で復元なしの通常起動になります。候補が壊れていれば`Y`後の検証で止まり、fallbackを含めて復元できない限りWorldを開きません。
- `ASK`の無回答は、プロンプト表示後に`ask_timeout_seconds`で指定した時間（既定60秒）で確定します。dirty exitでは`Y`、`MANUAL`では未解決としてWorld loadを拒否します。入力はpre-worldのServer consoleだけで受け付けます。
- `MANUAL`: 勝手にWorldを戻さず、Recovery Required状態で管理者を待ちます。

いずれもRuntime Capsule、World integrity、Required Chunk、Block Event、FakePlayerの検証に失敗した場合は既定でstartを拒否し、`RECOVERY_FAILED`または`RECOVERY_FROZEN`に留まります。STRICTではさらにTick Domain、scheduled tick、BlockEntity ticker、Carpet、観測可能なWorld runtimeのEquivalenceがblocking条件になります。未対応状態は`UNSUPPORTED`、旧形式で欠落した値は`MISSING_FROM_OLD_FORMAT`として表示し、明示的な許可がない限り再開しません。

ASK/MANUALでValid checkpointが1件もない場合は、存在しない候補へのYやtimeout復元を行わず、Crash時点のWorldを凍結して`/ac recovery continue`またはcrash-rawへのabortを待ちます。AUTO dirtyの自動復元に候補がない場合はfail closedです。format version 2のcheckpointは認証済み`parentCheckpointId`を辿るため、過去checkpointへ戻した後に放棄された新しい枝へ勝手にroll-forwardしません。legacy version 1では現在IDより小さい世代だけをfallback候補にします。

checkpointのSHA-256検証中は、対象IDと検証開始・完了をログへ表示します。`retention_count`が多くても、ASKや`latest`指定の起動前処理でValidな最新世代より古いcheckpointを先行して全件検証することはありません。AUTOの旧世代検証は、最新候補が無効または復元失敗し、かつ旧checkpointへのfallbackが許可されている場合だけ行います。World置換前には全fileを検証し、置換後のRuntime setupではmanifest/runtime/Player/FakePlayer payloadだけを再認証するため、同じWorld全体の二重hashは行いません。

## LoggingとConsistency

`[logging] level`は`OFF`、`IMPORTANT`、`NORMAL`、`VERBOSE`、`TRACE`です。console/fileを個別に切り替えられ、fileはsize rotationされます。`language`はAntiCrash独自のコマンド応答と復旧・checkpoint・snapshotログに適用され、Minecraft/Fabric/Java/OS/外部Modのログは変更しません。`WARN`はRecovery後にも警告できる最小限の非機密recordを永続化し、`JOURNAL`はTransaction ID、checkpoint ID、tick、Player UUID、source/destination、位置、ItemStack ID/count/NBT、before/after、reconciliation statusをJSONLへ記録します。1 recordは1 MiBに制限し、巨大NBTは識別情報を残して省略します。

## 既知の限界

- Java/Fabric/Carpetの内部実装を使う部分は、1.18.2 Yarn build.4と実際のmerged bytecodeを基準にし、Carpet内Factoryは反射的に対応するため全派生版を保証しません。対応する詳細とUnsupported一覧は[Recovery Equivalence](RECOVERY-EQUIVALENCE.md)を参照してください。
- Portable generationはtick境界でWorldを固定し、dirty Chunkを保存してIO workerを封印してから（未書き込みの書き込みはqueueから写し取り、ディスクへの書き込み完了は待たない）、Logical Cutで独立file setとseal journalを作ります。コピーが必要なfileのdetachが終わればWorld/Entity/Mutationを再開でき、manifest/checksum/fsync/retentionはworkerへ移ります。変更なしfileはsize/mtime/ctimeとcheckpoint間Hardlinkのリンク数で判定して検証済み前回checkpointから再利用します。旧manifestは起動時の完全検証でこのメタデータをメモリへ移行し、直接指定・ctime非対応環境など移行できない場合だけSHA-256を照合して安全に再利用します。
- `/save-off`中はCheckpointを作成しません。Vanillaの保存がEntityと`data/`を書かないため、整合したCheckpointを作れないからです（ログに1回通知し、`/save-on`で再開します）。
- 復旧後`/ac start`前に正常停止した場合（`RECOVERY_FAILED`で停止した場合を除く）、次回起動で同じCheckpointの復元をやり直します。Runtime Stateはメモリ上にしか無いためです。異常終了の復旧判断（ASK/MANUAL）待ちで停止した場合も、判断待ちから再開します。
- Supervisorは自身がSIGTERM等で終了させられると子ServerへSIGTERMを送り、`--shutdown-grace-ms`（既定120000）まで終了を待ちます。子ServerにとってはSIGTERMによる停止なので、AntiCrashは異常終了として扱います（次回起動で復旧）。
- `/stop`自体はcheckpoint待ちのためにcancelしません。停止前checkpointは既定5秒だけ待ち、間に合わなければ未確定分を破棄してVanillaの停止を続けます（`checkpoint.shutdown_timeout_seconds`）。
- crash原因はcrash-report/latest.logから判定できる範囲だけを分類します。`/stop`などMinecraftのServer threadから実行された停止だけを正常終了とし、`kill`/SIGTERM、kill -9、電源断、OS crashなどは異常終了（原因不明を含む）として扱います。
- 実行中の外部Player DataやMySQLをrollback/reconcileしません。JOURNALは警告とSPIを提供するだけです。
- Btrfs snapshot、Reflink、CRIU、Watchdog、Proxy配下の切断挙動、Carpet ActionPackの全フィールド、EntityList内部順序の安全な再構成、Chunk Ticketの完全復元、Java Random内部state、ゲーム内の実装動作は、実Server/Carpet/TMC構成でのRuntime Reviewが必要です。`OPTIMIZED does not guarantee tick-boundary equivalence`。
- 本リポジトリのUnit Testとbuildは実行済みですが、実Minecraft Server、プレイヤー接続、TMC装置、音声やProxyを含む統合確認は未実施です。

### 運用上のセキュリティ

AntiCrashは`server.properties`の`online-mode`やProxy firewallを変更しません。`offline-mode`のBackendをVelocity等のProxy配下で使う場合は、Backend portへの直接接続をFirewallで遮断し、利用中Proxyの認証済みPlayer forwardingを正しく構成してください。直接接続できるoffline-mode Serverでは名前・権限のなりすましが可能になるため、AntiCrashのcommand permissionだけでは保護できません。

新規作成する`config/anticrash.toml`、内部JSON、restart marker、AntiCrashログはPOSIX filesystemで`0600`、新規`.anticrash/`内部ディレクトリは`0700`にします。既存設定を所有者以外が書き換えられる場合、または既存`.anticrash/`へ所有者以外がアクセスできる場合は警告します。共有管理のため意図的にgroup accessを許可する場合、そのgroupの全員をcheckpoint・Player情報へアクセスできる管理者として扱ってください。

`snapshot.verify_checksums=false`、復旧凍結中のBlock/Entity/Inventory変更許可、管理系コマンドのpermission level 0は明示設定として維持しますが、毎回警告します。コマンド応答は制御文字を除去して512文字、一覧は先頭20件に制限し、ログ・状態を利用したチャット出力の肥大化を防ぎます。Supervisorの`--dry-run`は`password`、`token`、`secret`等の一般的な引数を伏せ字にし、`--workdir`のsymbolic linkを拒否します。

## Build / Test

Project rootで実行します。

```bash
./gradlew test
./gradlew build
```

成果物は`build/libs/AntiCrash-1.0.0.jar`、`build/libs/AntiCrash-1.0.0-sources.jar`、`supervisor/build/libs/AntiCrash-Supervisor-1.0.0.jar`です。Supervisorの使い方は[COMPATIBILITY](COMPATIBILITY.md)と[DEVELOPMENT](DEVELOPMENT.md)を参照してください。

## License

GNU Lesser General Public License, Version 3.0 only (LGPL-3.0-only)。作者: AtsukiMC。詳細は[LICENSE](../LICENSE)を参照してください。

## Runtime Stateの監査

Vanillaの再起動で失われ、装置の動作を変える状態（動作中ピストンの進行度、End Gatewayのcooldown、ポータル転送待ち、短命のChunk ticket、tick順序など）を一覧化し、何を保存し、何が不要で、何が非対応かを[docs/RUNTIME-STATE-AUDIT.md](RUNTIME-STATE-AUDIT.md)にまとめています。実際の専用Serverで「中断しなかった場合」と「checkpointから復旧した場合」をtickごとに比較した結果は[docs/RUNTIME-QUALIFICATION.md](RUNTIME-QUALIFICATION.md)にあります。
