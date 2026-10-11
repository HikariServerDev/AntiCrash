# Recovery Guide

## Checkpoint engine停止時

`/ac status`でSUSPENDED / SUSPENDED_NEEDS_REBASE、reason、lastValid、parentを確認する。
QUARANTINEDは「候補なし」や「検証済み」と別で、改変checkpointは復旧・自動親再利用対象にしない。
破損ファイルをlive dataで埋めず、Worldを確認してから`/ac checkpoint base`で独立BASEを作る。
READY directoryを編集してquarantineを解除する方法は使用しない。OOM/予算不足も自動retryしない。
本番の既存データを自動修復する実装はない。[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)を参照。

## Dirty / Clean

起動時にrun markerを`cleanShutdown=false`で作成します。MinecraftのServer threadから実行された`/stop`またはAntiCrashのcontrolled restartが完了したときだけtrueにします。JVM shutdown hook（`kill`/SIGTERMを含む）からの`stop`は正常終了として記録しません。

```text
clean=true  -> AUTO/ASK/MANUAL: NORMAL_BOOT（最新Live Worldを使う）
clean=false -> PREBOOT_INSPECT（Worldを開く前に原因と候補を確認）
```

Crash report、`logs/latest.log`の範囲で`JAVA_EXCEPTION`、`WATCHDOG`、`OUT_OF_MEMORY`、`CRASH_REPORT`を分類できます。`kill`/SIGTERM、kill -9、電源断、OS crashなど、Server thread以外からの停止や証拠がない終了はdirtyとして扱い、後者は`UNKNOWN_DIRTY_EXIT`です。

## AUTO

`policy = "AUTO"`では、dirty exit時だけValid checkpointを検証し、既定でactive timelineの最新をpre-world-load restoreします。restoreできない場合は`fallback_to_previous_checkpoint`と`allow_restore_older_checkpoint`が有効なときだけ親checkpointを試します。最新が破損していても、強制使用はしません。format version 2では認証済み`parentCheckpointId`を辿るため、過去へ戻した後の放棄済み新規branchへroll-forwardしません。legacy version 1ではactive IDより小さい世代だけをfallback候補にします。

復旧中は以下の順です。

```text
World restore
  -> server world load
  -> Block Event/runtime validation
  -> WAITING_FOR_CHUNKS（OPTIMIZEDはpreload_mode、STRICTはv6の全Expected domain。Futureをmax_concurrent_loads件ずつ準備）
  -> Required Chunk READY
  -> FakePlayer create/apply
  -> READY_TO_START
  -> /ac start
```

`WAITING_FOR_CHUNKS`はWorldを凍結したまま必要Chunkがすべて準備完了になるのを待ちます。`ChunkRecoveryManager`は1.18.2のFuture-based APIを使い、Main ThreadでFutureをjoinせず、`recovery.chunks.max_concurrent_loads`（既定4）件までをserver loopごとに進めます。STRICTではExpected domainを一つでも省略しません。`max_preload_chunks`を超える場合はticketを追加せず、Expected/maximumを理由に`RECOVERY_FROZEN`へ留めます。`recovery.chunk_ready_timeout_seconds`（既定120秒、`0`で無効）は「準備がまったく進まなくなってから」の経過時間を測り、超過した場合はticketを解放して`RECOVERY_FAILED`へ落とします。進捗がある限り発動しないため、大きなpreload計画を途中で打ち切ることはありません。

## ASK

`policy = "ASK"`は、異常終了のあとも通常の停止のあとも、Worldを開く前にServer consoleで確認します。

| 前回の終了 | 質問 | `Y` | `N` | 無回答（`ask_timeout_seconds`経過） |
|---|---|---|---|---|
| 異常終了 | 直前のcheckpointへ戻すか | 復元 | 現在のWorldで起動 | `Y`として扱う |
| 通常の停止 | 停止時のcheckpointから再開するか | 復元して再開（動作中だった装置がそのまま続く。Worldのcopyと準備の分だけ起動が長くなり、`require_manual_start = true`なら`/ac start`が必要） | 保存されたWorldを通常起動（Vanillaの再起動と同じで、動作の途中だった装置は止まることがある） | `N`として扱う |

通常の停止のあとに`Y`を選べるよう、`create_on_clean_shutdown = true`（既定）で停止時にcheckpointを作っておいてください。候補がなければ質問せずに起動します。console入力を使えない環境（`ask_timeout_seconds = 0`でconsoleなし）では、通常の停止のあとは待たずに起動します。初回起動、`/ac checkpoint restore`などで復元を予約した再起動、復元の途中で止めたあとの再起動では質問しません。

