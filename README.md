# AntiCrash

**Minecraft Fabric server mod** · AtsukiMC · **v1.0.6** · **LGPL-3.0-only**

[Read this in Japanese](README-ja.md)

## English

AntiCrash creates checkpoints of world data and supported runtime state. By default, recovery keeps game time paused until preparation finishes; run `/ac start` to resume. Compatible Carpet versions enable FakePlayer restoration.

Recovery coverage varies by Minecraft version and installed mods. `OPTIMIZED` uses best-effort recovery. `STRICT` blocks resuming when required state is missing or differs from the checkpoint.

### Installation

Download the JAR for the exact Minecraft version from [Release 1.0.6](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.6). Install the Mod on the server; clients can join with a Vanilla client. Fabric API is optional. Install a compatible Carpet version for FakePlayer integration.

| Component | File name | Location |
| --- | --- | --- |
| Mod | `AntiCrash-1.0.6+mc<VERSION>.jar` | Server `mods/` folder |
| Supervisor (optional) | `AntiCrash-Supervisor-1.0.6+mc<VERSION>.jar` | Server directory, outside `mods/` |

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
java -jar AntiCrash-Supervisor-1.0.6+mc1.18.2.jar --workdir /path/to/server -- java -jar fabric-server-launch.jar nogui
```

The license is GNU Lesser General Public License v3.0 only (LGPL-3.0-only).

## Configuration reference

The following list covers all 156 configuration keys, including the schema version. Each section follows the generated TOML file.


<details>
<summary>General</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `config_version` | `1` | Configuration schema version; leave unchanged. |
| `language` | `"ja"` | AntiCrash message language: `ja` or `en`. |

</details>

<details>
<summary>Checkpoint schedule and retention</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `checkpoint.enabled` | `true` | Enables automatic checkpoint creation. |
| `checkpoint.interval_seconds` | `30` | Time between automatic checkpoints, in real seconds (1–86,400). |
| `checkpoint.create_on_manual_command` | `true` | Allows checkpoint commands to create checkpoints. |
| `checkpoint.create_on_clean_shutdown` | `true` | Creates a final checkpoint during a normal shutdown. |
| `checkpoint.shutdown_timeout_seconds` | `5` | Maximum time the server waits for that final checkpoint; `0` skips the wait. |
| `checkpoint.retention_count` | `20` | Maximum number of checkpoints to retain. Must be at least `1`; the newest is kept. |
| `checkpoint.retention_days` | `7` | Marks checkpoints older than this many days for deletion; `0` disables age-based cleanup. |
| `checkpoint.retention_max_gb` | `0` | Storage limit for checkpoints in GB; `0` means no size limit. |
| `checkpoint.save_batch_chunks` | `256` | Values `1`–`15` set the queued-write limit directly. Values `16`–`4,096` use a heap-based limit; the default `256` selects this adaptive mode. |

</details>

<details>
<summary>Checkpoint timing and performance</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `checkpoint.performance.target_barrier_ms` | `20` | Target duration for the checkpoint cut, in milliseconds. |
| `checkpoint.performance.warn_barrier_ms` | `50` | Logs a warning when the cut exceeds this duration. |
| `checkpoint.performance.critical_barrier_ms` | `100` | Logs a critical delay when the cut exceeds this duration. |
| `checkpoint.performance.warn_checkpoint_ms` | `5000` | Logs a warning when snapshot capture exceeds this duration. |
| `checkpoint.performance.rolling_enabled` | `true` | Spreads eligible snapshot preparation across server ticks. |
| `checkpoint.performance.rolling_budget_ms_per_tick` | `1.0` | Initial time budget for rolling work per tick, in milliseconds. |
| `checkpoint.performance.rolling_max_budget_ms_per_tick` | `3.0` | Maximum rolling-work budget per tick when adaptive budgeting is enabled. |
| `checkpoint.performance.adaptive_budget` | `true` | Adjusts the rolling-work budget based on observed load. |
| `checkpoint.performance.warn_finalize_ms` | `1000` | Logs a warning when checkpoint finalization exceeds this duration. |

</details>

<details>
<summary>Checkpoint generation, storage, and diagnostics</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `checkpoint.pipeline.max_inflight_generations` | `2` | Maximum number of checkpoint finalizers allowed in flight. |
| `checkpoint.incremental.base_interval` | `0` | Creates a BASE after this many generations; `0` disables periodic BASE creation. |
| `checkpoint.incremental.max_chain_depth` | `0` | Creates a BASE when the incremental chain reaches this depth; `0` disables this limit. |
| `checkpoint.io.background_threads` | `1` | Number of background checkpoint-finalization threads (1–16). |
| `checkpoint.io.max_mb_per_second` | `0` | Throttles background checkpoint I/O; `0` means unlimited. |
| `checkpoint.storage.minimum_free_mb` | `256` | Minimum free disk space required before publishing a checkpoint. |
| `checkpoint.durability.mode` | `"SAFE"` | Durability mode: `SAFE`, `BALANCED`, or `FAST`. `FAST` skips forcing world payload files and directories. |
| `checkpoint.engine.mode` | `"LOGICAL_DELTA"` | Checkpoint engine: `LOGICAL_DELTA`, `PORTABLE_GENERATION`, or `LEGACY_SAFE`. |
| `checkpoint.engine.fallback` | `"PORTABLE_GENERATION"` | Fallback engine: `PORTABLE_GENERATION` or `LEGACY_SAFE`. |
| `checkpoint.engine.single_inflight` | `true` | Allows only one logical checkpoint operation at a time. |
| `checkpoint.engine.allow_unsupported_persistent_state` | `false` | Allows checkpoint creation when unsupported persistent state is detected. This can reduce recovery fidelity. |
| `checkpoint.cache.max_mb` | `256` | Memory limit for the checkpoint candidate cache (16–4,096 MB). |
| `checkpoint.cache.budget_ms_per_tick` | `1.0` | Per-tick time budget for candidate-cache work, in milliseconds. |
| `checkpoint.mirror.enabled` | `true` | Enables audit-only tracking of observed world mutations. |
| `checkpoint.mirror.audit_interval_seconds` | `300` | Interval between mutation-audit summaries, in seconds; `0` disables periodic summaries. |
| `checkpoint.mirror.trace_limit` | `64` | Maximum number of recent mutation traces retained for diagnostics. |

</details>

<details>
<summary>Command permissions</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `commands.help_permission_level` | `4` | Permission level for `/ac help`. |
| `commands.status_permission_level` | `4` | Permission level for status commands. |
| `commands.start_permission_level` | `0` | Permission level for `/ac start`; `0` allows everyone. |
| `commands.start_force_permission_level` | `4` | Permission level for `/ac start --force`. |
| `commands.freeze_permission_level` | `4` | Permission level for `/ac freeze`. |
| `commands.checkpoint_permission_level` | `4` | Permission level for checkpoint commands. |
| `commands.recovery_permission_level` | `4` | Permission level for recovery commands. |
| `commands.fakeplayers_permission_level` | `4` | Permission level for FakePlayer commands. |
| `commands.journal_permission_level` | `4` | Permission level for journal commands. |
| `commands.config_permission_level` | `4` | Permission level for config commands. |
| `commands.debug_permission_level` | `4` | Permission level for debug commands. |

Permission levels range from `0` (everyone) to `4` (server operator level 4).

</details>

<details>
<summary>Snapshot backend</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `snapshot.backend` | `"AUTO"` | Backend: `AUTO`, `PORTABLE_GENERATION`, `GENERATION`, `JAVA_INCREMENTAL`, `LEGACY_SAFE`, `REFLINK`, `BTRFS`, or `CRIU_EXPERIMENTAL`. `AUTO` selects the portable generation path. |
| `snapshot.verify_checksums` | `true` | Verifies checkpoint checksums during validation and restore. |
| `snapshot.keep_crash_raw_world` | `true` | Keeps the pre-rollback world used by `/ac recovery abort`. |
| `snapshot.crash_raw_retention_count` | `10` | Maximum number of pre-rollback worlds to retain; minimum `1`. |
| `snapshot.io_threads` | `0` | File-copy thread count (0–64). `0` uses the selected operation's default; positive values set the thread count. |

</details>

<details>
<summary>Recovery behavior</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `recovery.policy` | `"AUTO"` | Recovery policy: `AUTO`, `ASK`, or `MANUAL`. |
| `recovery.fidelity` | `"OPTIMIZED"` | Validation mode: `OPTIMIZED` or `STRICT`. |
| `recovery.freeze_after_restore` | `true` | Keeps game time paused after restoration while preparation runs. |
| `recovery.require_manual_start` | `true` | Requires `/ac start` after recovery preparation. |
| `recovery.start_command` | `"/ac start"` | Text shown to players as the resume command. The registered command remains `/ac start`. |
| `recovery.restore_latest_valid_checkpoint` | `true` | Restores the newest checkpoint that passes validation. |
| `recovery.allow_restore_older_checkpoint` | `true` | Allows recovery to select an older valid checkpoint. |
| `recovery.fallback_to_previous_checkpoint` | `true` | Tries the previous checkpoint when the selected one cannot be used. |
| `recovery.ask_timeout_seconds` | `60` | Time limit for the `ASK` decision. `0` waits indefinitely only when input is available. |
| `recovery.ask_default_action` | `"ABORT"` | Compatibility setting; current timeout handling uses the exit type and ignores this value. |
| `recovery.allow_start_with_failed_validation` | `false` | Allows `/ac start --force` to bypass eligible semantic validation failures. Corrupt files and persistence failures remain blocking. |
| `recovery.spawn_restart_helper` | `false` | Starts a detached helper to relaunch the server after a restore request; ignored when launched through Supervisor. |
| `recovery.temporary_ticket_release_ticks` | `100` | Number of game ticks before temporary recovery chunk tickets are released (1–1,200,000). |
| `recovery.preload_mode` | `"ALL"` | Chunk selection for optimized recovery: `ALL`, `ENTITY_TICKING`, or `CRITICAL`. |
| `recovery.max_preload_chunks` | `100000` | Maximum number of chunks recovery may prepare. Recovery fails if it needs more. |
| `recovery.chunk_ready_timeout_seconds` | `120` | Fails recovery after this long with no chunk-readiness progress; `0` disables the timeout. |

</details>

<details>
<summary>Recovery chunk loading and STRICT checks</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `recovery.chunks.max_concurrent_loads` | `4` | Maximum concurrent asynchronous chunk loads (1–64). |
| `recovery.strict.allow_unsupported_runtime_state` | `false` | Allows STRICT recovery with unsupported Minecraft runtime state. |
| `recovery.strict.allow_unsupported_carpet_state` | `false` | Allows STRICT recovery with unsupported Carpet state. |
| `recovery.strict.require_entity_order` | `true` | Requires entity ordering to match the checkpoint. |
| `recovery.strict.require_block_entity_ticker_order` | `true` | Requires block-entity ticker ordering to match. |
| `recovery.strict.require_ticket_ledger` | `true` | Requires the chunk-ticket ledger to match or be supported. |
| `recovery.strict.players.require_checkpoint_players` | `false` | Requires every real player present at checkpoint time to be online before `/ac start`. |

</details>

<details>
<summary>Player actions while recovery is frozen</summary>

These options apply only while recovery is frozen. KeepAlive and AntiCrash recovery controls remain available.

| Setting | Default | Description |
| --- | --- | --- |
| `recovery.lock.allow_login` | `true` | Allows players to join while recovery is frozen. |
| `recovery.lock.allow_chat` | `true` | Allows chat while recovery is frozen. |
| `recovery.lock.allow_commands` | `true` | Allows the configured safe command set during recovery. |
| `recovery.lock.allow_player_movement` | `true` | Allows player movement. |
| `recovery.lock.allow_player_look` | `true` | Allows changing view direction. |
| `recovery.lock.allow_sneak` | `true` | Allows sneaking. |
| `recovery.lock.allow_block_break` | `false` | Allows breaking blocks. |
| `recovery.lock.allow_block_place` | `false` | Allows placing blocks. |
| `recovery.lock.allow_item_use` | `false` | Allows item use. |
| `recovery.lock.allow_entity_attack` | `false` | Allows attacking entities. |
| `recovery.lock.allow_entity_interact` | `false` | Allows interacting with entities. |
| `recovery.lock.allow_container_interaction` | `false` | Allows opening and using containers. |
| `recovery.lock.allow_item_drop` | `false` | Allows dropping items. |
| `recovery.lock.allow_item_pickup` | `false` | Allows picking up items. |

</details>

<details>
<summary>Real-player snapshots</summary>

By default, only dimension, position, and rotation are restored. Other player data remains unchanged unless enabled. Enabling inventory or progression fields can conflict with external player-data synchronization.

| Setting | Default | Description |
| --- | --- | --- |
| `player_snapshot.enabled` | `true` | Enables configured real-player snapshots. |
| `player_snapshot.dimension` | `true` | Saves and restores dimension. |
| `player_snapshot.position` | `true` | Saves and restores position. |
| `player_snapshot.rotation` | `true` | Saves and restores rotation. |
| `player_snapshot.gamemode` | `false` | Saves and restores game mode. |
| `player_snapshot.abilities` | `false` | Saves and restores player abilities. |
| `player_snapshot.inventory_main` | `false` | Saves and restores the main inventory. |
| `player_snapshot.inventory_armor` | `false` | Saves and restores armor slots. |
| `player_snapshot.inventory_offhand` | `false` | Saves and restores the offhand slot. |
| `player_snapshot.selected_hotbar_slot` | `false` | Saves and restores the selected hotbar slot. |
| `player_snapshot.experience_level` | `false` | Saves and restores experience level. |
| `player_snapshot.experience_progress` | `false` | Saves and restores progress toward the next level. |
| `player_snapshot.experience_total` | `false` | Saves and restores total experience. |
| `player_snapshot.ender_chest` | `false` | Saves and restores ender-chest contents. |
| `player_snapshot.health` | `false` | Saves and restores health. |
| `player_snapshot.absorption` | `false` | Saves and restores absorption hearts. |
| `player_snapshot.food_level` | `false` | Saves and restores food level. |
| `player_snapshot.food_saturation` | `false` | Saves and restores saturation. |
| `player_snapshot.food_exhaustion` | `false` | Saves and restores exhaustion. |
| `player_snapshot.potion_effects` | `false` | Saves and restores status effects. |
| `player_snapshot.velocity` | `false` | Saves and restores velocity. |
| `player_snapshot.fall_distance` | `false` | Saves and restores fall distance. |
| `player_snapshot.fire_ticks` | `false` | Saves and restores remaining fire ticks. |
| `player_snapshot.air` | `false` | Saves and restores air supply. |
| `player_snapshot.spawn_point` | `false` | Saves and restores the personal spawn point. |

</details>

<details>
<summary>Vanilla player files</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `player_snapshot.vanilla_files.restore_playerdata` | `false` | Restores the complete Vanilla player-data file. |
| `player_snapshot.vanilla_files.restore_stats` | `false` | Restores the player's statistics file. |
| `player_snapshot.vanilla_files.restore_advancements` | `false` | Restores the player's advancements file. |

These options replace whole files. Check compatibility with player-data synchronization or database mods before enabling them.

</details>

<details>
<summary>FakePlayer recovery</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `fakeplayer.enabled` | `true` | Enables FakePlayer snapshots and recovery. |
| `fakeplayer.auto_rejoin` | `true` | Rejoins saved FakePlayers automatically during recovery. |
| `fakeplayer.restore_before_unfreeze` | `true` | Restores FakePlayers before game time resumes. |
| `fakeplayer.require_all_ready_before_start` | `true` | Requires all saved FakePlayers to be ready before `/ac start`. |
| `fakeplayer.snapshot.dimension` | `true` | Saves and restores dimension. |
| `fakeplayer.snapshot.position` | `true` | Saves and restores position. |
| `fakeplayer.snapshot.rotation` | `true` | Saves and restores rotation. |
| `fakeplayer.snapshot.gamemode` | `true` | Saves and restores game mode. |
| `fakeplayer.snapshot.flying` | `true` | Saves and restores flying state. |
| `fakeplayer.snapshot.inventory` | `true` | Saves and restores inventory. |
| `fakeplayer.snapshot.selected_slot` | `true` | Saves and restores the selected hotbar slot. |
| `fakeplayer.snapshot.health` | `true` | Saves and restores health. |
| `fakeplayer.snapshot.hunger` | `true` | Saves and restores hunger state. |
| `fakeplayer.snapshot.effects` | `true` | Saves and restores status effects. |
| `fakeplayer.snapshot.sneaking` | `true` | Saves and restores sneaking state. |
| `fakeplayer.snapshot.sprinting` | `true` | Saves and restores sprinting state. |
| `fakeplayer.snapshot.movement` | `true` | Saves and restores movement input state. |
| `fakeplayer.snapshot.actions` | `true` | Saves and restores Carpet action-pack actions. |
| `fakeplayer.snapshot.action_internal_timing` | `true` | Preserves action timing fields such as interval, count, and next action. |
| `fakeplayer.snapshot.item_use_cooldown` | `true` | Saves and restores item-use cooldown. |
| `fakeplayer.snapshot.block_break_progress` | `true` | Saves and restores block-breaking progress. |

</details>

<details>
<summary>Login and notifications</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `login.retry_timeout_seconds` | `5` | Retries a login refused by AntiCrash during a checkpoint barrier or recovery freeze; `0` disconnects immediately (maximum 20 seconds). |
| `notifications.frozen_action_bar` | `true` | Shows an action-bar message explaining a recovery freeze. |
| `notifications.checkpoint_action_bar_delay_ticks` | `10` | Retained for config compatibility. Checkpoint creation does not show an action-bar message. |
| `notifications.ask_prompt_in_game` | `true` | Also shows the `ASK` prompt in-game; answers can be given with `/ac y` or `/ac n`. |

</details>

<details>
<summary>Consistency tracking</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `consistency.mode` | `"IGNORE"` | Tracking mode: `IGNORE` (off), `WARN` (warnings), or `JOURNAL` (detailed journal). |

</details>

<details>
<summary>Logging</summary>

| Setting | Default | Description |
| --- | --- | --- |
| `logging.level` | `"NORMAL"` | Detail level: `OFF`, `IMPORTANT`, `NORMAL`, `VERBOSE`, or `TRACE`. Fatal errors are still logged. |
| `logging.console` | `true` | Writes logs to the server console. |
| `logging.console_color` | `"AUTO"` | Console colors: `AUTO`, `ON`, or `OFF`. |
| `logging.chat_color` | `true` | Uses colors in command replies and in-game notices. |
| `logging.file` | `true` | Writes AntiCrash logs to a file. |
| `logging.max_file_mb` | `50` | Maximum size of each log file before rotation, in MB (1–10,240). |
| `logging.retained_files` | `10` | Number of rotated log files to retain (1–1,000). |
| `logging.show_checkpoint_contents` | `false` | Includes checkpoint file contents in detailed logs. |
| `logging.show_player_details` | `false` | Includes real-player details in logs. |
| `logging.show_fakeplayer_details` | `true` | Includes FakePlayer details in logs. |
| `logging.show_block_events` | `false` | Includes block-event details in logs. |
| `logging.show_file_operations` | `false` | Includes file-operation details in logs. |
| `logging.show_transaction_details` | `false` | Includes checkpoint transaction details in logs. |

</details>
