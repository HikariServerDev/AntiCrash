# AntiCrash

**Minecraft FabricサーバーMod** · AtsukiMC · **v1.0.3** · **LGPL-3.0-only**

[英語版](README.md)

## 概要

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

以下に、設定項目・初期値・用途をすべて記載しています。設定ファイルの項目を編集してください。

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

## 設定項目

設定形式の識別番号を含む156項目を、初期値と説明付きで記載しています。TOMLのセクションごとに分けています。


<details>
<summary>基本設定</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `config_version` | `1` | 設定形式の識別番号です。変更しません。 |
| `language` | `"ja"` | AntiCrashの表示言語です。`ja`または`en`を指定します。 |

</details>

<details>
<summary>保存間隔と保持</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `checkpoint.enabled` | `true` | 自動作成を有効にします。 |
| `checkpoint.interval_seconds` | `30` | 自動作成の間隔を実時間の秒数で指定します（1〜86,400）。 |
| `checkpoint.create_on_manual_command` | `true` | コマンドによる作成を許可します。 |
| `checkpoint.create_on_clean_shutdown` | `true` | 正常停止時に最終チェックポイントを作成します。 |
| `checkpoint.shutdown_timeout_seconds` | `5` | 最終保存を待つ上限時間です。`0`は待たずに終了します。 |
| `checkpoint.retention_count` | `20` | 保持する最大件数です。`1`以上を指定し、最新の1件は保持されます。 |
| `checkpoint.retention_days` | `7` | 指定日数より古いチェックポイントを削除対象にします。`0`は日数による削除を行いません。 |
| `checkpoint.retention_max_gb` | `0` | チェックポイントの容量上限（GB）です。`0`は上限なしです。 |
| `checkpoint.save_batch_chunks` | `256` | `1`〜`15`は保存待ちデータの上限です。`16`〜`4,096`はヒープ容量に応じた上限を使い、初期値`256`もこの方式です。 |

</details>

<details>
<summary>保存処理と性能</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `checkpoint.performance.target_barrier_ms` | `20` | チェックポイント境界処理の目標時間（ミリ秒）です。 |
| `checkpoint.performance.warn_barrier_ms` | `50` | 境界処理がこの時間を超えた場合に警告します。 |
| `checkpoint.performance.critical_barrier_ms` | `100` | 境界処理がこの時間を超えた場合に重要警告を出します。 |
| `checkpoint.performance.warn_checkpoint_ms` | `5000` | スナップショット取得がこの時間を超えた場合に警告します。 |
| `checkpoint.performance.rolling_enabled` | `true` | 対象データの準備を複数のサーバーtickに分けます。 |
| `checkpoint.performance.rolling_budget_ms_per_tick` | `1.0` | 1 tickあたりの段階処理時間の初期上限（ミリ秒）です。 |
| `checkpoint.performance.rolling_max_budget_ms_per_tick` | `3.0` | 自動調整を有効にした場合の1 tickあたりの最大処理時間です。 |
| `checkpoint.performance.adaptive_budget` | `true` | 負荷に応じて段階処理の時間上限を調整します。 |
| `checkpoint.performance.warn_finalize_ms` | `1000` | チェックポイントの最終確定がこの時間を超えた場合に警告します。 |

</details>

