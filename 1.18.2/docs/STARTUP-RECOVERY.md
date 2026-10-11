# Pre-World Recovery Decision

## Candidate discoveryとparent trust

manifestのworldFilesは多数になるため、startupは16 MiB上限のstreaming readerでidentity/timeだけ読み、残りをskipする。
DISCOVEREDはunverified。Y/AUTOのrestoreにはfull integrity verificationが必須。
clean/Nの通常起動は全Worldのparent hash/prehashを強制せず、最初のcheckpoint request時にlazy検証する。
同一processの完全なfile revisionに限りauthenticationを最大2件共有し、restart後は再認証する。
INVALID parentは永続quarantineし、次の周期に同じ親を再検証し続けない。
詳細は[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)。

## 目的

AntiCrashの起動復旧は、MinecraftがWorldを開いた後にrollbackを予約する方式ではなく、World load前に復旧方針を確定する。これにより、ASKの判断前にLive Worldを読み込んだり、復元途中のWorldをMinecraftが開いたりすることを防ぐ。

対象はMinecraft 1.18.2、Yarn `1.18.2+build.4`のDedicatedServer lifecycleである。Fabric entrypointは`MinecraftDedicatedServer.setupServer()`より前に実行されるため、AntiCrashはこの段階で軽量な起動計画を作る。

## 実際の1.18.2起動順序

Mapped source/merged bytecodeで確認した順序は次のとおり。

```text
MinecraftServer.startServer
  -> new server / Server thread
  -> Fabric entrypoint: AntiCrashMod.onInitialize
     -> AntiCrashController constructor
        -> recovery-state / run marker / pre-world decision
  -> MinecraftDedicatedServer.setupServer
     -> start daemon "Server console handler"
     -> bind network and configure PlayerManager
     -> MinecraftServer.loadWorld
  -> first server tick
```

この順序を利用し、pre-world ASKはDedicatedServerの通常console readerが作られる前に完了させる。AntiCrashはstdinに2つ目の`BufferedReader`を作らず、raw `InputStream`を短時間だけpollする。Y/Nを受け取った後、またはtimeout・EOF・I/O失敗で確定した後にreader/threadを残さない。通常console readerが開始した後のMinecraft command入力を先取りしないことが目的である。

## 起動計画

`StartupRecoveryPlan`はpre-worldの判断結果を不変オブジェクトとして表す。

| decision | 意味 |
|---|---|
| `CONTINUE_CURRENT_WORLD` | Worldを変更せず通常loadする。clean bootとASK dirtyのNが該当する |
| `RESTORE_CHECKPOINT` | 選択checkpointを完全認証し、transactional staging restore後にWorld loadする |
| `RESTORE_CRASH_RAW` | 明示的abortなどでcrash-rawを復元後にWorld loadする |

`PreviousExit`は`CLEAN`、`WATCHDOG`、`OUT_OF_MEMORY`、`CRASH`、`UNKNOWN`に分類する。分類は復元可否の根拠であり、MinecraftのCrash Reportにない原因を推測して正常終了扱いにはしない。

## Policy別の処理

### Clean boot

run markerはModの初期化中、Worldを読み込むずっと前に書かれる。そこでJVMが終わった起動（ほかのModが初期化に失敗した、portが使用中だった等）はWorldに何も書いていないので、その起動は「前回の終了状態」の判定に使わない。markerには`worldOpened`（World読み込み開始で`true`）と、その起動が始まった時点のWorldの状態を記録し、Worldを開かずに終わった起動を何回挟んでも、最後にWorldを開いていた起動の終了状態（clean / dirty）がそのまま引き継がれる。以前は、正常停止のあとにほかのModの設定errorで起動に失敗すると、次の起動が「異常終了」と判定され、正常に保存されたWorldのrollbackを提案していた。

clean markerの場合、AUTOとMANUALは高速な通常起動を行う。ASKだけは、候補のmetadataを読んでpre-worldで質問する（`Y`ならdirty ASKの`Y`と同じ手順で復元、`N`・無回答・入力不可なら通常起動）。以下は通常起動の場合である。候補checkpointの完全検証、crash-raw保存、ASK表示、Runtime Capsule復元、Recovery Freezeは行わない。正常終了したWorldを毎回checkpointへ戻さないための明示的なfast pathである。

### AUTO dirty

active timelineの候補をmetadataで列挙した後、最新候補から完全検証する。選択が確定してからのみ次を実行する。

```text
PREBOOT_INSPECT
  -> PREBOOT_PRESERVE_RAW
  -> PREBOOT_VERIFY
  -> PREBOOT_RESTORE
  -> RESTORING
  -> DedicatedServer.setupServer continues
  -> loadWorld
```

restore失敗時は設定で許可された認証済みparent/timeline候補だけへfallbackする。候補が全て無効、crash-raw保存失敗、transaction復旧不能の場合は`setupServer()`をfalseで終了し、Worldを開かない。

### ASK dirty

ASKはWorld load前に候補IDだけを表示する。`READY`とmanifestのcheckpoint ID、timestamp、bounded metadataを読むが、World payloadのhashやRuntime Capsuleの完全検証は行わない。候補は次のように表示される。

```text
【AntiCrash ASK / PRE-WORLD】前回の終了状態: UNKNOWN_DIRTY_EXIT。
未検証候補 checkpoint cp-000123 を復元しますか？
Y/NをServer consoleで入力してください。
```

* Y: crash-rawを保全し、完全verify、staging restore、commitを同じJVMで実行してからWorldを初回loadする。
* N: restore、crash-raw、freezeを行わず、現在のWorldを通常loadする。これは等価性を承認する操作ではない。
* timeout: `ask_timeout_seconds`を参照し、dirty ASKではYとして扱う。
* EOF/I/O unavailable: ASKでは安全側にY相当の復元を試みる。復元できなければWorld loadを拒否する。
* candidateなし: ASKは復元不能をログへ出して通常loadする。MANUALはWorld loadを拒否する。

