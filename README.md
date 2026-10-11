# AntiCrash

Technical Minecraft向けのサーバー用Fabric Mod。作者: **AtsukiMC**、ライセンス: **Apache-2.0**。

![AntiCrash icon](assets/anticrash-icon.jpg)

このREADMEは全バージョン共通です。各Minecraft版のコード・依存関係・テストは、版名のディレクトリに分離します。
**フォルダがあることと、対応Modが完成していることは別です。** 正確な対応状況は
各ディレクトリの`target.json`と[移植・検証記録](docs/MULTIVERSION.md)を参照してください。

## 対応状況

依頼された31版を管理しています。現在の実装基準は`1.18.2/`です。
`1.18/`と`1.18.1/`に加え、1.19以降も版別ソース・内部API・保存状態を順に移植しています。
最新の完了版は`python3 tools/multiversion.py status`と`build/build-report.json`で確認してください。
移植した内部処理の違いは[版別アダプター調査記録](docs/PORT-ADAPTERS.md)に記載しています。
未移植版の空Modや、1.18.2のJARを別名にしたものは配布しません。

2026-10-11の追加移植では、依頼対象の1.18～1.21.11と26.1～26.3、計30版を実装し、offline検証しています。
残る1.16.1は移植中です。専用予定tick基盤とread-only capture adapterを作成しましたが、本体への統合は未完了です。
全31版対応完了ではありません。
内部監査で見つかった既存版の注入先・NBT読込みの問題も修正し、全実装済み版を再検証しています。

```sh
python3 tools/multiversion.py status
```

`BUILD_VERIFIED`はその版のcompile・unit test・buildが成功した状態です。
Minecraft起動、Mixin適用、実装置の復元等価性まで保証するものではありません。
`NOT_PORTED` / `PORT_IN_PROGRESS`には使用可能な配布版はありません。
`IMPLEMENTED`はソース実装済みですが、最終の再テスト・配布成果物の検査が残る状態です。
異なるMinecraft版のCheckpointは互換とみなさず、直接復元しないでください。
Minecraft版の記載がない旧checkpointは原実装の1.18.2用と扱います。移植先の版に自動置換しません。

## 構成とビルド

```text
AntiCrash/
  README.md                  全版共通の説明
  assets/anticrash-icon.jpg   指定された画像（無加工）
  versions.json              対象31版と公式metadata
  1.18.2/                    現行ソース・docs・過去試験の記録
  <Minecraft version>/       版別のソースまたは未移植状態の記録
  tools/multiversion.py       逐次build・成果物検査
  build/                     成功した版の配布JARを集約
```

版ごとのビルド例:

```sh
cd 1.18.2
./gradlew test --no-daemon
./gradlew build --no-daemon
```

成功した`build`はModとSupervisorを共通`build/`へコピーします。名前にはMinecraft版が入ります。
26.xは版内の通常build後、`python3 tools/multiversion.py collect --version 26.1`等で検査・収集します。
収集ツールではZIP・SHA・版指定・アイコンに加え、本体/Supervisorのテスト結果とsource/JARの
更新時刻を検査します。本体を変更してJARだけ作り直した場合も、テストが古ければ収集を拒否します。

```text
AntiCrash-1.0.0+mc1.18.2.jar
AntiCrash-Supervisor-1.0.0+mc1.18.2.jar
```

全実装済み版をテスト・ビルドし、SHA-256とiconを検査するには:

```sh
python3 tools/multiversion.py build --available
```

全31版を対象にするには`--available`を付けません。未移植版がある場合は、実装済み版の処理後に
**非0終了**し、`build/build-report.json`へ`BLOCKED_NOT_PORTED`を記録します。
未移植版を成功扱いしません。処理は逐次実行、Gradle workerは1、Gradle heap上限は1.5GiBです。
プローブ/dev JARや旧名JARは共通配布ディレクトリへ集約しません。