<details>
<summary>世代・保存先・診断</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `checkpoint.pipeline.max_inflight_generations` | `2` | 同時に最終確定できるチェックポイント世代数の上限です。 |
| `checkpoint.incremental.base_interval` | `0` | 指定世代ごとにBASEを作成します。`0`は定期作成を無効にします。 |
| `checkpoint.incremental.max_chain_depth` | `0` | 差分チェーンが指定深度に達したときBASEを作成します。`0`は深度による作成を無効にします。 |
| `checkpoint.io.background_threads` | `1` | チェックポイント確定に使うバックグラウンドスレッド数です（1〜16）。 |
| `checkpoint.io.max_mb_per_second` | `0` | バックグラウンド保存の速度上限（MB/秒）です。`0`は無制限です。 |
| `checkpoint.storage.minimum_free_mb` | `256` | チェックポイント確定前に必要なディスク空き容量（MB）です。 |
| `checkpoint.durability.mode` | `"SAFE"` | 保存の耐久性設定です。`SAFE`・`BALANCED`・`FAST`を指定します。`FAST`ではワールド本体やディレクトリの同期書き込みを省略します。 |
| `checkpoint.engine.mode` | `"LOGICAL_DELTA"` | チェックポイント方式です。`LOGICAL_DELTA`・`PORTABLE_GENERATION`・`LEGACY_SAFE`を指定します。 |
| `checkpoint.engine.fallback` | `"PORTABLE_GENERATION"` | 主方式を使えない場合の代替方式です。 |
| `checkpoint.engine.single_inflight` | `true` | 論理チェックポイント処理を同時に1件に制限します。 |
| `checkpoint.engine.allow_unsupported_persistent_state` | `false` | 未対応の永続状態が検出された場合も作成を許可します。復元精度が下がる可能性があります。 |
| `checkpoint.cache.max_mb` | `256` | チェックポイント候補キャッシュの上限（MB）です（16〜4,096）。 |
| `checkpoint.cache.budget_ms_per_tick` | `1.0` | 候補キャッシュ処理に使う1 tickあたりの時間上限（ミリ秒）です。 |
| `checkpoint.mirror.enabled` | `true` | ワールド変更の監査用記録を有効にします。 |
| `checkpoint.mirror.audit_interval_seconds` | `300` | 監査情報をまとめる間隔（秒）です。`0`は定期出力を無効にします。 |
| `checkpoint.mirror.trace_limit` | `64` | 診断用に保持する変更履歴数の上限です。 |

</details>

<details>
<summary>コマンド権限</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `commands.help_permission_level` | `4` | `/ac help`の権限レベルです。 |
| `commands.status_permission_level` | `4` | 状態確認コマンドの権限レベルです。 |
| `commands.start_permission_level` | `0` | `/ac start`の権限レベルです。`0`は全員に許可します。 |
| `commands.start_force_permission_level` | `4` | `/ac start --force`の権限レベルです。 |
| `commands.freeze_permission_level` | `4` | `/ac freeze`の権限レベルです。 |
| `commands.checkpoint_permission_level` | `4` | チェックポイント操作の権限レベルです。 |
| `commands.recovery_permission_level` | `4` | 復旧操作の権限レベルです。 |
| `commands.fakeplayers_permission_level` | `4` | FakePlayer操作の権限レベルです。 |
| `commands.journal_permission_level` | `4` | ジャーナル確認の権限レベルです。 |
| `commands.config_permission_level` | `4` | 設定操作の権限レベルです。 |
| `commands.debug_permission_level` | `4` | デバッグコマンドの権限レベルです。 |

権限レベルは`0`（全員）から`4`（OPレベル4）までです。

</details>

<details>
<summary>スナップショット方式</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `snapshot.backend` | `"AUTO"` | 保存方式です。`AUTO`はポータブル方式を選びます。指定できる値は左記のとおりです。 |
| `snapshot.verify_checksums` | `true` | 検証・復元時にチェックサムを確認します。 |
| `snapshot.keep_crash_raw_world` | `true` | `/ac recovery abort`で戻せる巻き戻し前ワールドを保持します。 |
| `snapshot.crash_raw_retention_count` | `10` | 巻き戻し前ワールドの保持上限です。最低1件を保持します。 |
| `snapshot.io_threads` | `0` | ファイルコピーのスレッド数です（0〜64）。`0`は処理ごとの既定値、それ以外は指定した数を使います。 |

</details>

