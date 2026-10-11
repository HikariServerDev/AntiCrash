# Checkpoint Creation Incident — 2026-10-04 / final qualification 2026-10-05

## 判定と範囲

総合判定は **PARTIAL**。親判定の不整合を修正し、隔離環境で連続保存・復元を検証した。
元の運用環境で発生したOOMのstack traceと、Cowの実UUID/元NBTは提示されていない。
したがって元OOMの唯一の原因、元Worldの物理的な破損、UUID warningとの因果関係は断定しない。
Production server modified: NO。本番World、checkpoint、config、mods、Proxy、MySQL、systemdは変更していない。

## 修正前に再現した親の問題

呼び出し経路:

```text
CheckpointManager.runCapture
  -> DetachedGeneration.materialize
  -> verifiedParentFile
  -> JavaIncrementalBackend.previousFileMatches
```

Manifestはlive fileのmtime/ctimeと、保存されたcheckpoint fileのmtime/ctime/nlinkを別々に持つ。
full verifierはSHA-256を検証するが、reuse判定はctimeの変化とnlinkの増減で判定していた。

1. cp1を検証後、cp2がcp1のinodeへhardlinkを追加するとctimeが変わる。
   内容ハッシュは変わらないのに、cp1を再利用する次のchildが失敗した。
2. retentionでlinkが減った場合、旧判定はhashを確認せず許可した。
   同じsize、復元されたmtime、減ったnlinkという条件で、実際の改変まで見逃した。

`DetachedGenerationTest`の2つの新テストは修正前に両方FAILし、修正後にPASSした。
hardlink aliasによる実際のREADY内容改変が今回の原因だという証拠は得ていない。
確認できたのは **metadata-based false positive と corruption false negative** である。

## 修正後のimmutability

- size不一致は拒否。同じ記録ctime/mtimeのファイルだけmetadata fast pathを使う。
- ctime/mtimeが変化した場合は、nlinkが増えても減ってもSHA-256を再確認する。
- ctimeのないfilesystem、古いdestination metadataのないmanifestもhashで確認する。
- hash前後のrevisionが変われば拒否する。
- inherited fileが欠落/改変した場合、live fileで穴埋めしない。理由をMISSING、SIZE_MISMATCH、CHECKSUM_MISMATCH等に分離する。
- hardlinkはimmutable checkpoint間のみ。restore stagingとlive Worldには普通のcopyを使う。
- logical materializerは新しいstage内でcopy-on-writeする。queued region overlayは独立inodeにだけ書く。
- `CheckpointWriteGuard`は内部のJSON/text書込み、materialization、TreeCopyについてREADY配下への書込みを拒否する。
  これは外部プログラムを防ぐOS sandboxではない。READY markerを最後に作るcommitとfsyncは継続する。
- capture/verify/compactionの使用中親はpinでretentionから保護する。

## Verification / startup candidate

候補状態はDISCOVERED、VERIFYING、VALID、INVALID、QUARANTINED。
DISCOVEREDは未検証であり、復元してよいことを意味しない。

旧startup discoveryはmanifestの上限を256 KiBにしていた。一方full verifierは16 MiBだった。
worldFilesを多数持つ正しいmanifestがstartupだけで候補から落ちる、コード上の不整合があった。
現在は同じ16 MiB上限でstreaming parseし、checkpointId/utcTimestamp以外はskipする。
巨大なworldFiles object graphをpre-world ASKのために作らない。

clean/N起動直後に全worldのparent verify/prehashを開始しない。
checkpoint requestが来たときに初めて、reuse前の検証を開始する。
同一processで完全なfile revisionが変わっていない場合、最大2件の認証結果を共有する。
並行verifyは同じlockを通る。再起動を跨ぐVALID trust cacheは作らず、再度content authenticationする。
ctimeのないplatformは保守的に再検証する。

破損親はcheckpoint directory内の`quarantine.json`へ別管理し、READYの内容を修正しない。
次の自動周期に同じ破損親を再検証しない。quarantined generationはretentionでも自動削除しない。
このcatalogは検証結果の代わりではなく、再利用禁止の記録である。

## メモリとfailure control

確認したallocation amplification:

- `ChunkCandidateCache`が削除/eviction時のbyte数計算に`payload()`を使い、byte[]を毎回cloneしていた。
  内部計数は実配列長を使う。入替前にevictし、full cacheとincoming cloneの同時保持を減らした。
- `AtomicFiles.writeJson`が`GSON.toJson`で全JSON Stringを作り、書込み時にもう一度encodingしていた。
  現在はunique temporary fileのWriterへstreaming serializationする。
- JSON read、checksum read/hash/writeもstreaming化した。checksum lineは8 KiBで制限する。
  ハッシュ計算の改行規約は従来とbyte-compatibleである。
- rolling preparationがstorage drain Futureを捨てていた。
  現在は前batch完了を次tickでpollし、未完了なら次batchを作らない。main threadでjoinしない。
