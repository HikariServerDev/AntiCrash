# Multi-version migration — 2026-10-11

## 保持した現行コード

10月6〜7日のretention、queued IO、FakePlayer vehicle、Carpet、runtime等の変更を確認した。
変更前の298 source/test/docs/qualification filesをSHA-256で記録し、そのまま`1.18.2/`へ移動。
移動直後は[全298件のhash](source-before-migration.sha256)が一致した。
旧`build/`（過去の隔離World・ログ・試験結果を含む）、`.gradle/`、logsも`1.18.2/`へ移動し、削除していない。
過去レポート内の絶対パスは履歴として残す。旧パスの`AntiCrash/build/checkpoint-*`は現在
`AntiCrash/1.18.2/build/checkpoint-*`にある。

既存の保存/復旧のゲーム処理は保持した。最初の移動後には191 main Java filesがすべて一致した。
その後、複数版への移植に必要な`CheckpointManifest`の版定数と、`CheckpointStore`の版不一致拒否を追加した。
この時点ではそれ以外の189 main Java filesは移動前と同一だった。
追加の版移植監査で`BlockEntityMutationMixin`のselectorを`markDirty()V`へ限定した。
同名static overloadを誤って選ばないための修正であり、通常のdirty trackingは維持する。
初回の他の変更はbuild/metadata、icon、qualifierのJAR参照、版移植用の追加テストだった。
追加移植時にはunload(J)Zのselector限定とAtomicFilesの旧manifest識別も全版へ反映した。
1.18.2の移動直後の再実行はmain 245 / supervisor 13 tests、failure/error/skip 0。

## 状態の意味

- BUILD_VERIFIED: source・unit tests・buildを検証済み。ゲーム起動の合格ではない。
- IMPLEMENTED: sourceを実装済み。最終test/build・配布成果物検査は未完了。
- PORT_IN_PROGRESS: sourceを移植中。配布可能とは宣言しない。
- NOT_PORTED: metadata/移植状態の記録のみ。Minecraft対応コードとJARはまだない。

全31版の公式releaseと最低JavaをMojang version metadataで確認。
1.21.11までのYarn候補はFabric Metaから確認。
version pinとbuild方式を適用しただけではMixinやRuntime Contractの互換は保証できない。
各folderの`target.json`は実装状態のtruth source。`versions.json`も同じ状態に合わせる。

## 1.18 / 1.18.1の版専用実装

1.18.1の実mapped bytecodeで以下を確認した。

- `Chunk.setShouldSave(boolean)`は`needsSaving`へbooleanを書くだけ。
  1.18.2の`setNeedsSaving`呼出しを、この版の実メソッドへ変更。
- `ServerChunkManager.tick(BooleanSupplier)`はticket purge、ticket/holder update、`tickChunks()`、
  storage lifecycle、cache resetを順に呼ぶ。1.18.2の`tick(supplier, false)`は存在しない。
- 単純に1引数tickへ変えると復旧中にsimulationが進むため、その呼出しに限定した
  `VersionChunkLifecycle`とWrapOperationを追加。ticket/storage progressionは保持し、tickChunksだけを抑止。
  ThreadLocal scopeはnested/exceptionにもfinallyで復元する。通常tickには影響させない。
- Entityの`entityBounds` / `entityChangeListener`はこの版のYarn名。
- FallingBlockEntityの`discardTime`はclient-onlyの50ms removal grace。実bytecodeの`world.isClient`分岐を確認。
  server runtimeの保存対象とはしない。監査表のフィールドを黙って削除せず分類した。

1.18では更に`NbtNull` / `NULL_TYPE`がNBT終端の名前であることを確認し、codecを合わせた。
`Entity.inanimate`はEntityViewの衝突判定に使われる。FallingBlockEntityのconstructorとloadでは
defaultが同じでないため、loaded-entity extensionへ正確なbooleanを保存/復元する。
`intersectionChecked`はこの版には存在せず、BannerのNBT listは`patternListTag`というYarn名だった。
これらも実classと監査表を一致させた。

Checkpointには各版の正しい`minecraftVersion`を記録し、異なるMinecraft版のmanifestは完全検証時に拒否する。
旧formatでこのfield自体がない場合にGson defaultとして移植先の版が入る問題は修正した。
JSON読出し時だけ原実装1.18.2として扱い、他版への直接復元を拒否する。
この互換処理を未知の由来の旧Checkpointの版間migrationに使ってよいという意味ではない。

