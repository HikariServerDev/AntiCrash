# Checkpoint Engine

## Incident修正後の補足

READY generationはimmutable。parentのctime/nlink driftはcontent authenticationなしに許可も拒否もしない。
capture/verify/compaction中のparentをretentionからpinし、破損parentは別catalogでquarantineする。
rolling file preparationは最大16 chunksをsubmitし、前batchのstorage drain Futureを次tickでpollする。
Json/manifest/checksumの最終書込みはstreaming。candidate cacheはheapの1/16以下（最低16 MiB）、
fallbackのchunk/POI queueも16単位でdrainする。必要stateを削ってメモリを減らす方式ではない。
POI/PersistentStateのsemantic authorityがない場合、low-pauseはUNAVAILABLEとしてfile-backed fallbackする。
詳細は[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)。

この文書は、AntiCrash 1.0.0 の低停止時間 checkpoint 実装を、コードを読む別の開発者が追跡できる粒度で説明します。対象は Minecraft 1.18.2 / Fabric / Java 17 です。

## 設計の要点

Checkpointの正確性とI/Oの短縮は別の問題として扱います。

```text
RUNNING
  -> CHECKPOINT_BARRIER
  -> Runtime Capsule / Player / FakePlayerをdeep capture
  -> LOGICAL_DELTA CUT
       Exact-EpochのChunk/Entity NBT候補をmutation generationで検証
       LogicalDelta(JSON)をimmutable hand-off
  -> CHECKPOINT_SEALED
  -> CHECKPOINT_FINALIZING (World tickは再開済み)
       検証済み親viewをcopy-on-write materialize
       runtime payload / manifest / checksum / READY / retention
  -> RUNNING

Exactでない場合（候補欠落、observer uncertainty、未対応persistent state）
  -> bounded Vanilla save path
  -> PORTABLE_GENERATION または LEGACY_SAFE
  -> CHECKPOINT_SEALED
```

`CHECKPOINTING`と`CHECKPOINT_COMMITTING`はfallback/backend互換用に残しています。`checkpoint.engine.mode = "LOGICAL_DELTA"`が既定です。最初の論理差分の親がない場合は通常checkpointを勝手に全量freezeへ変換せず、`/ac checkpoint base`を要求します。`PORTABLE_GENERATION`は論理差分がExactでない場合の既定fallback、`LEGACY_SAFE`は明示された安全側経路です。

## クラスの責務

| クラス | 責務 |
|---|---|
| `CheckpointManager` | server threadの状態遷移、barrier、Futureのpoll、background executorへのimmutable hand-off |
| `CheckpointTiming` | `System.nanoTime()`によるphase計測、heap/non-heap/GC観測 |
| `RollingBudgetController` | 通常tick中のoptional dirty-chunk準備の1〜3 ms予算。未完了を捨てる判定はしない |
| `PortableGenerationBackend` | Logical Cut中にlive treeを親checkpointから独立化し、`GenerationSeal`を生成 |
| `GenerationSeal` | cut tick、parent、BASE/DELTA、PRESENT/ABSENT/TOMBSTONE、digestを保持する不変handoff |
| `JavaIncrementalBackend` | regular fileのstat/hash/copy/hardlink。Live Worldへのhardlinkは作らない |
| `CheckpointStore` | `.tmp`作成、checksum、READY、durability、atomic publish、retention、verify |
| `CheckpointManifest` | 復旧payloadと世代・性能・Runtime Capsuleの認証メタデータ |
| `GenerationGraph` | 世代の親子関係を辿る純粋なhelper（retentionでは使わない。test用） |
| `LogicalDeltaEngine` | server thread上でcandidate cache、mutation generation、entity lifecycleを管理し、Exact-Epochの差分を作る |
| `ChunkCandidateCache` | bounded immutable `ChunkSerializer` bytes。evictionは安全なrecapture/fallbackを発生させるだけ |
| `LogicalDeltaMaterializer` | worker上で親worldをcopy-on-write materializeし、region/entity NBTとlevel metadataを反映 |
| `CheckpointCompactor` | 検証済みcheckpointをLive Worldなしで新しいBASE rootへまとめる |
| `CheckpointMirror` | 1.18.2で安全に公開されていないVanilla queue/ticket mirrorを`UNSUPPORTED`として診断する |

