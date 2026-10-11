# AntiCrash

**Minecraft Fabric server mod** — by AtsukiMC · [LGPL-3.0-only](LICENSE)

![AntiCrash icon](assets/anticrash-icon.jpg)

---

## English

### What is AntiCrash?

AntiCrash is a checkpoint and recovery mod for Fabric Minecraft servers, with Technical Minecraft in mind. It can create periodic world checkpoints and inspect or restore a valid checkpoint during startup, according to the configured recovery policy. A restored world can remain frozen until an administrator resumes it with `/ac start`.

AntiCrash aims to reduce the impact of unexpected shutdowns. It does **not** promise a perfect, tick-for-tick replay of every Minecraft or modded state, and it does not replace regular backups.

### Available versions

The 30 `BUILD_VERIFIED` versions below have a version-specific Mod JAR in [`build/`](build/). Build verification means that the source compiled, unit tests passed, and the distributable was checked; it does not mean that the version has been qualified in a live Minecraft server. **1.16.1 is still being ported and has no distributable JAR.**

| Minecraft | Mod JAR | Fabric Loader | Java |
| --- | --- | --- | --- |
| 1.16.1 | Port in progress — unavailable | — | — |
| 1.18 | `AntiCrash-1.0.0+mc1.18.jar` | 0.17.3+ | 17+ |
| 1.18.1 | `AntiCrash-1.0.0+mc1.18.1.jar` | 0.17.3+ | 17+ |
| 1.18.2 | `AntiCrash-1.0.0+mc1.18.2.jar` | 0.17.3+ | 17+ |
| 1.19 | `AntiCrash-1.0.0+mc1.19.jar` | 0.17.3+ | 17+ |
| 1.19.1 | `AntiCrash-1.0.0+mc1.19.1.jar` | 0.17.3+ | 17+ |
| 1.19.2 | `AntiCrash-1.0.0+mc1.19.2.jar` | 0.17.3+ | 17+ |
| 1.19.3 | `AntiCrash-1.0.0+mc1.19.3.jar` | 0.17.3+ | 17+ |
| 1.19.4 | `AntiCrash-1.0.0+mc1.19.4.jar` | 0.17.3+ | 17+ |
| 1.20 | `AntiCrash-1.0.0+mc1.20.jar` | 0.17.3+ | 17+ |
| 1.20.1 | `AntiCrash-1.0.0+mc1.20.1.jar` | 0.17.3+ | 17+ |
| 1.20.2 | `AntiCrash-1.0.0+mc1.20.2.jar` | 0.17.3+ | 17+ |
| 1.20.3 | `AntiCrash-1.0.0+mc1.20.3.jar` | 0.17.3+ | 17+ |
| 1.20.4 | `AntiCrash-1.0.0+mc1.20.4.jar` | 0.17.3+ | 17+ |
| 1.20.5 | `AntiCrash-1.0.0+mc1.20.5.jar` | 0.17.3+ | 21+ |
| 1.20.6 | `AntiCrash-1.0.0+mc1.20.6.jar` | 0.17.3+ | 21+ |
| 1.21 | `AntiCrash-1.0.0+mc1.21.jar` | 0.17.3+ | 21+ |
| 1.21.1 | `AntiCrash-1.0.0+mc1.21.1.jar` | 0.17.3+ | 21+ |
| 1.21.2 | `AntiCrash-1.0.0+mc1.21.2.jar` | 0.17.3+ | 21+ |
| 1.21.3 | `AntiCrash-1.0.0+mc1.21.3.jar` | 0.17.3+ | 21+ |
| 1.21.4 | `AntiCrash-1.0.0+mc1.21.4.jar` | 0.17.3+ | 21+ |
| 1.21.5 | `AntiCrash-1.0.0+mc1.21.5.jar` | 0.17.3+ | 21+ |
| 1.21.6 | `AntiCrash-1.0.0+mc1.21.6.jar` | 0.17.3+ | 21+ |
| 1.21.7 | `AntiCrash-1.0.0+mc1.21.7.jar` | 0.17.3+ | 21+ |
| 1.21.8 | `AntiCrash-1.0.0+mc1.21.8.jar` | 0.17.3+ | 21+ |
| 1.21.9 | `AntiCrash-1.0.0+mc1.21.9.jar` | 0.17.3+ | 21+ |
| 1.21.10 | `AntiCrash-1.0.0+mc1.21.10.jar` | 0.17.3+ | 21+ |
| 1.21.11 | `AntiCrash-1.0.0+mc1.21.11.jar` | 0.18.4+ | 21+ |
| 26.1 | `AntiCrash-1.0.0+mc26.1.jar` | 0.18.4+ | 25+ |
| 26.2 | `AntiCrash-1.0.0+mc26.2.jar` | 0.19.3+ | 25+ |
| 26.3 | `AntiCrash-1.0.0+mc26.3.jar` | 0.19.5+ | 25+ |

