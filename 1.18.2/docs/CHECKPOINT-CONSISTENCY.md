# Checkpoint Consistency

## Parent immutabilityのincident修正

hardlink追加/削除によるctime変更はepoch/content変更の証拠ではない。
逆にnlink減少はimmutable contentの証拠でもない。metadata drift時はSHA-256とhash前後revisionを確認する。
inherited fileが失われた場合、live Worldで修復せずquarantineし、SUSPENDED_NEEDS_REBASEにする。
READY payloadの内容は修正しない。restore staging/liveとのinode共有は禁止する。
具体的な再現testとinode auditは[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)を参照。

## Contract

AntiCrashのExact Epoch Contractは「checkpoint Nの各payloadはLogical Cut N時点、またはNより前の状態で、その後のmutationがないことを証明できる状態でなければならない」という契約です。通常tick中のrolling preparationは候補cacheであり、checkpointの正しさを単独で証明しません。

Worldを再開してからLive Worldをscanする実装は、N+1の書込みをNへ混ぜるためportable経路では禁止です。server threadが作ったdeep immutable representation、またはLogical Cut中にdetachedされたfileだけをbackgroundへ渡します。

## Runtime stateとfilesystemの境界

Runtime Capsuleは`RuntimeCapsuleManager.capture`でserver thread上に取得されます。Runtime Capsule v6のchunk domain、Block Event、Scheduled Tick、Moving Piston、BlockEntity ticker、Entity、Torch Burnout、World runtime、Ticket ledger、Carpet、FakePlayerの各値はJava object参照ではなくprimitive、文字列、packed position、sorted list、canonical fingerprintへ変換されます。

既定の`LOGICAL_DELTA`では、通常のLogical Cut中に`server.save(true, true, false)`、global Chunk IO drain、全World filesystem scanを行いません。候補Chunk NBT、Entity region、tombstone、level.dat metadata、Runtime Capsuleをserver threadでimmutable化し、workerは認証済みparent viewへcopy-on-writeで適用します。`poi/`・`data/`などgeneric semantic capture不能なregular fileの有無だけはbarrier前にpreflightし、存在時は設定が明示的に許可しない限りfallbackします。

`LEGACY_SAFE`へfallbackした場合は、`server.save(true, true, false)`とbounded Chunk IO drainを使う旧安全経路を実行します。`PORTABLE_GENERATION`はflushなしで保存したあと、Chunk・Entity・POIの全IO workerを封印します（各workerは実行中のtaskと、それより前に積まれたtaskだけを終えて停止します）。封印中はどのregion fileにも書き込みが起きないので、「ディスク上のfile＋queueに残った書き込み」がそのcutそのものです。fileを切り離したらworkerを再開し、queueに残っていた書き込みはworkerがcheckpointのコピーへVanillaと同じ形式で書き込みます。`world/`のPRESENT fileはそのcutのsize/mtime/ctime/digestを持ち、未変更fileのINHERITEDは検証済みparentのimmutable fileから作られます。`playerdata`、`stats`、`advancements`などのVanilla fileはconfigで有効な場合だけcut中に別captureし、workerはLive copyを読みません。

Logical Deltaの初回BASEは`/ac checkpoint base`で明示します。再起動後にmutation baselineがない場合も、全Chunkを推測で差分扱いせず、設定されたfallbackへ切り替えます。これにより低停止時間のためにMixed Epochを公開することを防ぎます。

## 何を復元できるか

復元可能性を「保存できたか」と「新しいServer instanceへ安全に適用できるか」に分けます。

