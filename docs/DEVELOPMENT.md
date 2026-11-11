# AntiCrashの開発

AntiCrashは、共通のJavaソースをStonecutterでMinecraft各版へ展開してビルドします。開発時は対象版を一つ選び、変更に関係するAPI境界の版も検証してください。対応版と依存関係は`stonecutter.properties.toml`で管理しています。

## 開発環境

リポジトリのルートで、同梱のGradle Wrapperを使います。以下の環境が必要です。

| ツール | 用途 |
| --- | --- |
| JDK 25 | Gradleの実行と新しいMinecraft版のコンパイル |
| JDK 21 | 古いMinecraft版のコンパイル |
| JDK 17 | Supervisorのコンパイル |
| Python 3.11以上 | 整形、ソース構成検査、補助ツールのテスト |
| Bash・jq | CIのRelease組み立てと、その回帰テスト |

`JAVA_HOME`にJDK 25を指定し、JDK 17・21をGradle toolchainが検出できるようにします。Pythonのビルド・整形スクリプトは、`.toolchains/jdk-25*`がある場合はそのJDKを優先します。JDKやキャッシュはGitへ追加しません。

```sh
./gradlew --version
```

現在の構成はGradle 9.6、Stonecutter 0.9.8、Loom 1.17.21です。Gradleのworker数は1、ヒープ上限は1536 MB、認識CPU数は2に設定されています。版ごとのテストJVMには別途最大512 MBを使用します。

## ソースとビルド設定

| 場所 | 内容 |
| --- | --- |
| `src/main/java`・`src/main/resources` | Modの共通ソースとリソース |
| `src/test` | 単体テスト、Minecraft内部API・Mixinの検査 |
| `src/qualification` | 隔離サーバー用の検証Mod。通常の配布JARには含めません |
| `supervisor` | Minecraftに依存しない監視・再起動プログラム |
| `stonecutter.properties.toml` | 対応版、Java・Loader要件、APIフラグ、検査データの選択 |
| `settings.gradle.kts`・`stonecutter.gradle.kts` | 対象プロジェクトの選択、共通タスク、VCS基準版 |
| `build.gradle.kts`・`gradle/minecraft.gradle` | 各版の依存関係、テスト、JAR生成・検査 |
| `gradle/mixins` | 順序を含むMixin登録設定 |
| `qualification/fixtures` | 版ごとのEntity・BlockEntityフィールド監査データ |
| `scripts` | 整形、比較、全版ビルド、Release組み立ての補助ツール |

`versions/`はStonecutterが作る版別の作業・出力先です。ソースを手動で複製する場所ではありません。

## 一つの版をビルドする

Minecraft 1.18.2を対象にする例です。

```sh
./gradlew --no-daemon -PmcVersion=1.18.2 :1.18.2:build
```

このコマンドは対象版のコンパイル、単体テスト、配布JAR検査、Supervisorの生成・テストを実行します。検証Modに対応する版では`probeJar`も生成します。`-PmcVersion`は設定するプロジェクトを絞る指定です。実行対象の版は`:1.18.2:build`のようにタスクにも明記してください。

| 出力先 | 内容 |
| --- | --- |
| `versions/1.18.2/build/libs/` | Mod JARとソースJAR |
| `supervisor/build/libs/` | Supervisor JAR |
| `versions/1.18.2/build/qualification/` | 対応版の検証Mod |
| `dist/refactor/<Mod版>/` | ビルドしたMod・Supervisorの配布候補 |
| `versions/1.18.2/build/reports/tests/test/` | 対象版のテストレポート |

テストだけをやり直す場合は次を使います。

```sh
./gradlew --no-daemon -PmcVersion=1.18.2 :1.18.2:cleanTest :1.18.2:test :supervisor:test
```

## 整形と共通の検査

```sh
./gradlew -PmcVersion=26.3 qualityCheck
./gradlew -PmcVersion=26.3 formatApply
```