dirty exitでは、Worldを開く前に`READY`と小さなmanifestだけを読む未検証候補を選び、Server consoleへ終了原因とcheckpoint IDを表示します。ここで`Y`を入力した場合だけcrash-raw保存、完全なchecksum検証、staging restoreを行い、その同じJVMが初回World loadへ進みます。追加のcontrolled restartは行いません。

dirty exitで`N`を入力した場合は復元もfreezeも行わず、現在のLive Worldを通常起動します。ただしこれはcheckpoint時点との等価性を承認したことにはなりません。無回答は`ask_timeout_seconds`経過後にdirty時の既定値`Y`として扱います。候補が壊れている場合は`Y`の後に完全検証で検出し、fallbackも含めて復元できなければWorldを開かず停止します。候補が存在しない場合、ASKは通常起動へ進めますが、復元候補がないことをログへ明示します。

pre-worldの候補表示は認証ではありません。整合性検証の開始時と完了時には対象checkpoint IDをログへ出します。AUTOやASKの`Y`が古い世代を検証するのは、新しい候補が無効またはrestore失敗し、かつ`fallback_to_previous_checkpoint`と`allow_restore_older_checkpoint`の両方が有効な場合だけです。World置換前の完全検証後は、World読込後に小さなRuntime payloadだけを再認証し、World全体を二重にSHA-256検証しません。

pre-world ASKではServer consoleで単独の`Y`または`N`だけを受け付けます。DedicatedServerが通常のconsole readerを開始する前に一度だけ読み取るため、入力用の別スレッドや2つ目の`BufferedReader`は作りません。World load後の古いpersisted stateでは、既存の`/ac y` / `/ac n`経路を使わず、未解決ならWorld loadを拒否します。

- `/ac recovery restore <id|latest>`: pending restoreを書き、controlled restart後に次回起動前restore
- `/ac recovery continue`: crash-state Worldを選択し、restoreなしで開始準備
- timeout: `ask_timeout_seconds`で指定した時間（既定60秒）。dirty ASKは`Y`、`MANUAL`は未解決として停止

`ask_timeout_seconds`は0以上で指定します。`0`はtimeoutなしです。タイマーは候補metadataを表示した直後に開始し、World load前の判断時間だけを測ります。`ask_default_action`は旧設定との互換のため残っていますが、pre-world ASKのtimeout判定には使用しません。

## Recovery Fidelity

`recovery.fidelity = "STRICT"`ではRuntime Capsule v6のRecovery Equivalence Contractを使用します。Checkpoint時点のDimension別loaded/FULL/block-ticking/entity-ticking/forced domain、Block Event、scheduled tick、moving piston、BlockEntity ticker、EntityList、Torch burnout、World runtime、Ticket ledger、Carpet、FakePlayerをカテゴリ別に比較し、`/ac start`直前にも再検証します。`require_checkpoint_players=true`なら、選択的な`players.json`の有効/無効に関係なく、Runtime Capsuleへ保存したReal PlayerのUUID/name/Dimension一覧も必須比較します。Mismatch、restore失敗、破損、旧形式の欠落は許可設定で無視せずFail Closedです。

`recovery.fidelity = "OPTIMIZED"`（既定）は、CRITICAL/ENTITY_TICKING/ALLの従来plannerを使う復旧です。既定の`preload_mode = "ALL"`はcheckpoint時にloadedだった全Chunkを当時のレベルまで準備してから再開します。`OPTIMIZED does not guarantee tick-boundary equivalence`。全TMC装置についてCheckpoint tick-boundary equivalenceを優先する場合はSTRICTを選び、必要Expected Chunk数に合わせて`max_preload_chunks`を設定してください。現在の1.18.2では`WORLD_RNG`、EntityList内部Mapの安全な順序再構成、Ticket Managerの丸ごと復元、Carpet time warpがUnsupportedです。既定ではその観測を理由にSTRICT checkpoint/startを止めます。

STRICTの状態は次のコマンドで確認できます。

```text
/ac recovery verify
/ac recovery verify --verbose
/ac debug equivalence
/ac debug equivalence --verbose
```

`/ac status`にもExpected/READY Chunk、current concurrent loads、pending Future、Equivalence結果、blocking category、Carpet completeness、Unsupported categoryが出ます。`READY`になる前に`/ac start`を実行しても拒否され、Login等で準備後にdomainが変わった場合も直前再検証で止まります。

