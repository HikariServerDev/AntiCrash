# Checkpoint Performance

## Incident後のmemory / backpressure

`/ac status`はengine suspension/reason/failure count/phase/last valid/parent/headroom/effective batchを表示する。
headroom guardはmin(128 MiB, heap max/8)。不足時にoptional cacheを解放し、GC後も不足なら保存を停止する。
guardは任意ModのNBTに対する厳密な全phase memory upper boundではない。
候補の内部size accountingはcloneせず、JSON/checksumはstreaming。rolling queueにはFuture-based backpressureがある。
OOMを同じ周期で繰り返さない。POI/dataがあるWorldではlow-pauseは未対応であり、fallbackの停止時間を測定して表示する。
[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)に1GB実試験のCSV/GC/inode/phase evidenceの読み方を記載。

## 目標と保証

既定の目標は`target_barrier_ms = 20`、警告50 ms、critical 100 msです。これは目標/観測閾値であって、正確性を壊して処理を20 msで打ち切るdeadlineではありません。大量のdirty Chunk、初回BASE、低速ディスク、Java GC、Vanillaのmetadata flushでは超過します。

既定の`LOGICAL_DELTA`では、通常tick中に`ChunkSerializer`で作ったimmutable candidateをbounded cacheへ蓄え、barrierではmutation generationとscheduled-tick auditを検証して論理差分だけをsealします。差分はworkerが認証済みparentへcopy-on-writeで適用するため、通常経路で全Worldをserver threadから再走査しません。候補不足、observer uncertainty、未対応の`poi/`/`data/`、初回起動でmutation baselineがない場合は、変更を推測せず`PORTABLE_GENERATION`または`LEGACY_SAFE`へfallbackします。

Logical Cut中に`executeQueuedTasks`、`server.save`、global Chunk IO drain、全World filesystem scanを実行しないことが低停止時間の要点です。ただし`poi/`と`data/`にgeneric capture不能なregular fileが存在するかのpreflightはbarrier前に行います。preflightが負荷または互換性の問題になる場合、候補を無理に公開せず設定されたfallbackへ進みます。

## 計測phase

`CheckpointTiming`は`System.nanoTime()`だけで期間を測ります。wall clock変更やNTPで負の時間になりません。manifestと`/ac debug checkpoint-performance last`には次のphaseが記録されます。

- `wait_for_tick_boundary`
- `barrier`
- `runtime_capture`
- `temporary_generation`
- `generation_capture`
- `vanilla_file_capture`
- `logical_cut`
- `payload_serialization`
- `commit`

Runtimeとdelta/finalizationの内訳も同じ`phaseDurationsMs`へ記録します。

- Runtime: `runtime.chunk_domain`、`runtime.world_runtime`、`runtime.entities`、
  `runtime.block_entity_tickers`、`runtime.torch_burnout`、`runtime.moving_pistons`、
  `runtime.transient_entities`、`runtime.ticket_ledger`、`runtime.block_events`、
  `runtime.carpet`、`runtime.scheduled_ticks`、`runtime.players`、`runtime.fakeplayers`
- Delta: `delta.chunk_scan`、`delta.chunk_candidate_validation`、`delta.entity_capture`、
  `delta.tombstones`、`delta.level_data`
- Finalization: `finalize.file_write`、`finalize.serialize`、`finalize.manifest`、
  `finalize.ready_commit`

`barrier`、`runtime_capture`、`logical_cut`は親処理を含むinclusive値です。内訳phaseは
原因切り分け用のexclusive寄りの計測で、同じ処理を別の集計名で二重加算して合計時間を
推定しないでください。実際の停止時間は`barrier`または`logical_cut`を基準にします。

同時にheap used/committed/max、non-heap used、GC collection count/time、Runtime Capsule size、world bytes、changed/reused count、generation file countersを記録します。通常ログの`checkpoint.performance`は一行の要約、manifestの`phaseDurationsMs`は機械可読な詳細です。

## 通常tickのrolling preparation

`rolling_enabled`がtrueの場合、CheckpointManagerはRUNNING中に最大`rolling_budget_ms_per_tick`（既定1 ms、adaptive時は最大3 ms）だけaccessible dirty Chunkを`StorageIoWorker`へ先行保存します。これはcheckpointのExpected domain、Runtime Capsule、file setを決めません。処理できなかったchunkはdirtyのまま残し、barrierが必ず処理します。

低速環境で通常tickが悪化する場合は`rolling_enabled = false`または予算を下げます。逆にSSDでbarrierのdirty backlogを減らす場合は最大予算を少しずつ上げます。`RollingBudgetController`は実測時間が予算を超えると下げ、十分余裕がある場合だけ上げます。

## Memory / OOM

1 GiB heapを前提に、次を優先します。

