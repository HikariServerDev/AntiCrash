# 版別アダプター調査記録

この記録はMinecraftを起動せず、各版のYarn mapped JAR／26.x非難読化JARを`javap`/ASMで調べた結果です。
`BUILD_VERIFIED`はunit test/buildの成功であり、Mixin適用や装置復旧のruntime qualificationではありません。

## 1.19 / 1.19.1 / 1.19.2

- Textは`Text.literal`。BlockPos/ChunkPosやレジストリーIDを保存形式に使い、Javaオブジェクトの置換はしない。
- VibrationListenerはVanilla自身のCodecが進行中の振動を保存する。旧1.18.2のSensor専用NBT拡張は削除し、
  Vanilla Codecを二重のauthorityにしない。FingerprintはListenerのCodec全体を正規化して取得する。
- SculkCatalystのSpreadManager cursorsもVanillaが保存する。tick/listenによる変化にdirty hookを追加。
- Furnace/CampfireのRecipeManager.MatchGetterは最後の一致レシピIDを保持し、そのIDを優先して探索する。
  単なる性能キャッシュではなく、複数レシピが一致する場合に意味が変わるためIDをNBT拡張に保存する。
  匿名クラスの実フィールド`id`をAccessorで操作し、RecipeManagerの内部Mapは置換しない。
- prediction ACKはsequence番号になる。復旧中に拒否した操作にもsequence ACKを返す。
  負番号はVanillaの不正packetとして切断し、server taskから例外を投げてサーバーを落とさない。
- 1.19.1以降のY/N chat処理は、署名・message chain/ACK検証後の`handleDecoratedMessage`で行う。
  packet入口で取り消して署名検証を飛ばす方式にはしない。
- 1.19.1のJukeboxは再生中のNBTが増えたが、JUKEBOX_PLAYの周期位相`ticksThisSecond`は未保存。
  位相を追加保存し、tick時にdirty扱いにする。
- 1.19.2のRegionFile削除メソッドは`delete(ChunkPos)`。実APIに合わせてgeneration/materializerを変更。
- World.randomはMinecraft独自Random。JDK reflectionで復元したように見せずWORLD_RNGをUNSUPPORTEDとする。

## 1.19.3 / 1.19.4

- Registry/RegistriesとTickPriorityのnamespace変更を反映。
- VibrationSelectorが新設され、まだ選択されていない振動候補もruntime stateになる。
  選択済みvibrationだけをhashせず、Vanilla Codec全体をhashする。dirty hookも候補状態を取りこぼさない。
- Runtime-field auditはASMでclassfileを読む方式に変更。Knotのaccess wideningを受けないraw mapped JARを
  reflectionでロードしてVerifyErrorになることを、監査skipで隠さない。
- Mixin監査にcallback引数・static性・CallbackInfo/Returnableの検査を追加。
  `markDirty()V`やJukeboxの実instance tickをdescriptorで指定し、同名overloadの誤適用を防ぐ。
- 1.19.4のServerPlay接続フィールドはprivate。Mixin Accessorで取得し、公開フィールドを仮定しない。
- WorldTickScheduler.scheduleTicksの新3引数版もepoch mutation trackingの対象にする。
- Archaeologyの刷毛進捗・次回使用/減衰時刻・方向をNBT拡張に保存する。
  Vanillaは一部値を読んでも書かないため、read/writeの実bytecode双方を確認した。

## 1.20 / 1.20.1

- ChunkLevelType.BLOCK_TICKING、WrapperProtoChunk、DragonFight.Dataなどの実APIへ移植。
- SculkはVibrations.ListenerData/Callback/Tickerへ分割される。ListenerData Codec全体をfingerprint対象にする。
  callbackから意味的な位置を取得してdirty扱いにする。新Calibrated Sensorも同じ経路で扱う。
- EntityのgroundCollision、supportingBlockPos、forceUpdateSupportingBlockPosを保存/復元し、fingerprintにも含める。
  接地判定の新runtime状態を旧フィールド名の置換だけで済ませない。
- Signの表/裏textとwaxed、BrushableBlockEntity等の監査を更新。

## 1.20.2

- CommonNetworkHandlerとconfiguration phaseの導入を反映。接続Accessorとcustom payload hookをCommon側へ移す。
  play phaseでのみ復旧待機queueを使用し、configuration phaseのMod handshakeを凍結しない。