Checkpoint保存前には、すでにメモリ上にあるchunkのmoving pistonとchunk-localなblock/fluid scheduled tickを保存対象としてdirty化します。1.18.2のVanillaはこれらをchunk NBTへ保存できますが、`needsSaving=false`のchunkをシリアライズしないためです。ログの`一時状態をcheckpoint保存対象にしました`（英語設定では`Marked transient state for checkpoint serialization`）で、対象chunk数・moving piston数・予定tick数を確認できます。処理は既存chunkだけを検査し、新しいchunkのロード、全chunkのdirty化、lighting処理の変更は行いません。

## MANUAL

`policy = "MANUAL"`ではdirty exit後も自動rollbackしません。候補metadataだけをpre-worldで表示し、Server consoleの`Y`/`N`を待ちます。入力がない、stdinが利用できない、または候補がない場合は安全側にWorld loadを進めず停止します。手動運用で続行する場合は、管理者が明示的なpending restoreまたは運用手順で再起動し、`/ac recovery continue`は既にWorldが開いている旧状態の互換操作としてのみ使用します。

## `/ac start`とFreeze

`require_manual_start=true`が既定です。`/ac start`は以下をすべて確認してから、次の安全なtick boundaryで`STARTING -> RUNNING`にします。

- World snapshot restore success
- Runtime CapsuleとBlock Event restore success
- Required chunks ready
- FakePlayers ready（`require_all_ready_before_start=true`時）
- checkpoint integrity success

失敗時はstartを拒否し、**どの条件で止まっているか**を応答に含めます（例: `必要Chunkが準備できていません（READY 12/256、失敗=0）`、`ASKの回答待ちです`、`復旧に失敗しています。続行するには /ac start --force が必要です`）。`/ac status`もrecovery中は同じ理由を1行で表示するため、logを追わずに次の操作を判断できます。`/ac start --force`は`allow_start_with_failed_validation=true`を明示し、かつpre-world World復元自体は完了していて、その後のRuntime/Chunk/FakePlayer等のsemantic setupだけが失敗した場合に限り有効です。破損したconfig/internal state、state永続化失敗、未完了World restore、ASK判断待ちは迂回できません。

Real Playerが`READY_TO_START`または`RECOVERY_FROZEN`中にログインすると、ゲーム内時間が停止中であることと`recovery.start_command`（既定`/ac start`）を2行で案内します。日本語・英語は`language`に従います。FakePlayer、ASKのY/N待ち、checkpoint barrierには表示しません。`/ac start`を受理した直後は予約メッセージを返し、実際に安全な境界で`RUNNING`になった時点で接続中の全Real Playerへ再開完了メッセージを送ります。

### 凍結中の表示

checkpoint barrierとrecoveryはWorldを意図的に止めるため、表示が何もないとPlayerには不明な停止に見えます。`notifications.frozen_action_bar`（既定true）は現在のphaseをaction barへ表示します。

| State | 表示内容 |
|---|---|
| `CHECKPOINT_BARRIER` / `CHECKPOINTING` | checkpoint作成中。Actionbarは表示しない（旧`checkpoint_action_bar_delay_ticks`設定は互換のため保持） |
| `WAITING_FOR_CHUNKS` | 必要Chunk `n/m` |
| `RESTORING_FAKEPLAYERS` | FakePlayer復元中 |
| `READY_TO_START` / `RECOVERY_FROZEN` | `recovery.start_command`で再開できる旨 |
| `PREBOOT_DECISION` | Server consoleの`Y` / `N`待ち。Worldはまだ開いていない |
| `RECOVERY_FAILED` | 復旧失敗。管理者の対応が必要 |

凍結が解けた時点でaction barを消去します。FakePlayerへは送りません。この表示はfreeze条件・復旧判断・checkpoint内容を一切変更しません。

pre-world ASKの質問はWorldとPlayerがまだ存在しないため、Server consoleにだけ表示します。`notifications.ask_prompt_in_game`は旧post-world表示との互換設定ですが、新しい起動経路では使用しません。DedicatedServerのconsole readerと競合しないよう、AntiCrashは同じ標準入力を短時間だけ直接読み、回答後は入力スレッドを残しません。

## Controlled restart

明示的な`/ac checkpoint restore`、`/ac recovery restore`、`/ac recovery abort`はpending stateを先に保存してからVanilla shutdownを開始します。復元自体は`recovery-state.json`のpending restoreだけで駆動されるため、再起動の手段に関係なく次回起動時に適用されます。一方、dirty ASKの`Y`とAUTOは、World load前にその場で復元するためpending restartを作りません。

