# 開発・検証

## 必要な環境

Gradle 9.6 を JDK 25 で実行する。JDK 17 と 21 もインストールし、Gradle toolchain が検出できるようにする。Python スクリプトには Python 3.11 以上が必要。JDK はリポジトリに含めない。Gradle は worker 1、最大ヒープ 1536 MB、認識 CPU 2 に制限している。

## 1 版を変更・検証する

```sh
./gradlew -PmcVersion=1.18.2 :1.18.2:build
./gradlew -PmcVersion=26.3 qualityCheck
python3 scripts/verify_artifacts.py --version 1.18.2 --collect
```

`-PmcVersion` は今回必要なプロジェクトだけを設定し、メモリを節約する。指定しなければ全版が IDE に表示される。Stonecutter の active を切り替える操作は、別々の Gradle 呼び出しで行う。異なる版の切替タスクを同じ呼び出しに混ぜない。コミット時の active と `vcsVersion` は最新の対応版（現在26.3）。新版追加時はTOML末尾の版がVCS基準になる。`./gradlew -PmcVersion=26.3 stonecutterSwitchTo26.3` で基準へ戻す。

ソース内の条件コメントは公式 Stonecutter が管理する。無効な枝もソースなので削除しない。formatter は import の削除・並べ替えなどを行わず、別の枝専用の import も保持する。条件コメントの追加後は対象版だけでなく、隣接する API 境界の版もビルドする。

`./gradlew qualityCheck` で整形・ソース構成・Pythonスクリプトの回帰テストをまとめて実行する。個別実行は `formatCheck`、`sourceCheck`、`scriptTest`。`formatApply` はJavaソースを整形する。`./gradlew check` はこれらと現在の版・Supervisorの単体テストを実行する。CIの入口は `release.yml`（Verify and release）。`checks.yml` の品質検査と `build.yml` の版別ビルドを再利用し、PR・main pushごとに一度だけ実行する。各版の `build` はJavaテスト、対応版のprobe生成、ModとSupervisorの `verifyDistribution`（ZIP CRC・ライセンス・メタデータ・不要クラス検査）も行う。

formatterはgoogle-java-formatを使う。Pythonの前後処理は無効なStonecutter枝と条件コメントを保護するために残す。標準formatterへ直接置き換える場合は、非アクティブ版のコードやimportを削除しないことを確認する。

### 条件分岐の整理

連続した排他的な版別処理は `if / else if` でまとめる。同時に成立する条件は独立させる。`python3 scripts/check_conditions.py --baseline <変更前コミット>` は公式Stonecutterで変更前後を全対応版へ展開し、選択されたJavaトークンの一致を確認する。Gradle・Minecraftのコンパイルは行わない。ヒープ上限は512 MiB、認識CPU数は2。

## 全版を生成する

```sh
python3 scripts/build_matrix.py --all --qualification
# 全モデルを一度に設定する標準 Gradle 集約タスク
./gradlew buildAll
```

各版を別の Gradle プロセスで順番に実行し、全版の test/build と JAR 検査を行う。Supervisor のテストは独立コンポーネントとして一度だけ実行する。`--qualification` は元から probe がある版で probeJar も作成する。標準集約タスクは全版を一つの Gradle プロセスで設定する。モデルの保持メモリを抑える場合は Python を使う。全版の単体テストだけなら `./gradlew testAll`、プロセスを版ごとに分けるなら `python3 scripts/build_matrix.py --all --test-only`。複数の `--version` で対象を絞れる。

通常 JAR は `versions/<Minecraft>/build/libs/`、Supervisor JAR は `supervisor/build/libs/` に作成される。検査した候補は `dist/refactor/<Mod version>/` に集まる。公開済み `dist/<Mod version>/` は上書きしない。ログと機械可読な検証結果は `build/matrix/<日時>/`。`--output` で保存先を指定できる。

## 移行前との比較

```sh
python3 scripts/build_matrix.py --all --qualification \
  --baseline-dir /absolute/path/to/preserved-jars --output /absolute/path/to/evidence
```

同じ Mod バージョンの移行前 Mod/Supervisor JAR 全60本を保存したディレクトリを指定する。ASM によりデバッグ情報を除いた実行コードを比較する。未許可のリソース差分や注入契約の差分があれば失敗する。比較用 JAR がなければ、通常の整合性検査だけを行い、等価性検証済みとは扱わない。

## 新版の追加

1. TOML に Minecraft、Java release/compiler、Loader、難読化の有無、Fabric API と API フラグを追加する。
2. mapped bytecode を調査し、関係する条件枝と Mixin descriptor・注入位置を対応させる。
3. 登録する Mixin を順序付き JSON プロファイルへ明示する。既存と同じなら再利用する。
4. フィールド監査の TSV を確認し、完全一致の場合だけ既存 fixture を再利用する。
5. test/build、Mixin 契約、配布 JAR、必要な隔離サーバー検証を実施する。

`versions.json` の公開履歴へ追加するのは正式リリースを作るとき。ビルド可能であることと、実サーバーでの確認は別に記録する。

## 実 Minecraft の検証

既存の承諾済み EULA を持つ隔離検証環境だけを使う。本番ディレクトリ・ワールド・設定・Mod・プロセスを変更しない。検証プロセスは一度に一つとし、起動した PID だけを停止する。

起動と Mixin 適用、forced chunk とチェストを含む BASE/DELTA、READY/manifest/全チェックサム、正常停止と異常終了後の復旧を確認する。凍結中の game time が進まないこと、`/ac recovery verify` と `/ac start`、復元後のブロックとインベントリを検証する。これだけで全 TMC 装置の動作保証にはならない。

## Releaseの自動生成

`gradle.properties` の `mod_version` と同じタグをpushすると、品質検査・全版ビルド・成果物検査後にdraft Releaseを作成する。PRとmain pushでは同じ工程で成果物を組み立てるだけで、Releaseは作成しない。手動実行の `publish` も、同じ版番号のタグを選択した場合のみ許可する。既存Releaseは上書きしない。

`assemble_release.sh` は全対象版のMod/Supervisor JARの名前が完全に揃うことと、各版のJUnitレポートを確認し、JAR・SHA256SUMS・artifacts.jsonだけを組み立てる。ログやテストレポートは検証用workflow artifactに保存し、Releaseには添付しない。CIのテスト数・commit・run URLは記録するが、実Minecraftの動作確認や旧JARとの等価性を証明したものではない。