- CustomPayloadはtyped payload record。channel取得はpayload.id()。既存deferred queueの上限は維持。
- PlayerManager.onPlayerConnectにConnectedClientData引数が追加される。Callback署名を更新。
- ServerChunkManagerのsave内処理はupdateChunksへ改名。ticket ageを止めるWrapOperationのtargetを変更。
- NBT readerはsize trackerを明示するAPIになる。既存16MiB制限を維持し、無制限readerに置換しない。
  NbtIo.writeのnamed-root方式はこの版でも残っており、packet専用のunnamed-root方式へ変更しない。

## 1.20.3 / 1.20.4

- CommandManager.executeのreturn typeがvoidになる。拒否時はcancelし、FakePlayer command fallbackの内部scopeは
  Supplierから明示的にnullを返す。理由表示とinternal-command bypassは維持。
- 新Vanilla TickManagerのrate/frozen/stepTicks/shouldTickをRuntime Capsuleのoptional fieldへ追加。
  checkpoint/recovery barrier中はstepを消費させない。Minecraft本体のtick制御とCarpetを混同しない。
- Vanilla sprintはwall-clock測定を含むためUNSUPPORTED。STRICTでは許可設定がない限り不完全なcheckpointとして扱う。
  復旧時にsprintを再現したことにはせず、fingerprintにsprinting状態を残す。
- 古いcapsuleでVanilla tick stateがない場合は、STRICTの復旧検証でMISSING_FROM_OLD_FORMATをblocking mismatchとする。
  同じcapsule version 7の追加optional fieldで、旧版の欠落がデフォルト値に化けないようnullを使う。
- Crafter countdownはVanilla NBTに存在するが、中間tickでdirtyにならない。active countdown時だけdirty hookを追加。
- TrialSpawnerのVanilla codecデータを保存するためserver tickでdirty扱いにする。ただしforceActivate/custom detectorの
  runtime復元は未実装であり、存在するchunkではTRIAL_SPAWNER_RUNTIMEをUNSUPPORTEDとして明示する。
- 1.20.4はNBT size trackerのYarn名がNbtSizeTrackerになる。

## 1.20.5 / 1.20.6

- Java 21が必要。Mod compile targetとfabric metadata / Mixin compatibilityも21へ変更。
  Supervisorはゲームクラスに依存しないためJava 17のまま。
- ChunkHolder Futureの結果はEitherではなくOptionalChunkになる。完了をpollしてisPresent/orElseを確認し、
  非同期復旧・同時load上限を保持する。Future.joinで同期チャンクロードに戻さない。
- POI/Entity storageはChunkPosKeyedStorageがIO workerを持つ。実フィールドstorageAccess/storage/workerを
  Accessorで辿る。旧workerの架空フィールドを追加したり、IO sealを省略したりしない。
- GameEventはRegistryEntryになる。vibration listener/catalyst listenerのcallback署名とdescriptorを変更。
- アイテムはregistry-aware components codecで保存する。空slotはencodeAllowEmpty、復元はfromNbtの
  Optional失敗をIOExceptionとして扱い、不明なアイテムを黙って空にしない。
- World読込前はdynamic registryを解決できないため、bounded NBT envelope/slot数/id/countを検証する。
  復元時はplayerのWrapperLookupで全slotをdecode・検証し、すべて成功してから初めて書き換える。
  事前検証を完全なcomponents semantic検証と称しない。旧Count形式の版間インベントリは拒否する。
- EnderChest/BossBar/BlockEntity NBTもWrapperLookupを渡す。BlockEntity runtime extensionのcallbackにも
  新引数を追加し、NBT hookを失わない。
- NbtHelperのBlockPosはint arrayになる。新Optional読出しに合わせ、保存済みportal/supporting positionを扱う。
- detached region用StorageKeyはdiagnostic/JFRラベル。実ワールドへ繋がない。既存のinode分離・CRC・
  region overlayのコピー/削除テストを同じように実行する。
- Vaultの通常dataはVanilla codecにあるが、runtime cooldown等のExact restoreは未対応。
  VAULT_RUNTIMEとして明示し、STRICTのunsupported policyに従う。TrialSpawnerの制約も継続する。

## 1.21 / 1.21.1

- ThreadedAnvilChunkStorageはServerChunkLoadingManagerになる。保存先の世代分離やentity unload追跡は維持。
- ChunkHolder savingFutureは処理完了だけを表す。proto chunkはisSavableでgeneration参照とFuture完了を確認し、
  getLatestから取得する。完了FutureのObjectをChunkへcastしない。例外完了はgetNowで表に出し、joinはしない。