明示的なcontrolled restartの扱いは3通りです。Supervisorを前面起動している場合はhandshakeが成立し、`.anticrash/state/restart-request`を書いてSupervisorが再起動します。Supervisorなしで`recovery.spawn_restart_helper = true`のときだけ、detachedなrestart helperを起動します。既定の`false`では**プロセスを一切起動せず、markerも書かずに**停止し、管理者が手動で起動します。helperは実行中プロセスから起動commandを再構成するため、start scriptの環境を再現できず、wrapper経由で起動している構成では動作しません。

Supervisor前面起動では、Supervisorが旧Java childの終了を待ち、同じconsoleのstdin/stdout/stderrを継承したchild JVMを同じwindowで再実行します。Supervisorは起動時・child終了時・controlled restart受理時・異常終了再試行時にSupervisor/child PID、終了code、試行回数を出力します。childには`-XX:+ExitOnOutOfMemoryError`を付け、OOMでServer threadだけが死んでworkerが残る状態をプロセス終了として検出します。Supervisor modeは`ANTICRASH_SUPERVISED=1`だけでは成立せず、固定markerの絶対pathも一致する場合だけhandshakeとして認めます。markerはreplacement起動前にclaimし、旧版などが残したstale markerもpending restore適用完了後に除去します。

復元を予約したstopでは、`MinecraftServer.shutdown`のreturn（AntiCrashが出力できる最後の地点）で`復元を予約しました。次回起動時にWorldへ適用されます。起動してください。`を出力します。この時点でWorldはまだ置き換わっていません。AntiCrashはMinecraftがWorldを開く前に復元を適用するため、実際のrollbackは次回起動の初期化中に行われます。予約は`recovery-state.json`へ永続化されるので、いつ起動しても適用されます。この行が出るのは復元を予約したstopだけで、次回起動がWorldを置き換えてpending requestを消費した後の通常stopでは出ません。

World restore後に2回目のprocess restartは行いません。replacement JVMがそのままWorld load、Chunk/FakePlayer準備、`READY_TO_START`まで進みます。`start.sh`はModの必須条件ではなく、Supervisorを前面起動するための入口です。直接Java起動でも通常のcheckpoint・復旧は動作しますが、通常運転中のJVM crash/OOM、ASKの再起動後のconsole入力、管理ツールの1 PID監視まで同じwindowで保証するにはSupervisorを標準利用してください。Supervisorは短時間の連続失敗だけを`--max-restarts`で制限し、`--stable-runtime-ms`以上稼働した後は再試行回数をリセットして非0終了を再起動します。通常の`/stop`はmarkerを作らずexit code 0で終了するため自動再起動対象外です。

## Interaction Lock

凍結中にclientから届くModの通信（custom payload）は、破棄せずに次のように扱います。

- `minecraft:register` / `minecraft:unregister` / `minecraft:brand`と`carpet:hello`はそのまま処理します。Worldを変えない挨拶と登録で、これが届かないとServerはそのclientをCarpet clientとして扱わず、creativeNoClipのようにclient側にもruleが必要な機能が、接続し直すまで効きません。
- それ以外は保持し、`/ac start`でWorldが動き出したあと、届いた順に処理します（1 Server全体で4,096件まで）。
- Fabric APIのchannelとして登録されている通信は、AntiCrashより先にFabric APIが受け取るため、従来どおり凍結中も処理されます。

拒否したpacketには、Vanillaと同じ**訂正packet**を返します。Minecraftはblock/inventory操作を無言で捨てません。`onPlayerAction`は必ず`PlayerActionResponseS2CPacket`で応答し、`onPlayerInteractBlock`は影響する2つのblock stateを再送し、`onClickSlot`は`ScreenHandler.syncState()`を呼びます。Clientはこの応答を受けて、先行描画した変更を取り消します。HEADでcancelするだけではこの応答が失われ、checkpoint barrierやRecovery Freeze中に壊した・置いたblockがClient側に残り続けます（ゴーストブロック）。AntiCrashは拒否時に同じ訂正を送ります。

HEAD injectionはMinecraftがpacketをServer threadへ移す前のNetty threadでも走るため、訂正の送信は`MinecraftServer.execute`でServer threadへ載せてからWorldを読みます。Server threadへ移った後にしか安全に判定できないcontainer判定も、その2回目の実行まで遅らせます。

