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
