# Minecraft 1.18.2 Runtime State 監査（Runtime State Audit）

AntiCrashの目的は「Worldファイルが戻ること」ではなく、**checkpoint直後の次のGame Tickが、Crashしなかった場合の次のGame Tickと同じsimulationから始まること**です。この文書は、Minecraft 1.18.2（Yarn 1.18.2+build.4）がsave/reloadで保存しないRuntime Stateを一覧にし、それぞれを保存するか・しないか・できないかを決めた正式な仕様です。

判断の条件は次の3つがすべて成り立つことです。

1. Vanillaの再起動で失われる
2. 未来のServer simulationを変える
3. 安全に復元できる

fieldが存在することも、Vanillaが保存しないことも、それだけでは保存する理由になりません。

## 分類

| 分類 | 意味 |
|---|---|
| PERSISTED_BY_VANILLA | VanillaのNBT等で保存される。AntiCrashは二重に保存しない |
| DERIVED_ON_LOAD | 保存されないが、保存済みの状態から同じ意味の値に作り直される |
| CLIENT_ONLY / VISUAL_ONLY | Server simulationに影響しない |
| TICK_LOCAL | tick境界をまたいで意味を持たない |
| RESTORABLE_TRANSIENT | 失われ、simulationを変え、安全に戻せる。AntiCrashが保存・復元する |
| OBSERVE_ONLY | 記録・表示はできるが、安全に戻す方法がない |
| UNSUPPORTED | 復元しない。影響を明記する |

UNKNOWN（未調査）は**0件**です。`RuntimeStateAuditCoverageTest`が、compile対象のMinecraft jarから全BlockEntity classと下記Entity classのinstance fieldを読み、この文書の元データ（`src/test/resources/runtime-audit/*.tsv`）と突き合わせます。Minecraftの更新でfieldが増えると、判断を追加するまでtestが失敗します。

## 監査範囲

- BlockEntity: 全38 class（抽象classを含む）、141 field。内訳: CLIENT_ONLY 16、DERIVED_ON_LOAD 23、OBSERVE_ONLY 3、PERSISTED_BY_VANILLA 74、RESTORABLE_TRANSIENT 5、TICK_LOCAL 3、UNSUPPORTED 8、VISUAL_ONLY 9
- Entity: 装置に使われる13 class、151 field。内訳: CLIENT_ONLY 19、DERIVED_ON_LOAD 17、OBSERVE_ONLY 1、PERSISTED_BY_VANILLA 50、RESTORABLE_TRANSIENT 8、TICK_LOCAL 30、UNSUPPORTED 13、VISUAL_ONLY 13
- World / ServerWorld / ServerChunkManager、Chunk Ticket全種、Player / FakePlayer、Container session

## 今回見つかった保存漏れ（CRITICAL / HIGH / MEDIUM）