`qualityCheck`は整形の確認、ソース配置・ビルド入力の検査、補助スクリプトのテストを実行します。Javaの単体テストは各版の`test`または`build`で実行してください。`formatApply`はソースを書き換えるため、実行後に差分を確認します。

整形にはgoogle-java-format（AOSP、字下げ4スペース）を使います。`scripts/StonecutterFormat.java`がStonecutter本体の処理系で全対象版へ展開し、非アクティブな枝も、その枝が有効になる版での形に整えます。どの版に切り替えても字下げが揃い、条件コメントは開く枝の最初の行と同じ列、`//?}`は対応する`//? if`と同じ列に置かれます。どの版がactiveでも整形結果は同じです。

整形は字下げと改行だけを変えます。importの削除・並べ替え、修飾子の並べ替え、長い文字列の分割は行わず、版専用のimportも保持します。整形のたびに、全対象版で選ばれるJavaトークンが整形前と一致すること、結果をactive版へ切り替えてもStonecutterが書き換えないことを検査します。実行にはGradleキャッシュ内のStonecutterとKotlin標準ライブラリを使うため、未取得の場合は先に一度Gradleを実行してください。

## Stonecutterの版切り替えと条件分岐

コミット時のactive版とVCS基準版は、現在は26.3です。VCS基準版はTOMLの最後の対象版から決まります。IDEで別の版を編集した場合は、コミット前に基準版へ戻してください。

```sh
./gradlew -PmcVersion=1.18.2 stonecutterSwitchTo1.18.2
# 対象版で編集・検証したあとに戻す
./gradlew -PmcVersion=26.3 stonecutterSwitchTo26.3
```

版切り替えは一回のGradle呼び出しにつき一つにします。非アクティブな枝はコメントとして保存されるため、通常の不要コードと判断して削除しないでください。

APIと処理が同じ部分は条件の外へ出し、差がある箇所だけを条件分岐にします。同じimport、メソッド宣言、ループ全体を版ごとに繰り返す必要はありません。同じAPI変更が多くの箇所に現れる場合は、条件をヘルパーへ集めて呼び出し側を条件なしにします。ID（`ResourceLocation`/`Identifier`、`ResourceKey`の文字列化、組み込みレジストリの参照）は`util/VersionIds`を、26.1でrecordになった`ChunkPos`の座標・long変換は`util/VersionChunkPos`を、1.21.5でOptionalになったNBT読み取りは`runtime/VersionNbt`を、1.21.6でviewになった保存と復元は`runtime/VersionStorage`を、1.19で変わったテキスト生成と26.1で分かれたプレイヤーへのメッセージ送信は`runtime/VersionMessages`を、1.21.11で変わったコマンド権限レベルの判定は`command/VersionPermissions`を、1.20で変わったEntityのLevel取得と1.21.6で変わったServerPlayerのサーバー取得は`util/VersionLevels`を使います。Client側の判定には全版で`Level.isClientSide()`を直接使います。`if / else if`を使うかは条件の意味に合わせて選び、分岐数を減らすために版固有の挙動を失わないようにしてください。

条件整理前後のJavaトークンを全版で比較する補助コマンドもあります。

```sh
python3 scripts/check_conditions.py --baseline <変更前コミット>
```

この比較はコメントと空白を除外しますが、importやその順序の変更も差分になります。コンパイルや名前解決の同一性は検証しません。import整理を含む変更では、全版のビルドとJAR内の実行コード比較を併用してください。

## 全対応版を検証する

ローカルでメモリ使用量を抑える場合は、版ごとにGradleプロセスを終了する実行方法を使います。

```sh
python3 scripts/build_matrix.py --all
```

各版のテストを再実行し、ビルド、JAR検査、ログと`summary.json`の保存まで行います。保存先の初期値は`build/matrix/<日時>/`です。`--output`で変更できます。`--version 1.18.2 --version 26.3`のように対象を絞ることもできます。

Gradleだけで全版を実行する場合は以下を使います。全版を設定するため、`-PmcVersion`は付けません。