The Loader values are the versions declared by each build target. Check the matching version directory for its exact dependencies and status. Checkpoints are not interchangeable between Minecraft versions.

### Installation

- **Dedicated server:** put the matching AntiCrash JAR in the server's `mods` folder. Players do not need to install AntiCrash and can join with an unmodded client.
- **Singleplayer/integrated server:** install the matching JAR in the Minecraft instance's `mods` folder.
- Fabric API is optional for the ports that declare it as a suggestion. Carpet is optional; supported Carpet integrations are version-dependent.
- The optional `AntiCrash-Supervisor-1.0.0+mc<VERSION>.jar` is a separate Java program, **not** a mod. Do not put it in `mods/`. It can supervise the Minecraft process and keep restart input/output in the same console. See the [recovery guide](1.18.2/docs/RECOVERY.md).

### How to use it

On first start, review `config/anticrash.toml`. AntiCrash uses `language = "ja"` by default; set it to `"en"` for English messages. Recovery policy, checkpoint interval, command permission levels, and freeze behavior are configurable. By default, `/ac start` is available without operator permission; other commands use their configured permission levels.

Useful commands include `/ac status`, `/ac checkpoint now`, `/ac recovery verify`, and `/ac start`. Exact options and defaults may differ by Minecraft port; see the [configuration reference](1.18.2/docs/CONFIGURATION.md) and [command reference](1.18.2/docs/REFERENCE.md).

### What it does

- Creates configurable checkpoints and validates checkpoint data before recovery.
- Supports recovery decisions at startup and can keep a restored world frozen until explicitly resumed.
- Captures/restores selected vanilla runtime state and supported Carpet/FakePlayer state, with diagnostics for unsupported or mismatched state.
- Provides a separate Supervisor for controlled process restarts and restart monitoring.

### Good to know

- Build and unit-test verification is not the same as live-server compatibility testing. Runtime integration is not verified for the version ports listed above.
- `STRICT` recovery can refuse to resume when required state is missing or does not match. `OPTIMIZED` is a lighter best-effort mode: **OPTIMIZED does not guarantee tick-boundary equivalence.**
- No mod can safely promise exact recovery for every mod, machine, asynchronous task, random state, or thread schedule. In particular, test important TMC contraptions on a separate copy of your world first.
- Keep independent backups. World size and configuration affect checkpoint time, storage, and memory use; an out-of-memory termination can still lose changes after the last valid checkpoint.
- The repository's `1.16.1/` directory is an incomplete port, not a usable release.

### Repository layout

Each Minecraft version has its own source/build directory. Tested distributable JARs are collected in [`build/`](build/); temporary Gradle outputs and local toolchains are not part of that distribution folder. The requested icon is in [`assets/`](assets/), and the full license is in [`LICENSE`](LICENSE).

---

## 日本語

### AntiCrashとは？

