# AntiCrash 構造移行の検証結果

基準: `e16c24604bed20f7029fc12db72194a8b272c0e3`。この表は構造移行時の1.0.2候補に対する記録。公開用1.0.3の再ビルド・検証結果は末尾に記録している。保存形式は変更していない。

| Minecraft | ビルド・単体テスト | テスト数 | Mixin契約 | JAR実行コード・リソース | 隔離サーバー |
| --- | --- | ---: | --- | --- | --- |
| 1.18 | PASS | 481 | PASS | PASS | NOT_RUN |
| 1.18.1 | PASS | 481 | PASS | PASS | NOT_RUN |
| 1.18.2 | PASS | 479 | PASS | PASS | PASS |
| 1.19 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.19.1 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.19.2 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.19.3 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.19.4 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.20 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.20.1 | PASS | 482 | PASS | PASS | NOT_RUN |
| 1.20.2 | PASS | 482 | PASS | PASS | PASS |
| 1.20.3 | PASS | 485 | PASS | PASS | NOT_RUN |
| 1.20.4 | PASS | 485 | PASS | PASS | NOT_RUN |
| 1.20.5 | PASS | 487 | PASS | PASS | NOT_RUN |
| 1.20.6 | PASS | 487 | PASS | PASS | NOT_RUN |
| 1.21 | PASS | 489 | PASS | PASS | NOT_RUN |
| 1.21.1 | PASS | 489 | PASS | PASS | NOT_RUN |
| 1.21.2 | PASS | 491 | PASS | PASS | NOT_RUN |
| 1.21.3 | PASS | 491 | PASS | PASS | NOT_RUN |
| 1.21.4 | PASS | 494 | PASS | PASS | NOT_RUN |
| 1.21.5 | PASS | 500 | PASS | PASS | NOT_RUN |
| 1.21.6 | PASS | 507 | PASS | PASS | NOT_RUN |
| 1.21.7 | PASS | 507 | PASS | PASS | NOT_RUN |
| 1.21.8 | PASS | 507 | PASS | PASS | NOT_RUN |
| 1.21.9 | PASS | 509 | PASS | PASS | NOT_RUN |
| 1.21.10 | PASS | 509 | PASS | PASS | NOT_RUN |
| 1.21.11 | PASS | 515 | PASS | PASS | PASS |
| 26.1 | PASS | 521 | PASS | PASS | PASS |
| 26.2 | PASS | 524 | PASS | PASS | PASS |
| 26.3 | PASS | 527 | PASS | PASS | PASS |

本体テスト 14821 件、独立した Supervisor テスト 13 件。旧版別テストとケース一覧も一致。
隔離サーバーでは初期設定の OPTIMIZED で BASE/DELTA、全チェックサム、正常停止、異常終了からの復旧、凍結、verify、start、ブロックとチェスト内容を確認した。
その他の24版の実サーバー検証、全TMC装置、Carpetを含む全Mod組み合わせ、検証用probeの全シナリオ、Windows実行、ホスト上のGitHub Actionsは未実施。
通常配布JARには検証Mod・単体テスト・ログを含めない。構造移行のローカル検証時点では、公開済み配布JARとGitHubは変更していない。本番サーバー変更: NO。

## 構造と追加検査

- `src` と独立 Supervisor の Java ファイルは 959 → 360、物理行数は 185,084 → 66,875。生成コードと補助スクリプトを除き、同じ範囲を比較した。
- 完全一致ファイル群は 21 → 0、2個目以降のコピーは 65 → 0。これらは参考値であり、安全性の判定条件ではない。
- 30版の旧ソースディレクトリ、common、99個の shared ディレクトリを廃止。API差分は同じクラス内の条件、実行時設定は16個の順序付きMixinプロファイル、分類期待値は38個のTSVに整理した。
- Supervisor 13件と補助ツール22件が成功。従来probeがある27版のprobe JARも生成した。
- 26.3 → 1.21.11 → 1.18.2 の切替後に全Javaソースのバイト列が一致した。
- `buildAll testAll` が全30版で成功し、30版の本体テストを実行した。単なる前回結果の再利用ではない。
- クラス集合・注釈・メソッド・命令・例外テーブルとリソースが移行前と一致する。除外はデバッグ情報と明示的なビルド情報のみ。JSON配列の順序と名前空間・Loader・Mixin属性は比較する。
- 改変した命令定数とMixin登録順序を検査が拒否することも確認した。