- fallback dirty chunk batchはconfig上限と16の小さい方。
  POIも16 chunkごとに独立storage queueをdrainする。
  （2026-10-05変更: 16件ごとのdrainは同期書き込みのServerで切り出しを数秒止めていたため、`PORTABLE_GENERATION`ではheapに応じた上限と、heapの残りが少ないときのdrainに置き換えた。[CHECKPOINT-ENGINE.md](CHECKPOINT-ENGINE.md)と[CONFIGURATION.md](CONFIGURATION.md)を参照。legacy engineは従来どおり。）
- candidate cache実効上限はconfig上限とheapの1/16（最低16 MiB）の小さい方。
  1 GiB heapでは最大64 MiB。必要chunkや保存stateの省略には使わない。
- snapshot/manifest/capsuleの全量deep-copyによるcompactionを避け、manifestをstreaming readする。

各capture前とchunk serialization前に、`max - used`がmin(128 MiB, max/8)以上か確認する。
不足時はoptional candidateを捨てて1回GCを要求し、なお不足ならMEMORY_BUDGETで停止する。
これは安全余裕のguardであり、任意ModのNBTサイズや全phaseの厳密なallocation upper boundではない。
1 GiBで任意Worldが必ず保存できるという保証はしない。

`CheckpointFailureGate`はOOM / MEMORY_BUDGET / PARENT_INVALIDで即時停止し、通常failureも3連続で停止する。
通常I/O failureには30/60/120秒のbackoffがある。保存間隔configそのものは変えていない。
OOMをwrapped exceptionとして受けてもfatal categoryを維持する。
shutdownの最終checkpointは停止中エンジンを自動再試行しない。

`/ac status`と`/ac debug checkpoint-performance`にengine status、reason、consecutiveFailures、
lastValid、parent、phase、lowPause availability、finalizer、heap headroom、実効batchを表示する。
PARENT_INVALIDならSUSPENDED_NEEDS_REBASE。管理者が状況を確認し、`/ac checkpoint base`で独立BASEを作る。
`/ac checkpoint`は明示retry。ただし親が失われたLOGICAL_DELTAではBASEを要求する。
quarantine済みの旧親を自動で復活させることはない。

## POI / PersistentStateのdecision gate

Minecraft 1.18.2 / Yarn build.4のmapped sourceと実classの`javap`で確認:

- `PersistentStateManager.loadedStates`は`Map<String, PersistentState>`。
  `save()`はloaded stateを巡回し、`PersistentState.save(File)`を呼ぶ。
- `SerializingRegionBasedStorage.loadedElements`、`unsavedElements`、`worker`が存在する。
  private `save(ChunkPos)`は`method_20367`でsectionをcodec encodeし、`StorageIoWorker.setResult`へ渡す。
  `method_20367`はunsaved setを更新するため、汎用のread-only async captureではない。
- `PistonExtensionBlock.createBlockEntity`はnullを返す。
  test-only probeのmoving piston fixtureは、実際のpublic constructorと`addBlockEntity`で作る必要がある。

今回、任意PersistentState subclassの意味的captureを安全と宣言しない。
POI/dataがあるWorldでは **LOW_PAUSE_UNAVAILABLE / file authority fallback** を維持する。
Vanilla level/PersistentState save + POI save + storage seal + detached files/queued NBTで同じepochを保持する。
`allow_unsupported_persistent_state=true`でもstale POI/dataを許すlogical pathには進まずfallbackする。
Thread/Future/lighting queueのexact runtime serializationへは変更していない。

## UUID warning

`Cow/260`の260はnetwork entity IDであり、UUIDではない。
Entity ID counterのcapture/restoreと、Vanilla ServerEntityManagerのUUID重複検出は別である。
通常checkpoint作成時にlive EntityのUUIDを書き換える実装は追加していない。
隔離試験のNether cowではUUID重複warningは0件だったが、元warningが無関係だという証明にはならない。
元UUID、region/chunk、複数NBT occurrenceがないため **INCONCLUSIVE** とする。推測でentityを削除しない。

## 試験と証拠

再現/回帰テスト:

- hardlink追加後の正常な親再利用、retention link削除＋同size/mtime改変の検出。
- actual mutation quarantine、再起動後のquarantine維持、並行verifyの同一結果、retention pin。
- 256 KiBを超えるmetadata-only candidate discovery。
- READY write guard、shared stage inodeの書込み拒否。
- 50回のOOM/parent fault gate cycle。
- 32 MiB child JVMで50 cache cyclesと60 MiB JSON streaming。
  同じ旧whole-String方式は隔離childでOutOfMemoryErrorを再現する。
- checksum digest byte compatibility、trailing JSON拒否、既存v7/restore/fakeplayer tests。

実試験手順（全て新しい`build/checkpoint-incident-*`内）:

```bash
./gradlew test build probeJar --no-daemon
python3 qualification/checkpoint_incident.py --count 25 --timeout 600
python3 qualification/checkpoint_inode_audit.py build/checkpoint-incident-<generated-id>
python3 qualification/checkpoint_incident_startup.py build/checkpoint-incident-<generated-id>
```