<details>
<summary>復旧動作</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `recovery.policy` | `"AUTO"` | 復旧方法です。`AUTO`・`ASK`・`MANUAL`を指定します。 |
| `recovery.fidelity` | `"OPTIMIZED"` | 復旧状態の確認方法です。`OPTIMIZED`または`STRICT`を指定します。 |
| `recovery.freeze_after_restore` | `true` | 復元後、準備が進む間ゲーム内時間を停止します。 |
| `recovery.require_manual_start` | `true` | 復旧準備後に`/ac start`を要求します。 |
| `recovery.start_command` | `"/ac start"` | プレイヤー向けに表示する再開コマンドです。実際のコマンドは`/ac start`です。 |
| `recovery.restore_latest_valid_checkpoint` | `true` | 検証に通る最新のチェックポイントを復元します。 |
| `recovery.allow_restore_older_checkpoint` | `true` | より古い有効なチェックポイントの選択を許可します。 |
| `recovery.fallback_to_previous_checkpoint` | `true` | 選択したチェックポイントが使えない場合、前の世代を試します。 |
| `recovery.ask_timeout_seconds` | `60` | `ASK`の回答期限です。`0`は入力手段が使える場合に限り無期限で待ちます。 |
| `recovery.ask_default_action` | `"ABORT"` | 互換性のための設定です。現在の無回答時動作は終了種別で決まり、この値は使いません。 |
| `recovery.allow_start_with_failed_validation` | `false` | 対象となる意味検証の失敗を`/ac start --force`で続行できるようにします。破損ファイルや永続化失敗は解除できません。 |
| `recovery.spawn_restart_helper` | `false` | 復元予約後にサーバーを起動する補助プロセスを使います。Supervisor経由では無視されます。 |
| `recovery.temporary_ticket_release_ticks` | `100` | 復旧用の一時チャンクticketを解放するまでのtick数です（1〜1,200,000）。 |
| `recovery.preload_mode` | `"ALL"` | OPTIMIZEDで準備するチャンクの選び方です。`ALL`・`ENTITY_TICKING`・`CRITICAL`を指定します。 |
| `recovery.max_preload_chunks` | `100000` | 復旧で準備するチャンク数の上限です。必要数が超える場合は復旧を失敗させます。 |
| `recovery.chunk_ready_timeout_seconds` | `120` | チャンク準備が進まない状態の許容時間（秒）です。`0`は時間切れ判定を無効にします。 |

</details>

<details>
<summary>チャンク読み込みとSTRICT検証</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `recovery.chunks.max_concurrent_loads` | `4` | 同時に読み込むチャンク数の上限です（1〜64）。 |
| `recovery.strict.allow_unsupported_runtime_state` | `false` | 未対応のMinecraft実行状態があってもSTRICT復旧を許可します。 |
| `recovery.strict.allow_unsupported_carpet_state` | `false` | 未対応のCarpet状態があってもSTRICT復旧を許可します。 |
| `recovery.strict.require_entity_order` | `true` | Entityの順序がチェックポイントと一致することを要求します。 |
| `recovery.strict.require_block_entity_ticker_order` | `true` | BlockEntity tickerの順序が一致することを要求します。 |
| `recovery.strict.require_ticket_ledger` | `true` | チャンクticketの記録が一致し、対応済みであることを要求します。 |
| `recovery.strict.players.require_checkpoint_players` | `false` | チェックポイント時にいた全実プレイヤーが揃うまで`/ac start`を拒否します。 |

</details>

<details>
<summary>復旧中のプレイヤー操作</summary>

復旧中だけ適用されます。KeepAliveとAntiCrashの復旧操作は引き続き利用できます。

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `recovery.lock.allow_login` | `true` | 復旧停止中のログインを許可します。 |
| `recovery.lock.allow_chat` | `true` | 復旧停止中のチャットを許可します。 |
| `recovery.lock.allow_commands` | `true` | 復旧中に読み取り中心の許可コマンドを使えるようにします。 |
| `recovery.lock.allow_player_movement` | `true` | プレイヤーの移動を許可します。 |
| `recovery.lock.allow_player_look` | `true` | 視点変更を許可します。 |
| `recovery.lock.allow_sneak` | `true` | スニークを許可します。 |
| `recovery.lock.allow_block_break` | `false` | ブロック破壊を許可します。 |
| `recovery.lock.allow_block_place` | `false` | ブロック設置を許可します。 |
| `recovery.lock.allow_item_use` | `false` | アイテム使用を許可します。 |
| `recovery.lock.allow_entity_attack` | `false` | Entityへの攻撃を許可します。 |
| `recovery.lock.allow_entity_interact` | `false` | Entityとの操作を許可します。 |
| `recovery.lock.allow_container_interaction` | `false` | コンテナの操作を許可します。 |
| `recovery.lock.allow_item_drop` | `false` | アイテム投棄を許可します。 |
| `recovery.lock.allow_item_pickup` | `false` | アイテム取得を許可します。 |