| State | Class | Vanilla | 影響 | AntiCrashの対応 | 復元時点 |
|---|---|---|---|---|---|
| ピストンの実際の進行度 | PistonBlockEntity.progress | `lastProgress`を`progress`として保存し、読み込み時に両方へ入れる | 動作中のピストンが半ステップ戻り、到着が1 tick遅れる。飛行式装置・Trencher・World Eaterが止まる | 拡張NBT `Progress`、`SavedWorldTime` | Chunk読み込み時（readNbt） |
| 醸造中の材料 | BrewingStandBlockEntity.itemBrewing | 保存しない | 読み込み後の最初のtickで「材料が変わった」と判定され、醸造が0からやり直しになる | 拡張NBT `ItemBrewing` | Chunk読み込み時 |
| End Gatewayのcooldown | EndGatewayBlockEntity.teleportCooldown | 保存しない | cooldown中のGatewayが再起動直後に転送する | 拡張NBT `TeleportCooldown` | Chunk読み込み時 |
| End GatewayのAge | EndGatewayBlockEntity.age | 保存するが、毎tick進むのにChunkを保存対象にしない | checkpointに古いAgeが残り、2400 tickごとのcooldown開始がずれる | 毎tick保存対象にする | — |
| 巣箱のTicksInHive | BeehiveBlockEntity.bees | 同上（保存するが保存対象にしない） | 蜂の退出が遅れる | 蜂がいる間は保存対象にする | — |
| スカルクセンサーの待機中の振動 | SculkSensorListener.event / distance / delay | 1.18.2は`last_vibration_frequency`だけ保存（listenerの保存は1.19から） | 信号が1回消える | 拡張NBT `Vibration`、待機中は保存対象にする | Chunk読み込み時 |
| ポータル転送待ち | Entity.inNetherPortal / netherPortalTime / lastNetherPortalPosition | 保存しない（`PortalCooldown`は保存される） | 触れ直してから1 tick遅れて転送 | 拡張NBT `Portal` | Entity読み込み時 |
| 読み込み直後の1 tick | Entity.firstUpdate / touchingWater | 保存しない。読み込んだEntityは1 tickだけ「新規」扱い | 最初のtickで`isInLava()`がfalse、入水時にSPLASH | 拡張NBT `Ticked`、`Water` | Entity読み込み時 |
| ダメージ無敵時間 | Entity.timeUntilRegen、LivingEntity.lastDamageTaken | 保存しない | 無敵時間が消え、次のダメージが早く入る | 拡張NBT `Regen`、`LastDamage` | Entity読み込み時 |
| 乗り物の再搭乗cooldown | Entity.ridingCooldown | 保存しない | 降りた直後のEntityがすぐ乗り直す | 拡張NBT `RidingCooldown` | Entity読み込み時 |
| ItemEntity / 経験値オーブのage | Entity.age | 保存しない（`Age`は別field） | Itemのmerge周期（age % 40）、静止Itemの移動判定、オーブの追尾周期がずれる | 拡張NBT `Age`（この2種のみ） | Entity読み込み時 |
| ItemEntityの移動判定位相 | (age + network id) % 4 | network idは読み込みごとに振り直される | 静止Itemが動き出すtickがずれる | 拡張NBT `IdPhase`。network id自体は変えず、この式だけ保存した位相を使う | Entity読み込み時 |
| 経験値オーブのmerge判定 | (相手のnetwork id − 自分のnetwork id) % 40 | network idは読み込みごとに振り直される | 重なったオーブのうちどれがmergeするかが変わる（実機検証で検出） | 拡張NBT `IdPhase`（オーブは40での余り）。比較だけ保存した値を使う | Entity読み込み時 |
| 新しいEntityのnetwork id | Entity.CURRENT_ID（static counter） | 保存しない（JVMごとに0から） | 復旧後に生まれたItem・オーブのidの余りが変わり、上の2つの判定がずれる（実機検証で検出） | Runtime Capsule v7 `entityIdCounter`。counterを「現在値以上で、checkpoint時と40での余りが同じ値」に進める。idの一意性は変わらない | 最初のtickの直前 |
| ChunkのInhabitedTime | Chunk.inhabitedTime | 保存するが、毎tick進んでもChunkを保存対象にしない | checkpointのChunkデータには最後に保存された時点の古い値が残る。地域難易度に影響（実機検証で検出） | Runtime Capsule v7 `inhabitedTimes`（読み込み済みChunkすべて） | World読み込み後、凍結中 |
| FakePlayerの選択中アイテム | PlayerEntity.selectedItem | 保存しない（空から始まる） | 最初のtickで「持ち替えた」と判定され、攻撃cooldownが0に戻る（実機検証で検出） | 復元時にmain handの内容を入れる | FakePlayer復元時 |
| Carpetの実行中のrule | carpet.settings.ParsedRule（Carpet本体と各拡張） | `carpet.conf`にはdefaultにしたruleだけが保存される | `/carpet <rule> <値>`で変えたruleが復旧後に元へ戻る。creativeNoClipが効かなくなる、装置が前提にしているruleが変わる | Runtime Capsule v7 `carpetRules`。Carpet自身のsetterで設定し直す | World読み込み後、凍結中 |
| ディメンションの無人timer | ServerWorld.idleTimeout | 保存しない | Playerも強制ロードもないディメンションは300 tickでEntityとBlockEntityが止まる。再起動で0に戻り、止まっていたものが300 tick動く | Runtime Capsule v7 | World読み込み後、凍結中 |
| 天候の遷移値 | World.rainGradient / thunderGradient | 保存しない（0か1にする） | 遷移中の明るさ・雨判定がずれる | Runtime Capsule v7 | 同上 |
| InhabitedTimeの加算基準 | ServerChunkManager.lastMobSpawningTime | 保存しない（0から始まる） | 再起動後の最初のtickで、tick中の全ChunkのInhabitedTimeにWorldの年齢がまるごと加算される | World時間に合わせる | 同上 |
| 短命のChunk Ticket | PORTAL / POST_TELEPORT / UNKNOWN | 保存しない | あと数tick読み込まれていたはずのChunkが読み込まれない | 残り時間つきで作り直す | 最初のtickの直前 |
| AntiCrash自身の復旧用ticket | — | — | 再開後も100 tick残り、unloadされるはずのChunkを保持していた | 恒久ticketが再現できていれば最初のtickの前に解除 | 最初のtickの直前 |
| tick順序 | World.blockEntityTickers、EntityList | 読み込み順に作り直される | どのホッパー・ピストン・TNTが先に動くかが変わる | 記録した順序に並べ直す（OPTIMIZEDでも） | 最初のtickの直前 |
| トーチの焼き切れ履歴 | RedstoneTorchBlock.BURNOUT_MAP | 保存しない | 焼き切れの判定が変わる | 復元（OPTIMIZEDでも） | 復旧準備時 |
| FakePlayerの使用中アイテム | LivingEntity.activeItemStack / itemUseTimeLeft | 保存しない | 弓の引き・食事・盾が0からやり直し | fakeplayers.json | FakePlayer復元時 |
| FakePlayerの攻撃cooldown | LivingEntity.lastAttackedTicks | 保存しない | 次の攻撃の威力が変わる | fakeplayers.json | 同上 |
| FakePlayerのItem cooldown | ItemCooldownManager | 保存しない | エンダーパール等がすぐ使える | fakeplayers.json（残りtick） | 同上 |

## 保存不要と判断したもの（主要なもの）

- **HopperBlockEntity.lastTickTime**: 書くのは`serverTick`の1か所（毎tick、移送の前に現在tickを入れる）、読むのはホッパー間の移送で受け側のcooldownを7にするか8にするかの比較1か所だけです。比較の意味は「相手はこのtickですでにtickしたか」で、読み込み後の初期値0は「まだ」を表し、tick境界では常に正しい値です。lazy chunkのホッパーも同じ結果になります。`HopperLastTickTimeTest`が全tick順序について、live値と読み込み後の値で判定が一致することを確認します。`transferCooldown`は別のfieldで、Vanillaが保存します（AntiCrashは保存対象にするだけ）。
- **PistonBlockEntity.lastProgress**: Vanillaが`progress`キーで保存します。AntiCrashは上書きしません。
- **Entity.portalCooldown**: Vanillaが`PortalCooldown`で保存します。
- **HopperMinecartEntity.transferCooldown / enabled、TNTのfuse、FallingBlockのtimeFalling**: Vanillaが保存します。
- **Conduit.active、Beacon.level**: World時間の剰余で決まるtickに再計算され、その同じtickで効果が適用されます。
- **Entity.horizontalCollision等**: 自分のmoveで書き直してから読まれます。

## 追加データの置き場所（Authority）

同じ状態を2か所が決めないようにします。