`MixinTargetAuditTest`は実mapped classからtarget selector、Shadow、Accessor、Invoker、At INVOKEの存在を確認する。
これは実Mixin transformationの適用確認ではない。injection ordinal、callback semantic、他Mod競合は別途runtime qualificationが必要。
Carpetの各版の実挙動も未検証。新たなMinecraft serverは起動していない。

## 未移植版

各版の現在の状態はtarget.json / versions.jsonを参照する。1.19以降の追加移植を進めている。
Entity manager、scheduled ticks、NBT/registry、network/command、chunk ticket/lifecycle、runtime extensionsの
版専用adapterと監査が必要。空のFabric modで代用しない。

26.xはJava 25を要求し、Yarn/remap中心の旧buildを使用しない。
当初のJDKは17/21だった。現在はproject-local `.toolchains/`にSHA検証済みTemurin 25を用意し、
一括ツールは26.xだけに使用する。global Javaや実サーバーの起動環境は変更していない。
根拠: [Fabric 26.1](https://fabricmc.net/2026/03/14/261.html)、
[Fabric 26.2](https://fabricmc.net/2026/06/15/262.html)、
[Fabric 26.3](https://www.fabricmc.net/2026/09/15/263.html)。

## 成果物の取り扱い

名前は`AntiCrash-1.0.0+mc<version>.jar`、Supervisorにも同じversion suffixを付ける。
成功したtest/buildのみ、共通`build/`へModとSupervisorを集約。
各版のbuild/libsに残る旧JAR・sources・probe・devは集約しない。
`tools/multiversion.py collect --available`はexact Minecraft dependency、ZIP整合性、指定画像のhashを検査。
その結果は`build/collect-report.json`に記録。

未移植版も含む全対象buildは非0終了し、未完成を成功に見せない。
OOMを避けるためGradleは逐次、worker 1、heap 1536MiB。World/Checkpointをコピーして検証しない。

## 初回offline検証（後続の移植前の履歴）

`python3 tools/multiversion.py build --available`で3版を逐次test/buildし、最終JARを再収集した。

| Minecraft | main tests | supervisor tests | build |
|---|---:|---:|---|
| 1.18 | 250/250 | 13/13 | PASS |
| 1.18.1 | 250/250 | 13/13 | PASS |
| 1.18.2 | 248/248 | 13/13 | PASS |

failure/error/skipは全て0。共通ツールのunit testsも3/3 PASS。
MixinTargetAudit、runtime-field coverage、NBT終端、不正Minecraft版の拒否を含む。
初回ModのSHA-256（現在の再ビルド成果物のhashではない）:

```text
1.18    39432ef24da280c8faaa23cdb3303fe6955febf799f7acd8811ef363b5ee4467
1.18.1  7b9a4acf11b862b7effea67aa62bd95e4a78be7c1588cd3364aa6c3667dbf94b
1.18.2  bc1097c1a68e4213e4dd39b940041610a41fad408128e7276af440a771d8ae0a
```

Mod/SupervisorのZIP整合性、target Minecraft一致、指定画像のbyte hash一致、copy元/先のhash一致を検査した。
`build/build-report.json`は3版の成功記録、`build/collect-report.json`は全31版の対象を扱い、
未移植28版をBLOCKED_NOT_PORTEDとして記録する。全31版collectの非0終了は期待した未完了検出。
この結果を全31版対応、runtime/TMC recovery PASS、production-readyとは報告しない。

## 追加移植と現在の検証

[PORT-ADAPTERS.md](PORT-ADAPTERS.md)に実mapped bytecodeから確認した各版の差分を記録する。
VibrationのVanilla codec、recipe match cache、Jukebox位相、Entity接地状態、Common Network Handler、
Vanilla TickManager等を、その版の実APIに合わせる。旧名JARを新名にするだけの対応は行わない。
追加移植後に全実装済み版を逐次test/buildし、build/build-report.jsonへ成果物とSHAを出力する。
`tools/record_ports.py BUILD_VERIFIED <versions...>`はXML test結果・JAR identity・source更新時刻を確認して
target/matrixを更新する。sourceがJARより新しい場合やskip/failureがある場合は昇格しない。

## 残る工程

未移植版の実装、各版のmapped bytecode監査、Carpet検証、実Mixin適用、保存/復元differential qualification。
初回の3版も、今回追加移植した版も、runtime qualificationをこの作業で実施したとは扱わない。
全31版対応完了ではない。

## 2026-10-11 27版時点の到達範囲（後続26.x移植前の履歴）

初回の1.18/1.18.1/1.18.2に対し、1.19から1.21.11までの依頼対象24版を追加移植した。
全27版で固有のMinecraft dependency、manifest version、Mod/Supervisor配布名を使う。
共通buildへはMod/Supervisor各27、計54JARを集約する。各版の実mapped JARのAPI・field matrix・
Mixin callback/WrapMethod signatureをoffline監査する。アイコンは指定画像とSHAで照合する。

この段階の残り4版は1.16.1、26.1/26.2/26.3だった。
空Modや改名JARは提供しない。1.16.1の実mapped JARでは
ServerTickScheduler（ordered TreeSet/current queue/consumed list）、WorldChunk.entitySections、
ServerWorld.entitiesById/entitiesByUuidを確認した。1.18.2以降のServerEntityManager/WorldTickSchedulerや
分離entity chunk IOをそのまま使用できない。旧NBT名もCompoundTag/ListTagとなる。
26.1の実JARはMojang namespaceのServerLevel/IOWorker/EntityStorage/RegionFileを持ち、Yarn名ではない。
Java 25、unobfuscated Loom、保存/registry bindingと版専用fixtureの実装・検証が残る。
フォルダ作成・公式dependency候補の記録を対応完了とは数えない。

1.21.5～1.21.11の変更にはOptional/view NBT、ticket countdown/flags、spawn pitch、per-dimension border、
kinetic motion historyが含まれる。詳細はPORT-ADAPTERS.mdを参照。
At INVOKEのcaller/ordinal監査を既存版にも追加し、1.20.5以降のentity保存注入先を修正した。
1.21.5以降のTicked/Waterは省略falseと不正型を区別する。修正後は全27版を逐次test/buildする。
成果物収集・BUILD_VERIFIED昇格には本体とSupervisor双方のcurrent source/test/JAR証跡が必要。
既存runtime qualification probeの新版API移植は未完了。本体buildとは分離し、実機試験済みとは扱わない。

最終test件数とCRC/SHA検査の最新結果は各target.jsonとbuild/build-report.jsonを参照する。
Unit test、filesystem/process fixture、registry bootstrapを実施したが、MinecraftServerやWorldは
起動・読込みしない。実Mixin適用、対応Carpetのhandshake/rules/FakePlayer、装置の復旧は実機未検証。

## 26.x追加後の現在の到達範囲

26.1/26.2/26.3のsource・field matrix・packet/clock/fluid/storage/recipe adapterを追加した。
全30版を逐次test/buildし、Mod/Supervisor各30、計60JARを共通buildへ収集する。
26.1 main 291 / supervisor 13、26.2 main 294 / supervisor 13、26.3 main 297 / supervisor 13 tests。
failure/error/skipはいずれも0。共通移植/収集ツールのunit testsは13/13 PASS。
詳細はPORT-ADAPTERS.md、最新の全版検査結果はbuild/build-report.jsonを参照する。

26.xはRuntime Capsule v8、それ以前の実装済み27版はv7。欠落clockやcategoryを空/MATCHへ変換しない。
foreign Minecraft checkpointの直接復元拒否は全版で維持する。
JARだけ新しくしてtestを省略したケースも収集・BUILD_VERIFIED昇格で拒否する。
残る未完成版は1.16.1（PORT_IN_PROGRESS）。専用tick ledgerのcoreテスト14/14 PASS、
read-only native scheduler capture adapterの独立compileはPASS。
1.18.2の本体をYarnへsource migrationしてAPI差分を調べたが、完全な本体compileはまだ失敗する。
coreの成功を本体buildやfull baselineの成功に置き換えない。Mod JAR生成は無効のまま。
[1.16.1の調査・残工程](../1.16.1/PORTING-INVESTIGATION.md)を参照する。
全31版対応、Carpet実機PASS、TMC完全再現とは宣言しない。
全31版collectは1.16.1をBLOCKED_NOT_PORTEDとして非0終了するのが正しい結果。
MinecraftServer/Worldの生成・起動、実Mixin適用・実checkpoint/world復元はこの作業では行っていない。

Production server modified: NO