| 状態 | 保存 | 復元/検証 |
|---|---|---|
| Block Event queue | 順序付きsnapshot | 新queueへ意味的に再構築 |
| Scheduled block/fluid tick | position/type/time/sub-order | queueへ再構築。失敗はSTRICTでblocking |
| Moving Piston | BlockEntity state、順序、chunk dirty marker | Vanilla objectへ適用・再検証 |
| Chunk domain | loaded/FULL/block/entity/forcedとreadiness | Futureをpollして集合一致を検証 |
| BlockEntity ticker | position/type/current/pending/order | objectそのものではなく意味的再構成 |
| Entity order / unload | UUID/type/network ID/iteration観測。unload境界はobserverで検出 | Entity orderはfingerprintのみ。unloadを最終region captureできない場合はLogical Deltaをfallbackし、内部Map置換はしない |
| Ticket ledger | type/level/argument/tickCreated/expiry観測 | 自然再生成を優先し、内部Mapはrestoreしない |
| Torch burnout | dimension/position/time/order | 新World keyへ再構成 |
| World RNG | 1.18.2の安全な公開stateなし | `UNSUPPORTED`。JDK `--add-opens`は追加しない |
| Carpet ActionPack | next/interval/count/continuous等 | Adapterが取得・適用できた場合だけCAPTURED |

Chunk NBTは`CHUNK_NBT`、Entity regionは`ENTITY_CHUNKS`、Scheduled Tick/Block EventはRuntime Capsuleをauthorityとします。Chunk NBT内のscheduler表現を別途再登録して二重化せず、Moving PistonはChunk内のBlockEntity NBTとRuntime ticker/orderの同じcapture epochを要求します。`LogicalDeltaMaterializer`はLive RegionFileを開かず、staging側のRegionFileだけを操作します。

Unknown Modがworld配下へ書くregular fileはportable cutのfile setへ含まれます。ただし、外部プロセス、DB、thread stack、CPU scheduling、非同期lighting queueの意味的状態は自動的に等価とはみなしません。Modがcheckpoint境界で安全にflushできない場合は、Extension APIまたはcritical sectionで明示的に参加させる必要があります。

## Generation file set

`logical-delta.json`はLogical Cutのimmutable semantic hand-offです。変更Chunk/Entity region、tombstone、generation index、component statusを保存します。`generation-seal.json`は一時Portable Generationのcontrol journalで、fallback時にcommit前に次を保存します。

- generation format、BASE/DELTA、parent checkpoint ID
- logical cut server tick / monotonic timestamp
- PRESENT/INHERITED/SEALED/TOMBSTONE counters
- sorted path、size、mtime、ctime、SHA-256、capture kind

sealは独立fileが揃ったことの証明であり、Minecraft内部Runtimeの完全な代替ではありません。manifest commit時にsealを再読し、manifestとfile setが同一であることを検証します。旧formatにsealがない場合、OPTIMIZEDでは既存互換経路を使えますが、STRICTで新しいexact generation evidenceが必要な処理は`MISSING_FROM_OLD_FORMAT`として扱います。

## `/ac start`直前の再検証

Chunk Futureがすべて完了した時点だけでは十分ではありません。Player login、FakePlayer rejoin、Carpet ticket、server taskの進行によって状態が変わるため、`/ac start`受付時に次をもう一度比較します。

1. checkpoint integrityとRuntime Capsule payload
2. Expected loaded/FULL/block-ticking/entity-ticking/forced domain
3. Block Event / Scheduled Tick / Moving Piston / ticker fingerprints
4. Carpet completenessとFakePlayer ActionPack timing
5. strict required player readinessとunsupported policy

Mismatch、corrupt、required unsupported、required Future未完了は`READY_TO_START`から先へ進めません。`--force`は破損、永続化失敗、未完了restore、config failureの迂回手段ではありません。

## Async TMC boundary

Thread scheduling、Word Tearing、Future内部順序をserializeして等価性を主張しません。将来の装置単位保護は、試行前checkpointと明示的なguardを組み合わせます。

```text
/ac guard begin <name> --crash rollback|keep|ask
/ac guard commit
/ac guard abort
/ac guard status
```

これは今回のportable generationの内部へ非同期thread復元を持ち込まないための境界です。

## 保存対象にするだけの状態

Vanillaが保存するのに、変化してもChunkを保存対象にしない値があります（ホッパーのcooldown、かまど、醸造台、スポナー、コンパレーター、動作中ピストン、End Gatewayの`Age`、巣箱の`TicksInHive`、待機中のスカルクセンサー）。AntiCrashはこれらが変化したtickにChunkを保存対象にします。値そのものはVanillaのキーに入ります。