| 状態 | 正（Authority） | AntiCrashが足すもの |
|---|---|---|
| ピストン | Chunk NBTの`progress`（= lastProgress）、blockState、facing、extending、source | 拡張NBTの`Progress`、`SavedWorldTime`。Runtime Capsuleのtick順序。Capsuleの進行度は照合専用で、書き戻さない |
| 予定tick | Chunk NBT | Runtime Capsuleは順序（subTickOrder）だけを戻す。二重登録しない |
| ポータル | Entity NBTの`PortalCooldown` | 拡張NBTの`Portal`（Vanillaが保存しない3 fieldだけ） |
| スカルク | BlockEntity NBTの`last_vibration_frequency` | 拡張NBTの`Vibration`（Vanillaが保存しない3 fieldだけ） |
| Ticket | Player・forced・start・dragonはVanillaが作り直す | 期限つきticketの残り時間 |

拡張NBTは`AntiCrash`という1つのcompoundで、`v`（現在1）を持ちます。新しい`v`のデータは推測せず無視します。AntiCrashなしでWorldを開いた場合、Vanillaはこのcompoundを無視します。

**書くのは「読み込まれたまま保存されるとき」だけです**（checkpoint、自動保存、停止時の保存）。VanillaがChunkやEntity sectionをunloadして保存し、後で読み込み直す場合、これらの状態が失われるのはCrashしなかった場合の本来の動作なので、拡張NBTは書きません。ディメンション移動（`Entity#copyFrom`）、`/data`、structure block、Client向けpacketにも入りません。

## Chunk Ticket

| Type | 期限 | 引数 | 自然に作り直されるか | 対応 |
|---|---|---|---|---|
| start | なし | Unit | される（World読み込み時） | 何もしない |
| forced | なし | ChunkPos | される（`chunks.dat`） | 何もしない |
| player | なし | ChunkPos | される（Player位置から、非同期） | 何もしない。FakePlayerは復旧中に配置される |
| dragon | なし | Unit | される（EnderDragonFightが毎tick管理） | 何もしない |
| portal | 300 tick | BlockPos | されない | 残り時間つきで作り直す |
| post_teleport | 5 tick | Entity id | されない | 残り時間つきで作り直す |
| unknown | 1 tick | ChunkPos | されない | 残り時間つきで作り直す |
| light | なし | ChunkPos | lighting engineが自分で追加・削除する | 作り直さない（削除されず残る危険がある）。UNSUPPORTED |

期限つきticketは、Vanillaがその種類に使うのと同じ`ChunkTicketManager`のmethodで追加し、`tickCreated`を「残りtick数が同じになる値」にします（`RuntimeOrderRestorerTest`）。内部Mapは置き換えません。自分で失効するので残りません。

AntiCrashの復旧用ticketは、checkpoint時の恒久ticket（player・forced・start・dragon）がすべて同じlevelで存在することを確認できた場合、最初のtickの前に解除します。確認できない場合（checkpoint時にいた実Playerが接続していない等）は従来どおり`temporary_ticket_release_ticks`の間保持し、復旧をDEGRADED（`TICKET_RUNTIME`）と表示します。

## 順序の復元の安全性

- **Entity**: `EntityList`の内部Mapを、同じMap・同じEntity instance・同じnetwork idのまま、挿入順だけ入れ直します。Entityの削除・再追加、network idの振り直し、`ServerEntityManager`・tracking・UUID検索の変更は行いません。反復中（`iterating != null`）は行いません。
- **BlockEntity ticker**: `blockEntityTickers`と`pendingBlockEntityTickers`を、同じinvoker instanceのまま並べ直します。削除済みinvokerはVanillaが次のpassで捨てるものなので順序を持ちません。同じ位置・名前のinvokerが重複していたら並べ直しません。
- 記録にないもの（復旧中に入ったPlayer等）は末尾に残します。記録にあって存在しないものがあれば、DEGRADEDと表示します。

## 復元の順序

```
Worldファイルを復元 → World読み込み（拡張NBTはここで各BlockEntity/Entityに戻る）
→ 凍結
→ 予定tickの順序番号、lcgBlockSeed、idleTimeout、天候の遷移値、lastMobSpawningTime、Carpet tick rate、Block Event
→ checkpoint時の全Chunkを当時のlevelまで準備
→ 動作中ピストンの順序、予定tickの順序、トーチの焼き切れ履歴
→ FakePlayer（位置・Inventory・使用中アイテム・cooldown・ActionPack）
→ READY_TO_START
→ /ac start
→ BlockEntity tickerの順序、Entityの順序、期限つきticket、復旧用ticketの解除、ピストン進行度の照合
→ 最初のGame Tick
```

## Container session（OBSERVE_ONLY）

チェストの閲覧者数（トラップチェストのレッドストーン出力）、シュルカーボックスの開閉は、Playerが開いている画面に属します。画面を開いているPlayerがいない状態で閲覧者数だけ戻すと、閉じられない「開いたまま」の状態になります。実Playerの接続は復元できず、FakePlayerの画面を開き直すとBlock Eventや隣接更新が新たに発生します。そのため復元せず、checkpoint時に画面を開いていたPlayerを`containerSessions`に記録し、`CONTAINER_SESSION`を非対応として表示します（STRICTでは開始を止めます）。再起動後、閲覧者数は5 tickごとの予定tickで0に更新され、トラップチェストの出力は消えます。

## UNSUPPORTED（復元しないもの）

| State | 影響 | 理由 |
|---|---|---|
| `World.random`、Entityごとの`random` | ランダムtick、ドロッパー・ディスペンサーのslot選択、Mobの行動、End Gatewayが複数Entityから選ぶ対象、スポナー。**Mobが少し違う程度ではなく、乱数を引く装置はすべて結果が変わり得ます** | `java.util.Random`の内部状態はJDKのprivate field。Unsafeや`--add-opens`は使わない。他Modが参照を持つため置き換えもしない |
| Entityの`age`（Item・経験値オーブ以外） | ageの剰余で動く処理の位相 | 全Entityで保存すると、毎checkpointで全Entity chunkを書き直すことになる |
| network id | `(age + id)`の剰余で動く処理（ItemEntity以外） | idを変えるとtracking・検索を壊す |
| Mob AI（goal、経路、Brainの一時memory） | Mobの行動 | 内部object参照が多く、安全に再構成できない |
| 実Playerの接続・開いている画面・睡眠 | トラップチェスト、睡眠によるスキップ | 接続を捏造しない |
| Bellのring / resonate | 襲撃者の発光（60 tick以内） | 聞いたEntityの参照が必要 |
| ShulkerBoxのanimation | 開閉中10 tickのcollisionとEntityの押し出し | Container sessionに属する |
| Boatの`ticksUnderwater`・`yawVelocity`、Minecartの`yawFlipped` | 水没による排出timer、降りる側 | 影響が小さい |
| 歩行距離の積算（`distanceTraveled`等） | 足音・STEPイベントの時点（スカルクセンサーが聞く） | 影響が小さい |
| EnderDragonFightの再召喚の進行、PhantomSpawner等のcooldown | ドラゴン再召喚の途中、自然スポーンの時点 | 影響が小さい／乱数依存 |
| lightingのworker内部状態、LIGHT ticket | — | 保存しない。Light suppressionの挙動は変えない |