AntiCrashがloginを拒否する状況（checkpoint barrier中、または`allow_login=false`のRecovery Freeze中）では、接続を即座に切らず`login.retry_timeout_seconds`（既定5秒）まで保留し、毎tick受け入れを再試行します。barrierは1秒未満で終わるため、ほとんどの場合Playerはそのまま参加できます。保留の対象はAntiCrashの拒否だけで、ban・whitelist・満員などVanillaの理由は保留されず従来どおり即座に適用されます。超過した場合も従来と同じkick理由になります。

checkpoint barrierが拒否するのは**Worldを変更し得るpacketだけ**です。`onUpdatePlayerAbilities`はVanillaでは`player.getAbilities().flying`へ代入するだけでWorldに触れないため、barrierでは拒否しません。以前は拒否していたため、server側の`abilities.flying`が古いまま残り、その値を読むCarpetの`creativeNoClip`が効かなくなることがありました。チャットもbarrierでは素通しし、コマンドは`CommandManager`側のpolicyが理由付きで拒否します（無言で握り潰さないため、barrier中に実行したコマンドが「効かない」ように見えません）。

Recovery中もNetwork KeepAliveを妨げず、既定ではLogin、Chat、Command、Movement、Look、Sneakを許可します。Block Break/Place、Item Use、Entity Attack/Interaction、Container、Drop、Pickupは既定で禁止です。`allow_login=false`なら新規接続を拒否し、`allow_commands=false`ならnetwork commandを止めます。

## crash-raw

AUTOでrollbackする前に、rollback前のWorldを`.anticrash/crash-raw/<run-id>/world`へ保存します。保存失敗後にrollbackを続けるとabort先を失うため、`keep_crash_raw_world=true`では保存に失敗した時点でfail closedにします。新規crash-rawは全file checksum、`checksums.sha256`、そのhashを持つ`READY`が揃ったものだけ利用できます。integrity metadata導入前のlegacy rawや破損rawはabort先にしません。`/ac recovery abort`はlive Worldをその場で再書換えせず、検証済みcrash-rawを次回起動前に戻すpending requestとして保存します。

## Clean shutdown

**通常の`stop` → 起動は、Vanillaの再起動と同じです。** Vanillaは停止時に「次のtickで処理するBlock Event」を保存しないため、動作の途中だった飛行式装置などは再起動後に止まることがあります（AntiCrashなしでも同じ）。AntiCrashがBlock Event、tick順序、ticketなどを戻すのは、checkpointを**復元**したときだけです。

装置を動かしたまま再起動したいときは、`policy = "ASK"`で起動時の質問に`Y`と答えるか、停止の代わりに次の手順を使います。

```text
/ac checkpoint now
/ac checkpoint restore latest     ← Serverが停止する。起動し直すと、そのcheckpointから再開する
```

復元を伴うので、起動には通常より時間がかかり（World fileのcopyとChunkの準備）、`require_manual_start = true`なら`/ac start`が必要です。checkpointを切ってから`restore`までの数tickは巻き戻ります。

正常終了のあと、AUTOとMANUALは次回起動時にRollback・候補完全検証・質問・crash-raw保存を行わず通常起動します。ASKは上の「ASK」のとおり質問し、無回答なら通常起動します。`create_on_clean_shutdown=true`の場合だけshutdown beginningでfinal checkpointを試みますが、`shutdown_timeout_seconds`（既定5秒）を超えて停止を遅らせません。final checkpointが失敗またはtimeoutしても、Vanilla shutdownを続け、normal shutdown markerはnormal終了として扱います。

## トラブルシューティング

1. `/ac status`でstate、selected checkpoint、freeze、start validationを確認します。
2. `/ac list`または`/ac checkpoint list`で確定済みcheckpoint IDを確認します。この一覧表示自体は全世代をSHA-256検証しません。
3. `/ac inspect cp-XXXXXX`でbackend、tick、bytes、changed/reused、Block Event数を確認します。
4. `.anticrash/logs/anticrash.log`と`.anticrash/state/debug-dump.json`を確認します。
5. `RECOVERY_FAILED`で再試行を繰り返さず、Worldとcheckpointのコピーを保全してから原因を調査します。
6. `WAITING_FOR_CHUNKS`直後にOOMが出る旧buildでは、全loaded/entity-ticking cache（例: 2489 Chunk）を再常駐させ、`ServerChunkManager.addTicket`へticket level `33`/`31`をradiusとして誤って渡し、level `0`/`2`相当の広大な範囲を同期ロードしていた可能性があります。修正版はFuture-based APIを使い、`max_concurrent_loads`（既定4）件ずつ処理します。API radiusはFULL `33 -> 0`、block-ticking `32 -> 1`、entity-ticking `31 -> 2`です。STRICTではExpected domainを`max_preload_chunks`で切り捨てず、上限不足なら復旧を凍結します。ログの`対象`、`READY`、`future`、`Equivalence`、`unsupported`を確認してください。Java childのOOMは復旧ticket解放後にプロセス終了としてSupervisorへ渡されます。