</details>

<details>
<summary>実プレイヤーの保存項目</summary>

初期設定ではディメンション・位置・向きだけを復元します。ほかの項目は有効にした場合のみ対象です。インベントリや経験値などを有効にすると、外部のプレイヤーデータ同期と競合する場合があります。

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `player_snapshot.enabled` | `true` | 実プレイヤーの設定済み項目を保存・復元します。 |
| `player_snapshot.dimension` | `true` | ディメンションを保存・復元します。 |
| `player_snapshot.position` | `true` | 位置を保存・復元します。 |
| `player_snapshot.rotation` | `true` | 向きを保存・復元します。 |
| `player_snapshot.gamemode` | `false` | ゲームモードを保存・復元します。 |
| `player_snapshot.abilities` | `false` | プレイヤー能力を保存・復元します。 |
| `player_snapshot.inventory_main` | `false` | メインインベントリを保存・復元します。 |
| `player_snapshot.inventory_armor` | `false` | 防具スロットを保存・復元します。 |
| `player_snapshot.inventory_offhand` | `false` | オフハンドを保存・復元します。 |
| `player_snapshot.selected_hotbar_slot` | `false` | 選択中のホットバースロットを保存・復元します。 |
| `player_snapshot.experience_level` | `false` | 経験値レベルを保存・復元します。 |
| `player_snapshot.experience_progress` | `false` | 次のレベルまでの経験値進捗を保存・復元します。 |
| `player_snapshot.experience_total` | `false` | 累計経験値を保存・復元します。 |
| `player_snapshot.ender_chest` | `false` | エンダーチェストの内容を保存・復元します。 |
| `player_snapshot.health` | `false` | 体力を保存・復元します。 |
| `player_snapshot.absorption` | `false` | 吸収体力を保存・復元します。 |
| `player_snapshot.food_level` | `false` | 満腹度を保存・復元します。 |
| `player_snapshot.food_saturation` | `false` | 隠し満腹度を保存・復元します。 |
| `player_snapshot.food_exhaustion` | `false` | 疲労度を保存・復元します。 |
| `player_snapshot.potion_effects` | `false` | ステータス効果を保存・復元します。 |
| `player_snapshot.velocity` | `false` | 速度を保存・復元します。 |
| `player_snapshot.fall_distance` | `false` | 落下距離を保存・復元します。 |
| `player_snapshot.fire_ticks` | `false` | 残りの炎上tickを保存・復元します。 |
| `player_snapshot.air` | `false` | 酸素量を保存・復元します。 |
| `player_snapshot.spawn_point` | `false` | 個人スポーン地点を保存・復元します。 |

</details>

<details>
<summary>Vanillaプレイヤーファイル</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `player_snapshot.vanilla_files.restore_playerdata` | `false` | Vanillaのプレイヤーデータファイル全体を復元します。 |
| `player_snapshot.vanilla_files.restore_stats` | `false` | プレイヤー統計ファイルを復元します。 |
| `player_snapshot.vanilla_files.restore_advancements` | `false` | プレイヤーの進捗ファイルを復元します。 |

有効にすると各ファイル全体を置き換えます。プレイヤーデータ同期やデータベース連携との競合を確認してください。

</details>

