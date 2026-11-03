# AntiCrash

**Minecraft Fabric** — by AtsukiMC · **v1.0.2** · **LGPL-3.0-only**

[English](#english) · [日本語](#日本語)

---

## English

### Overview

AntiCrash regularly saves the world along with states that Minecraft does not normally save, such as piston movement and queued block events. These saves are called **checkpoints**. Restoring one after an unexpected shutdown helps protect Technical Minecraft machines from breaking.

After restoration, AntiCrash prepares the required chunks and keeps **game time paused until `/ac start`**. This gives players time to join and get ready before their machines resume. With Carpet installed, AntiCrash also restores saved fake players and their actions.

| Feature | Default or requirement |
| --- | --- |
| Install on | **Server only** |
| Automatic checkpoints | **Every 30 seconds** |
| Recovery after an unexpected shutdown | **Automatic** (`AUTO`) |
| After restoration | **Paused until `/ac start`** |
| Fabric API | **Not required for basic use** |
| Carpet | **Optional** |

### Installation

Stop the server and back up the world first. Choose the v1.0.2 Mod JAR for your **exact Minecraft version** from [Release 1.0.2](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.2). For Minecraft 1.18.2, use `AntiCrash-1.0.2+mc1.18.2.jar`.

Put the Mod JAR in the server's `mods/` folder, then start the Fabric server. AntiCrash creates `config/anticrash.toml`, where you can change its settings. Players do not need to install the mod.

The Supervisor is a separate program for automatic restarts. If you use it, place its JAR in the server directory, **outside `mods/`**.

| File | Location | Purpose |
| --- | --- | --- |
| `AntiCrash-1.0.2+mc<VERSION>.jar` | Server `mods/` folder | Checkpoints and restoration |
| `AntiCrash-Supervisor-1.0.2+mc<VERSION>.jar` | Server directory | Process monitoring and restarts |

#### Supported Minecraft versions

| Minecraft | Minimum Fabric Loader | Minimum Java for the server |
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

Each listed Minecraft version has its own JAR. All 30 versions pass builds and automated checks. The same implementation was tested for basic checkpoint creation and recovery on isolated servers; this does not guarantee every machine or mod combination.

### Recovery guide

#### After an unexpected shutdown

With the default settings, the next startup checks and restores a valid checkpoint. AntiCrash then prepares the required chunks and saved state while game time stays paused.

When preparation finishes, run **`/ac start`** to resume. A message confirms that game time has started. You can check progress with `/ac status`.

By default, **any player can run `/ac start`**. Other commands require permission level 4. `/ac start` resumes the restored world; it does not choose a checkpoint.

#### Recovery policy

In the `[recovery]` section of the config, `policy` controls when to restore.

| Policy | After an unexpected shutdown | After a normal shutdown |
| --- | --- | --- |
| `AUTO` — default | Restore automatically | Start normally |
| `ASK` | Ask; no answer means **Y** | Ask; no answer means **N** |
| `MANUAL` | Ask; do not restore without an answer | Start normally |

When the question appears **before the world loads**, type `Y` or `N` in the **server console** and press Enter. `Y` restores a checkpoint after validation. `N` starts the saved world without restoring a checkpoint.

The answer deadline is `ask_timeout_seconds`, which defaults to **60 seconds**. Set it to `0` to wait indefinitely when an interactive terminal or input is available. Without either, `ASK` uses the default above and `MANUAL` refuses recovery. With `MANUAL`, reaching the deadline is treated as a recovery failure; it does not automatically restore. The older `ask_default_action` setting is kept for compatibility and does not change these rules.

After a normal shutdown, choosing `Y` also restores the supported machine state saved in the checkpoint. Choosing `N` starts the world as Vanilla would, so a machine stopped mid-cycle may not continue in the same way.

#### Recovery mode

In `[recovery]`, `fidelity` controls how strictly AntiCrash checks the restored state.

| Mode | Behavior |
| --- | --- |
| `OPTIMIZED` — default | Restore as much as the selected recovery method supports |
| `STRICT` | Keep game time paused if required state is missing, unsupported, or different from the checkpoint |

If STRICT refuses to start, `/ac recovery verify --verbose` shows the reason. **OPTIMIZED does not guarantee tick-boundary equivalence**: machines may not resume with exactly the same timing as at the checkpoint.

### Configuration

Edit **`config/anticrash.toml`**, then run `/ac config reload` to apply changes. Change startup recovery settings before the next startup. To edit an existing file, replace the relevant values without adding a second copy of the same section.

#### Language and automatic saving

At the top of the file, `language = "ja"` selects Japanese and `language = "en"` selects English. This changes AntiCrash messages, not Minecraft or Fabric logs.

Under `[checkpoint]`, `enabled = true` enables automatic checkpoints. `interval_seconds = 30` saves every 30 seconds of real time, regardless of the game's tick rate.

#### How many checkpoints to keep

The following settings are all under `[checkpoint]`.

**`retention_count = 20`** keeps the newest 20 checkpoints, subject to the other limits below. Use a whole number of at least 1. **`0` does not mean unlimited; AntiCrash changes it to `1` when loading the config.**

**`retention_days = 7`** makes checkpoints older than 7 days eligible for deletion. Set it to `0` to keep them regardless of age.

**`retention_max_gb = 0`** means there is no storage-size limit. For example, `10` enables a 10 GB limit and makes older checkpoints eligible for deletion when their total size exceeds it.

Exceeding any one of these limits can make an old checkpoint eligible for deletion. The newest checkpoint is always kept. With the defaults, AntiCrash keeps up to 20 checkpoints and removes ones older than 7 days, except for the newest one.

At 30 seconds per checkpoint, 20 checkpoints cover roughly the last 10 minutes. Setting `retention_days = 7` alone does not keep seven days of saves if the count limit is reached first.

#### Pausing after restoration and permissions

To wait for `/ac start` after restoration, keep both `freeze_after_restore` and `require_manual_start` set to `true` under `[recovery]`. Setting either to `false` requests automatic resume after preparation and checks.

Under `[commands]`, `start_permission_level = 0` lets all players resume game time. Change it to `4` to restrict this to administrators with permission level 4. The other commands default to level 4 and have their own permission settings.

#### Chunk loading

Under `[recovery]`, `preload_mode = "ALL"` prepares all chunks that were loaded at the checkpoint. `ENTITY_TICKING` selects chunks where entities were ticking; `CRITICAL` selects important locations such as moving pistons and fake players. These narrower selections can leave part of a machine unprepared. STRICT requires its full recorded chunk set regardless of this choice.

`max_preload_chunks` is the preparation limit, **100,000** by default. If more chunks are required, recovery fails instead of dropping them. Under `[recovery.chunks]`, `max_concurrent_loads = 4` limits simultaneous loads to four; it does not reduce the total number needed.

#### Players, fake players, and messages

By default, real players have only their dimension, position, and rotation restored. Inventory and experience are not rolled back. Optional fields under `[player_snapshot]` can change this, so check for conflicts before enabling them alongside a player-data synchronization mod.

Fake-player restoration is enabled under `[fakeplayer]`. With a compatible Carpet version, it restores saved actions and their timing as well as position and inventory. Recovery normally waits for all required fake players before resuming.

Checkpoint creation does **not** display an action-bar message. `[notifications].frozen_action_bar` controls messages while the world is paused for recovery. Under `[logging]`, `level = "NORMAL"` controls how much detail is logged.

#### All default settings

The full v1.0.2 defaults are below. Comments are omitted to make the values easier to find.

<details>
<summary>Show all 156 settings</summary>

```toml
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
target_barrier_ms = 20
warn_barrier_ms = 50
critical_barrier_ms = 100
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
mode = "LOGICAL_DELTA"
fallback = "PORTABLE_GENERATION"
single_inflight = true
allow_unsupported_persistent_state = false

[checkpoint.cache]
max_mb = 256
budget_ms_per_tick = 1.0

[checkpoint.mirror]
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
fidelity = "OPTIMIZED"
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
preload_mode = "ALL"
max_preload_chunks = 100000
chunk_ready_timeout_seconds = 120

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
console_color = "AUTO"
chat_color = true
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

</details>

### Main commands

Use the leading `/` in game; omit it in the server console.

| Command | Purpose |
| --- | --- |
| `/ac help` | Show command help |
| `/ac status` | Show saving and recovery progress |
| `/ac start` | Resume game time |
| `/ac freeze` | Pause game time |
| `/ac checkpoint now` | Save a checkpoint now |
| `/ac checkpoint list` | List saved checkpoints |
| `/ac checkpoint info <id>` | Show a checkpoint's details |
| `/ac checkpoint base` | Create an independent base checkpoint |
| `/ac checkpoint restore <id>` | Schedule restoration and restart; rolls back the world |
| `/ac recovery verify` | Check whether recovery is ready |
| `/ac debug equivalence` | Show differences from the checkpoint |
| `/ac recovery abort` | Cancel recovery and schedule a return to the preserved pre-rollback world, if available |
| `/ac fakeplayers` | Show saved fake-player information |
| `/ac config reload` | Reload settings |

Use `latest` instead of an ID to restore the latest checkpoint, for example `/ac checkpoint restore latest`. Add `--verbose` to either diagnostic command for details, for example `/ac recovery verify --verbose`.

### Restarting in the same console

To restart Minecraft automatically in the same window, launch it through the **Supervisor**. The Supervisor keeps watching Minecraft and shares the console, so you can still answer Y/N there. A normal `/stop` does not trigger a restart.

Place the matching Supervisor JAR in the server directory and use a command such as this one for 1.18.2:

```sh
java -jar AntiCrash-Supervisor-1.0.2+mc1.18.2.jar \
  --workdir /path/to/server -- \
  java -Xms1G -Xmx1G -jar fabric-server-launch.jar nogui
```

Replace the path, version, server JAR name, and memory options with your own. The command after `--` is your usual server launch command. The 1 GB setting is only an example; required memory depends on the world.

You can use checkpoints without the Supervisor. If you want its restart handling, change `start.sh` or your management tool to launch this command. PID, exit-code, and retry logs help identify a failed restart.

### When something goes wrong

If game time stays paused, check `/ac status`. If `/ac start` is rejected, run `/ac recovery verify --verbose` and `/ac debug equivalence --verbose` to see what is missing or different.

AntiCrash cannot guarantee recovery of every machine, mod, or asynchronous operation. Keep independent backups and test important machines on a world copy. Restoration rolls changes back to the selected checkpoint; it does not roll back external databases. Do not restore checkpoints across Minecraft versions.

### Building and license

Clone the whole repository. Run Gradle with **JDK 25** and install **JDK 17 and 21** as compilation/test toolchains. The server's minimum Java requirement in the version table does not change.

```sh
./gradlew -PmcVersion=1.18.2 :1.18.2:test :1.18.2:build
python3 scripts/build_matrix.py --all --qualification
```

The first command builds one version. The second tests and builds all 30 versions sequentially, checks the JARs, and collects candidates in `dist/refactor/1.0.2/`. The root `./gradlew buildAll` builds all targets in one Gradle invocation; the Python runner is recommended to keep memory usage bounded. Python 3.11 or newer is required for these scripts.

There is one `src/main/java` tree and one `src/test/java` tree. Stonecutter selects API differences using version conditions in the same files; `stonecutter.properties.toml` holds the target versions and pinned dependencies. `gradle/mixins` contains ordered Mixin profiles. `supervisor/` is an independent Java component. Isolated server probes live in `src/qualification` and never enter the Mod JAR. The generated `versions/` and `build/` directories are not source directories.

To add a Minecraft target, add its dependency/API properties to the TOML matrix, review all affected source conditions and Mixin targets against mapped bytecode, then run its tests, build, artifact inspection and isolated server checks. Do not assume nearby versions have identical APIs.

Version 1.0.2 fixes the chunk-storage Mixin failure and BASE checkpoint failure, and improves shared code, recovery validation, diagnostics, configuration tests, and Carpet integration. The save format is unchanged. The shared-source and audit improvements incorporate aomona's contribution. Download all supported Minecraft JARs from [GitHub Releases](https://github.com/HikariServerDev/AntiCrash/releases): tags `1.0.0`, `1.0.1`, and `1.0.2` identify each Mod release. Older releases are preserved unchanged; use 1.0.2 for the fixes. After building, run `python3 scripts/verify_artifacts.py --version 26.3 --collect` from the repository root, replacing `26.3` with the target version. This verifies the JARs and collects them in `dist/refactor/1.0.2/`. Generated JARs and logs are not stored in Git.

License: **GNU Lesser General Public License v3.0 only** (`LGPL-3.0-only`).

---

## 日本語

### 概要

AntiCrashは、Minecraftが通常は保存しないピストンの動作やブロックイベントなどの状態を、ワールドと一緒に定期保存するModです。この保存を**チェックポイント**と呼びます。異常終了後にチェックポイントへ戻すことで、TMC装置の破損を防ぐことを目指します。

復元後は必要なチャンクを読み込み、**`/ac start`を実行するまでゲーム内時間を停止**できます。プレイヤーが関わる装置でも、参加して準備を整えてから再開できます。Carpetを導入している場合は、保存したFakePlayerとその操作も自動で復元します。

| 主な仕様 | 初期設定・条件 |
| --- | --- |
| 導入先 | **サーバーのみ** |
| 自動保存 | **30秒ごと** |
| 異常終了後の復旧 | **自動復元**（`AUTO`） |
| 復元後 | **`/ac start`まで停止** |
| Fabric API | 基本利用では**不要** |
| Carpet | **任意** |

### 導入方法

サーバーを停止してワールドをバックアップしてから、[Release 1.0.2](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.2)にある、**Minecraftのバージョンと完全に一致するv1.0.2のMod JAR**を選んでください。Minecraft 1.18.2の場合は`AntiCrash-1.0.2+mc1.18.2.jar`です。

Mod JARをサーバーの`mods/`に入れ、Fabricサーバーを起動します。`config/anticrash.toml`が生成され、このファイルから設定を変更できます。プレイヤー側へのMod導入は不要です。

Supervisorは、自動再起動のための別プログラムです。使う場合は、JARを**`mods/`の外にあるサーバーディレクトリ**へ置いてください。

| ファイル | 配置先 | 用途 |
| --- | --- | --- |
| `AntiCrash-1.0.2+mc<VERSION>.jar` | サーバーの`mods/` | 保存・復元 |
| `AntiCrash-Supervisor-1.0.2+mc<VERSION>.jar` | サーバーディレクトリ | プロセス監視・再起動 |

#### 対応Minecraftバージョン

| Minecraft | Fabric Loaderの最低バージョン | サーバー実行時のJavaの最低バージョン |
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

各バージョンに専用のJARがあります。30版すべてでビルドと自動検査を通過しています。同じ実装で隔離サーバーの基本的な保存・復旧も確認していますが、すべての装置やModの組み合わせを保証するものではありません。

### 復旧ガイド

#### 異常終了後の動作

初期設定では、次回起動時に有効なチェックポイントを確認して復元します。その後、ゲーム内時間を止めたまま、必要なチャンクや保存した状態を準備します。

準備が終わったら**`/ac start`**を実行してください。ゲーム内時間が再開され、完了メッセージが表示されます。途中の状況は`/ac status`で確認できます。

初期設定では、**`/ac start`は誰でも実行できます**。その他のコマンドには権限レベル4が必要です。`/ac start`は復元済みのワールドを再開するもので、チェックポイントを選ぶコマンドではありません。

#### 復旧ポリシー

設定ファイルの`[recovery]`にある`policy`で、いつ復元するかを選びます。

| ポリシー | 異常終了後 | 正常終了後 |
| --- | --- | --- |
| `AUTO`（初期設定） | 自動復元 | 通常起動 |
| `ASK` | 復元するか質問。無回答は**Y** | 復元するか質問。無回答は**N** |
| `MANUAL` | 復元するか質問。無回答では復元しない | 通常起動 |

**ワールド読み込み前に質問が表示されたら、サーバーコンソールに`Y`または`N`を入力し、Enterを押します。** `Y`なら検証後に復元し、`N`ならチェックポイントを使わず通常起動します。

回答期限は`ask_timeout_seconds`で、初期値は**60秒**です。`0`では、操作できる端末や到着済みの入力がある場合に無期限で待ちます。入力手段がない場合、`ASK`は上の表の回答を使い、`MANUAL`は復旧を開始しません。`MANUAL`で期限を過ぎた場合は、自動復元せず復旧失敗として扱います。旧設定の`ask_default_action`は互換性のために残っている項目で、これらの動作は変更しません。

正常終了後に`Y`を選ぶと、停止時のチェックポイントに保存された装置の状態も復元します。`N`はVanillaと同じ通常起動なので、動作途中で停止した装置が同じように動き続けるとは限りません。

#### 復旧モード

`[recovery]`の`fidelity`で、復元した状態をどこまで厳密に確認するかを選びます。

| モード | 動作 |
| --- | --- |
| `OPTIMIZED`（初期設定） | 選んだ復旧方式で対応できる範囲を復元 |
| `STRICT` | 必要な状態が欠けている・未対応・保存時と異なる場合は、時間を止めたままにする |

STRICTで再開できないときは、`/ac recovery verify --verbose`で理由を確認できます。OPTIMIZEDでは、保存時とまったく同じ動作タイミングで再開する保証はありません。

### 設定方法

**`config/anticrash.toml`**を編集し、`/ac config reload`で再読み込みします。起動時の復旧設定は、次回起動前に変更してください。既存ファイルを編集するときは、同じセクションを追加せず、その項目の値を書き換えます。

#### 言語と自動保存

ファイル先頭の`language = "ja"`で日本語、`language = "en"`で英語になります。変わるのはAntiCrashのメッセージで、MinecraftやFabricのログの言語は変わりません。

`[checkpoint]`の`enabled = true`で自動保存が有効になります。`interval_seconds = 30`は、実時間で30秒ごとに保存する設定です。ゲーム内のtick速度を変えても、この秒数は変わりません。

#### 保存を何件残すか

次の3項目は、すべて`[checkpoint]`内の設定です。

**`retention_count = 20`**は、新しいチェックポイントを20件残す設定です。指定できるのは1以上の整数です。**`0`は無制限という意味にはならず、読み込み時に`1`へ補正されます。**

**`retention_days = 7`**は、作成から7日を過ぎたチェックポイントを削除対象にする設定です。`0`なら、日数を理由に削除しません。

**`retention_max_gb = 0`**は、保存容量に上限を設けない設定です。例えば`10`にすると、合計が10GBを超えたときに古いチェックポイントが削除対象になります。

件数・日数・容量のどれかの上限を超えると、古いチェックポイントが削除対象になります。ただし、最新の1件は残します。初期設定では「新しいものを最大20件残し、7日を過ぎたものは削除する。ただし最新の1件は残す」という動作です。

30秒ごとに保存して20件残す場合、保存されるのはおおよそ直近10分です。`retention_days = 7`にしていても、件数の上限に先に達すれば7日分は残りません。

#### 復元後の停止と権限

復元後に`/ac start`を待たせるには、`[recovery]`の`freeze_after_restore`と`require_manual_start`を両方`true`にします。どちらかを`false`にすると、準備と検証が終わったあとに自動再開する設定になります。

`[commands]`の`start_permission_level = 0`では、誰でも時間を再開できます。`4`に変えると、権限レベル4の管理者だけが再開できます。他のコマンドは初期値が4で、それぞれ個別に権限を設定できます。

#### チャンクの読み込み

`[recovery]`の`preload_mode = "ALL"`では、保存時に読み込まれていた全チャンクを準備します。`ENTITY_TICKING`はエンティティが動作していたチャンク、`CRITICAL`は移動中のピストンやFakePlayerなどの重要地点を選びます。対象を絞ると、装置の一部が準備されないことがあります。STRICTでは、この設定にかかわらず保存時の必要な全チャンクが要求されます。

`max_preload_chunks`は準備するチャンク数の上限で、初期値は**100,000**です。必要数が上限を超えた場合、対象を減らして続行せず、復旧を失敗させます。`[recovery.chunks]`の`max_concurrent_loads = 4`は、同時に読み込む数を4件にする設定で、最終的に必要なチャンク数は変わりません。

#### プレイヤー・FakePlayer・通知

通常プレイヤーは、初期設定ではディメンション・位置・向きだけを復元します。インベントリや経験値は巻き戻しません。`[player_snapshot]`で対象を増やせますが、プレイヤーデータを同期するModと併用する場合は、設定が競合しないか確認してください。

FakePlayerの復元は`[fakeplayer]`で有効になっています。対応するCarpetを導入していれば、位置や持ち物に加え、保存した操作とそのタイミングも復元します。通常は、必要なFakePlayerがそろってから時間を再開します。

チェックポイント作成中にアクションバーの文字は表示しません。`[notifications].frozen_action_bar`は復旧中の停止表示に関する設定です。ログの詳しさは`[logging]`の`level = "NORMAL"`で変更できます。

#### 初期設定の全項目

v1.0.2の初期値は次のとおりです。値を探しやすいよう、説明コメントは省略しています。

<details>
<summary>全156項目の初期設定を表示する</summary>

```toml
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
target_barrier_ms = 20
warn_barrier_ms = 50
critical_barrier_ms = 100
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
mode = "LOGICAL_DELTA"
fallback = "PORTABLE_GENERATION"
single_inflight = true
allow_unsupported_persistent_state = false

[checkpoint.cache]
max_mb = 256
budget_ms_per_tick = 1.0

[checkpoint.mirror]
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
fidelity = "OPTIMIZED"
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
preload_mode = "ALL"
max_preload_chunks = 100000
chunk_ready_timeout_seconds = 120

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
console_color = "AUTO"
chat_color = true
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

</details>

### 主なコマンド

ゲーム内では先頭に`/`を付け、サーバーコンソールでは省きます。

| コマンド | 用途 |
| --- | --- |
| `/ac help` | コマンドヘルプを表示 |
| `/ac status` | 保存・復旧の進行状況を表示 |
| `/ac start` | ゲーム内時間を再開 |
| `/ac freeze` | ゲーム内時間を停止 |
| `/ac checkpoint now` | 今すぐチェックポイントを作成 |
| `/ac checkpoint list` | 保存済みチェックポイントを一覧表示 |
| `/ac checkpoint info <id>` | 指定したチェックポイントの詳細を表示 |
| `/ac checkpoint base` | 独立した基準チェックポイントを作成 |
| `/ac checkpoint restore <id>` | 復元と再起動を予約。ワールドが巻き戻ります。 |
| `/ac recovery verify` | 復旧の準備ができているか確認 |
| `/ac debug equivalence` | 保存時の状態との差分を表示 |
| `/ac recovery abort` | 復旧を中止し、退避済みの巻き戻し前ワールドへ戻す処理を予約 |
| `/ac fakeplayers` | 保存したFakePlayerの情報を表示 |
| `/ac config reload` | 設定を再読み込み |

最新の保存へ戻す場合は、IDの代わりに`latest`を指定し、`/ac checkpoint restore latest`と入力します。詳しい診断結果を見る場合は、`/ac recovery verify --verbose`や`/ac debug equivalence --verbose`を使います。

### 同じコンソールで再起動する方法

Minecraftを同じウィンドウで自動再起動したい場合は、**Supervisor**から起動します。SupervisorがMinecraftの監視を続け、同じコンソールでログ確認やY/N入力ができます。通常の`/stop`では自動再起動しません。

対応するSupervisor JARをサーバーディレクトリに置き、次のように起動します。1.18.2の例です。

```sh
java -jar AntiCrash-Supervisor-1.0.2+mc1.18.2.jar \
  --workdir /path/to/server -- \
  java -Xms1G -Xmx1G -jar fabric-server-launch.jar nogui
```

パス・Minecraft版・サーバーJAR名・メモリ指定は、自分の環境に合わせて変更してください。`--`より後ろには、普段使っているサーバーの起動コマンドを指定します。1GBは例であり、必要なメモリ量はワールドによって変わります。

Supervisorなしでも保存・復元は使えます。Supervisorの再起動機能を使う場合に、`start.sh`や管理ツールの起動コマンドを変更してください。再起動できなかった場合は、PID・終了コード・試行回数のログを確認できます。

### うまく動かないときは

時間が止まったままなら、まず`/ac status`で状況を確認してください。`/ac start`が拒否される場合は、`/ac recovery verify --verbose`と`/ac debug equivalence --verbose`で、不足しているものや保存時との違いを確認できます。

すべての装置・Mod・非同期処理の復元を保証するものではありません。通常のバックアップも残し、大切な装置はワールドのコピーで復旧を試してください。復元すると選んだチェックポイント以降の変更は巻き戻りますが、外部データベースまでは巻き戻りません。異なるMinecraft版のチェックポイントは使用しないでください。

### ビルドとライセンス

リポジトリ全体を取得し、Gradleの実行に**JDK 25**を使ってください。コンパイル・テスト用に**JDK 17と21**も必要です。サーバー実行時の最低Javaバージョンは、対応表のとおりです。

```sh
./gradlew -PmcVersion=1.18.2 :1.18.2:test :1.18.2:build
python3 scripts/build_matrix.py --all --qualification
```

1つ目は1.18.2だけをビルドします。2つ目は全30版を順番にテスト・ビルドし、JARの検査後に`dist/refactor/1.0.2/`へ集めます。ルートの`./gradlew buildAll`でも全版をビルドできます。メモリを抑える場合は、版ごとにGradleを終了するPythonスクリプトを使ってください。スクリプトにはPython 3.11以上が必要です。

ソースは`src/main/java`、単体テストは`src/test/java`に集約しています。同じファイル内のStonecutter条件分岐でAPI差分を扱い、対応版と依存関係は`stonecutter.properties.toml`で管理します。`gradle/mixins`には登録順を維持したMixin設定、`supervisor/`には独立した監視プログラムを置いています。隔離サーバー検証用の`src/qualification`は、配布Modに含まれません。`versions/`と`build/`は生成物です。

Minecraft版を追加するときは、TOMLへ依存関係とAPI設定を追加します。関係するソース条件とMixin対象を実際のバイトコードで確認し、その版のテスト・ビルド・JAR検査・隔離サーバー検証を行ってください。近いバージョンでもAPIが同じとは限りません。

1.0.2ではチャンク保存時のMixin適用失敗とBASE保存失敗を修正し、共有コード・復旧判定・診断・設定テスト・Carpet連携を改善しました。従来の保存形式は維持しています。共通化・監査の改善にはaomonaさんの提案を取り入れています。

各Minecraft版のJARは[GitHub Releases](https://github.com/HikariServerDev/AntiCrash/releases)で配布します。タグ`1.0.0`・`1.0.1`・`1.0.2`がModの各版に対応します。過去版は当時のJARをそのまま残しています。今回の修正を使う場合は1.0.2を選んでください。ビルド後、リポジトリのルートで`python3 scripts/verify_artifacts.py --version 26.3 --collect`を実行すると、JARを検査して`dist/refactor/1.0.2/`へ集められます。`26.3`は対象のMinecraft版に置き換えてください。生成物やログはGitには含めません。

ライセンスは**GNU Lesser General Public License v3.0 only**（`LGPL-3.0-only`）です。