Y後に初回World loadを行うため、ASK Yのための2回目のprocess restartは発生しない。pre-worldの質問にはゲーム内Playerや`/ac y`/`/ac n`を使用しない。World load後に質問する設計へ戻すと、既に開かれたRegionFileとLive Worldを安全にrollbackできないためである。

### MANUAL dirty

MANUALは自動rollbackしない。候補metadataを表示してY/Nを受けるが、入力なし、stdin unavailable、候補なしの場合はWorldを開かず`RECOVERY_FAILED`として停止する。これは「管理者の判断が必要な状態」を通常起動と取り違えないためのfail-closed境界である。

## World restore transaction

`WorldRestorer`はWorldのregular fileをliveへ一つずつ置換しない。処理は次のとおり。

```text
verify checkpoint
  -> restore-work/<transaction>/world を作成
  -> checkpoint payloadをstageへcopy/hash
  -> 除外対象(playerdata/stats/advancements/session.lock)をliveから明示的に引継ぎ
  -> stage treeを検証
  -> restore-transaction.json = PREPARED をforce
  -> live Worldを同一filesystem内のbackupへATOMIC_MOVE
  -> marker = COMMITTING をforce
  -> stage Worldをlive名へATOMIC_MOVE
  -> marker = COMPLETE
  -> backup、stage、markerを削除
```

`ATOMIC_MOVE`が使えない場合は非atomicなfile-by-file fallbackを行わずrestoreを失敗させる。プロセスがrenameの間で停止した場合、次回の`WorldRestorer`初期化時にtransaction markerを検証する。

* liveが旧World、backupが存在: stageをliveへ再開してcommitを完了する。
* liveが新World、backupが存在: 新Worldを採用し、backupだけを削除する。
* liveが欠落、backupが存在: backupをliveへ戻す。
* liveが欠落、stageが存在: stageをliveへ移す（元々liveが存在しなかったケース）。
* marker、stage、backupがwork root外またはsymlink: fail closed。

この復旧処理が終わるまでDedicatedServerのWorld loadへ進ませないため、restore途中の混在Worldを通常起動しない。

## 状態と拒否境界

| State | World load | 意味 |
|---|---:|---|
| `NORMAL` | 許可 | 通常起動 |
| `PREBOOT_INSPECT` | 未許可 | dirty原因・候補metadataを調査中 |
| `PREBOOT_DECISION` | 未許可 | ASK/MANUALのY/N待ち |
| `PREBOOT_PRESERVE_RAW` | 未許可 | dirty Worldの保全中 |
| `PREBOOT_VERIFY` | 未許可 | checkpoint完全認証中 |
| `PREBOOT_RESTORE` | 未許可 | staging transaction中 |
| `RESTORING` | 許可 | disk restore完了後、Runtime復元へ進む |
| `RECOVERY_FAILED` | 拒否 | 起動計画または復元を安全に確定できない |

`MinecraftDedicatedServerMixin`は`setupServer()`の先頭で`allowWorldLoadBeforeServer()`を確認する。falseの場合はconsole reader、network bind、`loadWorld()`より前にsetupを終了する。このため、復元失敗時に一度Worldを開いてから止めることはない。

## 通常起動後のRecovery

restore後だけはWorld load returnでRuntime Capsule、Block Event、scheduled tick、moving piston、BlockEntity ticker、Carpet/FakePlayer状態を適用し、Future-based Chunk準備をserver loopへ分割する。Main ThreadでFutureを`.join()`せず、`max_concurrent_loads`で同時load数だけを制限する。準備完了後はSTRICTならEquivalenceを再検証し、`/ac start`直前にも再検証してから最初のGame Tickを許可する。

pre-world Nで通常起動したWorldには、復元用の`/ac start`待ちを作らない。Nは「現在Worldをそのまま開く」という判断であり、別のcheckpoint復元を行う場合は`/ac recovery restore`などの明示的pending restoreを使用する。

## 診断ログ

次のphaseログで、長い起動や復元がどこで止まったかを確認できる。

```text
startup.candidate_discovery
startup.decision_wait
startup.crash_raw
startup.checkpoint_restore
startup.world_load
```

各phaseは`startup.phase`として経過msを出す。pre-worldの候補検出時間とY/N待機時間を分離するため、質問前のFabric/Minecraftログを回答timeoutへ算入しない。

## 手動確認項目

実サーバーでの確認はユーザーが実施する。AntiCrash側のUnit/Fake testとbuildは実サーバーの動作保証ではない。

1. clean `/stop`後にASKでも質問、crash-raw、完全verifyが出ず、通常Worldが一度だけloadされること。
2. dirty終了後にWorld load前のconsoleへcheckpoint IDが表示され、Nで通常起動、Yで同じJVMのpre-world restoreになること。
3. timeoutが`recovery.ask_timeout_seconds`を使い、dirty ASKではYになること。
4. Y後に`startup.crash_raw`、`startup.checkpoint_restore`、`startup.world_load`が出ること。
5. 復元失敗やtransaction marker不整合時にWorld loadされず、理由と再試行方法が表示されること。
6. 復元後に`/ac recovery verify --verbose`、`/ac debug equivalence --verbose`、`/ac status`で差分とblocking categoryを確認すること。
7. Supervisorを使用する場合だけ、明示的controlled restartのchild PIDと同じconsoleのstdin継承を確認すること。pre-world ASK Y自体はSupervisorの再起動を要求しない。
