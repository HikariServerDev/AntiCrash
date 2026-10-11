# Architecture

## Checkpoint incident boundaries (2026-10-04)

`CheckpointFailureGate`はWorldのRUNNING状態と別にcheckpoint engineの停止/backoffを管理する。
OOM/予算不足/親破損後は自動checkpointを再試行せず、既存Worldのtickを不必要にfreezeしない。
`CheckpointStore`はprocess-local authentication、永続quarantine、parent pinを所有する。
`CheckpointWriteGuard`はREADYへの内部書込みを禁止する。clean/N startupではparent検証をcapture requestまで延期する。
詳細・qualification境界は[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)を参照。

## State Machine

`AntiCrashStateMachine`が永続Recovery Stateを読み、状態変更時に`.anticrash/state/recovery-state.json`へ書き込みます。

```text
NORMAL -> RUNNING -> CHECKPOINT_REQUESTED -> CHECKPOINT_BARRIER -> CHECKPOINTING
                                                                  -> CHECKPOINT_COMMITTING -> RUNNING
   │
   └─ dirty exit -> PREBOOT_INSPECT -> PREBOOT_DECISION (ASK/MANUAL)
                                      ├─ N (ASK) -> NORMAL -> World load
                                      ├─ Y/AUTO -> PREBOOT_PRESERVE_RAW -> PREBOOT_VERIFY
                                      │             -> PREBOOT_RESTORE -> RESTORING -> World load
                                      └─ unresolved MANUAL/corrupt -> RECOVERY_FAILED (no World load)

World load -> WAITING_FOR_CHUNKS -> RESTORING_FAKEPLAYERS -> READY_TO_START
             -> STARTING -> RUNNING
clean exit  -> NORMAL -> World load (ASKも含めて候補検証・質問なし)
validation failure -> RECOVERY_FAILED
```

`PREBOOT_*`はDedicatedServerの`setupServer()`がconsole reader、network bind、`loadWorld()`を開始する前に実行されます。ASKの候補表示は`READY`とbounded manifest metadataだけを読み、認証済みとは扱いません。`Y`またはAUTOへ進んだ場合だけcrash-raw、全payload検証、transactional staging restoreを行います。`N`はdirtyでもWorldを凍結せず通常起動します。clean bootにはこの経路を通さないため、通常起動のレイテンシとメモリ割り当てを増やしません。

`CHECKPOINT_BARRIER`/`CHECKPOINTING`と復旧系状態では、`ServerWorld.tick`、`ServerPlayerEntity.tick`をcancelし、`MinecraftServer.tickWorlds`内のCommand Function tickをskipします。checkpointのLive World copy中はログインとmutation packet、変更系Commandも止め、KeepAlive、`/stop`、完全一致する`/ac help`、`/ac status`だけを通します。`CHECKPOINT_COMMITTING`ではLive Worldを一切読まないため通常tickを再開します。復旧中はRecovery lock設定に従います。

## Tick Barrier

`MinecraftServerMixin`の`tick` returnでCheckpointManagerを呼びます。現在のServer Tickの途中には入らず、MinecraftのWorld/Entity/Block/Scheduled Tickが戻った後にだけcaptureします。既定の`LOGICAL_DELTA`では、Server threadはRuntime Capsuleとsemantic mutation boundaryをcaptureし、候補のimmutable NBTをExact Epochとしてsealします。この経路では`executeQueuedTasks`、`server.save(true, true, false)`、global Chunk IO drain、全World filesystem scanを実行しません。`CHECKPOINT_SEALED`へ移った時点でWorldを再開し、workerが認証済みparent viewをcopy-on-write materializeします。

logical cutが候補不足、mutation observer uncertainty、scheduled-tick mutation、未対応persistent stateなどでExactにならない場合だけ、同じbarrierをbounded staged saveへ切り替えます。このfallbackでは既存の`save_batch_chunks`と必要なVanilla flushを使い、`PORTABLE_GENERATION`または`LEGACY_SAFE`で完全にdetachしてから再開します。fallback理由はログとmanifestへ残り、論理差分の成功として隠しません。初回の論理差分に親がない場合は低停止時間契約を壊して自動的に全量freezeせず、`/ac checkpoint base`を要求します。

