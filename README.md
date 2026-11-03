# AntiCrash

**Minecraft Fabric server mod** · AtsukiMC · **v1.0.3** · **LGPL-3.0-only**

[English](#english) · [日本語](#日本語)

## English

### About

AntiCrash creates periodic checkpoints of world data and supported runtime state. After an unexpected shutdown, its recovery policy determines whether a checkpoint is restored. Recovery keeps game time paused until preparation finishes; by default, run `/ac start` to resume. With a compatible Carpet version, AntiCrash also restores fake players and their saved actions.

Recovery coverage depends on the Minecraft version and installed mods. `OPTIMIZED` provides best-effort recovery. `STRICT` keeps game time paused when required state is missing or differs from the checkpoint.

### Installation

Download the v1.0.3 JAR for the exact Minecraft version from [Release 1.0.3](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.3), then place it in the server's `mods/` folder. AntiCrash runs on the server, and clients can join with a Vanilla client. Fabric API is optional. Install a compatible Carpet version for fake-player integration.

Mod files are named `AntiCrash-1.0.3+mc<VERSION>.jar`. The optional Supervisor file is named `AntiCrash-Supervisor-1.0.3+mc<VERSION>.jar`.

### Supported versions

AntiCrash provides separate JARs for Minecraft 1.18, 1.18.1, 1.18.2; 1.19 through 1.19.4; 1.20 through 1.20.6; 1.21 through 1.21.11; and 26.1 through 26.3. The listed versions through 1.21.10 require Fabric Loader 0.17.3 or newer. Minecraft 1.18 through 1.20.4 requires Java 17 or newer; 1.20.5 through 1.21.10 requires Java 21 or newer. Minecraft 1.21.11 requires Loader 0.18.4 or newer and Java 21 or newer. Minecraft 26.1 requires Loader 0.18.4 or newer, 26.2 requires 0.19.3 or newer, and 26.3 requires 0.19.5 or newer; all three require Java 25 or newer.

### Recovery and settings

The server creates `config/anticrash.toml` on first launch. The default checkpoint interval is 30 seconds, the default language is Japanese, and the default recovery policy is `AUTO`. `AUTO` restores a valid checkpoint after an unexpected shutdown and starts normally after a clean shutdown. `ASK` requests a `Y` or `N` answer in the server console before loading the world; its default timeout is 60 seconds. `MANUAL` waits for an explicit recovery decision. Set `language` to `"en"` or `"ja"`, and change the checkpoint interval or recovery policy in the same file.

Use `/ac status` to view progress, `/ac start` to resume game time, `/ac checkpoint now` to create a checkpoint, and `/ac recovery verify` to check recovery readiness. `/ac start` requires permission level 0 by default; other commands require level 4.

### Supervisor

The optional Supervisor monitors Minecraft and can restart it in the same console after a crash or controlled restart. Place its matching JAR in the server directory, outside `mods/`, and launch it with the server command, for example: `java -jar AntiCrash-Supervisor-1.0.3+mc1.18.2.jar --workdir /path/to/server -- java -jar fabric-server-launch.jar nogui`.

## 日本語

### 概要

AntiCrashは、ワールドデータと対応する実行状態のチェックポイントを定期的に作成するMinecraft Fabric用サーバーModです。異常終了後は設定に応じてチェックポイントを復元します。復旧の準備中はゲーム内時間を停止し、初期設定では`/ac start`で再開します。対応するCarpetが導入されている場合は、FakePlayerと保存した操作も復元します。

復元範囲はMinecraftのバージョンや導入Modによって異なります。`OPTIMIZED`は可能な範囲で復元します。`STRICT`は必須状態が欠けている場合やチェックポイントと異なる場合、ゲーム内時間の再開を保留します。

### 導入方法

[Release 1.0.3](https://github.com/HikariServerDev/AntiCrash/releases/tag/1.0.3)から、Minecraftのバージョンが完全に一致するJARをダウンロードし、サーバーの`mods/`へ入れてください。AntiCrashはサーバーに導入し、クライアントはVanillaのまま参加できます。Fabric APIは任意です。FakePlayer連携には対応するCarpetを使います。

Modのファイル名は`AntiCrash-1.0.3+mc<VERSION>.jar`です。任意のSupervisorは`AntiCrash-Supervisor-1.0.3+mc<VERSION>.jar`です。

### 対応バージョン

Minecraft 1.18・1.18.1・1.18.2、1.19〜1.19.4、1.20〜1.20.6、1.21〜1.21.11、26.1〜26.3に対応し、各版専用のJARを配布しています。1.18〜1.21.10の対応版ではFabric Loader 0.17.3以上が必要です。Javaは1.18〜1.20.4で17以上、1.20.5〜1.21.10で21以上が必要です。1.21.11はLoader 0.18.4以上とJava 21以上が必要です。26.1はLoader 0.18.4以上、26.2は0.19.3以上、26.3は0.19.5以上が必要で、いずれもJava 25以上を使用します。

### 復旧と設定

初回起動時に`config/anticrash.toml`が作成されます。チェックポイント間隔の初期値は30秒、言語は日本語、復旧ポリシーは`AUTO`です。`AUTO`は異常終了後に有効なチェックポイントを復元し、正常終了後は通常起動します。`ASK`はワールド読み込み前にサーバーコンソールで`Y`または`N`を求めます。回答期限の初期値は60秒です。`MANUAL`は明示的な復旧判断を待ちます。言語は`language`を`"ja"`または`"en"`にして選び、保存間隔や復旧ポリシーも同じファイルで変更できます。

`/ac status`で進行状況、`/ac start`でゲーム内時間の再開、`/ac checkpoint now`でチェックポイントの作成、`/ac recovery verify`で復旧準備の状態を確認できます。初期設定では`/ac start`は権限レベル0、その他のコマンドはレベル4が必要です。

### Supervisor

任意のSupervisorを使うと、クラッシュや制御された再起動の後にMinecraftを同じコンソールで再起動できます。対応するSupervisorのJARを`mods/`の外にあるサーバーディレクトリへ置き、サーバー起動コマンドを渡して実行します。1.18.2の場合は、例えば`java -jar AntiCrash-Supervisor-1.0.3+mc1.18.2.jar --workdir /path/to/server -- java -jar fabric-server-launch.jar nogui`です。

ライセンスは**GNU Lesser General Public License v3.0 only**（`LGPL-3.0-only`）です。