background workerへ渡す`CaptureContext`には、Minecraftの`World`、`Chunk`、NBT object、queue、Entity objectを入れません。portable/logical経路では`context.world`を`null`にし、workerがLive Worldを読む実装を構造上許しません。server threadからworkerへ渡るのは、deep-copied JSON payload、immutable Base64 NBT、manifest index、認証済み親pathだけです。

## Logical Delta v2のExact-Epoch flow

### 1. 通常tick中のcandidate preparation

`CheckpointManager.prepareDirtyChunksWithinRollingBudget`はRUNNING中に最大`checkpoint.cache.budget_ms_per_tick`だけ`LogicalDeltaEngine.prepareCandidates`を呼びます。`ServerWorld`のaccessible `WorldChunk`をserver threadで列挙し、`ChunkSerializer.serialize(ServerWorld, WorldChunk)`の結果を直ちにbyte配列へdetachして`ChunkCandidateCache`へ入れます。候補には`captureEpoch`、Chunk mutation generation、SHA-256が付きます。workerやMinecraftのChunk objectへは渡しません。

候補の淘汰、候補不足、capture epochが未来、generation mismatchは「変更なし」の根拠になりません。cut時に候補が取得できなければ`candidate_missing_or_stale`として論理経路を閉じ、設定されたfallbackへ移ります。初回BASEで全Chunk NBTをキャッシュする設計にはしていないため、1GiB環境で候補を無制限に貯めません。

### 2. Mutation boundary

`WorldChunk.setBlockState`、`setBlockEntity`、`removeBlockEntity`、`BlockEntity.markDirty`、`World.markDirty`をMixinから`ChunkMutationTracker`へ通知します。Piston tickは既存の`PistonBlockEntityMixin`が`World.markDirty`を呼ぶため、progressの変更が候補generationへ反映されます。`WorldTickScheduler`のschedule/attach/detachは、1.18.2のschedulerがWorld参照を持たないため、Engineがserver worldのscheduler objectをdimensionへ登録してからChunkPos単位でgenerationを進めます。未登録schedulerは推測せず`uncertain`としてfallbackします。

EntityのremoveとChunk跨ぎは`EntityLifecycleTracker`へ bounded eventとして記録します。`ThreadedAnvilChunkStorage.unloadEntity`も観測します。unloadはdespawnとは違い、Entity regionへ永続化される可能性があるため、最終immutable entity-region captureを持たない現実装では`entity_unload_requires_barrier_fallback`としてLogical Deltaを公開しません。event overflow、移動先のcapture不足、未観測のmutationもExact扱いしません。これは全Java heapのuniversal WALではなく、候補を「変更なし」と誤認しないための保守的なinvalidate boundaryです。

### 3. Exact cut

barrier開始後、logical pathは`executeQueuedTasks`、`server.save`、global Chunk IO drain、全World filesystem scanを実行しません。`TransientChunkStateMarker`とRuntime Capsule captureを行った後、`LogicalDeltaEngine.captureAtCut`が次を確認します。

- mutation observerがuncertainでない
- POI/dataなど汎用captureできないpersistent fileの扱いが設定と一致する
- 現在accessibleなChunkごとに、baselineからgenerationが変わった場合だけ同じgenerationのcandidateがある
- 現在のEntity chunk payloadとlifecycle eventに矛盾がない
- capture中にmutation versionまたはscheduled-tick auditが変化していない

全条件を満たした場合、変更Chunk、変更Entity region、tombstone、level.dat metadataを`LogicalDelta`としてsealedします。変更されていないChunk/entity regionはparentのauthenticated materialized viewをauthorityとしてindexだけを持ち、巨大な重複NBTを作りません。