通常の自動・手動checkpointでは、Server threadはworkerのFutureを待ちません。worker完了は各tickの短いpollで反映されます。`/stop`をcancelしてcheckpoint完了までServerを擬似稼働させることはありません。normal shutdownは`checkpoint.shutdown_timeout_seconds`の範囲内だけ進行中またはfinal checkpointを待ち、超過時は`.tmp`を復旧対象外のまま破棄してVanilla shutdownを続けます。Watchdog停止は待機しません。

monotonic deadlineは`System.nanoTime()`で管理し、TPSが落ちても600tickを待つ設計にしていません。

## Snapshot Protocol

`SnapshotManager`がbackendを選びます。前回checkpointの整合性検証は通常tick中に先行実行し、barrierへ持ち込みません。I/O worker上の`JavaIncrementalBackend`はsize、mtime、Unix ctimeが前回の検証済みmanifestと一致するfileを前回checkpointからHardlinkします。Hardlink失敗時も前回checkpointからcopyし、Live WorldをHardlink元にはしません。変更fileだけをLive Worldからhash/copy/target hashします。旧manifestにctimeがない場合やctime非対応Filesystemでは、Live fileを1回hashして保存済みSHA-256と一致すれば前回checkpointを再利用します。Recovery時のverifyは必ず全対象を再ハッシュします。

`playerdata`、`stats`、`advancements`、`session.lock`はWorld snapshotから除外します。必要なVanilla fileは`VanillaFileSnapshot`で明示的に別保存します。

manifest format version 2は`parentCheckpointId`を持ち、増分元として実際に検証したcheckpointを親にします。Recovery fallbackはREADY/checksum/manifestで認証した親chainだけを辿るため、過去checkpointへ戻した後に数値IDだけで放棄済みの新しいbranchへ移りません。format version 1は互換上、active IDより小さい世代だけを候補にします。

## Logical Delta v2のデータ境界

`LogicalDeltaEngine`はserver thread専用です。通常tickでは`ChunkSerializer.serialize`の結果を`ChunkCandidateCache`へ保存し、cutではgeneration、capture epoch、global mutation version、scheduled-tick auditを検証します。candidateはbyte配列とhashだけを保持し、workerへMinecraft objectを渡しません。

mutation hookは`WorldChunk`のblock/block-entity変更、`BlockEntity.markDirty`、`World.markDirty`、WorldTickSchedulerのschedule/attach/detach、Entityのremove／Chunk跨ぎを観測します。これはuniversal write-ahead logではありません。対象外またはobserver overflowを「不変」と推測せず、`LogicalDeltaEngine`はExactを返さずfallbackします。

worker側の`LogicalDeltaMaterializer`は検証済みparent worldをcopy-on-writeでdetachし、changed block region、entity region、UUID tombstone、`level.dat` payloadを反映します。1.18.2 `RegionFile`の引数は`(regionFilePath, regionDirectory, dsync)`であり、親と宛先のhard linkを差分適用前に置換します。`CheckpointCompactor`も同じ認証境界で、Live Worldを参照せず完全viewを新しいBASE rootへまとめます。

`CheckpointMirror`はVanillaのscheduler/ticket内部を置き換えません。1.18.2で安全な完全mirror APIがないため、enabled時も`UNSUPPORTED`／diagnostic-onlyを返し、fast pathの根拠にはしません。`/ac debug mirror audit`はこの制限、observer invalidation、mutation versionを確認するためのものです。

## 凍結時間

Checkpointは2つの局面でWorldを止めます。どちらもTick境界の一貫性のために必要ですが、長さは実装で決まります。

**Logical Cut（既定の`LOGICAL_DELTA`）**: 通常tick中に作ったcandidateをgenerationで検証し、変更Chunk/Entityだけをimmutable bytesとしてsealedします。親の変更されていないfileはbackgroundで再利用し、差分regionだけをcopy-on-writeします。capture中にmutationが観測された場合はExact扱いせず、次tickからsafe staged pathへ戻ります。logical pathのbarrierは全World saveをしないため、保存頻度を変えずに停止量を抑えます。

**Staged Barrier（fallback/backend）**: dirty chunkをserializeし、IO workerへdrainしてからVanillaのflushへ移ります。server tick loopは凍結中も20 tick/sで進むため、1 tickあたりの保存数がそのままbarrierの長さになります。1 tickあたり4件・毎tick drainという旧実装では、1,011 dirty chunkが13.4秒の凍結になりました。現在は`checkpoint.save_batch_chunks`（既定256）件をserializeしてから1回drainし、1 tickの上限時間内は同じtickで続行します。