## 実行環境と制限

Gradle 9.6 / Loom 1.17.21 / Stonecutter 0.9.8。Gradleの最大ヒープは1536 MiB、テストは512 MiB、workerは1、隔離Minecraftは一度に1インスタンス・最大ヒープ1 GiBに制限した。統合ビルドは約5分10秒。比較条件を揃えた性能ベンチマークではないため、速度向上やピークメモリ削減の主張はしない。

成果物は `dist/refactor/1.0.2/` の60本と `SHA256SUMS`。公開済みReleaseのJARは上書きしていない。

## Final Status

**PASS** — 構造移行、全版ビルド・単体テスト・Mixin契約・JAR等価性検証が完了。実Minecraftは表に示した6版で確認し、未実施範囲を分けて記録した。

## Release 1.0.3 の再ビルド

公開済み1.0.2とJAR内部のデバッグ情報・ビルド情報が変わるため、1.0.3として再生成した。全30版のビルド、本体14,821件・Supervisor13件・補助ツール26件のテストが成功。配布JAR60本の整合性と、公開済み1.0.2からバージョン表示・デバッグ情報・明示したビルド情報だけを除いた実行コード・リソースの等価性を確認した。保存形式と実行動作は維持する。

上の6版の隔離サーバー試験は構造移行時の同等実装1.0.2候補に対する記録であり、1.0.3のJARによる実サーバー再試験は未実施。今回の公開作業では本番サーバーを変更していない。1.0.3の成果物は `dist/1.0.3/` に保存し、Git管理から除外する。配布先はGitHub Release、タグは `1.0.3`。

公開前の書式検査で、無効な分岐内の字下げが整形のたびに増える問題を修正した。Javaの文字列とコメントを区別し、ブロックコメント本文を退避・復元する。無効な分岐とJavadocの保持、Javaコードの整形、繰り返し実行の安定性を検証した。修正対象はPythonの整形ツールとそのテストであり、配布Javaコードは追加変更していない。

## Release 1.0.4

30版すべてのビルド、本体テスト14,821件、Supervisorテスト13件、補助テスト31件が成功した。60本のJARを生成し、JAR構造・メタデータ・ライセンス・SHA-256を検査した。199個の変更JavaソースはStonecutterで30版へ展開し、条件分岐整理前のソースと選択トークンが一致することを確認した。比較では、意図した1.0.4の版番号と設定ヘッダーを一時的に正規化した。

1.18〜1.21.11のソースをYarnから公式Mojang mappingsへ切り替えたため、これらのJARは1.0.3とclass bytecodeが完全一致しない。Mixin refmapがある26版では、Mixinごとの中間namespaceの参照先を比較し、参照先の集合と数が一致した。1.18.2では代表Mixinの命令列も一致し、`@Inject`対象名はYarnの`tick`からMojang名の`serverTick`へ変わっていた。全旧版のclass bytecode同等性や実サーバー上のMixin適用は確認していない。26.1〜26.3では、版番号と明示したビルド情報を正規化した後の実行classとリソースが1.0.3と一致した。

実Minecraftサーバーの起動・復旧は実施していない。本番サーバーへの変更はない。1.0.4の配布物は `dist/1.0.4/` にMod/Supervisorの各30本、`artifacts.json`、`SHA256SUMS` を置く。ビルドログやテストログは配布物に含めない。