### 4. Background materialization

`LogicalDeltaMaterializer`はworkerで`JavaIncrementalBackend`を親checkpoint worldへ適用します。親とsourceが同じauthenticated viewである場合のhard linkによるctime/nlink変化は、外部writerとは区別して扱います。差分regionを変更する前にcopy-on-writeし、`RegionFile`の1.18.2 constructor order `(regionFilePath, regionDirectory, dsync)`を守ります。Entity tombstoneはUUIDで対象だけを除去します。Live `ServerWorld`、`WorldChunk`、scheduler、Entity objectには触れません。

### 5. Safe fallback

logical cutがExactでないとき、同じ`PendingBarrier`を同じ呼び出しの中でPortable Generationのcutへ戻します。Legacy backendだけは従来どおりVanilla flushを使います。fallback理由は`checkpoint.logical_fallback`とmanifestのcomponent statusへ残し、logical deltaを成功扱いしません。

`/ac checkpoint base`は親がない、または意図的に完全なrootを作り直したいときの明示操作です。確定済みviewを長期間親として保持したくない場合は、Worldを読むことなく`/ac checkpoint compact`でoffline compactionを要求できます。compactionは検証・copy・atomic commitをworkerで行い、ServerWorldを止めません。

## Exact Epoch / Generation Fence

世代Nのsource of truthは、rolling preparationではなくLogical Cutです。rolling中にhashしたdigestは、cut時点のsize/mtime/ctimeと一致した場合だけ高速経路に利用されます。不一致や外部writerを検出したfileはcut時に再hash/copyします。

portable filesystemでWorldを再開した後にmutableなfileを読むと、NとN+1の内容が混ざります。そのため、CoW snapshotを必須にせず正確性を守るために次のFenceを採用しています。

1. 現在tickを完了させる。以後barrierが終わるまでServer threadのtask queue（Playerのpacket、console command）を保留し、拒否はしない。以下はすべて同じ呼び出しの中で行い、tickを挟まない。
2. Entityをflushする（読み込み中のEntity sectionを取り込んでから保存）。
3. `CHECKPOINT_BARRIER`中にRuntime Capsuleをdeep copyする。
4. dirty chunk（アンロード待ち・アンロード中のChunk、`ReadOnlyChunk`の中身を含む）をbounded batchでserializeし、`MinecraftServer#save`をflush・ticket処理なしで実行する。Chunk・POI・EntityのIO queueの書き込み完了を待つ（fsyncはしない）。
5. 全fileのsize/mtime/ctimeを親の記録と比較し、`RegionBasedStorage#write`の記録とも照合して、どちらかが変更を示すfileをtemporary generationへcopyする（`DetachedGeneration`）。
6. `CHECKPOINT_FINALIZING`へ進み、同じtickの中でWorldを再開する。
7. workerはdetached copyをhashし、未変更fileを検証済みparentからhardlink（BASEではcopy）して`generation-seal.json`をforceする。Live Worldは再参照しない。

これにより、後続tickの書込みが世代Nへ入ることはありません。Java hardlinkが使えない場合は同じparent filesystem内の通常copyへfallbackします。Live Worldとのhardlink、未検証parentからのreuse、busy loopは行いません。

## BASE / DELTA

DELTAは親IDを持ちますが、現在のportable実装では各世代を完全なmaterialized world viewとして保存します。つまり復元時に親をオンラインで合成する必要はなく、親は認証済みtimelineとretention dependencyのために保持されます。`checkpoint.incremental.base_interval`または`max_chain_depth`を設定した場合、そこに達すると次の世代をBASEとして作り、parentを切ります。既定の`0`では定期的なBASEを作りません。各世代は完全なfile集合で、復元は親を参照しないため、BASEはWorld全体のcopyを1つ増やすだけです（s1では約11分ごとに19 GB、1日約1.5 TBの書き込みでした）。BASEもDELTAと同じ短いcutで切り出し、workerが検証済みの親checkpointからhardlinkではなくcopyでfileを作ります（以前は凍結中にLive World全体をcopyしており、19 GBのWorldで25〜30秒止まっていました）。