26.xの本体とビルドにはJava 25以上が必要です。[Fabricの26.1移行説明](https://fabricmc.net/2026/03/14/261.html)
に従い、Yarn/remapではなくMinecraftの非難読化された名前を使用します。
一括ビルドは`AC_JAVA25_HOME`、Java 25以上の`JAVA_HOME`、このprojectの`.toolchains/jdk-*`の順でJDKを探します。
Java 17/21版の環境は変更せず、Java 25がなければ26.xだけ明確なエラーとして扱います。

```sh
AC_JAVA25_HOME=/absolute/path/to/jdk-25 python3 tools/multiversion.py build --available
```

直接26.x内でビルドする場合は、その呼出しの`JAVA_HOME`にJava 25のJDKを指定してください。
`.toolchains`は開発用のローカルJDKで、配布JARには同梱しません。

## 導入

1. サーバーと同じMinecraft版の、検証済みMod JARだけを`mods/`へ配置します。
2. その版のFabric LoaderとJava要件を満たしてください。Carpetは任意で、対応するMinecraft版のものを使います。
3. 初回起動で作成される`config/anticrash.toml`を確認します。
4. 自動再起動も使う場合はSupervisorを前面プロセスとして起動します。

Supervisor JARはModではありません。`mods/`には入れません。
現在の実装済み版では、1.18～1.20.4はJava 17以上、1.20.5～1.21.11はJava 21以上、26.xはJava 25以上が必要です。
Loaderの最低指定は1.18～1.21.10が0.17.3、1.21.11/26.1が0.18.4、26.2が0.19.3、26.3が0.19.5です。
Supervisorは全版Java 17でビルドします。子MinecraftのJavaは、その版の要件を満たすものを指定してください。
SupervisorはMinecraftの入出力を同じコンソールへ継承し、再起動時も監視する親プロセスが残ります。
起動スクリプト名は`start.sh`でなくても構いません。
詳細は[復旧手順](1.18.2/docs/RECOVERY.md)を参照してください。

## 機能と限界

- 定期Checkpoint、checksum検証、ワールド読み込み前の復旧判断。
- 復元後は凍結し、確認後に`/ac start`で再開。
- ピストン、予定tick、Block Event、実行順序などのruntime stateを版ごとに記録・復元。
- 対応CarpetのFakePlayer・ActionPack等を保存。取得不能な状態は隠さず診断。
- READY世代は不変。書込み可能なlive Worldとhardlinkを共有しません。
- OOM・不正な親などでは保存処理を停止し、不完全な世代を復元しません。

すべてのTMC装置・Mod・乱数・thread schedulingの完全再現を保証するものではありません。
Worldの規模によって必要メモリと停止時間は変わります。通常バックアップも併用してください。
保存中のActionbar通知は表示しません。

### 26.xの保存形式

1.18～1.21.11はRuntime Capsule v7、26.xはv8です。26.xでは整数時刻だけでなくWorld Clockの
小数位相・速度・停止状態を検証し、実際の復元は保存済みVanilla SavedDataが担います。
v7等で必須Clock情報が欠ける場合、STRICTでは`MISSING_FROM_OLD_FORMAT`として開始を拒否します。
Capsule versionが同じでも異なるMinecraft版のcheckpointは直接復元できません。
この制限は、Vanillaの通常のワールド更新を一律禁止するものではありません。
流体接触状態、26.2の硫黄タイマー、26.3の醸造レシピ選択なども版別に保存・比較します。
詳細と未対応状態は[版別内部実装](docs/PORT-ADAPTERS.md)を確認してください。

## 設定

以下は共通する主な設定の例です。既存configをこの抜粋で置き換える必要はありません。
各版の生成configと[設定リファレンス](1.18.2/docs/CONFIGURATION.md)を優先してください。

```toml
language = "ja"  # "ja" または "en"。AntiCrashのメッセージのみ。

[checkpoint]
interval_seconds = 30

[commands]
start_permission_level = 0
checkpoint_permission_level = 4
recovery_permission_level = 4
debug_permission_level = 4

[recovery]
policy = "ASK"
fidelity = "OPTIMIZED"
freeze_after_restore = true
require_manual_start = true
ask_timeout_seconds = 60
preload_mode = "ALL"

[recovery.chunks]
max_concurrent_loads = 4
```

権限レベルは0〜4。既定でOPなしで使えるのは`/ac start`のみです。
`ask_timeout_seconds`は実際の回答期限で、30秒固定ではありません。

| ポリシー | 異常終了後 | 正常終了後 |
|---|---|---|
| AUTO | 検証して復元 | 現在のWorldで起動 |
| ASK | Yで復元、Nで継続。無回答はY | Yで復元、Nで継続。無回答はN |
| MANUAL | 管理者の判断を待つ | 通常起動 |

初回起動や候補なしなど、質問しない経路もあります。
STRICTは観測可能な実行状態の等価性を再開条件とし、欠落・不一致を原則拒否します。
OPTIMIZEDは軽量best-effortです。
**OPTIMIZED does not guarantee tick-boundary equivalence.**

## コマンド

| コマンド | 用途 |
|---|---|
| `/ac status` | 保存・復旧・メモリ等の状態 |
| `/ac start` | 凍結解除を予約し、完了時に通知 |
| `/ac checkpoint now` | 手動Checkpoint |
| `/ac checkpoint base` | 独立BASEの作成 |
| `/ac checkpoint list` | 世代一覧 |
| `/ac checkpoint restore <id\|latest>` | 検証・復元・再起動 |
| `/ac recovery verify --verbose` | 復旧等価性の診断 |
| `/ac debug equivalence --verbose` | 詳細差分 |
| `/ac recovery abort` | 復旧を中止 |
| `/ac fakeplayers` | FakePlayerの状態 |
| `/ac help` | 利用できるコマンド |

版によって内部実装・取得可能状態が異なります。現行版の詳細資料は[1.18.2/docs](1.18.2/docs/REFERENCE.md)です。
本作業では本番サーバー・World・Checkpoint・Proxy・MySQLを変更していません。

## English summary

AntiCrash is a server-side Fabric checkpoint/recovery mod by AtsukiMC, licensed under Apache-2.0.
This repository separates Minecraft targets into version-named directories. A directory is not a compatibility claim:
check `target.json` and the build report. Only successfully tested/built ports are collected in the top-level `build/`.
Install the matching Minecraft JAR; never restore a checkpoint across Minecraft versions without a separately verified migration.
Runtime and TMC recovery qualification are separate from compilation/unit tests. Keep conventional backups.