**Portable Generation Cut（fallback）**: point-in-time copyをLive Worldから読む必要がある部分だけを凍結します。増分backendはsize/mtime/ctimeが親checkpointと一致するfileをhash無しでhard link再利用し、変更fileだけをdetachします。seal後のfinalizationではLive Worldを開かないため、copy完了後に長いmanifest/checksum/READY処理が続いてもWorldは再開済みです。初回BASEや大量dirty/変更fileではcut自体が長くなるため、計測値を見てrolling preparationとbatchを調整します。

定常状態ではfileの中身をほとんど読みません。10,000 file規模のWorldで変更が数件なら、凍結時間はhash量ではなく**per-file syscall数**で決まります。`Files.isDirectory`・`isSymbolicLink`・`isRegularFile`・`readAttributes`・`getAttribute("unix:ctime")`・`getAttribute("unix:nlink")`はいずれも個別にstatを発行するため、1 fileあたり6回のstatをprobeだけで消費していました。`FileFacts`は`unix`viewで同じ情報を1回のstatにまとめます（実測10.2 us → 3.6 us/file）。あわせて`createDirectories`の重複呼び出しをcacheし、copy自体を`snapshot.io_threads`本のthreadへ分散します（10,175 file all-reusedで1 thread 125 ms → 4 thread 75 ms）。

そのため`LiveWorldScan`が、incremental base検証の完了後・最初のcheckpointの前に、metadataが一致しないLive Worldのfileを**凍結せずに**hashします。自身のhash前後でmetadataが変わらなかったfileだけをcacheし、backendはcacheのsize/mtime/ctimeがhash時点と完全一致する場合だけそのdigestを信頼します。走査中に書き換わったfileはcacheされず、従来どおりLogical Cut中にhash/copyされます。cacheはcommit成功時に破棄します（以降はmanifestが現在のLive metadataを持つため）。

## Atomic Commit

1. `cp-XXXXXX.tmp`を作成
2. `world/`、runtime capsule、Real Player/FakePlayer snapshotを作成
3. `manifest.json`、`checksums.sha256`、`READY`を作成
4. regular fileとdirectoryをforce
5. 同一親directory内で`.tmp`をfinal nameへatomic move
6. 完全なcheckpoint一覧にretentionを適用（integrity検証はRecovery・明示inspection・次回増分元選択時に実施）

`READY`はchecksum listのhashです。VerifierはREADY、各checksum、manifest payload、symlink、未記載fileを確認します。`.tmp`、未完了、破損、path traversalをRecovery対象にしません。World置換前に全fileを一度検証し、置換後のRuntime setupでは認証済みchecksum listに対してmanifest・runtime capsule・Player/FakePlayer payloadだけを再hashします。すでに検証・stage済みのWorld全体を二重にhashしません。

commit時のchecksum生成はcheckpoint treeを1回だけ走査し、その結果からmanifest payload checksumと`checksums.sha256`の両方を作ります。`manifest.json`の書き出しも1回だけです。

`.tmp` checkpoint、`crash-raw/.tmp-<uuid>`、`restore-work/`のstagingは、それぞれのcommit/restore transactionが完了するまでは有効なcheckpointやWorldではありません。checkpointの一時ディレクトリは起動時に破棄します。World restoreだけは、プロセス停止でliveとstagingが混在しないよう`restore-transaction.json`を残します。次回のpre-world初期化でmarkerを検証し、liveが旧Worldのままならstagingをliveへ、liveが新Worldならbackupを削除し、liveが欠落していればbackupまたはstagingを一度だけ復旧してからWorld loadへ進みます。marker、stage、backupのpathは`restore-work`配下に限定し、symlinkと不正な外部pathはfail closedです。

## Runtime Capsule

`RuntimeCapsuleManager`はServer Tick、World game time、time of day、Dimension別のRuntime、`ChunkStateSnapshot`、`BlockEventSnapshot`を保存します。Runtime Capsule v6の`ChunkStateSnapshot`は、AntiCrashが重要と推測した範囲ではなく、Checkpoint境界でMinecraftが実際に観測できたloaded/FULL/block-ticking/entity-ticking/forced domainをChunkHolderのlevel、completed level、level type、sourceとともに記録します。Block Eventは`ServerWorldAccessorMixin`で`syncedBlockEventQueue`と`blockEventQueue`を読み、Dimension、BlockPos、Block ID、type、data、queueを順序を維持して保存します。復元時は両queueを事前検証してからclearし、保存順に同じqueueへ追加します。