`GenerationSeal.fileSet`はsorted pathの次の状態を表します。

- `PRESENT`: cut時にdestinationへ存在し、digest/size/capture kindが一致する。
- `TOMBSTONE`: parentにはあったがcut時には存在しない。
- `ABSENT`: optional pathがcut時に存在しないことを表す診断状態。payload fileは作らない。

commit時にmanifest、seal journal、world file metadata、seal countersを相互検証します。PRESENTのdigestがworld manifestと違う、INHERITEDにparentがない、TOMBSTONEがworldへ現れる、sealとmanifestのfile setが違う場合はcheckpointを公開しません。

## Backpressure / Retention

server threadのactive finalizerは安全側に1世代です。`max_inflight_generations`は将来の複数generation queueの上限として保持し、現在は`activeJob != null`で次のcapture要求を保留します。temporary generation、Runtime JSON、manifest、checksum、READYのいずれかが失敗した世代はfinal checkpointに昇格しません。

retentionは、`retention_count` / `retention_days` / `retention_max_gb`を超えた世代を古い順に削除します。最新の世代、使用中（pin）の親、quarantine済みの世代は削除しません。

各世代はそれ自体で完全なfile集合です（変わっていないfileはhard link）。古い世代を消しても新しい世代のbyteは減らず、復元は復元する世代だけを読みます。以前は「保持する世代のすべての祖先」を削除対象から外していましたが、定期BASEのないchain（既定）ではすべての世代が最新の祖先になるため、**何も削除されませんでした**（`retention_count = 20`のServerに1,400世代・約100 GBが残り、commitのたびに全manifestを読み直して15〜40秒かかっていた）。祖先の保護はやめています。

- 1回のcommitで削除するのは最大8世代・約2秒までです。たまった世代は以後のcommitで少しずつ消します（1つのcommitを数分止めないため）。
- 古い世代を消すと、最新の世代と共有しているinodeのctimeが変わります。次の世代は親fileを記録済みのctimeで見分けるので、そのままでは毎回World全体をhashし直すことになります。削除の直前に記録と一致していたfileに限り、削除後のctime / link数を最新の世代の（memory上の）記録へ反映します。性能計測行の`counts={… parentFilesRehashed=N}`が通常0であることで確認できます。

## Durability

`CheckpointStore.commit`は`cp-XXXXXX.tmp`内にpayload、manifest、checksums、READYを作り、同じparent directoryへatomic moveします。

- `SAFE`: 新規payload、control metadata、temporary directory、publish directoryをforce。
- `BALANCED`: 新規fileとtemporary directoryをforceし、publish directory fsyncを省略する場合がある。
- `FAST`: manifest/checksums/READYだけをforceする。電源断でworld payloadだけが失われる可能性があるが、READY/checksum検証が失敗する世代はRecoveryで使わない。

既定は`SAFE`です。`FAST`は「壊れたcheckpointを使う」モードではありませんが、成功率と復旧候補の耐電源断性を下げます。

## Legacy backend

legacy backendは削除していません。明示指定時は従来どおりWorld copyが完了するまでfreezeし、copy完了後にcommitします。この経路をportableのEpoch Contractと混同しないため、`AUTO`からは選択しません。

## 失敗時の原則

Barrier、seal、payload serialization、checksum、fsync、atomic publishのいずれかが失敗した場合、temporaryを削除できる範囲で削除し、未完了世代をRecovery候補へ追加しません。既存のlast valid checkpointは変更しません。`CHECKPOINT_FINALIZE_FAILED`はログ診断用に使い、通常運転のWorldを不要に再凍結しません。