- ChunkHolder#getWorldChunkはticking Futureしか見ないため、これをloaded集合の代用にしてはいけない。
  getLatest/WrapperProtoChunkからWorldChunkを読み、loadedToWorldの実フラグを確認する。
  current holdersとpending unload holders双方を観測する。FULL availabilityはaccessible Futureで別に確認する。
- Entityの旧nether-portalフィールドはPortalManagerへ移る。Portalが登録Blockである場合は
  registry ID、pos、ticksInPortal、inPortalを保存し、新しいmanagerへ意味的に復元する。
  独自の非Block Portal実装はUNSUPPORTEDとして扱い、Java Objectをserializeしない。
- TNTのteleportedフラグはVanilla NBTにないが、爆発behaviorを変える。FallingBlockのshouldDupeも
  portal duplication経路に関与する。両方をloaded-entity NBT拡張とfingerprintへ追加した。
- ProjectileのlastDeflectedEntityはJava identityに依存する未対応state。
  実値が存在するとPROJECTILE_DEFLECTION_RUNTIMEを明示する。架空の復元成功にしない。
- Jukeboxのevent位相は新Managerのticks_since_song_started % 20になる。Vanilla自体がこの値を保存するため、
  旧ticksThisSecond拡張を撤去し、再生中のdirty hookのみを維持する。二重の保存authorityを作らない。

## 1.21.2

- ChunkSerializerはSerializedChunk recordへ分割された。`fromChunk(ServerWorld, Chunk)`がsection/lightのcopyと
  blockEntity NBT取得を行い、`serialize()`がNBTへ変換する。loaded-runtimeの保存scopeは前者に置く。
  async側のserialize時にlive BlockEntityを読み直さず、既存epoch cut/IO sealを保持する。
- StorageIoWorkerはSequencedMapとSupplier型runへ変化する。Entity storageのSimpleConsecutiveExecutorを
  Vanilla同様runAllで排出する。Chunk Futureは引き続きpollし、main threadでjoinしない。
- Recipe cacheのIDはServerRecipeManagerのRegistryKeyになる。CampfireではgetTickerが作る関数closureに移る。
  native tickerを意味の変わらないdelegateで包み、WorldChunk.wrapTicker時に新しいcacheをBEへ関連付ける。
  最初のtickだけで関連付けると、再点火後・最初のtick前のcheckpointが古いIDを拾うため不十分。
- PlayerInputは七つのbooleanを持つrecordになる。反対方向の同時入力も含めて127通りではなく全128通りを
  packed bitsで保存し、Carpetのanalog forward/strafeとは別にrestoreする。欠落を勝手に無入力とみなさない。
- Item cooldownのkeyはItemではなくgroup Identifierになる。独自componentのgroupをregistry itemとして
  誤って拒否しない。Hunger exhaustionは実private fieldへAccessorを使う。
- Creaking Heartのpuppet/timer/comparator、experimental minecart controller、hopper minecartの新transfer flag、
  boat turn/underwater state、非空のEntity collision queueはExact restore未対応としてSTRICTで明示的にblock。
  通常NBTのexplosion_power、FurnaceMinecart pushVecのPushX/PushZはVanillaが保存する。
- raw mapped JARのChunkPos初期化がregistriesを要求するため、offline test用JARへLoaderの実装
  `PackageAccessFixer`だけを適用しVanilla registriesをbootstrapする。instructionの置換、Mixin適用、
  MinecraftServer生成・world読み込み・server起動はしない。fixture JARは配布しない。
- WrapMethodのsignature監査を追加した結果、ServerEntityManagerのunload overloadへの曖昧なselectorを発見。
  chunk unloadの`unload(J)Z`を明示する修正を既存移植版にも適用し、再test/build対象とした。

## 1.21.3 / 1.21.4

- 1.21.3でも自身のmapped JARに対して全Mixin selectorとruntime-field matrixを再監査する。
  1.21.2と似た更新だからといってMinecraft dependency範囲を広げず、版専用JARとcheckpoint identityを使う。
- 1.21.4のFurnaceはlitTimeRemaining/litTotalTime/cookingTimeSpent/cookingTotalTimeへ変更。
  read/write双方が四つをsnake_caseのNBTへ保存する。旧fuelTimeの「再計算」分類を残さない。