1.18.2の`PistonBlockEntity`は`progress`を更新しますが、そのtickだけでは所有chunkの`needsSaving`を立てません。一方、Vanillaのchunk保存は`needsSaving`がfalseのchunkを`ChunkSerializer`へ渡さないため、moving pistonのNBT（pushed block、facing、progress、extending、source）が直前状態より古いままcheckpointへ入る可能性があります。`PistonBlockEntityMixin`は比較器更新を伴わない`World.markDirty`だけをtick後に行い、通常のVanilla保存にも反映します。`TransientChunkStateMarker`はcheckpoint境界で既存のmoving pistonと予定tickを再確認する保険です。これによりmoving blockを別ファイルとして推測保存することも、lightingやchunk loadingの非同期処理を変更することもありません。

Chunk復元は`OPTIMIZED`では既存の`preload_mode`（CRITICAL/ENTITY_TICKING/ALL）で軽量なbest-effort範囲を選び、`STRICT`ではv6 domainのrequired rowを1件も上限で切り捨てずExpectedにします。1.18.2の公開`getChunkFutureSyncOnMainThread`はserver threadから呼ぶと内部executorを完了まで駆動するため、`ChunkRecoveryManager`は実バイトコードで確認したprivate `ServerChunkManager.getChunkFuture`をMixin invoker経由で呼び、Futureを保持してserver loopへ戻ります。`recovery.chunks.max_concurrent_loads`（既定4）は同時Future数だけを制限し、Main Threadで`.join()`しません。Vanilla 1.18.2の`ChunkStatus.FULL`に対応するticket level `33`、block-ticking `32`、entity-ticking/forced `31`を、APIが要求するradius `0`、`1`、`2`へ変換して追加・解除します。旧level `33`/`31`をradiusとして渡す経路やlevel `2`の広域ticketは使用しません。STRICTのExpected数が`max_preload_chunks`を超えた場合はticketを追加せず、Recoveryを凍結したまま拒否します。Forced chunkの既存状態は維持し、Recoveryが追加したforced stateだけ失敗時に戻します。

Carpetがある場合は`carpet.helpers.TickSpeed`のtick rate、MSPT、`process_entities`、pause/freeze、FakePlayer ActionPackの選択フィールドをRuntime Capsule v6と別payloadへ保存・復元します。Carpetのtime warpは実行者・callbackのJava参照を新しいServerへ安全に再結合できないため`UNSUPPORTED`です。Carpet導入済みでcapture/restoreに失敗した場合はSTRICTでは`INCOMPLETE`のまま進めず、`allow_unsupported_carpet_state`を明示した場合だけそのカテゴリのUnsupportedを許可します。Carpet未導入は`NOT_APPLICABLE`です。

各Runtimeカテゴリにはcanonical fingerprintと件数・差分があります。BlockEntity tickerはcurrent/pendingを位置・type・name・orderから意味的に再構成します。EntityListはUUID、type、network ID、iteration orderを観測しますが、1.18.2の`ServerEntityManager`内部Mapをreflectionで置換しません。そのため既定STRICTではEntity orderを必須観測カテゴリとして、復元後に一致を確認できない場合はstartを止めます。ただし`require_checkpoint_players=false`では未接続のReal Playerをこの比較から除外し、`require_checkpoint_players=true`のときだけReal Playerの一覧・相対順を要求します。この期待一覧は選択的な`players.json`とは独立したRuntime Capsuleの軽量payloadです。Chunk Ticket ledgerはtype/level/argument/tickCreated/expiryを診断用に捕捉しますが、Vanilla invariantと自然再生成Ticketを壊さないため丸ごと書き戻しません。非空ledgerの比較結果は`UNSUPPORTED`であり、`require_ticket_ledger=false`の明示設定時だけblockingを解除します。`World.random`のJDK内部stateも強制公開せず、`WORLD_RNG=UNSUPPORTED`として扱います。

## Pre-world-load Restore

