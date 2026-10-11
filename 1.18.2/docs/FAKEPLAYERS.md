# FakePlayers

## Optional Carpet integration

AntiCrashはCarpetをhard dependencyにしません。Carpet未導入時は本体、World checkpoint、Recovery、Real Player snapshotは使え、FakePlayer部分だけが無効になります。Carpet 1.18.2基準は1.4.69です。

`CarpetIntegration`はFakePlayer classを起動時に検出し、snapshot時にFakePlayerだけをReal Playerから分離します。Recoveryでは既存UUID/nameを探し、不在かつ`auto_rejoin=true`ならCarpetのFactoryまたは安全な`player <name> spawn` fallbackを試します。

## Restore order

```text
World restore
 -> server world load
 -> RECOVERY_FROZEN
 -> Required Chunk READY
 -> FakePlayer create
 -> dimension / position / rotation
 -> gamemode / inventory / selected slot
 -> flying / sneaking / sprinting / movement
 -> health / hunger / effects / riding
 -> ActionPack
 -> READY_TO_START
```

FakePlayerの復元に失敗し、`require_all_ready_before_start=true`なら`/ac start`は拒否します。Freeze中はUSE、ATTACK、Block Break等のmutation packetを既定で止めるため、復元中に装置を進めません。

STRICTではCarpetを検出したのにFakePlayerまたはTickSpeedのcapture/restoreに失敗した場合、`INCOMPLETE`/`UNSUPPORTED`のまま`READY_TO_START`へ進みません。`allow_unsupported_carpet_state=true`は、time warpなど安全に再結合できないCarpet stateを承知した運用だけで使う明示的な例外です。Carpet未導入は`NOT_APPLICABLE`で、FakePlayerが存在するcheckpointをCarpetなしで復元する場合は不足状態として停止します。

## ActionPack phase

Carpet内部のActionPackを反射的にJSON化します。設定で選択した次の状態はsnapshot時とrestore前に必須fieldを検証し、欠落したsnapshotを確定・適用しません。

- actions map、ActionType
- `currentBlock`、`blockHitDelay`、`isHittingBlock`、`curBlockDamageMP`
- `sneaking`、`sprinting`、`forward`、`strafing`
- `itemUseCooldown`
- Actionごとの`done`、`limit`、`interval`、`offset`、`count`、`next`、`isContinuous`

例えば`/player BB use interval 4`の内部`next=2`をcheckpointした場合、文字列コマンドを再発行して4へresetせず、snapshot JSONの`next`を復元します。ActionPack本体とfinal actions-map参照は維持しつつ、map内容は復元用Action objectで一括再構成します。Actionのfinal timing fieldはconstructorから再生成し、`done`、`count`、`next`を適用します。`currentBlock`はgeneric objectとして扱わず`BlockPos`（x/y/z）として明示保存・再構築し、旧snapshotのgeneric表現も互換読込みします。初期1.0.0が非採掘時の`currentBlock=null`を省略したsnapshotは、`isHittingBlock=false`かつ進捗0の場合だけnullへ移行します。採掘中の座標欠落は推測せず拒否します。

`TickSpeed`のtickrate、MSPT、`process_entities`、pause/freezeと、FakePlayerの`next`、`interval`、`count`、`continuous`等はRuntime fingerprintにも含まれます。Carpetのtime warpは実行者・callbackの参照を新しいServerへ安全に戻せないため、厳密なCheckpoint対象外として表示します。

## Limitations

Carpetのprivate class、field、Factory signatureは版によって変わります。AntiCrashは既知の名前を反射探索し、選択項目が欠ける場合はfail closedにしますが、未選択の全private field、完全なFactory再join、乗り物の複雑なmount順序までは保証しません。Fabric Carpet 1.4.69 JARのActionPack field構造は静的確認済みです。実際のFakePlayerでinterval use、continuous use、sneak/sprint、movement、selected slot、block break progressを確認するRuntime Reviewは別途必要です。

## Logical Deltaとの関係

`LOGICAL_DELTA`はFakePlayerの復元方式を置き換えません。FakePlayer本体とActionPackはRuntime Capsule／`fakeplayers.json`の既存経路で復元し、Chunk・Entityのlogical payloadはWorld viewの差分だけを担当します。candidate不足やCarpet capture/restore failureを「現在のFakePlayerでよい」と補完せず、Strictでは`READY_TO_START`へ進めません。通常のLogical Cut中はCarpetのActionPackをtickさせず、復元後の`/ac start`直前にAction timingとFakePlayer fingerprintを再検証します。

## Carpetのrule

Carpetのruleは、defaultにしたもの（`carpet.conf`）以外は再起動で消えます。checkpointにはCarpet本体と各拡張のruleの値を保存し、復旧時に設定し直します。通常の停止 → 起動（復元なし）ではCarpetの動作どおり消えるので、常に有効にしたいruleは`/carpet setDefault <rule> <値>`を使ってください。

## 乗り物

FakePlayerが乗っているトロッコ・ボート等（とその同乗Entity）は、切り出しの時点のNBTを`fakeplayers.json`に保存し、復元時にそのNBTから作り直してから乗せます。

Vanillaは、Playerが乗っているEntityをChunkのEntityデータへ書きません。その乗り物はPlayerのデータfile（`playerdata/<uuid>.dat`の`RootVehicle`）にだけ入ります。playerdataはcheckpointの対象外で、復元後に残っているのは「最後にPlayerが保存された時点」のものです。以前はFakePlayerが参加したときにVanillaがそこから読み込んだ乗り物をそのまま使っていたため、復元したcheckpointより新しい（または古い）位置の乗り物にBotが乗り、**Botの位置がcheckpointと違う**ことがありました。checkpoint時に乗っていなかったBotが、playerdata由来の乗り物に乗った状態で現れた場合は、その乗り物を取り除きます。

乗り物を保存していない古いcheckpointでは従来どおりの動作になります。実Playerの乗り物は対象外です（そのPlayerが再接続したときに、Playerデータから読み込まれます）。

## 使用中アイテムとcooldown

ActionPackとは別に、Player側の状態を保存・復元します: 使用中の手と残り使用時間（弓の引き、食事、盾）、最後の攻撃からのtick数、Item cooldown（残りtick）。FakePlayerがコンテナを開いていた場合、その画面は復元しません（`CONTAINER_SESSION`として非対応を表示）。