## BlockEntity field一覧

| Class | Field | 分類 | Risk | 判断 |
|---|---|---|---|---|
| AbstractFurnaceBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| AbstractFurnaceBlockEntity | `burnTime` | PERSISTED_BY_VANILLA | NONE | BurnTime; AntiCrash marks the chunk dirty while it changes |
| AbstractFurnaceBlockEntity | `fuelTime` | DERIVED_ON_LOAD | NONE | recomputed from the fuel item in readNbt |
| AbstractFurnaceBlockEntity | `cookTime` | PERSISTED_BY_VANILLA | NONE | CookTime; dirty-marked by AntiCrash |
| AbstractFurnaceBlockEntity | `cookTimeTotal` | PERSISTED_BY_VANILLA | NONE | CookTimeTotal |
| AbstractFurnaceBlockEntity | `propertyDelegate` | DERIVED_ON_LOAD | NONE | view of the fields above |
| AbstractFurnaceBlockEntity | `recipesUsed` | PERSISTED_BY_VANILLA | NONE | RecipesUsed |
| AbstractFurnaceBlockEntity | `recipeType` | DERIVED_ON_LOAD | NONE | constructor argument |
| BannerBlockEntity | `customName` | PERSISTED_BY_VANILLA | NONE | CustomName |
| BannerBlockEntity | `baseColor` | DERIVED_ON_LOAD | NONE | from the block |
| BannerBlockEntity | `patternListNbt` | PERSISTED_BY_VANILLA | NONE | Patterns |
| BannerBlockEntity | `patterns` | CLIENT_ONLY | NONE | render cache |
| BarrelBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| BarrelBlockEntity | `stateManager` | OBSERVE_ONLY | LOW | viewer count of open screens; see CONTAINER_SESSION |
| BeaconBlockEntity | `beamSegments` | DERIVED_ON_LOAD | LOW | rescanned from the column above over the first ticks; an effect application can be skipped once |
| BeaconBlockEntity | `field_19178` | DERIVED_ON_LOAD | LOW | scan buffer for beamSegments |
| BeaconBlockEntity | `level` | DERIVED_ON_LOAD | LOW | recomputed every 80 ticks of world time |
| BeaconBlockEntity | `minY` | DERIVED_ON_LOAD | LOW | scan cursor |
| BeaconBlockEntity | `primary` | PERSISTED_BY_VANILLA | NONE | Primary |
| BeaconBlockEntity | `secondary` | PERSISTED_BY_VANILLA | NONE | Secondary |
| BeaconBlockEntity | `customName` | PERSISTED_BY_VANILLA | NONE | CustomName |
| BeaconBlockEntity | `lock` | PERSISTED_BY_VANILLA | NONE | Lock |
| BeaconBlockEntity | `propertyDelegate` | DERIVED_ON_LOAD | NONE | view |
| BedBlockEntity | `color` | DERIVED_ON_LOAD | NONE | from the block |
| BeehiveBlockEntity | `bees` | PERSISTED_BY_VANILLA | MEDIUM | Bees incl. TicksInHive; the count advanced without marking the chunk dirty, AntiCrash now marks it |
| BeehiveBlockEntity | `flowerPos` | PERSISTED_BY_VANILLA | NONE | FlowerPos |
| BellBlockEntity | `lastRingTime` | UNSUPPORTED | LOW | rate limit for notifying villagers (60 ticks) |
| BellBlockEntity | `ringTicks` | UNSUPPORTED | LOW | ring lasts 50 ticks; raider glow is applied during it |
| BellBlockEntity | `ringing` | UNSUPPORTED | LOW | see ringTicks |
| BellBlockEntity | `lastSideHit` | VISUAL_ONLY | NONE | animation direction |
| BellBlockEntity | `hearingEntities` | UNSUPPORTED | LOW | entity references collected when the ring started; not restorable without them |
| BellBlockEntity | `resonating` | UNSUPPORTED | LOW | raider glow after 40 ticks |
| BellBlockEntity | `resonateTime` | UNSUPPORTED | LOW | see resonating |
| BlastFurnaceBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| BlockEntity | `type` | DERIVED_ON_LOAD | NONE | from the block |
| BlockEntity | `world` | DERIVED_ON_LOAD | NONE | set when the chunk loads |
| BlockEntity | `pos` | PERSISTED_BY_VANILLA | NONE | x/y/z |
| BlockEntity | `removed` | TICK_LOCAL | NONE | a removed block entity is not saved |
| BlockEntity | `cachedState` | DERIVED_ON_LOAD | NONE | cache of the block state |
| BrewingStandBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| BrewingStandBlockEntity | `brewTime` | PERSISTED_BY_VANILLA | NONE | BrewTime; dirty-marked by AntiCrash |
| BrewingStandBlockEntity | `slotsEmptyLastTick` | DERIVED_ON_LOAD | NONE | only decides whether to rewrite the same block state |
| BrewingStandBlockEntity | `itemBrewing` | RESTORABLE_TRANSIENT | HIGH | not saved: the first tick after a load sees a changed ingredient and resets the brew; AntiCrash extension ItemBrewing |
| BrewingStandBlockEntity | `fuel` | PERSISTED_BY_VANILLA | NONE | Fuel |
| BrewingStandBlockEntity | `propertyDelegate` | DERIVED_ON_LOAD | NONE | view |
| CampfireBlockEntity | `itemsBeingCooked` | PERSISTED_BY_VANILLA | NONE | Items |
| CampfireBlockEntity | `cookingTimes` | PERSISTED_BY_VANILLA | NONE | CookingTimes; Vanilla marks dirty while cooking |
| CampfireBlockEntity | `cookingTotalTimes` | PERSISTED_BY_VANILLA | NONE | CookingTotalTimes |
| ChestBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| ChestBlockEntity | `stateManager` | OBSERVE_ONLY | MEDIUM | viewer count; a trapped chest's redstone output. Belongs to an open player screen: CONTAINER_SESSION |
| ChestBlockEntity | `lidAnimator` | CLIENT_ONLY | NONE | lid animation |
| CommandBlockBlockEntity | `powered` | PERSISTED_BY_VANILLA | NONE | powered |
| CommandBlockBlockEntity | `auto` | PERSISTED_BY_VANILLA | NONE | auto |
| CommandBlockBlockEntity | `conditionMet` | PERSISTED_BY_VANILLA | NONE | conditionMet |
| CommandBlockBlockEntity | `commandExecutor` | PERSISTED_BY_VANILLA | NONE | Command, SuccessCount, LastExecution, ... |
| ComparatorBlockEntity | `outputSignal` | PERSISTED_BY_VANILLA | NONE | OutputSignal; dirty-marked by AntiCrash |
| ConduitBlockEntity | `ticks` | VISUAL_ONLY | NONE | animation counter |
| ConduitBlockEntity | `ticksActive` | VISUAL_ONLY | NONE | animation |
| ConduitBlockEntity | `active` | DERIVED_ON_LOAD | LOW | recomputed when world time % 40 == 0, the same tick effects are applied |
| ConduitBlockEntity | `eyeOpen` | VISUAL_ONLY | NONE | animation |
| ConduitBlockEntity | `activatingBlocks` | DERIVED_ON_LOAD | LOW | rescanned with active |
| ConduitBlockEntity | `targetEntity` | DERIVED_ON_LOAD | NONE | looked up from targetUuid |
| ConduitBlockEntity | `targetUuid` | PERSISTED_BY_VANILLA | NONE | Target |
| ConduitBlockEntity | `nextAmbientSoundTime` | VISUAL_ONLY | LOW | sound only, but draws from the world RNG |
| DaylightDetectorBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| DispenserBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| DropperBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| EnchantingTableBlockEntity | `ticks` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `nextPageAngle` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `pageAngle` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `field_11969` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `field_11967` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `nextPageTurningSpeed` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `pageTurningSpeed` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `field_11964` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `field_11963` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `field_11962` | CLIENT_ONLY | NONE | book animation |
| EnchantingTableBlockEntity | `customName` | PERSISTED_BY_VANILLA | NONE | CustomName |
| EndGatewayBlockEntity | `age` | PERSISTED_BY_VANILLA | MEDIUM | Age; advanced without marking the chunk dirty, AntiCrash now marks it. age % 2400 starts a cooldown |
| EndGatewayBlockEntity | `teleportCooldown` | RESTORABLE_TRANSIENT | HIGH | not saved: a gateway on cooldown teleports at once after a load; AntiCrash extension TeleportCooldown |
| EndGatewayBlockEntity | `exitPortalPos` | PERSISTED_BY_VANILLA | NONE | ExitPortal |
| EndGatewayBlockEntity | `exactTeleport` | PERSISTED_BY_VANILLA | NONE | ExactTeleport |
| EndPortalBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| EnderChestBlockEntity | `lidAnimator` | CLIENT_ONLY | NONE | lid animation |
| EnderChestBlockEntity | `stateManager` | VISUAL_ONLY | NONE | viewer count drives only the lid |
| FurnaceBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| HopperBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| HopperBlockEntity | `transferCooldown` | PERSISTED_BY_VANILLA | NONE | TransferCooldown; dirty-marked by AntiCrash |
| HopperBlockEntity | `lastTickTime` | DERIVED_ON_LOAD | NONE | only compared with another hopper's value to learn whether that hopper already ticked this tick; the load default 0 means 'not yet', which is true at a tick boundary. See docs and HopperLastTickTimeTest |
| JigsawBlockEntity | `name` | PERSISTED_BY_VANILLA | NONE | saved |
| JigsawBlockEntity | `target` | PERSISTED_BY_VANILLA | NONE | saved |
| JigsawBlockEntity | `pool` | PERSISTED_BY_VANILLA | NONE | saved |
| JigsawBlockEntity | `joint` | PERSISTED_BY_VANILLA | NONE | saved |
| JigsawBlockEntity | `finalState` | PERSISTED_BY_VANILLA | NONE | saved |
| JukeboxBlockEntity | `record` | PERSISTED_BY_VANILLA | NONE | RecordItem |
| LecternBlockEntity | `inventory` | DERIVED_ON_LOAD | NONE | view of book |
| LecternBlockEntity | `propertyDelegate` | DERIVED_ON_LOAD | NONE | view |
| LecternBlockEntity | `book` | PERSISTED_BY_VANILLA | NONE | Book |
| LecternBlockEntity | `currentPage` | PERSISTED_BY_VANILLA | NONE | Page |
| LecternBlockEntity | `pageCount` | DERIVED_ON_LOAD | NONE | from the book |
| LockableContainerBlockEntity | `lock` | PERSISTED_BY_VANILLA | NONE | Lock |
| LockableContainerBlockEntity | `customName` | PERSISTED_BY_VANILLA | NONE | CustomName |
| LootableContainerBlockEntity | `lootTableId` | PERSISTED_BY_VANILLA | NONE | LootTable |
| LootableContainerBlockEntity | `lootTableSeed` | PERSISTED_BY_VANILLA | NONE | LootTableSeed |
| MobSpawnerBlockEntity | `logic` | PERSISTED_BY_VANILLA | NONE | Delay and spawn settings; Delay dirty-marked by AntiCrash |
| PistonBlockEntity | `pushedBlock` | PERSISTED_BY_VANILLA | NONE | blockState |
| PistonBlockEntity | `facing` | PERSISTED_BY_VANILLA | NONE | facing |
| PistonBlockEntity | `extending` | PERSISTED_BY_VANILLA | NONE | extending |
| PistonBlockEntity | `source` | PERSISTED_BY_VANILLA | NONE | source |
| PistonBlockEntity | `progress` | RESTORABLE_TRANSIENT | CRITICAL | not saved (Vanilla loads lastProgress into it): the piston arrives one tick late; AntiCrash extension Progress |
| PistonBlockEntity | `lastProgress` | PERSISTED_BY_VANILLA | NONE | saved under the key progress |
| PistonBlockEntity | `savedWorldTime` | RESTORABLE_TRANSIENT | MEDIUM | tick of the last advance, compared with world time by PistonBlock; AntiCrash extension SavedWorldTime |
| PistonBlockEntity | `field_26705` | CLIENT_ONLY | NONE | client-side removal delay |
| SculkSensorBlockEntity | `listener` | RESTORABLE_TRANSIENT | HIGH | pending event, distance and delay are not saved in 1.18.2; AntiCrash extension Vibration |
| SculkSensorBlockEntity | `lastVibrationFrequency` | PERSISTED_BY_VANILLA | NONE | last_vibration_frequency |
| ShulkerBoxBlockEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| ShulkerBoxBlockEntity | `viewerCount` | OBSERVE_ONLY | LOW | belongs to an open player screen: CONTAINER_SESSION |
| ShulkerBoxBlockEntity | `animationStage` | UNSUPPORTED | LOW | reset to CLOSED; an opening box pushes entities for 10 ticks |
| ShulkerBoxBlockEntity | `animationProgress` | UNSUPPORTED | LOW | see animationStage |
| ShulkerBoxBlockEntity | `prevAnimationProgress` | VISUAL_ONLY | NONE | interpolation |
| ShulkerBoxBlockEntity | `cachedColor` | DERIVED_ON_LOAD | NONE | from the block |
| SignBlockEntity | `texts` | PERSISTED_BY_VANILLA | NONE | Text1-4 |
| SignBlockEntity | `filteredTexts` | PERSISTED_BY_VANILLA | NONE | FilteredText1-4 |
| SignBlockEntity | `editable` | TICK_LOCAL | NONE | only while a player edits |
| SignBlockEntity | `editor` | TICK_LOCAL | NONE | editing player |
| SignBlockEntity | `textsBeingEdited` | CLIENT_ONLY | NONE | render cache |
| SignBlockEntity | `filterText` | CLIENT_ONLY | NONE | render cache |
| SignBlockEntity | `textColor` | PERSISTED_BY_VANILLA | NONE | Color |
| SignBlockEntity | `glowingText` | PERSISTED_BY_VANILLA | NONE | GlowingText |
| SkullBlockEntity | `owner` | PERSISTED_BY_VANILLA | NONE | SkullOwner |
| SkullBlockEntity | `ticksPowered` | VISUAL_ONLY | NONE | dragon head animation |
| SkullBlockEntity | `powered` | VISUAL_ONLY | NONE | read from the world every tick |
| SmokerBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |
| StructureBlockBlockEntity | `structureName` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `author` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `metadata` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `offset` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `size` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `mirror` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `rotation` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `mode` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `ignoreEntities` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `powered` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `showAir` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `showBoundingBox` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `integrity` | PERSISTED_BY_VANILLA | NONE | saved |
| StructureBlockBlockEntity | `seed` | PERSISTED_BY_VANILLA | NONE | saved |
| TrappedChestBlockEntity | `-` | PERSISTED_BY_VANILLA | NONE | no fields of its own |