dirty exitの判定とcheckpoint選択はMod initializerで行い、pending restoreは次の`loadWorld`より前に処理します。`WorldRestorer`はcheckpoint integrityを検証し、restore stagingへcopyしてから除外対象のliveファイルを明示的に引き継ぎます。その後、live World directory全体を同一filesystem内のbackupへatomic renameし、staging Worldをlive名へatomic renameします。`PREPARED -> COMMITTING -> COMPLETE`のtransaction markerをforceしてから各renameを行うため、途中停止時も次回のpre-world recoveryが混在directoryを通常起動しません。atomic directory exchangeを保証できないfilesystemではrestoreを拒否し、旧来の1ファイルずつ置換へfallbackしません。Worldが既に開かれた後の危険なin-place rollbackは行いません。

Recovery後の`loadWorld` returnでRuntime/Block Eventを適用し、Chunk Futureを複数server-loopへ分割して準備してからFakePlayerを復元し、`READY_TO_START`へ遷移します。STRICTでは準備完了時だけでなく`/ac start`受付直前にも、Chunk domain、Runtime fingerprint、Carpet/FakePlayer、checkpoint integrityを再取得します。`require_manual_start=true`なら、管理者が`/ac start`を実行するまで最初のWorld Tickを許可しません。実際に`RUNNING`へ永続遷移した後でのみ、接続中のReal Playerへ再開完了を通知します。

## Recovery Equivalence Contract

STRICTの契約は「重要そうなChunkを推測して動かす」ことではなく、Checkpoint境界で観測したMinecraftの実行可能状態を復旧後に再構成し、再検証してからTickを再開することです。比較は`RecoveryEquivalenceReport`のカテゴリ単位で行い、`MATCH`/`MISMATCH`/`UNSUPPORTED`/`MISSING_FROM_OLD_FORMAT`/`INCOMPLETE`/`NOT_APPLICABLE`を区別します。Hashだけでは理由を説明できないため、verbose診断には最大20件の座標・順序差分を残します。

STRICTで完全な安全復元APIが存在しない状態（EntityList内部Map、Ticket Manager内部Map、JDK Random内部state、Carpet time warp等）は、捕捉できる範囲をfingerprintへ含め、復元できないものを黙ってMATCHにはしません。`allow_unsupported_runtime_state`、`allow_unsupported_carpet_state`、各`require_*`はその制限を承知した明示的な運用選択です。破損、Mismatch、旧形式の欠落、restore失敗はこの許可で迂回できません。

## 責務分離

- `AntiCrashMod`: Fabric entrypoint
- `AntiCrashController`: lifecycle/recovery coordination
- `AntiCrashConfig`: User Config only
- `InternalStateManager`: `.anticrash` only
- `CheckpointManager` / `SnapshotManager`: barrier and disk checkpoint
- `RuntimeCapsuleManager` / `ChunkRecoveryManager`: in-memory runtime
- `PlayerSnapshotManager` / `CarpetIntegration`: selective player restore
- `WorldRestorer`: pre-world file restore
- `ConsistencyManager` / `TransactionJournal`: external data boundary
- `AntiCrashCommand`: Configurable permission-level command surface
- `AntiCrashLogger`: level/rotation

## Low-pause Checkpoint Engine

通常の`AUTO` checkpointは、従来の「copyが終わるまで全体を凍結する」モデルから、`Logical Cut -> CHECKPOINT_SEALED -> CHECKPOINT_FINALIZING`へ分離されています。portable filesystemでEpochを守るため、変更・未証明fileのdetachだけはcut中に完了させます。seal後のRuntime JSON、manifest、checksum、READY、retentionはdetached destinationだけを読むbackground workerです。workerへMinecraft objectやLive World pathを渡しません。

実装の詳細は[CHECKPOINT-ENGINE.md](CHECKPOINT-ENGINE.md)、Epochと復元可能性は[CHECKPOINT-CONSISTENCY.md](CHECKPOINT-CONSISTENCY.md)、計測とOOM対策は[CHECKPOINT-PERFORMANCE.md](CHECKPOINT-PERFORMANCE.md)を参照してください。

## Runtime Stateの拡張保存

Vanillaが保存しないがsimulationに影響する状態のうち、BlockEntityとEntityに属するものは、Chunk/EntityのNBT内の`AntiCrash` compound（`RuntimeExtensionNbt`）に保存します。World全体に属するもの（tick順序、ticket、無人timer、天候の遷移値）はRuntime Capsuleに保存します。どのfieldをなぜ保存する／しないかは[RUNTIME-STATE-AUDIT.md](RUNTIME-STATE-AUDIT.md)が正式な仕様です。
