# Runtime Qualification（実機での復旧検証）

AntiCrash 1.0.0の復旧が、**中断しなかった場合と同じsimulationを続けられるか**を、実際のMinecraft専用Serverで確認した記録です。unit testではなく、Vanilla / Fabricの実際のlifecycleで動かしています。

## 結論

**QUALIFIED**

- 対応済みとしている状態（ピストン、予定tick、Block Event、tick順序、ホッパー、醸造、End Gateway、ポータル、Entityの一時状態、ticket、ディメンションtimer、天候、トーチ、FakePlayer）で、復旧後の差分は0件でした。
- 残った差分は、文書化済みの非対応状態（下の「既知の差分」）だけです。
- Worldの破損、Entityの重複・消失、ポータルの往復、復旧用ticketの残留はありませんでした。

これは「JVM processの完全なsnapshot」を意味しません。非対応の状態は[既知の制限](#既知の制限)に列挙しています。

## 2026-10-05 再検証（不具合修正後）

本番Serverのlogとcheckpoint（閲覧のみ）およびsourceの精査で見つかった不具合を修正し、同じ方法で検証し直しました。修正内容は[CHANGELOG.md](../CHANGELOG.md)の「Unreleased - 2026-10-05」にあります。下の「環境」以降は2026-10-01時点の記録で、jarのhashと実行番号はそのときのものです。

| 項目 | 値 |
|---|---|
| Jar | `build/libs/AntiCrash-1.0.0.jar` |
| SHA-256 | `1c30b311c3cc631373f684abfc3c763cf512197141ae6bd6ad64b9e8d3e55a4f` |

検証の大半（22 run、stress、再起動の確認）は1つ前のbuild `f1a10865…`で行い、最終buildはそこから設定fileのコメント文だけが違います。最終buildでは通常4 run・追加Scenario 1 run・下の起動/再起動の確認をやり直しました。

| 内容 | Runs | 結果 |
|---|---|---|
| 通常run（70 tick） | 12 + 4 | 差分は既知の2種（非Item Entityの`age`、`unknown` ticket）だけ。飛行式装置3台が動作中のcutを含む（最大22個のピストンが動作途中） |
| 追加Scenario（90 tick。Item cooldown、POST_TELEPORT、再搭乗cooldown、**トロッコに乗ったBot**） | 5 + 1 | 同上 |
| 長時間run（340 tick） | 2 | 同上（+unload待ちのholder） |
| **大量のdirty chunk**（cutの2 tick前に約500 chunkを変更） | 2 + 3 | 同上。書き込み待ち0回、切り出し59〜103 ms、復旧後のblockはすべて一致 |
| コンテナを開いたまま | 1 | `CONTAINER_SESSION=UNSUPPORTED`とDEGRADEDを表示（既知の非対応） |
| Worldを開く前に失敗した起動（`qualification/failed_start.py`） | 1 | 正常停止のあとに2回失敗しても次の起動はclean。crashのあとに失敗した場合はdirtyのまま復元される |
| checkpoint → `/ac checkpoint restore latest` → 起動（`qualification/clean_stop.py`） | 1 | crash扱いにならず復元され、装置は動き続ける。照合は全カテゴリMATCH |

再開直前の照合は、コンテナrunを除く全runで全カテゴリMATCH（対象なしはNOT_APPLICABLE）でした。retentionは`retention_count = 3`にして毎commitで働かせ、`parentFilesRehashed`は常に0でした。

この再検証から、runの間も含めてすべての実行を強制終了（crash）で終えています。通常の停止は「次のtickのBlock Event」を捨てる（Vanillaの動作）ため、runの合間の停止で飛行式装置が止まり、その後のrunが装置を検証できていなかったためです。各runは前のrunのcheckpointからの復旧で始まるので、復旧が連続しても装置が動き続けることも同時に確認しています。

### 通常の停止と再起動について

`stop`してから起動する通常の再起動では、AntiCrashはRuntime stateを戻しません（[RECOVERY.md](RECOVERY.md)の「Clean shutdown」）。動作途中の装置を止めずに再起動するには、`/ac checkpoint now`のあと`/ac checkpoint restore latest`を使います。

## 環境

| 項目 | 値 |
|---|---|
| Jar | `build/libs/AntiCrash-1.0.0.jar` |
| SHA-256 | `f77128f8d92923020624a158faf54a9a4495d22bf62430214c066f9c2c3bbf96` |
| Runtime Capsule | v7 |
| Minecraft | 1.18.2（専用Server） |
| Fabric Loader | 0.17.3 |
| Java | 21 |
| Memory | `-Xms1G -Xmx2G`、cgroup上限4 GB（swapなし） |
| Mods | fabric-api 0.77.0+1.18.2、fabric-carpet 1.18.2-1.4.69、AntiCrash 1.0.0、検証用probe mod |
| World | flat、view-distance 4、simulation-distance 4、`doMobSpawning=false`、`randomTickSpeed=0`、`doWeatherCycle=false`、`doFireTick=false` |
| AntiCrash設定 | `policy = "AUTO"`、`fidelity = "OPTIMIZED"`、`require_manual_start = false`、`preload_mode = "ALL"`、定期checkpointなし（検証用に手動で切る） |
| 場所 | `build/runtime-qualification/env`（プロジェクト内の隔離環境。本番のfileへのsymlinkなし、DB接続なし、port 25620） |

このSHA-256は、最後の`./gradlew build`の成果物と同じです（検証後にjarは変わっていません）。古いjar `7bed6f87…`は使っていません。

## 方法（Control vs Recovery）

1回の検証（run）は、同じWorldから始める2つの実行でできています。

1. **Control**: Serverを起動し、決まったgame tickにコマンドを実行するscriptで装置を動かす。tick `cp`でcheckpointを切り、**そのまま止めずに**N tick進めてから、JVMを`Runtime.halt`で強制終了する（crashの再現）。
2. **Recovery**: Serverを起動する。AntiCrashがdirty exitを検出し、そのcheckpointから自動復旧して再開する。同じscriptの続きが同じgame tickに実行され、同じN tickを進める。

probe modが、checkpoint以降のすべてのgame tickの終わりにWorldの状態をtextへ書き出し、2つの実行を**tickごとに全行比較**します。比較する内容:

- 対象範囲の全block（state込み）、全Block EntityのNBT、Block Entity tickerの順序
- 全EntityのNBT、Entityのtick順序、`timeUntilRegen`、`age`
- 予定tick（block / fluid、trigger tick、priority、sub-tick順）、Block Event queue、動作中ピストンの順序と進行度
- Chunk ticket（type、level、残りtick）、Chunkごとのlevel / tick状態、Dimensionごとの集計
- ChunkのInhabitedTime、トーチの焼き切れ履歴
- Worldの時刻、ディメンションの無人timer、天候の遷移値
- FakePlayerの位置、使用中アイテムの残り時間、攻撃cooldown、Item cooldown、体力

NBTはkey順に正規化し、network idやUUIDのように再起動で変わってよい値は比較から外しています。checkpointを切るtickはrunごとに変え、装置のさまざまな位相で切っています。

これとは別に、AntiCrash自身が再開の直前にRuntime stateを取り直してcheckpointと照合します（[RECOVERY-EQUIVALENCE.md](RECOVERY-EQUIVALENCE.md)）。その結果も全runで記録しています。

検証scriptは`qualification/`、probe modは`src/probe/`にあります。

## 事前レビュー

| 項目 | 結果 |
|---|---|
| `lastMobSpawningTime` | `ServerWorld.tick`は毎tick`chunkManager.tick(…, true)`を呼び、`tickChunks`は最初に`lastMobSpawningTime = world.getTime()`を代入する。したがってtick境界では常にWorld時間と等しく、復元時にWorld時間を入れるのは近似ではなく厳密。実機ではInhabitedTimeの行が全runで一致 |
| 復旧用ticket | 再開の直前に、checkpoint時の恒久ticket（player / forced / start）がすべて再現されていることを確認してから解除する。解除**後**にChunk domainを取り直して照合する（全runで`CHUNK_DOMAIN=MATCH`）。再現されるまで再開を待つ（最大200 server tick）。再現できなければ解除せずDEGRADEDにする |
| ポータルの往復（旧jarの不具合） | 拡張NBTは「読み込まれたまま保存されるEntity chunk save」の中でしか書かない。`Entity#copyFrom`（ディメンション移動）では書かれない。unit test 3件（scope、thread、全`writeNbt`注入の検査）と、下のポータルScenarioで確認 |
| 新しいMixin | 実Serverで全Mixinが適用されることを`MixinEnvironment.audit()`で確認。起動logにMixinの警告なし |

## 結果

最終jarでの実行: 通常run 12回（各70 tick）、長時間run 3回（各340 tick）、追加Scenario run 5回（各90 tick）、コンテナrun 2回。それ以前の48回は、検証環境とfixtureの調整、および下の「検証で見つけて直した不具合」の修正に使いました（修正のたびにjarが変わるため、合格の根拠には数えていません）。

| Scenario | Runs | Result | First mismatch | Classification |
|---|---|---|---|---|
| A 単体ピストン（伸縮を繰り返す） | 20 | PASS | - | - |
| B 飛行式装置（2 piston slime engine、東向き） | 20（うち動作途中でcut 11） | PASS | - | - |
| C 多数ピストン（重い飛行式装置、同時に最大11個が動作中） | 20（動作途中でcut 11） | PASS | - | - |
| D ホッパー環 2組（配置順が逆、cooldown 1〜8の全位相） | 20 | PASS | - | - |
| E 醸造台（残り0〜355 tickでcut、完了まで比較3回） | 20 | PASS | - | - |
| F End Gateway（cooldown中にcut、cooldown中に来たItemが待つ） | 20 | PASS | - | - |
| G ネザーポータル: 転送待ちのEntityがいるtickでcut | 20 | PASS | - | - |
| G' ポータルの往復なし（転送後に戻らない） | 20 | PASS | - | - |
| H ポータルticket（PORTAL、残り299で保存、同じtickに失効） | 20（失効まで比較3回） | PASS | - | - |
| I 短命ticket POST_TELEPORT | 5 | PASS | - | - |
| I' 短命ticket UNKNOWN | 20 | 既知の差分 | +1 | WORLD_RNG_EFFECT |
| J スカルクセンサー（active / 待機中の振動） | 20 | PASS | - | - |
| K FakePlayerの使用中アイテム（弓を引いたまま） | 20 | PASS | - | - |
| K' FakePlayerの攻撃cooldown | 20 | PASS | - | - |
| K'' FakePlayerのItem cooldown（エンダーパール） | 5 | PASS | - | - |
| L TNT（落下中・Fuse進行中） | 20 | PASS | - | - |
| M 落下block | 20 | PASS | - | - |
| N Item（氷上を滑走中、静止中、cut後に新しく生まれたItem） | 20 | PASS | - | - |
| O 経験値オーブ（重なったオーブのmerge） | 20 | PASS | - | - |
| P ダメージ無敵時間（溶岩の中のゴーレム、regen 11〜19でcut） | 20 | PASS | - | - |
| Q 再搭乗cooldown（ボートから降ろされたhuskが60 tick後に乗り直す） | 5 | PASS | - | - |
| R 無人ディメンションのtimer（300を越えるまで比較） | 20（越えるまで3回） | PASS | - | - |
| S 天候の遷移（rainGradient 0.26〜0.70でcut、雨・晴れ両方向） | 20 | PASS | - | - |
| T InhabitedTime / `lastMobSpawningTime` | 20 | PASS | - | - |
| U トーチの焼き切れ履歴（3〜9件） | 20 | PASS | - | - |
| V Entityのtick順序 | 20 | PASS | - | - |
| W Block Entity tickerの順序 | 20 | PASS | - | - |
| X 予定tick / Block Event | 20 | PASS | - | - |
| Y Chunk domain（復旧用ticket解除後） | 20 | PASS | - | - |
| Z 開いたままのコンテナ（トラップチェスト） | 2 | 既知の差分。`CONTAINER_SESSION=UNSUPPORTED`とDEGRADEDを表示 | +5 | EXPECTED_UNSUPPORTED |
| 非Item Entityの`age` | 20 | 既知の差分 | +0 | EXPECTED_UNSUPPORTED |
| unload待ちのChunk holder | 3（長時間runのみ） | 既知の差分 | +305 | TEST_NONDETERMINISM |

20 = 通常12 + 長時間3 + 追加5。すべてのrunで、AntiCrashの再開直前の照合は全カテゴリ`MATCH`（対象がないときは`NOT_APPLICABLE`）で、DEGRADEDは0件でした（コンテナrunを除く）。

### Scenarioの補足

- **飛行式装置**: cutの位相はrunごとに違い、`progress`が0.5のとき、1.0（到着直前）のとき、動作の合間（予定tickだけがある）のときを含みます。西向きの3台目は検証の途中で自分の構造上の理由で止まりました（ControlとRecoveryで同じtickに止まっており、復旧とは無関係）。東向きと重い装置で必要回数を満たしています。
- **ポータル**: Itemを6個、ポータルに1 tickずつずらして届くように投げ、cutのtickにちょうど1個が「触れたが、まだ転送されていない」状態になるようにしました。全runで、そのItemはControlと同じtick（+1）に転送され、残りも+2、+3、+4に転送され、ネザー側に着いたあと戻りませんでした。
- **End Gateway**: 複数のEntityが同時にGatewayにいると、どれを転送するかをWorldの乱数で選びます。これは非対応のWORLD_RNGなので、fixtureは常に1個だけが待つようにしています。
- **ホッパー**: `lastTickTime`は保存していませんが（[監査](RUNTIME-STATE-AUDIT.md)で不要と判断）、2組の環のcooldownと中身は全tickで一致しました。

## 検証で見つけて直した不具合

差分を見つけるたびに、最初にずれたtickを特定し、原因を確認してから直し、regression testを足して実機で再検証しました。

| 見つかった差分 | 原因 | 修正 | Regression test |
|---|---|---|---|
| ChunkのInhabitedTimeが古い | Vanillaは毎tick加算するがChunkを保存対象にしない | Runtime Capsuleに全読み込み済みChunkの値を保存して戻す | `RuntimeCapsuleV7FileTest`、実機の`CHUNK_RUNTIME`照合 |
| 復旧用ticketが再開後も残る | FakePlayerのplayer ticketが作られる前に再開していた | 恒久ticketが再現されるまで再開を待つ | `RuntimeOrderRestorerTest` |
| FakePlayerの攻撃cooldownが0に戻る | `PlayerEntity.selectedItem`が空で、最初のtickに「持ち替え」と判定される | 復元時にmain handの内容を入れる | 実機の`FAKEPLAYER_USE_RUNTIME`照合 |
| 重なった経験値オーブのmergeが変わる | merge条件がnetwork idの差（% 40）で、idは再起動で振り直される | idの余りを保存し、この比較だけ保存値を使う | 実機（Scenario O） |
| cut後に生まれたItemの動き出すtickが変わる | network idのcounterがJVMごとに0から始まる | counterをcheckpoint時と同じ余り（% 40）に進める | `RuntimeCapsuleV7FileTest`、実機の`ENTITY_ID_PHASE`照合 |

## 既知の差分

| 差分 | 分類 | 説明 |
|---|---|---|
| Item・経験値オーブ以外のEntityの`age` | EXPECTED_UNSUPPORTED | 保存しない。全Entityに保存すると、checkpointのたびに全Entity chunkを書き直すことになる。Mob AIの周期などに影響 |
| `unknown` ticket | WORLD_RNG_EFFECT | 雨のとき、Vanillaはtick中のChunkごとに乱数で位置を選んで高さを調べ、その読み出しが1 tickのticketを作る。どのChunkに付くかがWorldの乱数で決まる。checkpoint時点のticketは復元している（`SHORT_LIVED_TICKETS=MATCH`） |
| 開いたままのコンテナ | EXPECTED_UNSUPPORTED | 閲覧者数は復元できない。トラップチェストの信号は復旧後に切れる。checkpointに記録し、復旧時に`UNSUPPORTED`とDEGRADEDを出す |
| unload待ちのChunk holder | TEST_NONDETERMINISM | 最後のticketが失効したChunkは、level −1（ゲームからは見えない）のまま、保存の完了を待ってmapから外れる。そのtickが1 tickずれることがある。失効したtickとChunkの内容は一致 |

## 既知の制限

次の状態は復元しません。該当する場合は`UNSUPPORTED`またはDEGRADEDとして表示されます。

- **WORLD_RNG**: Worldと各Entityの乱数の内部状態。乱数に依存する動作（Mobの湧き、作物、ドロップの散らばり、複数Entityからの選択など）は中断しなかった場合と同じにはなりません。
- **Mob AI**: goal、path、target、Item・オーブ以外の`age`。
- **Container session**: 開いたままのコンテナ、閲覧者数。
- **LIGHT ticket**、lightingの内部queue。
- 非同期workerの内部状態、thread、OS / JVMの状態。

検証していない範囲:

- `fidelity = "STRICT"`での実機run（今回の対象は既定の`OPTIMIZED`）。
- 巣箱、雷の遷移値、シュルカーボックス（実機fixtureなし。巣箱とシュルカーは監査とunit testの範囲）。
- Mobの湧きと`randomTickSpeed`（乱数に依存するため、厳密な比較の対象から外した）。
- ほかのModが追加するBlock Entity / Entity。
- 大規模World（検証Worldは約720 chunk）。

## 性能

検証Serverでのcheckpoint（720 chunk、Entity約220）: 停止時間（barrier）45 ms、うち44 msはtick境界の待ち、論理的な切り出しは10 ms。今回追加した`runtime.extensions`の段階は0 ms（1 ms未満）、Runtime Capsuleは約0.43 MBでした。追加の照合カテゴリは件数とdigestだけを保存するため、capsuleはEntity数に比例して大きくなりません。

## Release blocker

なし。

## 再現方法

```bash
./gradlew probeJar build
python3 qualification/rebuild.py          # 飛行式装置を初期位置へ戻す
python3 qualification/qualify.py 49 12    # 通常run
python3 qualification/qualify.py 61 3 340 # 長時間run
python3 qualification/qualify.py 64 2 70 container
python3 qualification/qualify.py 68 5 90 extras
python3 qualification/report.py 49
```

環境（`build/runtime-qualification/env`）は、Serverのlibraryと上記のmodを置いた専用Serverのdirectoryです。本番Serverには一切触れません。
