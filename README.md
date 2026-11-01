# AntiCrash

**Minecraft Fabric server mod** — by AtsukiMC · **1.0.1** · **LGPL-3.0-only**

Automatic checkpoints and recovery for Technical Minecraft servers.
Technical Minecraftサーバー向けの、自動チェックポイント・復旧Modです。

[English](#english) · [日本語](#日本語)

---

## English

### What is AntiCrash?

AntiCrash periodically saves **checkpoints**: world data together with supported runtime state needed to resume machines. After an unexpected shutdown, it can restore a valid checkpoint and keep game time paused until you run `/ac start`.

It is designed for Technical Minecraft, including piston machines and Carpet fake players. Recovery support depends on the Minecraft version and installed mods; exact restoration of every machine or runtime state is not guaranteed.

**Players do not need to install AntiCrash. Keep separate world backups.**

### Installation

1. Stop the server and back up its world.
2. Choose the **1.0.1 Mod JAR matching your exact Minecraft version** from the repository's `build/` directory. For 1.18.2, use `AntiCrash-1.0.1+mc1.18.2.jar`.
3. Put it in the server's `mods/` directory and start the Fabric server.
4. AntiCrash creates `config/anticrash.toml`. Automatic checkpoints are enabled by default, every **30 seconds**.

Fabric API is not required for basic use. Carpet is optional; integration varies by Minecraft version. Players can join with an unmodded client.

| JAR | Where it goes | Purpose |
| --- | --- | --- |
| `AntiCrash-1.0.1+mc<VERSION>.jar` | Server `mods/` | Checkpoints and recovery |
| `AntiCrash-Supervisor-1.0.1+mc<VERSION>.jar` | Server directory, outside `mods/` | Optional monitoring and same-console restarts |

#### Supported versions

| Minecraft | Fabric Loader | Java to run the mod |
| --- | --- | --- |
| 1.18, 1.18.1, 1.18.2 | 0.17.3+ | 17+ |
| 1.19–1.19.4 | 0.17.3+ | 17+ |
| 1.20–1.20.4 | 0.17.3+ | 17+ |
| 1.20.5, 1.20.6 | 0.17.3+ | 21+ |
| 1.21–1.21.10 | 0.17.3+ | 21+ |
| 1.21.11 | 0.18.4+ | 21+ |
| 26.1 | 0.18.4+ | 25+ |
| 26.2 | 0.19.3+ | 25+ |
| 26.3 | 0.19.5+ | 25+ |

Each version in a range has its own JAR. These **30 targets passed builds, unit tests, and artifact checks**; this does not establish live-server compatibility. Loader requirements are those declared by each target. The 1.16.1 port is paused and has no release JAR.

### How recovery works

With the default settings:

1. AntiCrash creates checkpoints while the server runs.
2. After an unexpected shutdown, the next startup validates and restores a valid checkpoint.
3. Game time stays paused while required chunks and runtime state are prepared.
4. Check `/ac status` and `/ac recovery verify`, then run `/ac start` when ready. A message confirms when game time resumes.

**Restoring a checkpoint and resuming game time are separate steps.** `/ac start` resumes an already prepared world; it does not select or restore a checkpoint. Any player can use it by default; diagnostic commands require permission level 4.

#### When to restore: recovery policy

Set `policy` in `[recovery]`:

| Policy | After an unexpected shutdown | After a normal shutdown |
| --- | --- | --- |
| `AUTO` — default | Restore a valid checkpoint automatically. | Start the saved world without rollback. |
| `ASK` | Ask whether to restore. No answer defaults to **Y**. | Ask whether to restore the shutdown checkpoint. No answer defaults to **N**. |
| `MANUAL` | Require an explicit decision; do not restore automatically. | Start the saved world normally. |

For `ASK`, wait for the **AntiCrash Y/N prompt**, then type `Y` or `N` and press Enter **in the server console**, before the world loads. `Y` chooses restoration, subject to validation; `N` starts the current saved world without rollback.

The deadline is `ask_timeout_seconds` (default **60 seconds**); `0` waits indefinitely. The compatibility setting `ask_default_action` does not override these defaults. `MANUAL` does not silently choose restoration when no answer is available.

After a normal shutdown, `Y` restores the shutdown checkpoint's supported runtime state too. `N` follows a normal Vanilla startup, so a machine saved mid-cycle may not continue in the same way.

#### How strictly to check: recovery fidelity

| Mode | Behavior |
| --- | --- |
| `OPTIMIZED` — default | Best-effort recovery using the configured chunk preparation strategy. |
| `STRICT` | Compare observable chunk/runtime state with the checkpoint; stay paused if required state is missing, unsupported, or mismatched. |

**OPTIMIZED does not guarantee tick-boundary equivalence.** STRICT cannot reproduce every mod, random state, asynchronous task, or thread schedule either. Inspect blocking reasons before changing its safeguards.

### Configuration

Edit `config/anticrash.toml`. These are common defaults: **update the existing sections rather than replacing the whole file or adding duplicate sections**.

```toml
language = "ja" # "ja" or "en"

[checkpoint]
enabled = true
interval_seconds = 30
retention_count = 20
retention_days = 7
retention_max_gb = 0

[commands]
start_permission_level = 0
status_permission_level = 4
checkpoint_permission_level = 4
recovery_permission_level = 4

[recovery]
policy = "AUTO" # AUTO, ASK, MANUAL
fidelity = "OPTIMIZED" # OPTIMIZED, STRICT
freeze_after_restore = true
require_manual_start = true
ask_timeout_seconds = 60
preload_mode = "ALL"

[recovery.chunks]
max_concurrent_loads = 4
```

| Setting | Meaning |
| --- | --- |
| `language` | AntiCrash messages only: Japanese `ja` (default) or English `en`. Minecraft/Fabric logs keep their own language. |
| `interval_seconds` | Real-time checkpoint interval, independent of game tick rate. Range: 1–86,400 seconds. |
| `retention_count` / `retention_days` / `retention_max_gb` | Retention limits. At least the newest checkpoint is kept. `0` disables the days or GB limit, not the count limit. |
| `freeze_after_restore` + `require_manual_start` | Keep both `true` to wait for `/ac start`. Setting either to `false` requests automatic resume after preparation and checks. |
| `preload_mode` | `ALL`: all checkpoint-loaded chunks at recorded readiness. `ENTITY_TICKING`: entity-ticking chunks. `CRITICAL`: important anchors. STRICT still requires its complete expected domain. |
| `max_preload_chunks` | Chunk preparation safety limit (default 100,000). Exceeding it fails recovery instead of silently dropping chunks. |
| `max_concurrent_loads` | Simultaneous recovery chunk loads. This does not reduce the final required set or guarantee that it fits in memory. |

Permissions range from **0 to 4**; 0 allows non-operators. Only `/ac start` defaults to 0; other command permissions default to 4. Raise `start_permission_level` if only administrators should resume the world. Help, debug, config reload, and other command groups also have individual permission settings.

Use `/ac config reload` after editing. Change startup recovery settings before the next startup; reloading cannot undo a completed restore.

### Commands

These use the in-game `/` prefix. Omit it in the server console.

| Command | Purpose |
| --- | --- |
| `/ac help` | Show command help. |
| `/ac status` | Show checkpoint/recovery progress. |
| `/ac start` | Request resume after recovery checks pass. |
| `/ac freeze` | Pause game time manually. |
| `/ac checkpoint now` | Request a checkpoint. |
| `/ac checkpoint list` | List checkpoints. |
| `/ac checkpoint info <id>` | Inspect a checkpoint. |
| `/ac checkpoint base` | Create an independent base checkpoint. |
| `/ac checkpoint restore <id\|latest>` | Schedule restoration and restart. **Rolls world data back.** |
| `/ac recovery verify [--verbose]` | Check readiness and blocking reasons. |
| `/ac debug equivalence [--verbose]` | Show differences from checkpoint state. |
| `/ac recovery abort` | Abort recovery; where available, return to the preserved pre-rollback world. |
| `/ac fakeplayers` | Show saved Carpet fake-player information. |
| `/ac config reload` | Reload configuration. |

`[--verbose]` means optional; for example, `/ac recovery verify --verbose`.

### Automatic restart in the same console

**The Supervisor is optional for checkpoints, but recommended for automatic restarts in one console.** It stays in the foreground, launches Minecraft as a child process, and shares console input/output, including Y/N answers.

Place the matching Supervisor JAR in the server directory. Launch it instead of launching Minecraft directly. Example for 1.18.2:

```sh
java -jar AntiCrash-Supervisor-1.0.1+mc1.18.2.jar \
  --workdir /path/to/server -- \
  java -Xms1G -Xmx1G -jar fabric-server-launch.jar nogui
```

Adjust the path, version, server JAR, and memory options. After `--` comes your normal Minecraft launch command. **The 1 GB example is not a memory requirement or guarantee.**

The Supervisor handles controlled restore restarts and retries abnormal exits. A normal `/stop` does not restart the server. Repeated short failures stop at the retry limit; logs include PID, exit code, and retry count.

Change `start.sh` or your management tool's launch command only if using this monitoring method. Direct launch still supports checkpoints and recovery, without the Supervisor's same-console restart handling.

### Troubleshooting and limits

| Symptom | What to check |
| --- | --- |
| Game time stays paused | Check `/ac status` and `/ac recovery verify --verbose`. Use `/ac start` after preparation succeeds. |
| Start is rejected | Read the missing-chunk/runtime mismatch reason. Use `/ac debug equivalence --verbose` for details. STRICT deliberately blocks unsafe resume. |
| No Y/N question | Default policy is `AUTO`. With `ASK`, watch the startup console before world loading. |
| Restart does not happen | Check whether you launched through the Supervisor, then inspect its exit/retry logs. |
| Out of memory | World size and loaded chunks affect requirements. Checkpointing/recovery need extra memory; no fixed heap size is guaranteed. |

Keep independent backups and test on a world copy. Never restore across Minecraft versions. Sudden termination can lose changes since the last valid checkpoint. Player-data rollback depends on selective snapshot settings; external databases are not rolled back along with the world.

### Building and contributing

Clone the whole repository; version directories need shared sources. Run `./gradlew test build` inside the desired version directory. Use Java 21 for older targets' Gradle builds, Java 25 for 26.x, and install JDK 17 for the Supervisor toolchain. Server runtime requirements are listed above.

At the root, `python3 scripts/check_sources.py` checks source membership; `python3 scripts/format_java.py --check` checks Java formatting using JDK 25. Version-specific adapters stay separate; shared code lives in `common/`, `shared/`, and `supervisor/`.

1.0.1 preserves checkpoint formats, recovery behavior, defaults, and permissions. Shared-source/audit improvements incorporate aomona's contribution; the experimental Stonecutter build is not required. Older 1.0.0 JARs remain for comparison; choose 1.0.1 for this release. Existing 1.0.0 checkpoints still require integrity validation and the same Minecraft version.

License: **GNU Lesser General Public License v3.0 only** (`LGPL-3.0-only`).

---

## 日本語

### AntiCrashとは？

AntiCrashは、**チェックポイント**を定期的に保存するModです。ワールドデータと、装置の再開に必要な実行中の状態のうち対応するものを記録します。異常終了後に有効なチェックポイントを復元し、`/ac start`を実行するまでゲーム内時間を停止しておけます。

ピストン装置やCarpetのフェイクプレイヤーなど、Technical Minecraftでの利用を想定しています。復旧できる範囲はMinecraft版や導入Modで異なり、あらゆる装置・実行状態の完全復元を保証するものではありません。

**プレイヤー側への導入は不要です。通常のワールドバックアップも別途保管してください。**

### 導入方法

1. サーバーを停止し、ワールドをバックアップします。
2. リポジトリの`build/`から、**Minecraft版が完全に一致する1.0.1のMod JAR**を選びます。1.18.2の場合は`AntiCrash-1.0.1+mc1.18.2.jar`です。
3. サーバーの`mods/`へ入れ、Fabricサーバーを起動します。
4. `config/anticrash.toml`が生成されます。初期設定では**30秒ごとの自動チェックポイント作成**が有効です。

基本利用にFabric APIは不要です。Carpetも必須ではありませんが、連携できる機能はMinecraft版によって異なります。Modなしのクライアントでも参加できます。

| JAR | 配置先 | 用途 |
| --- | --- | --- |
| `AntiCrash-1.0.1+mc<VERSION>.jar` | サーバーの`mods/` | チェックポイント作成・復旧 |
| `AntiCrash-Supervisor-1.0.1+mc<VERSION>.jar` | `mods/`の外、サーバーディレクトリ | 任意で使う監視・同一コンソールでの再起動 |

#### 対応バージョン

| Minecraft | Fabric Loader | Mod実行時のJava |
| --- | --- | --- |
| 1.18・1.18.1・1.18.2 | 0.17.3以上 | 17以上 |
| 1.19〜1.19.4 | 0.17.3以上 | 17以上 |
| 1.20〜1.20.4 | 0.17.3以上 | 17以上 |
| 1.20.5・1.20.6 | 0.17.3以上 | 21以上 |
| 1.21〜1.21.10 | 0.17.3以上 | 21以上 |
| 1.21.11 | 0.18.4以上 | 21以上 |
| 26.1 | 0.18.4以上 | 25以上 |
| 26.2 | 0.19.3以上 | 25以上 |
| 26.3 | 0.19.5以上 | 25以上 |

範囲内の各バージョンに専用JARがあります。**30版でビルド・ユニットテスト・成果物検査を通過**していますが、実サーバーでの互換性確認を意味しません。Loader欄は各版が宣言する要件です。1.16.1の移植は中断中で、配布用JARはありません。

### 復旧の流れ

初期設定では、次のように動作します。

1. 稼働中にチェックポイントを自動作成します。
2. 異常終了後の次回起動時に、有効なチェックポイントを検証・復元します。
3. 必要なチャンクや実行状態を準備する間、ゲーム内時間を停止します。
4. `/ac status`と`/ac recovery verify`で状況を確認し、準備完了後に`/ac start`で再開します。再開完了時にはメッセージが出ます。

**「チェックポイントの復元」と「ゲーム内時間の再開」は別の操作です。** `/ac start`は準備済みのワールドを再開するコマンドで、復元するチェックポイントを選ぶものではありません。初期設定では誰でも使えますが、状態確認コマンドには権限レベル4が必要です。

#### いつ復元するか：復旧ポリシー

`[recovery]`の`policy`で指定します。

| 設定 | 異常終了後 | 正常終了後 |
| --- | --- | --- |
| `AUTO`（初期設定） | 有効なチェックポイントを自動復元 | 巻き戻さず、保存済みワールドで起動 |
| `ASK` | 復元するか質問。無回答は**Y** | 停止時チェックポイントを復元するか質問。無回答は**N** |
| `MANUAL` | 明示的な判断を待ち、自動復元しない | 保存済みワールドで通常起動 |

`ASK`では、**AntiCrashのY/N質問が表示されたら、サーバーコンソールに`Y`または`N`を入力し、Enterを押します。** ワールド読み込み前に判断します。`Y`は検証のうえで復元、`N`は巻き戻さず保存済みワールドで起動する選択です。

回答期限は`ask_timeout_seconds`（初期値**60秒**）です。`0`なら無期限で待ちます。互換性用の`ask_default_action`は、この無回答時の動作を変更しません。`MANUAL`では、無回答を理由に自動復元へ進みません。

正常終了後でも、`Y`を選ぶと停止時チェックポイントに含まれる対応済みの実行状態を復元します。`N`はVanillaと同じ通常起動なので、動作途中で保存された装置が同じように動き続けるとは限りません。

#### どこまで厳密に確認するか：復旧モード

| モード | 動作 |
| --- | --- |
| `OPTIMIZED`（初期設定） | 設定されたチャンク準備方式で、可能な範囲の復旧を行います。 |
| `STRICT` | 観測できるチャンク・実行状態を照合し、必須状態の欠落・未対応・不一致があれば停止を維持します。 |

**OPTIMIZEDはtick境界の等価性を保証しません。** STRICTでも、あらゆるMod・乱数・非同期処理・スレッドの実行順を再現できるわけではありません。安全制限を変更する前に、再開できない理由を確認してください。

### 設定

`config/anticrash.toml`を編集します。以下は主な初期値です。**ファイル全体を置き換えたり、同じセクションを追加したりせず、既存の該当項目を書き換えてください。**

```toml
language = "ja" # "ja" または "en"

[checkpoint]
enabled = true
interval_seconds = 30
retention_count = 20
retention_days = 7
retention_max_gb = 0

[commands]
start_permission_level = 0
status_permission_level = 4
checkpoint_permission_level = 4
recovery_permission_level = 4

[recovery]
policy = "AUTO" # AUTO・ASK・MANUAL
fidelity = "OPTIMIZED" # OPTIMIZED・STRICT
freeze_after_restore = true
require_manual_start = true
ask_timeout_seconds = 60
preload_mode = "ALL"

[recovery.chunks]
max_concurrent_loads = 4
```

| 項目 | 意味 |
| --- | --- |
| `language` | AntiCrashのメッセージ言語。初期値は日本語の`ja`、英語は`en`です。Minecraft・Fabricのログの言語は変わりません。 |
| `interval_seconds` | 自動作成の間隔。ゲームのtick速度に依存しない実時間の秒数で、1〜86,400を指定できます。 |
| `retention_count`・`retention_days`・`retention_max_gb` | 保持数・日数・容量の制限。最新の1件は最低限保持します。日数・GBの`0`はその制限なしですが、保持数には指定できません。 |
| `freeze_after_restore`＋`require_manual_start` | 両方が`true`なら復元後に`/ac start`を待ちます。どちらかを`false`にすると、準備・検査後の自動再開を要求します。 |
| `preload_mode` | `ALL`は保存時にロードされていた全チャンクを当時の準備状態まで読み込みます。`ENTITY_TICKING`はエンティティtick対象、`CRITICAL`は重要地点を選択。STRICTでは必要な全対象の一致が要求されます。 |
| `max_preload_chunks` | チャンク準備数の安全上限。初期値は100,000で、超過時は対象を黙って削らず復旧を失敗させます。 |
| `max_concurrent_loads` | 復旧時の同時チャンク読み込み数。最終的な必要数を減らす設定ではなく、メモリ不足を必ず防げるものでもありません。 |

権限レベルは**0〜4**で、0はOPなしでも使用可能です。初期値が0なのは`/ac start`だけで、他のコマンド権限は4です。管理者だけが再開できるようにするには`start_permission_level`を上げます。ヘルプ・デバッグ・設定再読み込みなどにも個別の権限設定があります。

編集後は`/ac config reload`で再読み込みできます。起動時の復元設定は次回起動前に変更してください。再読み込みで完了済みの復元が取り消されることはありません。

### コマンド

以下はゲーム内での表記です。サーバーコンソールでは先頭の`/`を省きます。

| コマンド | 用途 |
| --- | --- |
| `/ac help` | コマンドヘルプを表示 |
| `/ac status` | チェックポイント・復旧の進行状況を表示 |
| `/ac start` | 復旧検査に通過したワールドの再開を要求 |
| `/ac freeze` | ゲーム内時間を手動で停止 |
| `/ac checkpoint now` | チェックポイント作成を要求 |
| `/ac checkpoint list` | チェックポイント一覧を表示 |
| `/ac checkpoint info <id>` | 指定チェックポイントの情報を表示 |
| `/ac checkpoint base` | 独立した基準チェックポイントを作成 |
| `/ac checkpoint restore <id\|latest>` | 復元と再起動を予約。**ワールドデータが巻き戻ります。** |
| `/ac recovery verify [--verbose]` | 復旧の準備状況と再開できない理由を確認 |
| `/ac debug equivalence [--verbose]` | チェックポイントとの状態差分を表示 |
| `/ac recovery abort` | 復旧を中止。利用可能な場合、退避した巻き戻し前ワールドへ戻す |
| `/ac fakeplayers` | Carpetのフェイクプレイヤー保存情報を表示 |
| `/ac config reload` | 設定を再読み込み |

`[--verbose]`は省略可能な引数です。詳細表示する場合は、例えば`/ac recovery verify --verbose`と入力します。

### 同じコンソールで自動再起動する

**チェックポイント機能にSupervisorは必須ではありませんが、同じコンソールで自動再起動したい場合に推奨します。** Supervisorが前面に残ってMinecraftを子プロセスとして起動し、Y/Nの入力を含むコンソールの入出力を共有します。

対応するSupervisor JARをサーバーディレクトリに置き、Minecraftを直接起動する代わりにSupervisorから起動します。1.18.2の例です。

```sh
java -jar AntiCrash-Supervisor-1.0.1+mc1.18.2.jar \
  --workdir /path/to/server -- \
  java -Xms1G -Xmx1G -jar fabric-server-launch.jar nogui
```

パス・Minecraft版・サーバーJAR名・メモリ指定は環境に合わせます。`--`より後ろが普段のMinecraft起動コマンドです。**例の1GBは必要メモリ量や動作保証ではありません。**

Supervisorは復元に伴う再起動や異常終了時の再試行を行います。通常の`/stop`では再起動しません。短時間で失敗が続く場合は再試行上限で停止し、PID・終了コード・試行回数をログで確認できます。

`start.sh`や管理ツールの起動コマンドを変更するのは、この監視方式を使う場合です。直接起動でもチェックポイント・復旧機能は使えますが、Supervisorによる同一コンソールでの再起動処理は利用できません。

### 困ったとき・注意事項

| 症状 | 確認すること |
| --- | --- |
| ゲーム内時間が止まっている | `/ac status`と`/ac recovery verify --verbose`を確認し、準備完了後に`/ac start`を実行します。 |
| 再開が拒否される | 必要チャンク・実行状態の不一致を確認します。詳細は`/ac debug equivalence --verbose`で表示できます。STRICTは安全に再開できない場合、停止を維持します。 |
| Y/Nの質問が出ない | 初期設定は`AUTO`です。`ASK`の場合はワールド読み込み前の起動コンソールを確認します。 |
| 再起動が行われない | Supervisor経由で起動したかと、終了・再試行ログを確認します。 |
| メモリ不足になる | 必要量はワールドやロード対象に依存します。保存・復旧時には追加メモリが必要で、一定のヒープサイズでの動作保証はありません。 |

独立したバックアップを保管し、ワールドのコピーで復旧を試してください。異なるMinecraft版間での復元はできません。突然の終了では最後の有効チェックポイント以降の変更が失われることがあります。プレイヤーデータを巻き戻す範囲は選択保存の設定によって異なり、外部データベースまでワールドと一緒に巻き戻るわけではありません。

### ビルド・開発

共通ソースが必要なため、リポジトリ全体を取得します。対象バージョンのディレクトリで`./gradlew test build`を実行してください。旧版のGradle実行にはJava 21、26.xにはJava 25を使い、Supervisor用にJDK 17も用意します。サーバー実行時のJava要件は上の表のとおりです。

ルートの`python3 scripts/check_sources.py`でソースの所属・重複を、`python3 scripts/format_java.py --check`でJava書式を検査できます（書式検査はJDK 25）。版別アダプターは分離し、共通コードは`common/`・`shared/`・`supervisor/`に置いています。

1.0.1は保存形式・復旧動作・初期値・権限を維持した保守更新です。aomonaさんの提案から共通化・監査の改善を取り入れています。実験的なStonecutter構成は不要です。比較用に1.0.0のJARも残っていますが、今回の配布物は1.0.1です。既存の1.0.0チェックポイントにも、整合性検査と同じMinecraft版であることが必要です。

ライセンス：**GNU Lesser General Public License v3.0 only**（`LGPL-3.0-only`）。