- `background_threads = 1`
- `recovery.chunks.max_concurrent_loads = 4`
- `checkpoint.cache.max_mb`でcandidate NBTをboundedにする（evictionは安全なrecapture/fallbackを発生させるだけ）
- Entityは全個体の複製を常時保持せず、Chunk単位の変更regionをcut時に作る
- dirty Chunkのbounded batchとbatchごとのIO drain
- Runtime Capsuleのdeep immutable primitive representation
- detached fileを再利用し、未変更fileの再hashを避ける
- commit後にlive hash cacheとtemporary referencesを解放

全Chunkを同時Future loadしたり、Main ThreadでFutureを`.join()`したりしません。STRICTのExpected Chunk数をOOM対策のために減らすこともありません。上限不足ならRecoveryを凍結して理由を表示します。

`checkpoint.io.max_mb_per_second`はbackground finalizerのI/O throttle設定です。portable pathのEpochを守るため、変更fileのdetach（cut中のcopy）をこの値で遅くしたり、resume後へ移したりしません。hash、hardlink、BASEのcopyはresume後のworkerが行います。通常は0のままにします。

実測（19 GB・約12,000 file、NVMe/ext4）では、旧実装のcutは平均約1.8秒、BASE世代では25〜31秒Worldを止め、その間のPlayer packetを破棄していました。現在のcutで凍結中に行うfile作業は、全fileのstat（並列）と、変更されたfile数十個のcopy（約300 MiBで40〜70 ms）だけです。

`CheckpointMirror`と`ScheduledTickMutationTracer`は診断専用です。Vanillaのscheduler queueやChunk Ticket Managerを複製・置換せず、bounded traceを超えた場合は`UNSUPPORTED`/fallbackとして扱います。mirrorを有効にしても、メモリ使用量やcheckpointの正確性が増すことはありません。

## 調整項目

| 設定 | 影響 |
|---|---|
| `save_batch_chunks` | 通常は変更不要。16未満にしたときだけ、その件数ごとに切り出しがdiskへの書き込みを待つ |
| `rolling_budget_ms_per_tick` | 通常tickの小さな準備量と次回barrierのdirty backlog |
| `snapshot.io_threads` | resume後のworkerのhash/hardlink/copy並列度（0=2） |
| `background_threads` | JSON/commit/retentionの並列度。世代は無制限に並列化しない |
| `base_interval` / `max_chain_depth` | BASE（workerによる親からの全量copy）の頻度と親dependency長。既定`0`は定期的なBASEなし |
| `checkpoint.engine.mode` / `fallback` | `LOGICAL_DELTA`を通常経路にし、証明できないcutを安全なbackendへ退避 |
| `checkpoint.cache.max_mb` / `budget_ms_per_tick` | 通常tick中のcandidate作成量とheap上限。小さすぎる場合はfallbackが増える |
| `checkpoint.mirror.enabled` / `trace_limit` | scheduler mutationの診断量。Vanilla queueの保存範囲は増えない |
| `durability.mode` | 電源断後のcheckpoint残存率。既定SAFEを推奨 |
| `minimum_free_mb` | 空き容量不足時の公開防止 |

数値は実機のstorage、world file数、dirty Chunk数で変わります。平均値だけでなく最大barrier、GC pause、OOM発生時のheapを確認してください。

## 診断コマンド

```text
/ac debug checkpoint-performance
/ac debug checkpoint-performance last
/ac debug checkpoint-performance reset
/ac status
```

`/ac status`にも直近checkpointのphase、Barrier、Finalization、generation counters、heap、last errorが表示されます。ログの`checkpoint.performance`、`checkpoint.barrier_slow`、`checkpoint.snapshot_slow`、`checkpoint.finalize_slow`を時系列で比較してください。

## 実機で確認するログ

実Serverでの検証はこのリポジトリの自動testには含めません。ユーザーが確認する際は、checkpoint IDごとに次を保存してください。

1. `checkpoint.transient_state_marked`
2. `checkpoint.logical_candidates_prepared`または`checkpoint.logical_fallback`
3. `checkpoint.performance`
4. `checkpoint.diagnostic`
5. `checkpoint.committed`
6. `/ac debug checkpoint-performance last`、`/ac debug mirror audit`
7. `/ac recovery verify --verbose`と`/ac debug equivalence --verbose`

初回は`/ac checkpoint base`で親viewを作り、以後は`LOGICAL_DELTA`の`logicalDeltaChunkCount`、candidate cacheのeviction、fallback理由を確認します。`/ac checkpoint compact`は確定済みviewのoffline BASE化ですが、live Worldの現在状態をcheckpointへ追加する処理ではありません。

Trencherなどの再現試験では、破損の見た目だけで成功判定せず、Logical Cut tick、Expected/Actual Chunk Domain、Scheduled Tick、Moving Piston、BlockEntity ticker、Carpet ActionPackの差分を同じ試験ケースで比較します。