AntiCrashは、Technical Minecraftでの利用を想定したFabricサーバー向けのチェックポイント・復旧Modです。設定した間隔でワールドのチェックポイントを作成し、起動時に復旧ポリシーに従って候補を検証・復元できます。復元後にワールドを停止状態に保ち、管理者が`/ac start`で再開する運用にも対応しています。

予期しない終了による影響を減らすことを目的としていますが、Minecraftや他Modのあらゆる状態をtick単位で完全再現する保証はありません。通常のバックアップの代わりにはなりません。

### 対応バージョン

以下のうち30版は`BUILD_VERIFIED`で、版別のMod JARを[`build/`](build/)に収録しています。これはソースのコンパイル、ユニットテスト、成果物の検査が完了したという意味で、実Minecraftサーバー上での動作確認を意味しません。**1.16.1は移植中で、配布JARはありません。**

| Minecraft | Mod JAR | Fabric Loader | Java |
| --- | --- | --- | --- |
| 1.16.1 | 移植中 — 利用不可 | — | — |
| 1.18 | `AntiCrash-1.0.0+mc1.18.jar` | 0.17.3以上 | 17以上 |
| 1.18.1 | `AntiCrash-1.0.0+mc1.18.1.jar` | 0.17.3以上 | 17以上 |
| 1.18.2 | `AntiCrash-1.0.0+mc1.18.2.jar` | 0.17.3以上 | 17以上 |
| 1.19 | `AntiCrash-1.0.0+mc1.19.jar` | 0.17.3以上 | 17以上 |
| 1.19.1 | `AntiCrash-1.0.0+mc1.19.1.jar` | 0.17.3以上 | 17以上 |
| 1.19.2 | `AntiCrash-1.0.0+mc1.19.2.jar` | 0.17.3以上 | 17以上 |
| 1.19.3 | `AntiCrash-1.0.0+mc1.19.3.jar` | 0.17.3以上 | 17以上 |
| 1.19.4 | `AntiCrash-1.0.0+mc1.19.4.jar` | 0.17.3以上 | 17以上 |
| 1.20 | `AntiCrash-1.0.0+mc1.20.jar` | 0.17.3以上 | 17以上 |
| 1.20.1 | `AntiCrash-1.0.0+mc1.20.1.jar` | 0.17.3以上 | 17以上 |
| 1.20.2 | `AntiCrash-1.0.0+mc1.20.2.jar` | 0.17.3以上 | 17以上 |
| 1.20.3 | `AntiCrash-1.0.0+mc1.20.3.jar` | 0.17.3以上 | 17以上 |
| 1.20.4 | `AntiCrash-1.0.0+mc1.20.4.jar` | 0.17.3以上 | 17以上 |
| 1.20.5 | `AntiCrash-1.0.0+mc1.20.5.jar` | 0.17.3以上 | 21以上 |
| 1.20.6 | `AntiCrash-1.0.0+mc1.20.6.jar` | 0.17.3以上 | 21以上 |
| 1.21 | `AntiCrash-1.0.0+mc1.21.jar` | 0.17.3以上 | 21以上 |
| 1.21.1 | `AntiCrash-1.0.0+mc1.21.1.jar` | 0.17.3以上 | 21以上 |
| 1.21.2 | `AntiCrash-1.0.0+mc1.21.2.jar` | 0.17.3以上 | 21以上 |
| 1.21.3 | `AntiCrash-1.0.0+mc1.21.3.jar` | 0.17.3以上 | 21以上 |
| 1.21.4 | `AntiCrash-1.0.0+mc1.21.4.jar` | 0.17.3以上 | 21以上 |
| 1.21.5 | `AntiCrash-1.0.0+mc1.21.5.jar` | 0.17.3以上 | 21以上 |
| 1.21.6 | `AntiCrash-1.0.0+mc1.21.6.jar` | 0.17.3以上 | 21以上 |
| 1.21.7 | `AntiCrash-1.0.0+mc1.21.7.jar` | 0.17.3以上 | 21以上 |
| 1.21.8 | `AntiCrash-1.0.0+mc1.21.8.jar` | 0.17.3以上 | 21以上 |
| 1.21.9 | `AntiCrash-1.0.0+mc1.21.9.jar` | 0.17.3以上 | 21以上 |
| 1.21.10 | `AntiCrash-1.0.0+mc1.21.10.jar` | 0.17.3以上 | 21以上 |
| 1.21.11 | `AntiCrash-1.0.0+mc1.21.11.jar` | 0.18.4以上 | 21以上 |
| 26.1 | `AntiCrash-1.0.0+mc26.1.jar` | 0.18.4以上 | 25以上 |
| 26.2 | `AntiCrash-1.0.0+mc26.2.jar` | 0.19.3以上 | 25以上 |
| 26.3 | `AntiCrash-1.0.0+mc26.3.jar` | 0.19.5以上 | 25以上 |