## Checkpoint世代と復元整合性

`AUTO`のportable generationは、Logical Cut時点で独立file setと`generation-seal.json`を作ってからWorldを再開します。復元対象は`READY`、checksums、manifest、seal、world file metadataをすべて通過した世代だけです。親chainのmanifestが読めない場合はretentionも削除を止めます。旧checkpointにexact generation evidenceがない場合、OPTIMIZEDでは互換利用できますが、STRICTでは欠落をMATCH扱いしません。

復元後の検証では、Chunk Futureの完了だけでなく`/ac start`直前にもChunk Domain、Runtime fingerprint、Carpet/FakePlayer、checkpoint integrityを再検証します。差分を調べる場合は`/ac recovery verify --verbose`、`/ac debug equivalence --verbose`、`/ac debug checkpoint-performance last`とmanifestの`phaseDurationsMs`を同時に保存してください。

## Logical Delta checkpointの運用

低停止時間の通常経路は`[checkpoint.engine] mode = "LOGICAL_DELTA"`です。初回は通常の自動checkpointを全量freezeへ暗黙変換せず、`/ac checkpoint base`を明示して親となるBASEを作成します。その後は候補cache、mutation hook、Exact logical cut、workerのcopy-on-write materializationで世代を作ります。

`checkpoint.logical_fallback`が出た場合は、原因（candidate不足、generation baseline不足、scheduler uncertainty、persistent file、mutation中など）を解決するまでLogical Delta成功とは扱いません。設定されたfallbackが`PORTABLE_GENERATION`なら既存のbounded staged saveへ移り、どの経路でも確定できない場合は前回Valid checkpointを維持します。`/ac debug checkpoint-performance verbose`でcandidate bytes、eviction、logical delta件数、phaseを確認してください。

`/ac checkpoint compact`は確定済みの最新viewをLive Worldなしで新しいBASEへまとめるoffline処理です。compaction完了だけでは、保存前後のゲームTickやRuntime Capsuleが新しくなるわけではありません。実機でのTMC確認では、checkpoint ID、Logical Cutのserver tick、`/ac recovery verify --verbose`、`/ac debug equivalence --verbose`を同じ試験の証跡として保管してください。

## 再開直前の復元とDEGRADED

`/ac start`で状態が`RUNNING`になった直後、最初のGame Tickの前に、Block Entity tickerの順序、Entityの順序、期限つきChunk ticket（残り時間つき）を復元し、動作中ピストンの進行度をcheckpointの記録と照合します。checkpoint時の恒久ticketがすべて再現されていれば、AntiCrashの復旧用ticketもここで解除します。

復元のあと、復旧用ticketを解除した状態で、checkpointを切り出したときと同じ方法でRuntime stateを取り直し、カテゴリごとにcheckpointの記録と照合します。結果は1行で出ます。

```text
最初のtickの前にRuntime stateを照合しました: CHUNK_DOMAIN=MATCH, SCHEDULED_TICKS=MATCH, …, PORTAL_ENTITY_RUNTIME=MATCH 1/1, …, PISTON_RUNTIME=MATCH 16/16, FAKEPLAYER_USE_RUNTIME=MATCH 1/1, CONTAINER_SESSION=NOT_APPLICABLE
```

`MATCH n/n`のnは対象の件数です（例: 動作中のピストン16個）。checkpointにその記録がない場合は`MISSING_FROM_OLD_FORMAT`、開いたままのコンテナがあった場合は`CONTAINER_SESSION=UNSUPPORTED`になり、MATCHにはしません。`MISMATCH`と`UNSUPPORTED`はDEGRADEDとして残ります。同じ内容は`/ac status`にも出ます。

復元できなかった項目がある場合、Worldは再開しますが、`復旧が <category> で不完全（DEGRADED）です`を重要ログに出し、`/ac status`に次の復旧まで表示します。STRICTでは従来どおり開始前に止めます。詳細は[RUNTIME-STATE-AUDIT.md](RUNTIME-STATE-AUDIT.md)。
