# AntiCrash

**Minecraft Fabric server mod** · AtsukiMC · **v1.0.3** · **LGPL-3.0-only**

[English](#english) · [日本語](#日本語)

## English

AntiCrash creates checkpoints of world data and supported runtime state. By default, recovery keeps game time paused until preparation finishes; run `/ac start` to resume. Compatible Carpet versions enable FakePlayer restoration.

Recovery coverage varies by Minecraft version and installed mods. `OPTIMIZED` uses best-effort recovery. `STRICT` blocks resuming when required state is missing or differs from the checkpoint.

### Installation

Download the JAR for the exact Minecraft version from [Release 1.0.3](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.3). Install the Mod on the server; clients can join with a Vanilla client. Fabric API is optional. Install a compatible Carpet version for FakePlayer integration.

| Component | File name | Location |
| --- | --- | --- |
| Mod | `AntiCrash-1.0.3+mc<VERSION>.jar` | Server `mods/` folder |
| Supervisor (optional) | `AntiCrash-Supervisor-1.0.3+mc<VERSION>.jar` | Server directory, outside `mods/` |

### Supported versions

| Minecraft | Minimum Fabric Loader | Minimum Java |
| --- | --- | --- |
| 1.18, 1.18.1, 1.18.2 | 0.17.3 | 17 |
| 1.19–1.19.4 | 0.17.3 | 17 |
| 1.20–1.20.4 | 0.17.3 | 17 |
| 1.20.5–1.20.6 | 0.17.3 | 21 |
| 1.21–1.21.10 | 0.17.3 | 21 |
| 1.21.11 | 0.18.4 | 21 |
| 26.1 | 0.18.4 | 25 |
| 26.2 | 0.19.3 | 25 |
| 26.3 | 0.19.5 | 25 |

### Recovery and settings

The server creates `config/anticrash.toml` on first launch. These are the main defaults:

| Setting | Default | Purpose |
| --- | --- | --- |
| `language` | `ja` | AntiCrash message language; `en` is also available |
| `[checkpoint] interval_seconds` | `30` | Automatic checkpoint interval in seconds |
| `[recovery] policy` | `AUTO` | Recovery behavior after shutdown |
| `[recovery] fidelity` | `OPTIMIZED` | Recovery validation mode |
| `[recovery] ask_timeout_seconds` | `60` | Time allowed for an `ASK` response |

The full catalog below lists every configuration key, its default, and its purpose. Edit the generated file in place; settings are grouped by TOML section.

| Policy | Behavior |
| --- | --- |
| `AUTO` | Restores a valid checkpoint after an unexpected shutdown; starts normally after a clean shutdown. |
| `ASK` | Prompts for `Y` or `N` in the server console before the world loads. On timeout, it selects `Y` after an unexpected shutdown and `N` after a clean shutdown. |
| `MANUAL` | Waits for an explicit recovery decision after an unexpected shutdown. |

Use `/ac status` to view progress, `/ac start` to resume game time, `/ac checkpoint now` to create a checkpoint, and `/ac recovery verify` to check recovery readiness. By default, `/ac start` requires permission level 0; other commands require level 4.

### Supervisor

The optional Supervisor monitors Minecraft and can restart it in the same console after a crash or controlled restart. Start it with the server command after `--`; for example:

```sh
java -jar AntiCrash-Supervisor-1.0.3+mc1.18.2.jar --workdir /path/to/server -- java -jar fabric-server-launch.jar nogui
```

## 日本語

AntiCrashは、ワールドデータと対応する実行状態のチェックポイントを作成するMinecraft Fabric用サーバーModです。初期設定では、復旧の準備が終わるまでゲーム内時間を停止し、`/ac start`で再開します。対応するCarpetではFakePlayerも復元できます。

復元範囲はMinecraftのバージョンや導入Modによって異なります。`OPTIMIZED`は可能な範囲で復元します。`STRICT`は必須状態が欠けている場合やチェックポイントと異なる場合、ゲーム内時間の再開を保留します。

### 導入方法

[Release 1.0.3](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.3)から、Minecraftのバージョンに合ったJARをダウンロードしてください。Modはサーバーへ導入し、クライアントはVanillaのまま参加できます。Fabric APIは任意です。FakePlayer連携には対応するCarpetが必要です。

| 内容 | ファイル名 | 配置先 |
| --- | --- | --- |
| Mod | `AntiCrash-1.0.3+mc<VERSION>.jar` | サーバーの`mods/` |
| Supervisor（任意） | `AntiCrash-Supervisor-1.0.3+mc<VERSION>.jar` | `mods/`の外にあるサーバーディレクトリ |

### 対応バージョン

| Minecraft | Fabric Loaderの最低バージョン | Javaの最低バージョン |
| --- | --- | --- |
| 1.18・1.18.1・1.18.2 | 0.17.3 | 17 |
| 1.19〜1.19.4 | 0.17.3 | 17 |
| 1.20〜1.20.4 | 0.17.3 | 17 |
| 1.20.5〜1.20.6 | 0.17.3 | 21 |
| 1.21〜1.21.10 | 0.17.3 | 21 |
| 1.21.11 | 0.18.4 | 21 |
| 26.1 | 0.18.4 | 25 |
| 26.2 | 0.19.3 | 25 |
| 26.3 | 0.19.5 | 25 |

### 復旧と設定

初回起動時に`config/anticrash.toml`が作成されます。主な初期設定は次のとおりです。

| 設定 | 初期値 | 内容 |
| --- | --- | --- |
| `language` | `ja` | AntiCrashの表示言語。`en`も選択できます |
| `[checkpoint] interval_seconds` | `30` | 自動チェックポイントの間隔（秒） |
| `[recovery] policy` | `AUTO` | 終了後の復旧方法 |
| `[recovery] fidelity` | `OPTIMIZED` | 復旧状態の検証方法 |
| `[recovery] ask_timeout_seconds` | `60` | `ASK`の回答期限（秒） |

下の一覧に、設定キー・初期値・用途をすべて記載しています。生成された設定ファイルを直接編集してください。一覧はTOMLのセクションごとに分けています。

| ポリシー | 動作 |
| --- | --- |
| `AUTO` | 異常終了後に有効なチェックポイントを復元し、正常終了後は通常起動します。 |
| `ASK` | ワールド読み込み前に、サーバーコンソールで`Y`または`N`を尋ねます。期限切れの場合、異常終了後は`Y`、正常終了後は`N`として扱います。 |
| `MANUAL` | 異常終了後、明示的な復旧判断を待ちます。 |

`/ac status`で進行状況、`/ac start`でゲーム内時間の再開、`/ac checkpoint now`でチェックポイントの作成、`/ac recovery verify`で復旧準備の状態を確認できます。初期設定では`/ac start`は権限レベル0、その他のコマンドはレベル4が必要です。

### Supervisor

任意のSupervisorを使うと、クラッシュや制御された再起動の後にMinecraftを同じコンソールで再起動できます。`--`の後ろにサーバー起動コマンドを指定します。1.18.2の場合は次のとおりです。

```sh
java -jar AntiCrash-Supervisor-1.0.3+mc1.18.2.jar --workdir /path/to/server -- java -jar fabric-server-launch.jar nogui
```

ライセンスは**GNU Lesser General Public License v3.0 only**（`LGPL-3.0-only`）です。

## Complete configuration reference

The catalog includes all 156 keys generated by AntiCrash, including the configuration schema version. Descriptions are shown in English and Japanese. Values such as `0` have key-specific meanings; check the description for that setting.

<details>
<summary>General / 基本設定</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `config_version` | `1` | Configuration schema version; leave unchanged. / 設定形式の識別番号です。変更しません。 |
| `language` | `"ja"` | AntiCrash message language: `ja` or `en`. / AntiCrashの表示言語です。`ja`または`en`を指定します。 |

</details>

<details>
<summary>Checkpoint schedule and retention / 保存間隔と保持</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `checkpoint.enabled` | `true` | Enables automatic checkpoint creation. / 自動作成を有効にします。 |
| `checkpoint.interval_seconds` | `30` | Time between automatic checkpoints, in real seconds (1–86,400). / 自動作成の間隔を実時間の秒数で指定します（1〜86,400）。 |
| `checkpoint.create_on_manual_command` | `true` | Allows checkpoint commands to create checkpoints. / コマンドによる作成を許可します。 |
| `checkpoint.create_on_clean_shutdown` | `true` | Creates a final checkpoint during a normal shutdown. / 正常停止時に最終チェックポイントを作成します。 |
| `checkpoint.shutdown_timeout_seconds` | `5` | Maximum time the server waits for that final checkpoint; `0` skips the wait. / 最終保存を待つ上限時間です。`0`は待たずに終了します。 |
| `checkpoint.retention_count` | `20` | Maximum number of checkpoints to retain. Must be at least `1`; the newest is kept. / 保持する最大件数です。`1`以上を指定し、最新の1件は保持されます。 |
| `checkpoint.retention_days` | `7` | Marks checkpoints older than this many days for deletion; `0` disables age-based cleanup. / 指定日数より古いチェックポイントを削除対象にします。`0`は日数による削除を行いません。 |
| `checkpoint.retention_max_gb` | `0` | Storage limit for checkpoints in GB; `0` means no size limit. / チェックポイントの容量上限（GB）です。`0`は上限なしです。 |
| `checkpoint.save_batch_chunks` | `256` | Values `1`–`15` set the queued-write limit directly. Values `16`–`4,096` use a heap-based limit; the default `256` selects this adaptive mode. / `1`〜`15`は保存待ちデータの上限です。`16`〜`4,096`はヒープ容量に応じた上限を使い、初期値`256`もこの方式です。 |

</details>

<details>
<summary>Checkpoint timing and performance / 保存処理と性能</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `checkpoint.performance.target_barrier_ms` | `20` | Target duration for the checkpoint cut, in milliseconds. / チェックポイント境界処理の目標時間（ミリ秒）です。 |
| `checkpoint.performance.warn_barrier_ms` | `50` | Logs a warning when the cut exceeds this duration. / 境界処理がこの時間を超えた場合に警告します。 |
| `checkpoint.performance.critical_barrier_ms` | `100` | Logs a critical delay when the cut exceeds this duration. / 境界処理がこの時間を超えた場合に重要警告を出します。 |
| `checkpoint.performance.warn_checkpoint_ms` | `5000` | Logs a warning when snapshot capture exceeds this duration. / スナップショット取得がこの時間を超えた場合に警告します。 |
| `checkpoint.performance.rolling_enabled` | `true` | Spreads eligible snapshot preparation across server ticks. / 対象データの準備を複数のサーバーtickに分けます。 |
| `checkpoint.performance.rolling_budget_ms_per_tick` | `1.0` | Initial time budget for rolling work per tick, in milliseconds. / 1 tickあたりの段階処理時間の初期上限（ミリ秒）です。 |
| `checkpoint.performance.rolling_max_budget_ms_per_tick` | `3.0` | Maximum rolling-work budget per tick when adaptive budgeting is enabled. / 自動調整を有効にした場合の1 tickあたりの最大処理時間です。 |
| `checkpoint.performance.adaptive_budget` | `true` | Adjusts the rolling-work budget based on observed load. / 負荷に応じて段階処理の時間上限を調整します。 |
| `checkpoint.performance.warn_finalize_ms` | `1000` | Logs a warning when checkpoint finalization exceeds this duration. / チェックポイントの最終確定がこの時間を超えた場合に警告します。 |

</details>

<details>
<summary>Checkpoint generation, storage, and diagnostics / 世代・保存先・診断</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `checkpoint.pipeline.max_inflight_generations` | `2` | Maximum number of checkpoint finalizers allowed in flight. / 同時に最終確定できるチェックポイント世代数の上限です。 |
| `checkpoint.incremental.base_interval` | `0` | Creates a BASE after this many generations; `0` disables periodic BASE creation. / 指定世代ごとにBASEを作成します。`0`は定期作成を無効にします。 |
| `checkpoint.incremental.max_chain_depth` | `0` | Creates a BASE when the incremental chain reaches this depth; `0` disables this limit. / 差分チェーンが指定深度に達したときBASEを作成します。`0`は深度による作成を無効にします。 |
| `checkpoint.io.background_threads` | `1` | Number of background checkpoint-finalization threads (1–16). / チェックポイント確定に使うバックグラウンドスレッド数です（1〜16）。 |
| `checkpoint.io.max_mb_per_second` | `0` | Throttles background checkpoint I/O; `0` means unlimited. / バックグラウンド保存の速度上限（MB/秒）です。`0`は無制限です。 |
| `checkpoint.storage.minimum_free_mb` | `256` | Minimum free disk space required before publishing a checkpoint. / チェックポイント確定前に必要なディスク空き容量（MB）です。 |
| `checkpoint.durability.mode` | `"SAFE"` | Durability mode: `SAFE`, `BALANCED`, or `FAST`. `FAST` skips forcing world payload files and directories. / 保存の耐久性設定です。`SAFE`・`BALANCED`・`FAST`を指定します。`FAST`ではワールド本体やディレクトリの同期書き込みを省略します。 |
| `checkpoint.engine.mode` | `"LOGICAL_DELTA"` | Checkpoint engine: `LOGICAL_DELTA`, `PORTABLE_GENERATION`, or `LEGACY_SAFE`. / チェックポイント方式です。`LOGICAL_DELTA`・`PORTABLE_GENERATION`・`LEGACY_SAFE`を指定します。 |
| `checkpoint.engine.fallback` | `"PORTABLE_GENERATION"` | Fallback engine: `PORTABLE_GENERATION` or `LEGACY_SAFE`. / 主方式を使えない場合の代替方式です。 |
| `checkpoint.engine.single_inflight` | `true` | Allows only one logical checkpoint operation at a time. / 論理チェックポイント処理を同時に1件に制限します。 |
| `checkpoint.engine.allow_unsupported_persistent_state` | `false` | Allows checkpoint creation when unsupported persistent state is detected. This can reduce recovery fidelity. / 未対応の永続状態が検出された場合も作成を許可します。復元精度が下がる可能性があります。 |
| `checkpoint.cache.max_mb` | `256` | Memory limit for the checkpoint candidate cache (16–4,096 MB). / チェックポイント候補キャッシュの上限（MB）です（16〜4,096）。 |
| `checkpoint.cache.budget_ms_per_tick` | `1.0` | Per-tick time budget for candidate-cache work, in milliseconds. / 候補キャッシュ処理に使う1 tickあたりの時間上限（ミリ秒）です。 |
| `checkpoint.mirror.enabled` | `true` | Enables audit-only tracking of observed world mutations. / ワールド変更の監査用記録を有効にします。 |
| `checkpoint.mirror.audit_interval_seconds` | `300` | Interval between mutation-audit summaries, in seconds; `0` disables periodic summaries. / 監査情報をまとめる間隔（秒）です。`0`は定期出力を無効にします。 |
| `checkpoint.mirror.trace_limit` | `64` | Maximum number of recent mutation traces retained for diagnostics. / 診断用に保持する変更履歴数の上限です。 |

</details>

<details>
<summary>Command permissions / コマンド権限</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `commands.help_permission_level` | `4` | Permission level for `/ac help`. / `/ac help`の権限レベルです。 |
| `commands.status_permission_level` | `4` | Permission level for status commands. / 状態確認コマンドの権限レベルです。 |
| `commands.start_permission_level` | `0` | Permission level for `/ac start`; `0` allows everyone. / `/ac start`の権限レベルです。`0`は全員に許可します。 |
| `commands.start_force_permission_level` | `4` | Permission level for `/ac start --force`. / `/ac start --force`の権限レベルです。 |
| `commands.freeze_permission_level` | `4` | Permission level for `/ac freeze`. / `/ac freeze`の権限レベルです。 |
| `commands.checkpoint_permission_level` | `4` | Permission level for checkpoint commands. / チェックポイント操作の権限レベルです。 |
| `commands.recovery_permission_level` | `4` | Permission level for recovery commands. / 復旧操作の権限レベルです。 |
| `commands.fakeplayers_permission_level` | `4` | Permission level for FakePlayer commands. / FakePlayer操作の権限レベルです。 |
| `commands.journal_permission_level` | `4` | Permission level for journal commands. / ジャーナル確認の権限レベルです。 |
| `commands.config_permission_level` | `4` | Permission level for config commands. / 設定操作の権限レベルです。 |
| `commands.debug_permission_level` | `4` | Permission level for debug commands. / デバッグコマンドの権限レベルです。 |

Permission levels range from `0` (everyone) to `4` (server operator level 4). / 権限レベルは`0`（全員）から`4`（OPレベル4）までです。

</details>

<details>
<summary>Snapshot backend / スナップショット方式</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `snapshot.backend` | `"AUTO"` | Backend: `AUTO`, `PORTABLE_GENERATION`, `GENERATION`, `JAVA_INCREMENTAL`, `LEGACY_SAFE`, `REFLINK`, `BTRFS`, or `CRIU_EXPERIMENTAL`. `AUTO` selects the portable generation path. / 保存方式です。`AUTO`はポータブル方式を選びます。指定できる値は左記のとおりです。 |
| `snapshot.verify_checksums` | `true` | Verifies checkpoint checksums during validation and restore. / 検証・復元時にチェックサムを確認します。 |
| `snapshot.keep_crash_raw_world` | `true` | Keeps the pre-rollback world used by `/ac recovery abort`. / `/ac recovery abort`で戻せる巻き戻し前ワールドを保持します。 |
| `snapshot.crash_raw_retention_count` | `10` | Maximum number of pre-rollback worlds to retain; minimum `1`. / 巻き戻し前ワールドの保持上限です。最低1件を保持します。 |
| `snapshot.io_threads` | `0` | File-copy thread count (0–64). `0` uses the selected operation's default; positive values set the thread count. / ファイルコピーのスレッド数です（0〜64）。`0`は処理ごとの既定値、それ以外は指定した数を使います。 |

</details>

<details>
<summary>Recovery behavior / 復旧動作</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `recovery.policy` | `"AUTO"` | Recovery policy: `AUTO`, `ASK`, or `MANUAL`. / 復旧方法です。`AUTO`・`ASK`・`MANUAL`を指定します。 |
| `recovery.fidelity` | `"OPTIMIZED"` | Validation mode: `OPTIMIZED` or `STRICT`. / 復旧状態の確認方法です。`OPTIMIZED`または`STRICT`を指定します。 |
| `recovery.freeze_after_restore` | `true` | Keeps game time paused after restoration while preparation runs. / 復元後、準備が進む間ゲーム内時間を停止します。 |
| `recovery.require_manual_start` | `true` | Requires `/ac start` after recovery preparation. / 復旧準備後に`/ac start`を要求します。 |
| `recovery.start_command` | `"/ac start"` | Text shown to players as the resume command. The registered command remains `/ac start`. / プレイヤー向けに表示する再開コマンドです。実際のコマンドは`/ac start`です。 |
| `recovery.restore_latest_valid_checkpoint` | `true` | Restores the newest checkpoint that passes validation. / 検証に通る最新のチェックポイントを復元します。 |
| `recovery.allow_restore_older_checkpoint` | `true` | Allows recovery to select an older valid checkpoint. / より古い有効なチェックポイントの選択を許可します。 |
| `recovery.fallback_to_previous_checkpoint` | `true` | Tries the previous checkpoint when the selected one cannot be used. / 選択したチェックポイントが使えない場合、前の世代を試します。 |
| `recovery.ask_timeout_seconds` | `60` | Time limit for the `ASK` decision. `0` waits indefinitely only when input is available. / `ASK`の回答期限です。`0`は入力手段が使える場合に限り無期限で待ちます。 |
| `recovery.ask_default_action` | `"ABORT"` | Compatibility setting; current timeout handling uses the exit type and ignores this value. / 互換性のための設定です。現在の無回答時動作は終了種別で決まり、この値は使いません。 |
| `recovery.allow_start_with_failed_validation` | `false` | Allows `/ac start --force` to bypass eligible semantic validation failures. Corrupt files and persistence failures remain blocking. / 対象となる意味検証の失敗を`/ac start --force`で続行できるようにします。破損ファイルや永続化失敗は解除できません。 |
| `recovery.spawn_restart_helper` | `false` | Starts a detached helper to relaunch the server after a restore request; ignored when launched through Supervisor. / 復元予約後にサーバーを起動する補助プロセスを使います。Supervisor経由では無視されます。 |
| `recovery.temporary_ticket_release_ticks` | `100` | Number of game ticks before temporary recovery chunk tickets are released (1–1,200,000). / 復旧用の一時チャンクticketを解放するまでのtick数です（1〜1,200,000）。 |
| `recovery.preload_mode` | `"ALL"` | Chunk selection for optimized recovery: `ALL`, `ENTITY_TICKING`, or `CRITICAL`. / OPTIMIZEDで準備するチャンクの選び方です。`ALL`・`ENTITY_TICKING`・`CRITICAL`を指定します。 |
| `recovery.max_preload_chunks` | `100000` | Maximum number of chunks recovery may prepare. Recovery fails if it needs more. / 復旧で準備するチャンク数の上限です。必要数が超える場合は復旧を失敗させます。 |
| `recovery.chunk_ready_timeout_seconds` | `120` | Fails recovery after this long with no chunk-readiness progress; `0` disables the timeout. / チャンク準備が進まない状態の許容時間（秒）です。`0`は時間切れ判定を無効にします。 |

</details>

<details>
<summary>Recovery chunk loading and STRICT checks / チャンク読み込みとSTRICT検証</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `recovery.chunks.max_concurrent_loads` | `4` | Maximum concurrent asynchronous chunk loads (1–64). / 同時に読み込むチャンク数の上限です（1〜64）。 |
| `recovery.strict.allow_unsupported_runtime_state` | `false` | Allows STRICT recovery with unsupported Minecraft runtime state. / 未対応のMinecraft実行状態があってもSTRICT復旧を許可します。 |
| `recovery.strict.allow_unsupported_carpet_state` | `false` | Allows STRICT recovery with unsupported Carpet state. / 未対応のCarpet状態があってもSTRICT復旧を許可します。 |
| `recovery.strict.require_entity_order` | `true` | Requires entity ordering to match the checkpoint. / Entityの順序がチェックポイントと一致することを要求します。 |
| `recovery.strict.require_block_entity_ticker_order` | `true` | Requires block-entity ticker ordering to match. / BlockEntity tickerの順序が一致することを要求します。 |
| `recovery.strict.require_ticket_ledger` | `true` | Requires the chunk-ticket ledger to match or be supported. / チャンクticketの記録が一致し、対応済みであることを要求します。 |
| `recovery.strict.players.require_checkpoint_players` | `false` | Requires every real player present at checkpoint time to be online before `/ac start`. / チェックポイント時にいた全実プレイヤーが揃うまで`/ac start`を拒否します。 |

</details>

<details>
<summary>Player actions while recovery is frozen / 復旧中のプレイヤー操作</summary>

These options apply only while recovery is frozen. KeepAlive and AntiCrash recovery controls remain available. / 復旧中だけ適用されます。KeepAliveとAntiCrashの復旧操作は引き続き利用できます。

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `recovery.lock.allow_login` | `true` | Allows players to join while recovery is frozen. / 復旧停止中のログインを許可します。 |
| `recovery.lock.allow_chat` | `true` | Allows chat while recovery is frozen. / 復旧停止中のチャットを許可します。 |
| `recovery.lock.allow_commands` | `true` | Allows the configured safe command set during recovery. / 復旧中に読み取り中心の許可コマンドを使えるようにします。 |
| `recovery.lock.allow_player_movement` | `true` | Allows player movement. / プレイヤーの移動を許可します。 |
| `recovery.lock.allow_player_look` | `true` | Allows changing view direction. / 視点変更を許可します。 |
| `recovery.lock.allow_sneak` | `true` | Allows sneaking. / スニークを許可します。 |
| `recovery.lock.allow_block_break` | `false` | Allows breaking blocks. / ブロック破壊を許可します。 |
| `recovery.lock.allow_block_place` | `false` | Allows placing blocks. / ブロック設置を許可します。 |
| `recovery.lock.allow_item_use` | `false` | Allows item use. / アイテム使用を許可します。 |
| `recovery.lock.allow_entity_attack` | `false` | Allows attacking entities. / Entityへの攻撃を許可します。 |
| `recovery.lock.allow_entity_interact` | `false` | Allows interacting with entities. / Entityとの操作を許可します。 |
| `recovery.lock.allow_container_interaction` | `false` | Allows opening and using containers. / コンテナの操作を許可します。 |
| `recovery.lock.allow_item_drop` | `false` | Allows dropping items. / アイテム投棄を許可します。 |
| `recovery.lock.allow_item_pickup` | `false` | Allows picking up items. / アイテム取得を許可します。 |

</details>

<details>
<summary>Real-player snapshots / 実プレイヤーの保存項目</summary>

By default, only dimension, position, and rotation are restored. Other player data remains unchanged unless enabled. Enabling inventory or progression fields can conflict with external player-data synchronization. / 初期設定ではディメンション・位置・向きだけを復元します。ほかの項目は有効にした場合のみ対象です。インベントリや経験値などを有効にすると、外部のプレイヤーデータ同期と競合する場合があります。

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `player_snapshot.enabled` | `true` | Enables configured real-player snapshots. / 実プレイヤーの設定済み項目を保存・復元します。 |
| `player_snapshot.dimension` | `true` | Saves and restores dimension. / ディメンションを保存・復元します。 |
| `player_snapshot.position` | `true` | Saves and restores position. / 位置を保存・復元します。 |
| `player_snapshot.rotation` | `true` | Saves and restores rotation. / 向きを保存・復元します。 |
| `player_snapshot.gamemode` | `false` | Saves and restores game mode. / ゲームモードを保存・復元します。 |
| `player_snapshot.abilities` | `false` | Saves and restores player abilities. / プレイヤー能力を保存・復元します。 |
| `player_snapshot.inventory_main` | `false` | Saves and restores the main inventory. / メインインベントリを保存・復元します。 |
| `player_snapshot.inventory_armor` | `false` | Saves and restores armor slots. / 防具スロットを保存・復元します。 |
| `player_snapshot.inventory_offhand` | `false` | Saves and restores the offhand slot. / オフハンドを保存・復元します。 |
| `player_snapshot.selected_hotbar_slot` | `false` | Saves and restores the selected hotbar slot. / 選択中のホットバースロットを保存・復元します。 |
| `player_snapshot.experience_level` | `false` | Saves and restores experience level. / 経験値レベルを保存・復元します。 |
| `player_snapshot.experience_progress` | `false` | Saves and restores progress toward the next level. / 次のレベルまでの経験値進捗を保存・復元します。 |
| `player_snapshot.experience_total` | `false` | Saves and restores total experience. / 累計経験値を保存・復元します。 |
| `player_snapshot.ender_chest` | `false` | Saves and restores ender-chest contents. / エンダーチェストの内容を保存・復元します。 |
| `player_snapshot.health` | `false` | Saves and restores health. / 体力を保存・復元します。 |
| `player_snapshot.absorption` | `false` | Saves and restores absorption hearts. / 吸収体力を保存・復元します。 |
| `player_snapshot.food_level` | `false` | Saves and restores food level. / 満腹度を保存・復元します。 |
| `player_snapshot.food_saturation` | `false` | Saves and restores saturation. / 隠し満腹度を保存・復元します。 |
| `player_snapshot.food_exhaustion` | `false` | Saves and restores exhaustion. / 疲労度を保存・復元します。 |
| `player_snapshot.potion_effects` | `false` | Saves and restores status effects. / ステータス効果を保存・復元します。 |
| `player_snapshot.velocity` | `false` | Saves and restores velocity. / 速度を保存・復元します。 |
| `player_snapshot.fall_distance` | `false` | Saves and restores fall distance. / 落下距離を保存・復元します。 |
| `player_snapshot.fire_ticks` | `false` | Saves and restores remaining fire ticks. / 残りの炎上tickを保存・復元します。 |
| `player_snapshot.air` | `false` | Saves and restores air supply. / 酸素量を保存・復元します。 |
| `player_snapshot.spawn_point` | `false` | Saves and restores the personal spawn point. / 個人スポーン地点を保存・復元します。 |

</details>

<details>
<summary>Vanilla player files / Vanillaプレイヤーファイル</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `player_snapshot.vanilla_files.restore_playerdata` | `false` | Restores the complete Vanilla player-data file. / Vanillaのプレイヤーデータファイル全体を復元します。 |
| `player_snapshot.vanilla_files.restore_stats` | `false` | Restores the player's statistics file. / プレイヤー統計ファイルを復元します。 |
| `player_snapshot.vanilla_files.restore_advancements` | `false` | Restores the player's advancements file. / プレイヤーの進捗ファイルを復元します。 |

These options replace whole files. Check compatibility with player-data synchronization or database mods before enabling them. / 有効にすると各ファイル全体を置き換えます。プレイヤーデータ同期やデータベース連携との競合を確認してください。

</details>

<details>
<summary>FakePlayer recovery / FakePlayerの復旧</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `fakeplayer.enabled` | `true` | Enables FakePlayer snapshots and recovery. / FakePlayerの保存・復旧を有効にします。 |
| `fakeplayer.auto_rejoin` | `true` | Rejoins saved FakePlayers automatically during recovery. / 復旧時に保存済みFakePlayerを自動で再参加させます。 |
| `fakeplayer.restore_before_unfreeze` | `true` | Restores FakePlayers before game time resumes. / ゲーム内時間を再開する前にFakePlayerを復元します。 |
| `fakeplayer.require_all_ready_before_start` | `true` | Requires all saved FakePlayers to be ready before `/ac start`. / 保存したFakePlayerが揃うまで`/ac start`を拒否します。 |
| `fakeplayer.snapshot.dimension` | `true` | Saves and restores dimension. / ディメンションを保存・復元します。 |
| `fakeplayer.snapshot.position` | `true` | Saves and restores position. / 位置を保存・復元します。 |
| `fakeplayer.snapshot.rotation` | `true` | Saves and restores rotation. / 向きを保存・復元します。 |
| `fakeplayer.snapshot.gamemode` | `true` | Saves and restores game mode. / ゲームモードを保存・復元します。 |
| `fakeplayer.snapshot.flying` | `true` | Saves and restores flying state. / 飛行状態を保存・復元します。 |
| `fakeplayer.snapshot.inventory` | `true` | Saves and restores inventory. / インベントリを保存・復元します。 |
| `fakeplayer.snapshot.selected_slot` | `true` | Saves and restores the selected hotbar slot. / 選択中のホットバースロットを保存・復元します。 |
| `fakeplayer.snapshot.health` | `true` | Saves and restores health. / 体力を保存・復元します。 |
| `fakeplayer.snapshot.hunger` | `true` | Saves and restores hunger state. / 満腹度を保存・復元します。 |
| `fakeplayer.snapshot.effects` | `true` | Saves and restores status effects. / ステータス効果を保存・復元します。 |
| `fakeplayer.snapshot.sneaking` | `true` | Saves and restores sneaking state. / スニーク状態を保存・復元します。 |
| `fakeplayer.snapshot.sprinting` | `true` | Saves and restores sprinting state. / ダッシュ状態を保存・復元します。 |
| `fakeplayer.snapshot.movement` | `true` | Saves and restores movement input state. / 移動入力状態を保存・復元します。 |
| `fakeplayer.snapshot.actions` | `true` | Saves and restores Carpet action-pack actions. / CarpetのActionPack操作を保存・復元します。 |
| `fakeplayer.snapshot.action_internal_timing` | `true` | Preserves action timing fields such as interval, count, and next action. / interval・count・nextなど操作タイミングを保存・復元します。 |
| `fakeplayer.snapshot.item_use_cooldown` | `true` | Saves and restores item-use cooldown. / アイテム使用のクールダウンを保存・復元します。 |
| `fakeplayer.snapshot.block_break_progress` | `true` | Saves and restores block-breaking progress. / ブロック破壊の進捗を保存・復元します。 |

</details>

<details>
<summary>Login and notifications / ログインと通知</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `login.retry_timeout_seconds` | `5` | Retries a login refused by AntiCrash during a checkpoint barrier or recovery freeze; `0` disconnects immediately (maximum 20 seconds). / チェックポイント境界や復旧停止中にAntiCrashが拒否したログインを再試行します。`0`は即時切断です（最大20秒）。 |
| `notifications.frozen_action_bar` | `true` | Shows an action-bar message explaining a recovery freeze. / 復旧停止の理由をアクションバーに表示します。 |
| `notifications.checkpoint_action_bar_delay_ticks` | `10` | Retained for config compatibility. Checkpoint creation does not show an action-bar message. / 互換性のために残る設定です。チェックポイント作成時のアクションバー表示には使いません。 |
| `notifications.ask_prompt_in_game` | `true` | Also shows the `ASK` prompt in-game; answers can be given with `/ac y` or `/ac n`. / `ASK`の質問をゲーム内にも表示し、`/ac y`・`/ac n`で回答できるようにします。 |

</details>

<details>
<summary>Consistency tracking / 整合性の記録</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `consistency.mode` | `"IGNORE"` | Tracking mode: `IGNORE` (off), `WARN` (warnings), or `JOURNAL` (detailed journal). / 記録方法です。`IGNORE`（無効）・`WARN`（警告）・`JOURNAL`（詳細記録）を指定します。 |

</details>

<details>
<summary>Logging / ログ</summary>

| Setting | Default | Description / 説明 |
| --- | --- | --- |
| `logging.level` | `"NORMAL"` | Detail level: `OFF`, `IMPORTANT`, `NORMAL`, `VERBOSE`, or `TRACE`. Fatal errors are still logged. / 詳細度です。`OFF`・`IMPORTANT`・`NORMAL`・`VERBOSE`・`TRACE`を指定します。致命的エラーは常に記録します。 |
| `logging.console` | `true` | Writes logs to the server console. / サーバーコンソールへ出力します。 |
| `logging.console_color` | `"AUTO"` | Console colors: `AUTO`, `ON`, or `OFF`. / コンソールの色設定です。`AUTO`・`ON`・`OFF`を指定します。 |
| `logging.chat_color` | `true` | Uses colors in command replies and in-game notices. / コマンド応答やゲーム内通知に色を付けます。 |
| `logging.file` | `true` | Writes AntiCrash logs to a file. / AntiCrashのログをファイルへ出力します。 |
| `logging.max_file_mb` | `50` | Maximum size of each log file before rotation, in MB (1–10,240). / ローテーション前のログファイル上限（MB）です（1〜10,240）。 |
| `logging.retained_files` | `10` | Number of rotated log files to retain (1–1,000). / 保持するローテーション済みログ数です（1〜1,000）。 |
| `logging.show_checkpoint_contents` | `false` | Includes checkpoint file contents in detailed logs. / 詳細ログにチェックポイント内のファイル情報を含めます。 |
| `logging.show_player_details` | `false` | Includes real-player details in logs. / ログに実プレイヤーの詳細を含めます。 |
| `logging.show_fakeplayer_details` | `true` | Includes FakePlayer details in logs. / ログにFakePlayerの詳細を含めます。 |
| `logging.show_block_events` | `false` | Includes block-event details in logs. / ログにBlock Eventの詳細を含めます。 |
| `logging.show_file_operations` | `false` | Includes file-operation details in logs. / ログにファイル操作の詳細を含めます。 |
| `logging.show_transaction_details` | `false` | Includes checkpoint transaction details in logs. / ログにチェックポイントの処理詳細を含めます。 |

</details>