```sh
./gradlew buildAll
./gradlew testAll
```

`buildAll`は各版の`build`、`testAll`は各版とSupervisorの`test`を集約します。`qualityCheck`は別途実行してください。Gradleの集約タスクは複数版のモデルを同じプロセスに保持します。Pythonの実行方法とはログの保存方法やメモリ使用量が異なります。

## 変更前のJARとの比較

比較元として、同じMod版の変更前JAR一式を別ディレクトリへ保存します。

```sh
python3 scripts/build_matrix.py --all \
  --baseline-dir ../anticrash-before \
  --output ../anticrash-verification
```

比較ではクラスの集合、デバッグ情報を除いた実行コード、リソースを確認します。Gradle・Loomなどの許可されたビルド情報は比較対象から除外します。比較に失敗した場合は差分の理由を調べ、同じ動作と決めつけないでください。比較元がなければ、通常のビルド・整合性検査のみを実施した結果として記録します。

## 変更とコミット

一つのコミットには一つの目的を持たせます。例えば、条件分岐の共通化、検査ツールの修正、開発文書の更新は別コミットにします。一つの共通化に必要な複数ファイルは、同じコミットに含められます。

コミット前にステージした差分全体を確認してください。変更理由と影響範囲をコミット本文へ書き、import整理、設定変更、検査方式の変更なども省略しません。PRには全変更の一覧と検証結果を記載し、未検証の範囲も明示します。

```sh
git diff --stat
git diff --cached
git diff --cached --check
```

ログ、ワールド、ローカル設定、キャッシュ、ビルド出力はコミットしません。

## CIとRelease

CIの入口は`.github/workflows/release.yml`です。品質検査用の`checks.yml`と全版ビルド用の`build.yml`を呼び出し、成功後に配布物を組み立てます。PRとmainへのpushでは、検証用artifactを作成します。文書だけを変更するPRはワークフローの対象外です。

正式版を用意するときは、`gradle.properties`の`mod_version`と同じタグをpushします。検査成功後にdraft Releaseが作成されるため、内容を確認して公開します。手動実行で`publish`を有効にする場合も、その版のタグを選択する必要があります。既存Releaseを上書きする処理はありません。

Releaseの添付物は全対象版のMod・Supervisor JAR、`SHA256SUMS`、`artifacts.json`です。テストレポートやログは検証用artifactに保存します。マニフェストにはビルド元のcommit、CIのURL、テスト数、JARのハッシュを記録します。

## Minecraft対応版を追加する

1. `stonecutter.properties.toml`へ対象版と依存関係、Java要件、APIフラグを追加します。最後に追加した版がVCS基準版になることも確認します。
2. 対象MinecraftのAPIとbytecodeを調べ、互換処理とMixinの対象・注入位置を確認します。
3. 必要なMixin設定を`gradle/mixins`へ追加し、登録順序も確認します。
4. Entity・BlockEntityのフィールドを調査し、`qualification/fixtures`の監査データを用意します。既存データを使う場合も対象版との一致を確認します。
5. ビルド・テスト・配布JAR検査と、必要な隔離サーバー検証を実施します。

`versions.json`は公開版の検証記録です。正式リリース時に、実施した検証結果に合わせて更新してください。

## 隔離サーバーでの動作確認

実Minecraftの検証には、EULAに同意済みの専用テスト環境を使います。既存サーバーのワールド、設定、Mod、稼働プロセスを変更しないでください。検証サーバーは一度に一つとし、自分で起動したプロセスだけを停止します。

起動とMixin適用、forced chunkとチェストを含むBASE・DELTA作成、READY・manifest・チェックサム、正常終了と異常終了後の復旧を確認します。復旧準備中にゲーム内時間が進まないこと、`/ac recovery verify`と`/ac start`、復元したブロックとインベントリも確認します。

ビルド・単体テスト・JAR比較の結果と、Minecraft上での動作確認結果は別に記録してください。