launcher dependenciesは既存の隔離qualification environmentからcopyするが、Worldやstateはcopyしない。
loopback / dynamic port、`-Xms1G -Xmx1G`、systemdなし。
30秒のautomatic intervalは保持し、allocation stressは約5秒ごとのmanual requestで行う。
fixtureは1,526 loaded chunks、Nether entity region、110 moving pistons、FakePlayer interval use、
scheduled ticks、POI変更、scoreboard PersistentState変更を含む。
manual fixtureによる連続保存の合格は、本番Trencherの長期動作等価性の証明ではない。

`result.json`はgenerationごとのphase/heap/GC/changed/reused/bytesを持つ。
`inode-audit.json`はchecksum/aliasの全検査、inode/dev/nlink/mtime/ctime sampleを持つ。
`probe/memory.csv`と`memory.svg`は使用heap（未回収garbage込み）、`gc.log`はGC後occupancyを持つ。
shutdownのVanilla全量saveに伴うheap増加はcheckpoint中のbaselineと分けて読む。

保持する証拠:

- `build/checkpoint-incident-rbpp0zk2`: 25回成功、2回目以降moving piston 110、全checksum一致、live alias 0。
- `build/checkpoint-incident-izk1tr3s`: 25回成功、最終streaming/checksum変更後の中間artifact。
- `build/checkpoint-incident-4aaj2b8n`: 最終artifactで25/25成功。全25世代でmoving piston 110、loaded chunks 1,526。
  OOM、parent誤判定、probe errorは0。barrierは最小69 / 中央値92 / 最大341 ms。
  snapshot中央値22 ms。全READY payloadのhashは後続保存・live変更後も不変。
  起動/復元回帰後に追加のcp-000026も正常commitし、26世代のchecksum一致・live alias 0を再確認した。
  cp-000026のmoving piston 0は再開後にVanillaの移動が完了した結果で、連続25回のfixtureとは分ける。
- 初回`build/checkpoint-incident-_176t7px`はfixture/集計の不備を記録したPARTIAL証拠として残す。

最終JAR SHA-256:

```text
8d7f54dd777fa1ed81afbcf3428a3d048705ba6c85097e50134e7e5b5fde47fe
```

最終fixtureはLOW_PAUSE_DELTA 0/25、SAFE_FALLBACK 25/25。
開始直前のcp1 heap usedは527 MiB。Checkpoint期間中のFull GC後occupancyは390 / 389 / 390 MiB。
使用量サンプルはgarbage込み約953 MiBまで上がるが、GC後baselineの継続増加は観測していない。
world生成の1023 MiBや終了時Vanilla全量saveの使用量は、Checkpoint allocation peakと混同しない。
System.gc() guardによる69〜79 ms程度のFull GC pauseもあり、zero-pauseを達成したという報告はしない。

起動回帰の実結果:

- CLEAN、DIRTY+NはWorld復元なし、parent full validation開始0件。
- DIRTY+Yはpre-world full validation1件、ファイル復元、1,526/1,526 chunks、110 pistons、FakePlayer 1/1を復元。
- `/ac start`後の再開と、最初のtick前のruntime照合を観測。通常終了code 0。
- **診断はPARTIAL**: OPTIMIZEDで`/ac recovery verify --verbose`と`/ac debug equivalence --verbose`が
  `No recovery equivalence report is available`を表示した。v7が欠落している証拠ではなく、
  pre-start reportのavailabilityを別途修正・qualificationする必要がある。
  現コードの`AntiCrashController.refreshEquivalenceReport()`はfidelityがSTRICT以外なら即returnする。
  `renderEquivalence()`はその後reportがnullなら欠落として表示するため、OPTIMIZEDの正常なv7復元でも
  この表示になる。OPTIMIZED用read-only診断とSTRICTのstart gateを分離するのが次工程である。
  復元・再開の成功からこれらcommandの正しさを推定しない。
- `startup-result.json`は`startup_lifecycle_status`と`diagnostics_status`を分離し、総合PARTIALとする。
  `--analyze-only`は既存の隔離ログを再分類するだけでserverを起動しない。

Unit testはmain 225 / supervisor 13（失敗・error・skip 0）。
元の213 testsから増えたcoverageと実機fixtureの範囲を別々に扱う。

## なぜ旧testが見逃したか

通常の2世代テストだけでは、検証済み親へ別childがlinkを追加した後の再利用を検査できない。
retentionのnlink減少と、同size/mtimeの改変を同時に作るnegative testもなかった。
小さいJSON payloadではwhole-String allocationが見えず、server不要unit testだけではstorage backlogが見えない。
今後は上記再現、低heap子process、inode audit、連続実保存を独立したqualificationとして使う。

## 残るqualification

元運用OOMのstack/phase採取、元Cow UUID occurrence調査、長期30秒automatic cycle、
本番規模の大きいregion/NBT、全v7 piston/portal/ticket/fakeplayer differential suiteの再実行は未完了。
汎用POI/PersistentState semantic authority、全phaseの厳密なallocation budget、
file enumeration/copy/hash各工程の独立したtiming、OPTIMIZED pre-start診断reportも残る課題。
本番へ導入せず、必要な追加試験をユーザーが確認してから進める。
Runtime Capsuleはv7のまま。checkpoint schema/digestの意味は変更しない。