- pick操作はblock/entityの二packetになる。復旧ロックをforceMainThread直後に置き、
  world読出しの前かつserver thread上で拒否/Inventory resyncする。旧packet名への架空Injectは置かない。
- POST_TELEPORT ticketはなくなり、ENDER_PEARLはChunkPos argument・40tick expiryを持つ別種類になる。
  network entity idを入れず、保存したChunkPosと残り寿命をVanilla addTicketWithLevelへ渡す。
- Creaking Heartのpuppet UUIDはVanilla NBTに入るようになったが、内部ticksやtimer/comparator状態は
  未保存である。STRICTのCREAKING_HEART_RUNTIME制約は維持する。
- Fabric APIは本体ではなく別sourceSetの試験probeのみのcompile dependencyにする。
  通常の配布ModにFabric APIを必須化しない。probeの実機qualificationは別工程。

## 1.21.5

- [Fabricの1.21.5変更説明](https://fabricmc.net/2025/03/24/1215.html)を確認後、Yarn build.1の
  mapped bytecodeで内部APIを照合した。NBTのOptional化では必須extension値を`orElseThrow`で読み、
  heterogeneous listに非compound passengerが混在する場合は拒否する。numeric type検査は明示的に残す。
- ChunkTicketはargument/tickCreatedを廃止しticksLeftを持つ。ledgerはregistry ID・level・残り寿命を記録。
  PORTALはVanillaのPersistentStateで保存される。公開addTicketはtype identityとlevelで重複を防ぐので、
  同じticketを追加/更新し残り寿命のみ合わせる。内部ticket mapの置換はしない。
- ServerChunkManager.addTicket/removeTicketはradiusではなくlevelを受け取る。この版では33/32/31を直接渡す。
  復旧用ticketは非persist・Long.MAX_VALUE expiryの独自instanceで、通常typeと区別しledgerから除外。
  abort/復旧完了時に公開APIで解除する。Ticket level managerのloading/simulation分離も考慮し、
  entity readinessはfutureだけでなくVanillaのshouldTickEntityAtで再検証する。
- PlayerInventoryは装備をEntityEquipmentへ移すが、公開getStack/setStackの36～40は装備slotへ委譲する。
  選択slotはgetter/setterを使う。ItemStack OPTIONAL_CODEC・StatusEffectInstance CODECを使用し、
  全slot/effectのdecodeが終わるまでplayerへ適用しない。fallDistanceはdouble精度を保つ。
- Respawn recordとBlockPos/UUID Codecへ変更。ClickSlot packetはItemStackではなくhashを送るため、
  server threadへ移された後のserver-owned cursorをtransaction診断に使う。
- EntityのcustomDataはVanilla NbtComponent codec、StructureBlockのstrictはVanilla NBTが保存する。
  新collisionHandlerは同一tickのrunCallbacksで消費される。非空queuedCollisionChecksは引き続きunsupported。
  TNT minecartの非null damageSourceとGameTest実行系はExact未対応としてSTRICTをblockする。
- PersistentStateManager.saveはstartSaving().joinで完了待ちすることを確認。これはVanilla metadata保存の
  既存境界であり、chunk Futureのmain-thread joinを追加したものではない。
- 版別Mixin callback/WrapMethod・runtime field matrix、NBT不正型、effect flags、empty item、実ChunkTicket
  countdownのoffline testを実施する。Minecraft起動/実Mixin適用/Carpet・装置の実機検証は未実施。

## 1.21.6 / 1.21.7 / 1.21.8

- [Fabricの変更説明](https://fabricmc.net/2025/06/15/1216.html)と各版のmapped bytecodeを照合する。
  Entity/BlockEntity保存はReadView/WriteViewへ変更。拡張NBTはentity/loaded-chunk保存scopeに限定し、
  次元移動・通信データへ混入させない。WriteView.getは子を置換するため、scope内で拡張値を統合して
  Codec経由でputする。保存完了時にcacheを解放し、複数Mixinのextensionが消える回帰をテストする。
- Viewのencode/decode errorはthrowing ErrorReporterで検出する。エラーを空値扱いしてcheckpointを
  完全とみなさない。ItemStack/StackWithSlot/PlayerAbilities.PackedのCodecを使い、inventory全slotの
  validationが完了するまで適用しない。Entity.saveData、SculkSpreadManager.writeDataへ版別に対応。
- Entity collision queueはArrayDequeへ変更。Projectile/Item/Conduitの参照はLazyEntityReferenceとなり、
  Owner/Thrower/TargetはVanilla writeCustomDataが保存する。新alwaysSyncAbsoluteは通信方針のみ。
- SneakはPlayerInputへ移ったため、server thread移行直後にpress/release/sprintを含む状態差分を検査。
  凍結中の許可設定を、キー解除で迂回できないようにする。128×128通りの入力差分をテストする。
- 各版を独立にcompile/test/buildし、内部field/selector/codecテストを再実行する。offline registriesの
  初期化のみを使い、MinecraftServer/Worldの生成、実Mixin適用、実装置の受け入れ試験はしない。

## 1.21.9 / 1.21.10

- [Fabricの1.21.9/1.21.10変更説明](https://fabricmc.net/2025/09/23/1219.html)と版別mapped JARを確認。
  Entity.getEntityWorld/getEntityPos、GameProfile record、PlayerConfigEntryのlogin callbackへ変更。
  entity参照とBlockEntity.getWorldを区別し、型の違うAPIを一括で置き換えない。
- チケットはflags（SERIALIZE/FOR_LOADING/FOR_SIMULATION等）へ変更。復旧用typeは非serializeで
  loading/simulationのみを要求し、独自instanceとLong.MAX_VALUE expiryを使う。
  PLAYER_SPAWN/SPAWN_SEARCHは自然に生成される診断対象。未再構築の差分を一致とみなさない。
- RespawnはSpawnPoint(global position/yaw/pitch)とforcedへ変更。pitchもsnapshotと有限値検査へ含める。
  WorldBorderはdimensionごとのPersistentStateになった。level.datの旧migration fieldへ現状態を
  書き戻さず、PORTABLE_GENERATIONのファイル境界を維持する。論理PersistentState captureは未対応。
- FakePlayerの騎乗をstartRiding(vehicle,true,false)で復元。第三引数falseはmount GameEventを抑止し、
  復旧中に新規vibrationを発生させない。Projectile.checkedForLeftOwnerはloaded entity extensionと
  fingerprintへ追加。Shelf inventory/align flagとGameTest errorsはVanilla view codecが保存する。
- Mixin監査を、At INVOKEのcallee存在だけでなくcaller内の実呼出しとordinal数の確認まで強化。
  EntityChunkDataAccessの呼出しは1.20.5以降ChunkPosKeyedStorage.setであるため、旧StorageIoWorker
  を指すWrapOperationを関連する移植版で修正した。これは実Mixin適用テストの代替ではない。
- 1.21.5以降のsparse entity flags（Ticked/Water）は省略されたfalseと不正型を区別する。
  falseがない正常な拡張データでthrowせず、存在する不正型は引き続き拒否する。
- 新unpick形式にはLoom 1.11.8 / Gradle 8.14.2が必要。Gradle配布SHAを固定する。
  1.21.10のCommandManagerはparseAndExecuteへ変更。既存probeは実機用adapter未移植のため、
  明示的なenableRuntimeQualificationProbe指定時のみsourceSetを登録する。通常buildは本体・offline
  tests・Supervisorが対象。probeのcompile/runtime qualificationは未完了であり配布しない。

## 1.21.11

- [Fabricの1.21.11変更説明](https://fabricmc.net/2025/12/05/12111.html)とYarn build.6のmapped JARを確認。
  Loom 1.14.10 / Gradle 9.4.0（配布SHA固定）/ Loader 0.18.4へ更新。Gradle 9用にJUnit launcherを
  本体・Supervisorとも明示する。ゲーム側Java targetは21、Supervisorは17を維持。
- PermissionPredicate / Permission.Levelを通じて既存configの0～4を判定。0はOP不要、範囲外やnullは
  拒否する。全actor/required levelの組合せをテストし、回復確認の管理権限にも同じadapterを使う。
- ChunkPosKeyedStorageはVersionedChunkStorageへ変更。entity非空保存はsetNbt、削除はset(Supplier)。
  削除の経路でもdedup digestを破棄し、削除後に同じpayloadが再登場した場合の保存を省略しない。
  既存のbounded owner-aware IO sealは実workerを参照し続ける。
- Entity.movement/lastPosは速度とは別のkinetic movement history。knockedBackと合わせてMotionFrameへ
  保存。nullable previous position、有限値検査、NBT roundtrip、fingerprintを追加。FakePlayerも
  同状態をcapture/validate/restoreし、適用前decodeとcanonical比較へ含める。
- LivingEntityのattack cooldown fieldはticksSinceLastAttack。旧capsule項目名を内部互換のため保持し、
  実fieldだけを変更する。lastKineticAttackTimeはworld tick基準の履歴で、loaded entity/FakePlayerの
  extension・validation・fingerprintへ保存する。Carpet ActionPackのnext/count等は巻き戻さない。
- これらのoffline testsは実Mixin適用・新Carpet/kinetic武器・装置のruntime qualificationではない。

## 26.1 — 非難読化namespace / Runtime Capsule v8

- [Fabric 26.1](https://fabricmc.net/2026/03/14/261.html)と実26.1非難読化JARを照合。
  Java 25 / Loom 1.15.5 / Gradle 9.4.0 / Loader 0.18.4を固定する。
  `net.fabricmc.fabric-loom`を使い、Yarn mappingsやremapJarは使用しない。SupervisorはJava 17。
  1.21.11 Yarn/officialの対応をowner・descriptor単位で解析してソースとfield matrixを移行する。
  自作method・payload key等をVanilla field名に合わせて一括置換しない。
- ChunkPosはrecordの`pack/unpack/containing`、normal/overlay messageは別メソッドになった。
  ContainerInput、独立したServerboundAttackPacketにも対応。attackとinteractionを別々に制限し、
  PacketUtilsのserver-thread移行の後でのみworld/player状態を読む。
- PrimaryLevelData.createTagはUUIDだけを受け取り、プレイヤーNBTを埋め込まない。
  BossEvents・DragonFight・ClocksはSavedDataへ移る。PORTABLE_GENERATIONのsealed fileがauthorityであり、
  古いlevel.dat setterを呼んで二重に復元しない。論理SavedData captureの未対応状態は保持する。
- ServerClockManager.tickはServerLevel.tickの外で呼ばれる。checkpoint/recovery freeze時はこの呼出しも
  抑止する。Runtime Capsuleをv8へ上げ、packStateの全clockをIDで正規化し、totalTicks・partialTick・
  rate・pausedを記録。公開setterでは小数位相を復元できないため近似はしない。
  sealed Vanilla SavedDataによる復元後に、WORLD_RUNTIME fingerprintで全項目を比較する。
  Clock CODECと一致する有限値・正のrate・partialTick範囲・重複ID/件数制限を検査する。
  旧capsuleのnullは「未保存」であり空集合とみなさず、STRICTでWORLD_CLOCKS_V8の欠落をblockする。
- EntityFluidInteractionのtag別Trackerはheight・eyesInside・accumulatedCurrent・currentCountを持つ。
  loaded entity NBT extensionとFakePlayer snapshotへ意味的値を保存し、decode/domain validation後に
  既存Trackerの値だけを復元する。final map/Java objectそのものを置換・serializeしない。
  通常のVanilla world upgradeには旧extensionでFluidTrackersがない場合を許すが、foreign checkpointは拒否。
  ENTITY_TRANSIENTとFakePlayer canonicalにも流体状態を含める。
- offline fixtureは当該JARにLoaderのpackage-access変換を適用し、Registry bootstrapだけを使う。
  ItemStack component bindingには実world registryが必要なため、非空inventoryの実機qualificationは未実施。
  bytecode/Codec/serialization testsを実Mixin・Carpet適用成功とは報告しない。

## 26.2 — サーバーID発行 / 硫黄タイマー

- [Fabric 26.2](https://fabricmc.net/2026/06/15/262.html)と実JARを照合。
  Loom 1.17.21 / Gradle 9.6.0 / Loader 0.19.3 / Java 25。Capsule v8。
- Entity.ENTITY_COUNTERはServerLevel.ENTITY_COUNTERへ移動。実getNextEntityIdはcounterを進めつつ、
  0と既に存在するnetwork IDを避ける。新Accessorでserver counterを参照する。
  既存modulo 40 phase保持を維持するが、全network IDや他Mod独自ID規則の完全復元は保証しない。
- Entity.syncPositionはServerEntity.sendChangesがconsume/resetする位置同期flag。
  world physicsそのものではなく、fresh trackerの初期同期で再生成されるvisual stateとして分類する。
- BedBlockEntityは存在しなくなった。監査表の旧classを除き、新PotentSulfurBlockEntityを監査する。
  waitingCountdownはnative NBTのcountdownに保存されるが、server tickerはsetChangedを呼ばず更新する。
  checkpoint cutで既にloadedの硫黄chunkをdirty扱いにし、古いcountdownの再利用を防ぐ。
  新chunkのloadや全chunk同時Future発行はしない。
  eruptionTickはnative NBTにないsound/particle位相。SulfurFrameでcountdownと位相を追加保存し、
  loadAdditionalで復元、SULFUR_RUNTIMEで位置別に比較する。setLevelの初期化条件も実bytecodeで確認した。
  新categoryが欠ける旧capsuleをMATCH扱いしない。

## 26.3 — 流体domain分離 / data-driven醸造

- [Fabric 26.3](https://fabricmc.net/2026/09/15/263.html)と実JARを照合。
  Loom 1.17.21 / Gradle 9.6.0 / Loader 0.19.5 / Java 25。Capsule v8。
- 流体状態はfluid-type別Trackerのordered Listと、tag別CurrentAccumulatorのreference mapへ分離される。
  contactのfluid holder ID・height・eyes、currentのtag ID・height・vector・countを区別して保存する。
  getCurrentAccumulatorForは最初に一致するtagを選ぶため、capture/decodeで順序を並べ替えない。
  全payloadの型・重複・finite・件数・registry holderとcurrent domain/orderを検証してから値を変更する。
  Trackerは実getOrCreateTrackerFor factoryを呼び、既存collectionは置換しない。fresh tracker Listへは
  保存順で追加し、既存Listが想定外のdomain/orderなら拒否する。
  private return typeに依存するため、非難読化namespaceのこのfactoryだけを検証済みreflectionで呼ぶ。
  JDK内部reflection/add-opensには依存しない。offline native factory再利用・順序拒否もテストする。
- 醸造はdata-driven recipesとcomponent fuelになる。totalBrewTime・totalFuel・speedMultiplierはnative NBT、
  quickCheck.lastRecipeは非保存の意味的レシピ選択。BrewingRecipeMatchとしてIDだけを追加保存する。
  保存scope内で既存ItemBrewing拡張と統合し、recipe manager/mapは置換しない。
  idleも含む醸造chunkをcutで保存対象にし、燃料・速度・総時間・recipe preferenceをfingerprintへ含める。
  終了後のinactive ingredient cacheは次のbrew開始前に上書きされるため、比較上は無効として正規化する。
- hurtMarkedはsyncVelocity、恒久無敵はpermanentlyInvulnerableに変わり、一時無敵時間はpublic getterで取得。
  interpolationはEntityのclient handlerへ統合される。Projectile.canBreakはnative AdventureModePredicate CODEC。
  Sign.allowOpFeaturesはnative allow_op_features NBTであり、AntiCrashが有効化することはない。
- ServerboundUseItem(On)Packetはrecord accessorへ変更。予測ACK・凍結中の操作拒否を維持する。
  chunkのinhabited timeは引数なしincrementInhabitedTime()でtickごとに増えるため、消えた
  lastInhabitedUpdate fieldへ書き込む旧restore処理を除く。
  NaturalSpawner.createStateのServerLevel引数に対応し、freeze時の空observer viewに使うchunk getterは
  読込みを行わない。simulationを進めてHUDを初期化する方式にはしない。

## 旧checkpointのMinecraft識別

新規manifestは、その版のCURRENT_MINECRAFT_VERSIONを保存する。JSON読出しだけはInstanceCreatorを使い、
版の記載がない古いcheckpointを原実装の1.18.2として読む。Gsonのfield initializer任せだと、
未記載なのに例えば1.21.4と判定してしまうためである。明記されたnull/foreign versionを自動修復しない。
CheckpointStoreのversion一致検査はpayloadの検査・world変更より前に実行する。
1.18.2で従来保存した未記載のcheckpointの意味は保持するが、移植版へ直接復元しない。
この互換処理は異なるMinecraft版へのdata migrationではない。

## 実機受け入れ試験

各版でMinecraft起動時のMixin適用確認、Carpetの版別handshake/rules、FakePlayer action timing、
checkpoint中のdirty mutation、Trencher等のcrash/recovery差分、STRICT拒否理由をユーザー環境で確認する必要がある。
今回その実サーバー試験は行っていない。上記の保存済みstateだけで未知の全Mod/装置の等価性を保証しない。

Production server modified: NO