Loader欄は各版のビルド設定で宣言しているバージョンです。正確な依存関係や状態は各版のディレクトリを確認してください。異なるMinecraft版間でチェックポイントを共有することはできません。

### 導入方法

- **専用サーバー:** サーバーと同じMinecraft版のAntiCrash JARを`mods`フォルダへ入れます。プレイヤー側にAntiCrashは不要で、Modなしのクライアントから参加できます。
- **シングルプレイ:** 同じMinecraft版のJARをインスタンスの`mods`フォルダへ入れます。
- Fabric APIは各版の依存定義を確認してください（任意依存としている版があります）。Carpetは任意ですが、連携機能は版によって異なります。
- 任意の`AntiCrash-Supervisor-1.0.0+mc<VERSION>.jar`は別のJavaプログラムで、**Modではありません**。`mods/`には入れません。同じコンソールで再起動を監視する用途に使えます。[復旧ガイド](1.18.2/docs/RECOVERY.md)を参照してください。

### 使い方

初回起動時に作成される`config/anticrash.toml`を確認してください。既定の`language = "ja"`を`"en"`にするとAntiCrashのメッセージを英語にできます。復旧ポリシー、チェックポイント間隔、コマンド権限、凍結動作などを設定できます。既定では`/ac start`だけOP権限なしで使え、他のコマンドは設定された権限レベルに従います。

主なコマンドは`/ac status`、`/ac checkpoint now`、`/ac recovery verify`、`/ac start`です。詳しいオプションと既定値は版によって異なる場合があるため、[設定リファレンス](1.18.2/docs/CONFIGURATION.md)と[コマンドリファレンス](1.18.2/docs/REFERENCE.md)を確認してください。

### できること

- 設定可能なチェックポイント作成と、復旧前のデータ検証。
- 起動時の復旧判断。復元後、明示的な再開までワールドを凍結する運用。
- Vanillaの一部runtime stateと、対応するCarpet/FakePlayerの状態を保存・復元し、未対応・不一致を診断。
- 制御再起動やプロセス監視を行う独立Supervisor。

### ご注意

- ビルド・ユニットテストの成功は、実サーバーでの互換性試験とは異なります。上記バージョンのruntime integrationは未検証です。
- `STRICT`は必須状態が欠落・不一致の場合に再開を拒否できます。`OPTIMIZED`は軽量なbest-effort方式で、**tick境界の等価性を保証しません**。
- すべてのMod、装置、乱数、非同期処理、スレッド実行順を完全に再現する保証はありません。重要なTMC装置は、まずワールドの複製で十分に試してください。
- 独立したバックアップも必ず保管してください。必要な時間・容量・メモリはワールドや設定により異なり、OOM発生時には最後の有効なチェックポイント以降の変更が失われる可能性があります。
- `1.16.1/`は移植途中であり、利用可能なリリースではありません。

### リポジトリ構成

Minecraft版ごとにソースとビルド設定を分けています。配布用JARは[`build/`](build/)に集約し、Gradleの一時出力やローカルJDKは含めません。アイコンは[`assets/`](assets/)、ライセンス全文は[`LICENSE`](LICENSE)にあります。