<details>
<summary>FakePlayerの復旧</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `fakeplayer.enabled` | `true` | FakePlayerの保存・復旧を有効にします。 |
| `fakeplayer.auto_rejoin` | `true` | 復旧時に保存済みFakePlayerを自動で再参加させます。 |
| `fakeplayer.restore_before_unfreeze` | `true` | ゲーム内時間を再開する前にFakePlayerを復元します。 |
| `fakeplayer.require_all_ready_before_start` | `true` | 保存したFakePlayerが揃うまで`/ac start`を拒否します。 |
| `fakeplayer.snapshot.dimension` | `true` | ディメンションを保存・復元します。 |
| `fakeplayer.snapshot.position` | `true` | 位置を保存・復元します。 |
| `fakeplayer.snapshot.rotation` | `true` | 向きを保存・復元します。 |
| `fakeplayer.snapshot.gamemode` | `true` | ゲームモードを保存・復元します。 |
| `fakeplayer.snapshot.flying` | `true` | 飛行状態を保存・復元します。 |
| `fakeplayer.snapshot.inventory` | `true` | インベントリを保存・復元します。 |
| `fakeplayer.snapshot.selected_slot` | `true` | 選択中のホットバースロットを保存・復元します。 |
| `fakeplayer.snapshot.health` | `true` | 体力を保存・復元します。 |
| `fakeplayer.snapshot.hunger` | `true` | 満腹度を保存・復元します。 |
| `fakeplayer.snapshot.effects` | `true` | ステータス効果を保存・復元します。 |
| `fakeplayer.snapshot.sneaking` | `true` | スニーク状態を保存・復元します。 |
| `fakeplayer.snapshot.sprinting` | `true` | ダッシュ状態を保存・復元します。 |
| `fakeplayer.snapshot.movement` | `true` | 移動入力状態を保存・復元します。 |
| `fakeplayer.snapshot.actions` | `true` | CarpetのActionPack操作を保存・復元します。 |
| `fakeplayer.snapshot.action_internal_timing` | `true` | interval・count・nextなど操作タイミングを保存・復元します。 |
| `fakeplayer.snapshot.item_use_cooldown` | `true` | アイテム使用のクールダウンを保存・復元します。 |
| `fakeplayer.snapshot.block_break_progress` | `true` | ブロック破壊の進捗を保存・復元します。 |

</details>

<details>
<summary>ログインと通知</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `login.retry_timeout_seconds` | `5` | チェックポイント境界や復旧停止中にAntiCrashが拒否したログインを再試行します。`0`は即時切断です（最大20秒）。 |
| `notifications.frozen_action_bar` | `true` | 復旧停止の理由をアクションバーに表示します。 |
| `notifications.checkpoint_action_bar_delay_ticks` | `10` | 互換性のために残る設定です。チェックポイント作成時のアクションバー表示には使いません。 |
| `notifications.ask_prompt_in_game` | `true` | `ASK`の質問をゲーム内にも表示し、`/ac y`・`/ac n`で回答できるようにします。 |

</details>

<details>
<summary>整合性の記録</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `consistency.mode` | `"IGNORE"` | 記録方法です。`IGNORE`（無効）・`WARN`（警告）・`JOURNAL`（詳細記録）を指定します。 |

</details>

<details>
<summary>ログ</summary>

| 設定 | 初期値 | 説明 |
| --- | --- | --- |
| `logging.level` | `"NORMAL"` | 詳細度です。`OFF`・`IMPORTANT`・`NORMAL`・`VERBOSE`・`TRACE`を指定します。致命的エラーは常に記録します。 |
| `logging.console` | `true` | サーバーコンソールへ出力します。 |
| `logging.console_color` | `"AUTO"` | コンソールの色設定です。`AUTO`・`ON`・`OFF`を指定します。 |
| `logging.chat_color` | `true` | コマンド応答やゲーム内通知に色を付けます。 |
| `logging.file` | `true` | AntiCrashのログをファイルへ出力します。 |
| `logging.max_file_mb` | `50` | ローテーション前のログファイル上限（MB）です（1〜10,240）。 |
| `logging.retained_files` | `10` | 保持するローテーション済みログ数です（1〜1,000）。 |
| `logging.show_checkpoint_contents` | `false` | 詳細ログにチェックポイント内のファイル情報を含めます。 |
| `logging.show_player_details` | `false` | ログに実プレイヤーの詳細を含めます。 |
| `logging.show_fakeplayer_details` | `true` | ログにFakePlayerの詳細を含めます。 |
| `logging.show_block_events` | `false` | ログにBlock Eventの詳細を含めます。 |
| `logging.show_file_operations` | `false` | ログにファイル操作の詳細を含めます。 |
| `logging.show_transaction_details` | `false` | ログにチェックポイントの処理詳細を含めます。 |

</details>