## Entity field一覧

Mob（LivingEntityの各種）は、上の「Mob AI」のcategoryとして扱います。

| Class | Field | 分類 | Risk | 判断 |
|---|---|---|---|---|
| Entity | `type` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `world` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `uuid` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `uuidString` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `dimensions` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `standingEyeHeight` | DERIVED_ON_LOAD | NONE | constructed with the entity |
| Entity | `random` | UNSUPPORTED | MEDIUM | per-entity java.util.Random; part of the unsupported RNG state |
| Entity | `id` | OBSERVE_ONLY | MEDIUM | network id, handed out again at every load. ItemEntity's (age + id) % 4 move phase and ExperienceOrbEntity's (id difference) % 40 merge rule are kept by AntiCrash (IdPhase), and the id counter continues with the checkpoint's remainder modulo 40 so entities created after a restore keep both rules; other uses are unsupported |
| Entity | `passengerList` | PERSISTED_BY_VANILLA | NONE | Passengers |
| Entity | `vehicle` | PERSISTED_BY_VANILLA | NONE | Passengers |
| Entity | `ridingCooldown` | RESTORABLE_TRANSIENT | MEDIUM | 60 ticks after leaving a vehicle; AntiCrash extension RidingCooldown |
| Entity | `prevX` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `prevY` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `prevZ` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `prevYaw` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `prevPitch` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `lastRenderX` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `lastRenderY` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `lastRenderZ` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `trackedPosition` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `ignoreCameraFrustum` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `lastChimeIntensity` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `lastChimeAge` | VISUAL_ONLY | NONE | interpolation, tracking or sound |
| Entity | `pos` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `velocity` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `yaw` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `pitch` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `onGround` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `fallDistance` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `fireTicks` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `netherPortalCooldown` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `invulnerable` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `glowing` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `scoreboardTags` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `noClip` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `hasVisualFire` | PERSISTED_BY_VANILLA | NONE | Pos, Motion, Rotation, OnGround, FallDistance, Fire, PortalCooldown, Invulnerable, Glowing, Tags, (noClip per type), HasVisualFire |
| Entity | `blockPos` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `chunkPos` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `boundingBox` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `blockStateAtPos` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `stepHeight` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `dataTracker` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `changeListener` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `removalReason` | DERIVED_ON_LOAD | NONE | derived from position/type on load |
| Entity | `intersectionChecked` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `horizontalCollision` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `verticalCollision` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `field_36331` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `collidedSoftly` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `velocityModified` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `movementMultiplier` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `velocityDirty` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `pistonMovementDelta` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `pistonMovementTick` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `inPowderSnow` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `wasInPowderSnow` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `wasOnFire` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `speed` | TICK_LOCAL | NONE | rewritten by the entity's own move/baseTick before it is read; pistonMovement* is keyed by world time |
| Entity | `prevHorizontalSpeed` | UNSUPPORTED | LOW | walk distance accumulators: decide when the next step sound/STEP game event happens (a sculk sensor can hear it) |
| Entity | `horizontalSpeed` | UNSUPPORTED | LOW | walk distance accumulators: decide when the next step sound/STEP game event happens (a sculk sensor can hear it) |
| Entity | `distanceTraveled` | UNSUPPORTED | LOW | walk distance accumulators: decide when the next step sound/STEP game event happens (a sculk sensor can hear it) |
| Entity | `nextStepSoundDistance` | UNSUPPORTED | LOW | walk distance accumulators: decide when the next step sound/STEP game event happens (a sculk sensor can hear it) |
| Entity | `age` | RESTORABLE_TRANSIENT | MEDIUM | not saved. Restored for ItemEntity and ExperienceOrbEntity (merge and target cadence). UNSUPPORTED for other entities: saving it would rewrite every entity chunk at every checkpoint |
| Entity | `touchingWater` | RESTORABLE_TRANSIENT | MEDIUM | a loaded entity is 'new' for one tick: isInLava() is false and water entry splashes (a SPLASH game event). AntiCrash extension Ticked/Water |
| Entity | `firstUpdate` | RESTORABLE_TRANSIENT | MEDIUM | a loaded entity is 'new' for one tick: isInLava() is false and water entry splashes (a SPLASH game event). AntiCrash extension Ticked/Water |
| Entity | `fluidHeight` | TICK_LOCAL | NONE | recomputed at the start of baseTick |
| Entity | `submergedInWater` | TICK_LOCAL | NONE | recomputed at the start of baseTick |
| Entity | `submergedFluidTag` | TICK_LOCAL | NONE | recomputed at the start of baseTick |
| Entity | `timeUntilRegen` | RESTORABLE_TRANSIENT | MEDIUM | damage immunity window; AntiCrash extension Regen (+ LastDamage) |
| Entity | `inNetherPortal` | RESTORABLE_TRANSIENT | HIGH | pending dimension change; AntiCrash extension Portal |
| Entity | `netherPortalTime` | RESTORABLE_TRANSIENT | HIGH | pending dimension change; AntiCrash extension Portal |
| Entity | `lastNetherPortalPosition` | RESTORABLE_TRANSIENT | HIGH | pending dimension change; AntiCrash extension Portal |
| ItemEntity | `itemAge` | PERSISTED_BY_VANILLA | NONE | Age |
| ItemEntity | `pickupDelay` | PERSISTED_BY_VANILLA | NONE | PickupDelay |
| ItemEntity | `health` | PERSISTED_BY_VANILLA | NONE | Health |
| ItemEntity | `thrower` | PERSISTED_BY_VANILLA | NONE | Thrower |
| ItemEntity | `owner` | PERSISTED_BY_VANILLA | NONE | Owner |
| ItemEntity | `uniqueOffset` | VISUAL_ONLY | NONE | bobbing offset |
| TntEntity | `causingEntity` | UNSUPPORTED | LOW | who lit it; attribution of the explosion only |
| FallingBlockEntity | `block` | PERSISTED_BY_VANILLA | NONE | BlockState |
| FallingBlockEntity | `timeFalling` | PERSISTED_BY_VANILLA | NONE | Time |
| FallingBlockEntity | `dropItem` | PERSISTED_BY_VANILLA | NONE | DropItem |
| FallingBlockEntity | `destroyedOnLanding` | TICK_LOCAL | NONE | set and read while landing, in one tick |
| FallingBlockEntity | `hurtEntities` | PERSISTED_BY_VANILLA | NONE | HurtEntities |
| FallingBlockEntity | `fallHurtMax` | PERSISTED_BY_VANILLA | NONE | FallHurtMax |
| FallingBlockEntity | `fallHurtAmount` | PERSISTED_BY_VANILLA | NONE | FallHurtAmount |
| FallingBlockEntity | `blockEntityData` | PERSISTED_BY_VANILLA | NONE | TileEntityData |
| ExperienceOrbEntity | `orbAge` | PERSISTED_BY_VANILLA | NONE | Age |
| ExperienceOrbEntity | `health` | PERSISTED_BY_VANILLA | NONE | Health |
| ExperienceOrbEntity | `amount` | PERSISTED_BY_VANILLA | NONE | Value |
| ExperienceOrbEntity | `pickingCount` | PERSISTED_BY_VANILLA | NONE | Count |
| ExperienceOrbEntity | `target` | DERIVED_ON_LOAD | LOW | searched again when age % 20 == 1; age is restored by AntiCrash |
| AbstractMinecartEntity | `yawFlipped` | UNSUPPORTED | LOW | which way the cart counts as facing; decides the side passengers dismount to |
| AbstractMinecartEntity | `clientInterpolationSteps` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientX` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientY` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientZ` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientYaw` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientPitch` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientXVelocity` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientYVelocity` | CLIENT_ONLY | NONE | client interpolation |
| AbstractMinecartEntity | `clientZVelocity` | CLIENT_ONLY | NONE | client interpolation |
| HopperMinecartEntity | `enabled` | PERSISTED_BY_VANILLA | NONE | Enabled |
| HopperMinecartEntity | `transferCooldown` | PERSISTED_BY_VANILLA | NONE | TransferCooldown |
| HopperMinecartEntity | `currentBlockPos` | DERIVED_ON_LOAD | NONE | constant |
| FurnaceMinecartEntity | `fuel` | PERSISTED_BY_VANILLA | NONE | Fuel |
| FurnaceMinecartEntity | `pushX` | PERSISTED_BY_VANILLA | NONE | PushX |
| FurnaceMinecartEntity | `pushZ` | PERSISTED_BY_VANILLA | NONE | PushZ |
| TntMinecartEntity | `fuseTicks` | PERSISTED_BY_VANILLA | NONE | TNTFuse |
| StorageMinecartEntity | `inventory` | PERSISTED_BY_VANILLA | NONE | Items |
| StorageMinecartEntity | `lootTableId` | PERSISTED_BY_VANILLA | NONE | LootTable |
| StorageMinecartEntity | `lootSeed` | PERSISTED_BY_VANILLA | NONE | LootTableSeed |
| BoatEntity | `paddlePhases` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `bubbleWobbleStrength` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `bubbleWobble` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `lastBubbleWobble` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `field_7708` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `x` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `y` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `z` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `boatYaw` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `boatPitch` | CLIENT_ONLY | NONE | animation / client interpolation |
| BoatEntity | `pressingLeft` | TICK_LOCAL | NONE | input of the riding player |
| BoatEntity | `pressingRight` | TICK_LOCAL | NONE | input of the riding player |
| BoatEntity | `pressingForward` | TICK_LOCAL | NONE | input of the riding player |
| BoatEntity | `pressingBack` | TICK_LOCAL | NONE | input of the riding player |
| BoatEntity | `velocityDecay` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `waterLevel` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `field_7714` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `location` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `lastLocation` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `fallVelocity` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `onBubbleColumnSurface` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `bubbleColumnIsDrag` | TICK_LOCAL | LOW | recomputed from the surroundings at the start of every tick |
| BoatEntity | `ticksUnderwater` | UNSUPPORTED | LOW | 60-tick underwater eject timer and residual turn speed |
| BoatEntity | `yawVelocity` | UNSUPPORTED | LOW | 60-tick underwater eject timer and residual turn speed |
| ProjectileEntity | `ownerUuid` | PERSISTED_BY_VANILLA | NONE | Owner |
| ProjectileEntity | `owner` | DERIVED_ON_LOAD | NONE | looked up from ownerUuid |
| ProjectileEntity | `leftOwner` | PERSISTED_BY_VANILLA | NONE | LeftOwner |
| ProjectileEntity | `shot` | PERSISTED_BY_VANILLA | NONE | HasBeenShot |
| PersistentProjectileEntity | `inBlockState` | PERSISTED_BY_VANILLA | NONE | inBlockState |
| PersistentProjectileEntity | `inGround` | PERSISTED_BY_VANILLA | NONE | inGround |
| PersistentProjectileEntity | `inGroundTime` | UNSUPPORTED | LOW | ticks stuck in a block; only the 'fell out of the block' check |
| PersistentProjectileEntity | `pickupType` | PERSISTED_BY_VANILLA | NONE | pickup |
| PersistentProjectileEntity | `shake` | PERSISTED_BY_VANILLA | NONE | shake |
| PersistentProjectileEntity | `life` | PERSISTED_BY_VANILLA | NONE | life |
| PersistentProjectileEntity | `damage` | PERSISTED_BY_VANILLA | NONE | damage |
| PersistentProjectileEntity | `punch` | UNSUPPORTED | LOW | knockback level is not saved |
| PersistentProjectileEntity | `sound` | PERSISTED_BY_VANILLA | NONE | SoundEvent |
| PersistentProjectileEntity | `piercedEntities` | UNSUPPORTED | LOW | entities a piercing arrow already hit |
| PersistentProjectileEntity | `piercingKilledEntities` | UNSUPPORTED | LOW | advancement bookkeeping |

## 実Serverでの確認手順

新しいjarで作ったcheckpointから確認してください（古いcheckpointには拡張NBTがなく、ピストンは`MISSING_FROM_OLD_FORMAT`と表示されます）。

確認するログ: `/ac status`、`/ac recovery verify --verbose`、`/ac debug equivalence --verbose`、`latest.log`の次の行。

- `最初のtickの前にcheckpoint時のtick順序を復元しました: …`
- `最初のtickの前に動作中ピストンの進行度を照合しました: MATCH n/n`
- `checkpoint時の恒久ticketがすべて再現されたため、復旧用ticketを最初のtickの前に解除しました`
- `復旧が … で不完全（DEGRADED）です`が出ていないこと

| Test | 手順 | 期待 |
|---|---|---|
| A 飛行式装置 | ピストンが動いている最中にcheckpoint → kill → 復元 → `/ac start` | 止まらない。`MATCH n/n` |
| B Trencher / World Eater | 異なる動作段階で10回以上 | 毎回継続する |
| C ホッパー列 | 3連ホッパーにItemを流しながら | 最初の数tickの移送時点が同じ |
| D ホッパータイマー | 位相を記録してから | 位相が保たれる |
| E End Gateway | cooldown中（紫のビーム中）にcheckpoint | 再開直後に転送されない |
| F ネザーポータル | Entityがポータルに触れたtickでcheckpoint | 次のtickで転送される |
| G ポータル式Chunk loader | Itemが通過してから数十tick後 | Chunkが残り時間どおり保持される |
| H FakePlayerの弓 | 弓を引いている途中 | 引き時間が続きから |
| I トラップチェスト + FakePlayer | FakePlayerが開いた状態 | `CONTAINER_SESSION`が非対応として表示される（出力は消える） |
| J スカルクセンサー | 振動を受けて作動する前 | 作動する |
| K TNT / 落下ブロック | 複数が同時にある状態 | 順序と結果が同じ |
| L 醸造台 | 醸造の途中 | 残り時間が続きから（やり直しにならない） |
| M 無人のディメンション | Playerのいないネザーを300 tick以上放置してから | 復元後もEntityが動き出さない |
