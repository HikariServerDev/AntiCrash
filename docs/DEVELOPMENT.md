# 開発・検証

## 必要な環境

Gradle 9.6 を JDK 25 で実行する。JDK 17 と 21 もインストールし、Gradle toolchain が検出できるようにする。Python スクリプトには Python 3.11 以上が必要。JDK はリポジトリに含めない。Gradle は worker 1、最大ヒープ 1536 MB、認識 CPU 2 に制限している。

## 1 版を変更・検証する

```sh
./gradlew -PmcVersion=1.18.2 :1.18.2:build
python3 scripts/check_sources.py
python3 -m unittest discover -s scripts -v
python3 scripts/format_java.py --check
python3 scripts/verify_artifacts.py --version 1.18.2 --collect
```

`-PmcVersion` は今回必要なプロジェクトだけを設定し、メモリを節約する。指定しなければ全版が IDE に表示される。Stonecutter の active を切り替える操作は、別々の Gradle 呼び出しで行う。異なる版の切替タスクを同じ呼び出しに混ぜない。コミット時の active は 1.18.2。

ソース内の条件コメントは公式 Stonecutter が管理する。無効な枝もソースなので削除しない。formatter は import の削除・並べ替えなどを行わず、別の枝専用の import も保持する。条件コメントの追加後は対象版だけでなく、隣接する API 境界の版もビルドする。

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

1. TOML に Minecraft、Java release/compiler、Loader、Yarn、Fabric API と API フラグを追加する。
2. mapped bytecode を調査し、関係する条件枝と Mixin descriptor・注入位置を対応させる。
3. 登録する Mixin を順序付き JSON プロファイルへ明示する。既存と同じなら再利用する。
4. フィールド監査の TSV を確認し、完全一致の場合だけ既存 fixture を再利用する。
5. test/build、Mixin 契約、配布 JAR、必要な隔離サーバー検証を実施する。

`versions.json` の公開履歴へ追加するのは正式リリースを作るとき。ビルド可能であることと、実サーバーでの確認は別に記録する。

## 実 Minecraft の検証

既存の承諾済み EULA を持つ隔離検証環境だけを使う。本番ディレクトリ・ワールド・設定・Mod・プロセスを変更しない。検証プロセスは一度に一つとし、起動した PID だけを停止する。

起動と Mixin 適用、forced chunk とチェストを含む BASE/DELTA、READY/manifest/全チェックサム、正常停止と異常終了後の復旧を確認する。凍結中の game time が進まないこと、`/ac recovery verify` と `/ac start`、復元後のブロックとインベントリを検証する。これだけで全 TMC 装置の動作保証にはならない。
