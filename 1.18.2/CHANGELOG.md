# Changelog

## Unreleased - 2026-10-06

- **変更: `policy = "ASK"`は、通常の停止のあとの起動でも質問する。** これまでは、正常終了のあとはASKでも質問せずにそのまま起動していた。Worldを開く前に、停止時のcheckpointから再開するかをServer consoleで尋ねる。`Y`は復元して再開（動作の途中だった装置がそのまま続く）、`N`は保存されたWorldを通常起動。`ask_timeout_seconds`のあいだ無回答なら`N`として通常起動する（異常終了後は従来どおり`Y`）。AUTOとMANUALは変わらない。設定fileのコメントも実際の動作に合わせた。
- **修正: AntiCrashで復旧したあと、Carpetの一部機能（creativeNoClipなど）が使えなくなっていた。** 原因は2つ。
  - 復旧の凍結中、clientから届くModの通信（custom payload）をすべて破棄していた。凍結中に参加したPlayer（`/ac start`を打つために入る人）のCarpetの挨拶（`carpet:hello`）も捨てられ、ServerはそのclientをCarpet clientとして扱わず、ruleの値を送らなかった。client側とserver側の両方で有効になっている必要があるrule（creativeNoClip）は、そのPlayerが接続し直すまで効かなかった。ほかのModが自前のhookで受ける参加時の通信も同じように失われていた。挨拶と登録（`minecraft:register` / `unregister` / `brand`、`carpet:hello`）は凍結中でもそのまま通し、それ以外は破棄せずに保持して、再開後に届いた順でそのconnectionのnetwork threadから処理する（Fabric APIはnetwork thread上の呼び出しだけを処理するため）。Carpetのclient commandはcommand dispatcherを通るので、凍結中のcommand制限がそのまま効く。
  - Carpetのruleは、defaultにしたもの（`carpet.conf`）以外は再起動で消える。`/carpet creativeNoClip true`のように実行中に変えたruleは、復旧後に元へ戻っていた。Carpet本体と各拡張のruleの値をcheckpointに保存し、復旧時にCarpet自身のsetterで設定し直す（validatorが走り、clientにも通知される）。再開直前の照合に`CARPET_RULES`を追加した。
- 通常の停止 → 起動では、これまでどおりCarpetの実行中のruleは消える（Carpetの動作）。残したいruleは`/carpet setDefault`を使う。

## Unreleased - 2026-10-05

本番Serverのlog・checkpoint（閲覧のみ）とsourceの精査で見つかった不具合の修正。

- **修正（重大）: checkpointが削除されず、ディスクを使い続けていた。** retentionが「保持する世代のすべての祖先」を削除対象から外していたため、定期BASEのないchain（既定）では何も削除されなかった。`retention_count = 20`のServerに1,400世代・約100 GBが残り、1時間あたり4〜9 GB増えていた。副作用として、commitのたびに全世代のmanifestを読み直して15〜40秒かかり（以前は約1秒）、checkpointの間隔が30秒から約60秒に延び、停止時の最終checkpointが毎回5秒のtimeoutで破棄されていた。各世代はそれ自体で完全なfile集合（変わらないfileはhard link）なので、祖先の保護をやめた。たまった世代は1回のcommitで最大8世代・約2秒ずつ削除する。削除で共有inodeのctimeが変わっても次の世代がWorld全体をhashし直さないよう、最新世代の記録を更新する。
- **修正: Worldを読み込む前に終わった起動が、次の起動で「異常終了」と判定されていた。** run markerはModの初期化中に書かれるため、正常停止のあとにほかのModの設定errorで起動に失敗すると、次の起動が正常に保存されたWorldのrollbackを提案していた（ASKの無回答時は復元する）。Worldを開かずに終わった起動は判定に使わず、最後にWorldを開いていた起動の終了状態を引き継ぐ。
- **修正: 負荷の高いときに切り出しが数秒止まることがあった。** dirty chunkを16件保存するごとにdiskへの書き込み完了を待っていた（同期書き込みでは16回のfsync）。Chunkの生成やschematicの貼り付けで数百chunkを保存する切り出しでは、Serverが1〜6秒止まっていた（ゴーストブロックやラバーバンドの原因になる）。`PORTABLE_GENERATION`の切り出しはqueueに残った書き込みをqueueごと取り込むので、待つのはheapを守るときだけにした（上限はheapに比例。heapの残りが少ないときは早めに書き出す）。POIも同様。性能計測行に`chunk_save` / `chunk_drain`と`counts={savedChunks, ioWaits, queuedWrites, parentFilesRehashed}`を追加した。
- **修正: FakePlayerが乗っているトロッコ・ボートが、checkpointの時点の状態に戻っていなかった。** Vanillaは、Playerが乗っているEntityをChunkのEntityデータへ書かず、Playerデータfileにだけ保存する。そのfileはcheckpointの対象外なので、復元後のBotは「最後にPlayerが保存された時点」の乗り物に乗っていた（Botの位置がcheckpointと違う原因）。乗り物（と同乗Entity）を切り出し時点のNBTで`fakeplayers.json`に保存し、復元時にそこから作り直す。
- 修正: 復旧の凍結中にできた短命のchunk ticket（Chunkを参照するたびに1 tickだけ付くUNKNOWN ticket。読み込まれていないChunkならそのChunkも読み込む）を、再開の直前に取り除く。以前は、checkpointになかったChunkが数個読み込まれた状態で最初のtickが始まり、照合が`CHUNK_DOMAIN` / `BLOCK_ENTITY_TICKERS` / `SHORT_LIVED_TICKETS` / `CHUNK_RUNTIME`でMISMATCHになっていた。
- 修正: AntiCrash自身のBlock Entity参照（動作中ピストンの記録・照合）が、参照のたびにUNKNOWN ticketを作り、読み込まれていないChunkなら読み込んでいた。読み込み済みのChunkだけを見る参照に統一した。
- 修正: 拡張NBT（ピストンの進行度など）つきで保存されたChunkが、その後変更されないままunloadされると、拡張NBTがディスクに残り、同じ起動中に読み込み直したときにVanillaと違う状態（ピストンが半ステップ先）になり得た。unload時に拡張なしで書き直す。
- 修正: Chunk / Entityの保存中に例外が出ても、拡張NBTを書く範囲のflagが残らないようにした（wrapに変更）。
- 照合がMISMATCHのとき、何が違うかを表示する（`checkpoint only N: […]; now only M: […]`）。そのために、小さいカテゴリの照合用の行をRuntime Capsuleに保存する。
- checkpoint時に接続していた実Playerが再開時にいない場合、`ENTITIES … missing`のDEGRADEDにはせず、人数を通常logに出す。そのPlayerのticketが戻るのを最大200 tick待っていたのも、変化がなくなれば20 tickで打ち切る。

## Unreleased - 2026-10-04 — Checkpoint creation incident

- 親checkpointのctime/nlink driftはcontent hashで認証し、正常hardlinkを誤拒否しない。link減少だけで改変を許可しない。
- READY write guard、parent pin、永続quarantine、revision-bound process-local verify dedup、lazy parent validationを追加。
- startup candidate discoveryの256 KiB制限を16 MiB streaming metadata readへ修正。
- streaming JSON/checksum、clone-free cache accounting、heap-aware candidate上限、16-chunk batch、rolling IO backpressureでmemory amplificationを削減。
- OOM/親破損/予算不足のcircuit breaker、phase/headroom/failure diagnosticsを追加。intervalとheap指定は変更しない。
- v7継続。実証範囲・残る未確認事項は[incident report](docs/CHECKPOINT-INCIDENT.md)に記載。本番変更なし。

## Unreleased - 2026-10-01

実機での復旧検証（[docs/RUNTIME-QUALIFICATION.md](docs/RUNTIME-QUALIFICATION.md)）。隔離した専用Serverで「中断しなかった場合」と「checkpointから復旧した場合」をtickごとに全行比較し、見つかった差分を直した。結果はQUALIFIED。

- 再開の直前に、復旧用ticketを解除したうえでRuntime stateを取り直し、カテゴリごとにcheckpointと照合する（`OPTIMIZED`でも）。結果を1行でlogに出し、`/ac status`にも表示する。追加カテゴリ: `PISTON_RUNTIME`、`BREWING_RUNTIME`、`END_GATEWAY_RUNTIME`、`SCULK_RUNTIME`、`PORTAL_ENTITY_RUNTIME`、`ENTITY_TRANSIENT_RUNTIME`、`SHORT_LIVED_TICKETS`、`DIMENSION_RUNTIME`、`WEATHER_RUNTIME`、`CHUNK_RUNTIME`、`ENTITY_ID_PHASE`、`FAKEPLAYER_USE_RUNTIME`、`CONTAINER_SESSION`。古いcheckpointは`MISSING_FROM_OLD_FORMAT`になり、MATCHにはしない。
- 修正: ChunkのInhabitedTimeがcheckpointに古い値で残っていた（Vanillaは毎tick加算するがChunkを保存対象にしない）。読み込み済みChunkの値をRuntime Capsuleに保存して戻す。
- 修正: FakePlayerのplayer ticketが作り直される前にWorldを再開し、復旧用ticketが再開後も残ることがあった。checkpoint時の恒久ticketが再現されるまで再開を待つ。
- 修正: 復旧後の最初のtickでFakePlayerの攻撃cooldownが0に戻っていた（`PlayerEntity.selectedItem`が空で「持ち替え」と判定される）。
- 修正: 重なった経験値オーブのうちどれがmergeするかが再起動で変わっていた（条件がnetwork idの差で、idは振り直される）。
- 修正: 復旧後に新しく生まれたItemの動き出すtickが変わっていた（network idのcounterが0から始まる）。counterをcheckpoint時と同じ余りに進める。
- 開いたままのコンテナがあるcheckpointから復旧すると、`CONTAINER_SESSION=UNSUPPORTED`とDEGRADEDを表示する。
- 検証用のprobe mod（`src/probe`、本体のjarには含まれない）と検証script（`qualification/`）を追加。

## Unreleased - 2026-09-30

保存時のラグ（エリトラの挙動など）のさらなる短縮と、復旧直後に装置が壊れる問題の修正。

- Runtime State監査（`docs/RUNTIME-STATE-AUDIT.md`）。Minecraft 1.18.2がsave/reloadで保存しないがsimulationに影響する状態を、全BlockEntity 38 classと装置に使われるEntity 13 class、World、Chunk Ticket、Player/FakePlayerについて一覧化し、保存するもの・不要なもの・非対応のものを決めた。未調査は0件で、`RuntimeStateAuditCoverageTest`がMinecraftのclass定義と突き合わせる。
  - **動作中ピストンの進行度**（飛行式装置が再起動後に止まる原因）。Vanillaは1 tick前の値（`lastProgress`）だけを`progress`として保存し、読み込み時に現在値にも使うため、到着が1 tick遅れていた。実際の進行度と最後に進めたtickを追加で保存する。再開の直前に全ピストンをcheckpointの記録と照合し、`MATCH n/n`を出す。
  - **醸造台**: 醸造中の材料（`itemBrewing`）が保存されず、読み込み後の最初のtickで醸造が0からやり直しになっていた。
  - **End Gateway**: `teleportCooldown`が保存されず、cooldown中でも再起動直後に転送していた。`Age`は保存されるが毎tick進んでもChunkが保存対象にならず、古い値が残っていた。
  - **巣箱**: `TicksInHive`が保存対象にならず古い値が残っていた。
  - **スカルクセンサー**: 待機中の振動（1.18.2では保存されない）。
  - **Entity**: ポータル転送待ち、読み込み直後の1 tickだけ溶岩を検出しない状態（`firstUpdate`）、ダメージ無敵時間、乗り物の再搭乗cooldown、ItemEntityと経験値オーブの`age`、ItemEntityの移動判定の位相。
  - **World**: ディメンションの無人timer（`idleTimeout`。再起動で0に戻り、止まっていたEntityが300 tick動いていた）、天候の遷移値、`lastMobSpawningTime`（再起動後の最初のtickで全ChunkのInhabitedTimeにWorldの年齢が加算されていた）。
  - **Chunk Ticket**: 期限つきのPORTAL・POST_TELEPORT・UNKNOWNを残り時間つきで作り直す。AntiCrash自身の復旧用ticketは、checkpoint時の恒久ticketが再現できていれば最初のtickの前に解除する（以前は再開後も100 tick残り、unloadされるはずのChunkを保持していた）。
  - **tick順序**: Block Entity、Entity、トーチの焼き切れ履歴を`OPTIMIZED`でも復元する。
  - **FakePlayer**: 使用中アイテム（弓の引き時間など）、攻撃cooldown、Item cooldown。
  - 追加データは`AntiCrash`という1つのversion付きcompoundにまとめ、Vanillaが保存する値は上書きしない。書くのは読み込まれたまま保存されるとき（checkpoint・自動保存・停止時）だけで、Vanillaがunloadして読み込み直す場合の動作は変えない。
  - ホッパーの`lastTickTime`は調査のうえ保存不要と判断した（理由は監査文書と`HopperLastTickTimeTest`）。
  - 開いたままのコンテナ（トラップチェストの閲覧者数など）は安全に復元できないため、`CONTAINER_SESSION`として非対応を明示する。
- 復元が不完全な場合の扱いを変更。順序やticketを復元できなかったとき、以前はログだけ出して再開していた。`復旧が … で不完全（DEGRADED）です`を重要ログに出し、`/ac status`に次の復旧まで表示する。
- 読み込み済みだがtickしていないChunkに動作途中のピストンが残っていると、checkpointが`duplicate or unready moving piston`で失敗していた問題を修正（Vanillaで起きる正常な状態）。
- Runtime Capsuleをv7にした（v1〜v6は引き続き読める）。新しいjarで作ったcheckpointは、古いjarでは読めない。
- ディスク使用量と書き込み量を削減。
  - 定期的なBASE（`base_interval = 20`、`max_chain_depth = 32`）を既定で無効化（`0`）。BASEは毎回World全体（s1では19 GB）を新しくcopyしており、s1では約11分ごと、1日約1.5 TBを書き込み、保持中のcheckpointに常にWorld約2つ分（約38 GB）を置いていた。各世代はそれ自体が完全でchecksum付きのfile集合で、復元は親を参照しないため、BASEが増やすのは同じWorldのcopyだけ。設定すれば従来どおり作れる。
  - crash-raw（復元前のWorld）で、前回のcrash-rawと内容が同じfileをhard linkで共有するようにした。一致は両方のfileのSHA-256で確認し、各crash-rawは自分のchecksum一覧で個別に検証できる。以前は復元のたびに19 GBを丸ごとcopyしていた（s1では4回分で74 GB）。
  - 共有前に保存された古いcrash-raw同士も、Server起動後にbackgroundで同一fileを共有にする（読み込みは64 MB/sまで、低優先度）。

- checkpointの切り出しで、Chunk・Entity・POIの書き込みがディスクに届くのを待たないようにした。`sync-chunk-writes=true`ではその1件ごとが同期書き込みで、s1の停止時間（約0.17〜0.33秒）の大半がこの待ちだった。IO workerを一瞬だけ封印し、未書き込みの書き込みを写し取る。そのあいだにファイルを切り離し、写した書き込みはWorld再開後にworkerがcheckpointのコピーへVanillaと同じ形式で書き込む。本番Worldへの書き込みと`sync-chunk-writes`の同期性は一切変えていない。テスト環境（s1 Worldのcopy、FakePlayer 8体）で、保存の段階は60〜110 msから3〜9 msになり、停止時間は約110〜160 msになった。終了時checkpointとServer停止後のWorldを全Chunkで比較し、内容の一致を確認した（違いはVanillaが停止時にもう一度保存した9 Chunkの`LastUpdate`だけ）。
  - Entityのflushは、読み込み中のEntity sectionの読み込みだけを待つ（書き込み完了とfsyncは待たない）。
  - 切り出しの開始から終了までは、IO workerが新しい書き込みを始めないようにする（読み込みは止めない）。封印時に「実行中の1件」を待つ必要がなくなる。復旧直後など書き込みが途切れない状態では、region fileのcacheから外れる際のfsyncを含むこの1件の待ちが1回50〜200 msになっていた。書き込みは切り出し後に同じ順で再開する。
  - 書き込み待ちのChunkを読み込むとき、Vanillaはqueue上のNBTそのものに`__context`を書き込む。封印したNBTだけは読み込み側にコピーを渡し、万一書き換え中を検出した場合はそのcheckpointを保存せず失敗させる。
- 復旧時の既定`recovery.preload_mode`を`CRITICAL`から`ALL`へ、`max_preload_chunks`を256から100000へ変更。`CRITICAL`ではcheckpoint時に動いていたChunkの大半（s1では3451 Chunk）が準備されないまま再開し、再開後の数秒〜数十秒で順に読み込まれていた。その間、複数Chunkにまたがる装置は一部だけが動いて壊れ得た。`ALL`はcheckpoint時にloadedだった全Chunkを当時のレベル（entity-ticking・block-ticking・loaded）まで準備してから再開する。従来の`ALL`はblock-tickingだけのChunkをloadedのレベルでしか準備していなかったため、これも修正した。既存の設定ファイルに`CRITICAL`が書かれている場合は変更されないので、必要なら書き換えること。

## Unreleased - 2026-09-28

Pingが正常なのに発生していたラグ（ロールバック、開いたままのシュルカー、戻らないエリトラ、視点の巻き戻り）の修正。s1のログでは、Serverのtick遅延警告119件のうち118件がcheckpointの凍結によるものだった。

- checkpointの凍結中に届いたPlayerのpacketを破棄していた問題を修正。移動が捨てられて「moved too quickly」の引き戻しと視点の巻き戻りが起き、画面を閉じるpacketが捨てられてコンテナが開いたまま、`START_FALL_FLYING`が捨てられてエリトラがClient側だけ滑空状態のままになっていた。凍結中はServerのtask queue（packetとconsole command）を保留し、凍結後に届いた順で処理する。凍結が複数tickに渡る場合はPlayer自身のtickも止める。Recovery lockもcheckpoint中には適用しない。
- checkpointの凍結時間を短縮（旧：平均約1.8秒、最大31秒）。凍結中に行っていた次の処理をやめた、またはWorld再開後のworkerへ移した。
  - 未変更fileの約10,000個のhard link作成（全fileのstatは凍結中に並列で行う）。凍結中にcopyするかどうかは、size・mtime・ctimeの比較と、`RegionBasedStorage#write`の記録の両方で判定し、どちらか一方でも変更を示せばcopyする。
  - 変更fileのSHA-256計算（copyの前後で2回）。凍結中はcopyだけを行う。
  - 約20世代ごとのBASEで、Live World 19 GBを丸ごとcopyしていた処理（25〜31秒の凍結）。BASEは親checkpointからworkerがcopyする。
  - Vanillaのflush保存（全region fileのfsync、全ChunkHolderの待機、強制アンロード）。flushなしで保存し、IO queueの書き込み完了だけを待つ。
  - 凍結を複数tickに分けていたことによる、tickごとの待ち時間（通常は切り出したtickの中で終わる）。
  - 凍結中の状態遷移ごとの`recovery-state.json`のfsync（3回）。
- 凍結中にBlock Entityが削除されると、削除済みtickerの重複でcheckpointが`Runtime capsule contains an invalid ticker order row`で失敗していた問題を修正（1日約150回、毎回凍結だけが発生していた）。
- 保存漏れを修正。アンロード待ち・アンロード中のChunk、`ReadOnlyChunk`に包まれた変更済みChunk、保存されていないPOIが必ず保存されるようにした。POIのIO queueの完了を待たずにPOI fileをcopyしていた問題も修正。
- 切り出しの一貫性を強化。
  - 切り出しは必ず1回の呼び出し（tickの間）で完結させる。旧実装は2〜5tickにまたがっていたため、その間にCarpet（scarpet）やFabric APIのtickイベントなど他Modのtick処理が動き、Runtime Capsuleを取った後のWorldを変えられた。
  - Entityのflush（読み込み中のEntity sectionの取り込みを含む）をRuntime Capsuleより前に行い、Capsuleとfileが同じ状態を指すようにした。
  - 切り出し中の保存でticket処理（Chunkの読み込み・昇格・降格）を行わないようにした。
- scheduled tickの登録ごとに診断用の文字列を組み立てていた処理を、診断中だけに限定した。
- 使われないLOGICAL_DELTAの候補作成を、POI/`data/`のあるWorld（通常のWorldはすべて該当）では行わないようにした。この処理は1 msの予算とは別に、毎tick全Chunkの一覧を作り、比較のたびに文字列キーを作って並べ替えていた。キーは1回だけ作るようにした。LOGICAL_DELTAが使えない間は、ブロック変更・Block Entityの`markDirty`・scheduled tick・Entityの移動ごとに動いていた記録処理も止める（使えるようになったときは、それまでの記録を信用せず次のcheckpointで取り直す）。毎回のFallback警告は理由が変わったときだけ出す。
- 世代検証の比較をO(n²)からO(n)にした（約12,000 fileの世代で約1億回の比較）。
- FakePlayerがいるcheckpointが、すべて復元時に`Checkpoint manifest counters do not match recovery payloads`で拒否されていた問題を修正。checkpoint時の指紋はActionPackのnull項目（採掘していない間の`currentBlock`など）を含むが、`fakeplayers.json`ではGsonがnull項目を省くため、ファイルから計算し直した指紋と一致しなかった。null項目を除いて比較する。修正前の指紋は再現できないため照合しない（ファイル自体はSHA-256で検証済み）。
- FakePlayerの動作（USEやATTACKの継続など）が復元されず、`FakePlayer ActionPack verification failed`で復旧に失敗していた問題を修正。CarpetのActionTypeは定数ごとに本体を持つため実行時の型が`ActionType$1`になり、新しく作ったFakePlayerの空の動作表へキーを戻せなかった。列挙型本体の名前で記録・照合し、修正前の`ActionType$1`形式も読み込める。照合失敗時は差分の場所をログに出す。
- `bot.bed`のように`.`を含む名前のFakePlayerがいると、復旧が`Invalid FakePlayer name`で失敗していた問題を修正（Vanillaと同じく空白を除く印字可能ASCII 16文字まで許可）。
- `recovery.preload_mode = CRITICAL`でv6のcheckpointを復旧すると、準備対象外のFULL chunkを予定tickの入れ替え対象として要求し、`Scheduled-tick recovery chunk is not accessible`で必ず失敗していた問題を修正。予定tickを持つChunkは引き続き必須で、持たない未読み込みChunkは復元したChunkのデータ（cut時点のqueue）に任せる。
- 内容が変わらないEntity chunkの再書き込みを省くようにした。本番Worldでは連続するcheckpoint間でEntity chunkの約96%（1,178/1,233）が同一で、`sync-chunk-writes=true`では1件ごとに同期書き込みになるため、Entityの保存だけで切り出しが1秒以上止まっていた（修正後は約0.1秒）。書き込みに失敗したChunkは次回必ず書き直す。
- Chunkの昇格途中（entity-tickingの準備が先に終わった瞬間）にcheckpointを取ると`Entity-ticking chunk is not block-ticking`で失敗していた問題を修正。Vanillaで実際に起きる状態なので許容し、復旧はentity-tickingのレベルで準備する。

## Unreleased - 2026-09-27

保存漏れ・再起動時の不具合の修正。

- LOGICAL_DELTAのEntity保存を修正。`writeNbt`で保存していたため`id`が無く、復元時にEntityが全て捨てられていた。Vanillaと同じく`shouldSave`/`saveNbt`で保存し、Player・乗客の重複保存をやめ、読み込み途中のEntity sectionを空として上書きしないようにした。空のEntity chunkは削除し、書き換えたChunkにはtombstoneを適用しない。
- LOGICAL_DELTAの`level.dat`に`Data`の階層が無く、復元後のWorldが読めなかった問題を修正。world border・カスタムボスバー・エンダードラゴン戦もVanillaの保存と同様に更新してから保存する。
- POI/`data/`の有無の確認がOverworldだけで、ネザー・エンド・カスタムディメンションを見ていなかった問題を修正。
- `/save-off`中はCheckpointを作らないようにした（Vanillaが保存するのはChunkだけで、Entityと`data/`が保存されず中身が食い違うため）。rolling保存も止める。
- hopperのクールダウン、かまど系の燃焼・精錬進行、醸造台の醸造時間、スポナーのDelay、コンパレーターの出力値は、変化してもChunkが保存対象にならずCheckpointに古い値が残っていた。変化したときにChunkを保存対象にするよう修正。
- 復旧後`/ac start`前に`/stop`すると（`RECOVERY_FAILED`を除く）、次回起動が通常起動になりRuntime State（Block Event、予定tickの順序、Moving Piston、FakePlayer）が失われていた問題を修正。同じCheckpointの復元をやり直す。ASK/MANUALの異常終了判断待ちで停止した場合も判断待ちを再開する。`/ac start`後は選択中Checkpointの記録を消すようにした。
- 復旧凍結中はEntityが読み込まれず、再開後の最初のtickだけEntityがBlockより1tick遅れていた問題を修正。凍結中もEntityの読み込みだけを進め、必要ChunkはEntityの読み込み完了まで準備完了にしない。
- `recovery.preload_mode = CRITICAL`で予定tickのあるChunkをFULLで準備していたため、Checkpoint時にtickしていたクロックやチャンクローダーが再開後に止まったままになる問題を修正。Checkpoint時に観測したtickレベル（それより上げない）で準備する。
- BASE世代で復旧の時系列（親の連鎖）が途切れ、最新が壊れているときに古いCheckpointへfallbackできなかった問題を修正（manifestに`timelineParentCheckpointId`を追加）。
- `latest`指定の復元が、以前の復元で放棄した枝の新しいIDを選び得た問題を修正。現在の時系列から選ぶ。
- `/ac checkpoint restore`と`/ac checkpoint info`がServer thread上でWorld全体をSHA-256検証し、大きなWorldではWatchdogで落ちる恐れがあった問題を修正。World置換前の全体検証は従来どおり行う。
- compaction中に作られたCheckpointが、compaction完了時に古い世代で上書きされる問題、compaction結果がfsyncされない問題を修正。
- `/ac checkpoint`でBASEが無い場合などに状態が`CHECKPOINT_REQUESTED`のまま残り、定期Checkpointが止まる問題を修正。
- `/stop`時のfinal checkpointで、Chunk保存に必要なmain thread taskが実行されず必ずtimeoutしていた問題を修正。
- チャットでのASK回答（Y/N）がNetwork thread上で`server.stop`まで実行していた問題を修正。取引ジャーナルが1操作につき2回記録される問題を修正。
- Real Playerの位置復元がクライアントへ送られていなかった問題を修正。FakePlayerのチャンク読み込み位置も即時に更新する。
- Supervisorが自身の終了（SIGTERM等）を子Serverへ伝えず、Minecraftが孤立して残る問題を修正（`--shutdown-grace-ms`、既定120000）。

## 1.0.0 - 2026-08-26

- Zero/Low-Pause Checkpoint Engineを追加。AUTOをPortable Generationへ切り替え、Logical Cutで独立file setと`generation-seal.json`を確定してからWorldを再開し、Runtime payload/manifest/checksum/READY/retentionをbackground finalizerへ分離。
- BASE/DELTA世代、PRESENT/ABSENT/TOMBSTONE、親dependency-safe retention、periodic base boundary、durability mode、I/O throttle、monotonic phase/JVM/GC profiler、rolling preparation予算を追加。
- v3 Checkpoint Manifestへgeneration seal・file set・logical cut・performance evidenceを追加。古いcheckpointは後方互換で読めるが、portable sealの欠落をMATCH扱いしない。
- `docs/CHECKPOINT-ENGINE.md`、`docs/CHECKPOINT-CONSISTENCY.md`、`docs/CHECKPOINT-PERFORMANCE.md`を追加し、AUTO/legacy backend境界と実機検証範囲を明記。
- Supervisorが安定稼働後の非0終了で停止せず、短時間失敗burstの再試行回数だけを制限するよう修正。
- Minecraft 1.18.2 / Fabric Loader 0.17.3 / Java 17向けのServer Mod基盤を追加。
- Tick境界checkpoint、atomic commit、SHA-256 integrity、retention、Java incremental、Reflink、Btrfs、CRIU experimental backendを実装。
- dirty/clean marker、AUTO/ASK/MANUAL、pre-world restore、Recovery Freeze、chunk/block-event Runtime Capsuleを実装。
- Real Playerの個別snapshot、Carpet optional FakePlayer/ActionPack復元、Consistency Journal、`/ac` commands、logging rotationを実装。
- 任意起動のJava 17 Supervisorを追加。
- Unit Test、README、設計・運用ドキュメントを追加。
- checkpointの前回分整合性検証を通常tick中のpreflightへ移し、retention時の全checkpoint再hashを廃止。Live World copy後は`CHECKPOINT_COMMITTING`へ移ってtickを再開するよう修正。
- Java incrementalの変更なしfileをsize/mtime/ctimeで判定し、検証済み前回checkpointから再利用。変更fileだけをLive Worldからhash/copy/再hashするよう修正。
- `/stop`をcheckpoint完了までcancelしてServerを擬似稼働させていた停止経路を廃止。`checkpoint.shutdown_timeout_seconds`（既定5秒）を上限に待ち、超過時は未確定checkpointを破棄して通常停止を続けるよう修正。
- `AUTO`でReflink失敗後、同一セッション中に失敗を繰り返さずJava incrementalを直接選ぶよう修正。
- `/ac start`を既定でpermission level 0（OP不要）にし、その他のコマンドカテゴリと強制開始を`[commands]`から個別設定できるよう修正。
- ライセンス表記をApache License, Version 2.0へ変更。
- `config/anticrash.toml`へ`language = "ja"`（`ja`/`en`、既定は`ja`）を追加し、AntiCrash独自のコマンド・復旧・checkpoint・snapshotログを切り替え可能にした。
- crash-raw保存時にコピー元ディレクトリのルートを安全な子パスとして誤検出していた問題を修正。
- `ASK`でclean shutdown後も終了直前のcheckpoint IDを表示してY/Nを待ち、Yで復元予約、Nで復元なし起動するよう修正。`ask_timeout_seconds`（既定60秒）無回答時はcleanならN、dirtyならYとして扱い、console・Player chat・`/ac y|n`から回答可能にした。
- Minecraft 1.18.2のJVM shutdown hook（`kill`/SIGTERM）が呼ぶ`server.stop(true)`を正常停止と誤認し、ASKで無回答時Nになっていた問題を修正。Minecraft Server thread自身からの停止だけをcleanと判定し、ASKにも前回の終了状態を表示するようにした。
- Recovery Freezeで最初のWorld Tickを止めた状態でも、Carpet 1.4.69の`mobcaps` HUDが未初期化のSpawn情報を参照してServerを落とさない互換処理を追加。
- `allow_login=false`の接続拒否で初期化前のPlayer network handlerを参照していた問題と、`/ac start`後に初参加したReal Playerへsnapshotが適用されない問題を修正。
- shutdownで中断されたcheckpoint事前検証を破損扱いして、保持中checkpointすべてへ誤った「無効」警告を出す問題を修正。
- dirty exitのASK/MANUALと`latest`指定が、最新Validを決める前に保持中checkpointを全件SHA-256検証して起動を長時間無ログで止める問題を修正。候補を新しい順に逐次検証し、最初のValidで打ち切るよう変更するとともに検証開始・完了ログを追加。
- ASKの`Y`や復元コマンドがpending restoreを書いて正常停止するだけで、直接起動時およびSupervisor利用時にServerが再起動されなかった問題を修正。復元時限定のcontrolled restart marker/helperを追加し、通常の`/stop`とは分離。
- `READY_TO_START`または`RECOVERY_FROZEN`中にReal Playerがログインした場合、ゲーム内時間が停止中で`/ac start`から再開できることを日本語・英語で2行表示するよう追加。
- checkpoint manifest format 2へ認証済み`parentCheckpointId`を追加し、過去checkpointへの復元後に放棄済みの新しいbranchへ数値IDだけでroll-forwardする問題を修正。legacy format 1はactive IDより古い世代だけへfallback。
- 復旧Chunkごとにticket level `2`を付けて広大な周辺範囲を同期ロードし、2,489 Chunk・`-Xmx1G`の実ログでOOMになっていた問題を修正。FULL=`33`、entity-ticking/forced=`31`へ訂正し、既定`CRITICAL`（forcedと正確な復元anchorのみ）、上限付きpreload、最大2 Chunk/tickの段階準備へ変更。
- 復旧用replacement JVMへ`-XX:+ExitOnOutOfMemoryError`を付与し、helper/SupervisorがOOMをprocess failureとして検出できるよう修正。直接helperは異常終了・新規crash reportを監視して最大3回再試行し、stdout/stderrは同じconsoleへ継承。
- `/ac start`受理時の文言を再開予約として明確化し、実際に`RUNNING`へ遷移した時点で全Real Playerへ日本語・英語の再開完了メッセージを送るよう追加。
- controlled restartのSupervisor判定をflag+固定marker pathのhandshakeへ強化し、pending restore適用後にstale markerも除去。RESTORING stateを永続化できない場合はServer停止を開始しないよう修正。
- Carpet ActionPackの`BlockPos currentBlock`を明示形式で保存・復元し、選択したactions/timing/cooldown/block-break fieldをsnapshot時とrestore前に厳格検証するよう修正。
- checkpoint format 2 lineage、crash-raw/checkpoint integrity metadata、state/config atomic write、bounded read、symlink/path traversal対策、process lockを追加・強化。
- checkpoint中のコマンド許可を`/stop`、`/ac help`、`/ac status`の完全一致へ限定し、prefixによるmutation bypassを修正。
- log messageを64 KiB、transaction journal行を1 MiBに制限。巨大NBTはtransaction境界を残して省略し、WARN modeではPlayer/NBTを含まない最小recordだけを永続化。
- log rotationを`.1`が最新となる世代順へ修正し、config生成templateへ全設定の用途・危険性・権限・言語・Recovery timeoutの詳細コメントを追加。
- ASK/MANUALでValid checkpointがない場合に不可能なY/timeout復元へ進まず、明示continue/abortを待つfail-closed経路を追加。`--force`はpre-world restore後のsemantic validation失敗だけに限定。
- World置換前の完全SHA-256検証後、World読込後にも同じ10.9 GB級Worldを再hashしていた重複を廃止。2回目は認証済みchecksum listに対するmanifest/runtime/Player/FakePlayer payload再検証だけに限定。
- 復元payloadの欠損・symlink差し替え・counter/dimension不一致・重複Chunk・不正Player/FakePlayerをWorld runtime変更前に拒否し、staging copyもmanifest SHA-256と再照合するよう強化。
- 復旧失敗時にTemporary Chunk Ticketを即時解放し、失敗状態でChunk保持によるMemory圧迫が継続する問題を修正。
- 旧checkpointが非採掘状態の`currentBlock=null`を省略していた場合だけ安全に移行し、採掘中の欠落は引き続き拒否する互換処理を追加。
- Compound専用NBT readerにより`player_snapshot.ender_chest=true`のList rootを復元できなかった問題を修正。任意root型対応と16 MiB allocation limitを追加。
- AntiCrash file logへの改行注入と、Transaction Journal読込み時のUTF-8 byte上限迂回を修正。
- 1.18.2の`PistonBlockEntity`が進行度更新時にchunkをdirty化しないため、通常保存とAntiCrash checkpointでmoving pistonのNBTが古くなる問題を修正。
- checkpoint直前に既存chunkのmoving pistonとchunk-local scheduled tickを保存対象へ追加し、対象数をログとmanifestへ記録。
- 新規chunkのロード、全chunkのdirty化、lighting/chunk loadingの非同期処理変更は行わない。
- `/ac`単体の安全な案内、権限に応じた複数行help、checkpoint IDのTab補完、config reload後のcommand tree再送を追加。
- コマンド応答の制御文字除去・512文字上限と、checkpoint/FakePlayer/journal一覧の20件表示上限を追加。
- 新規設定・内部state・restart marker・AntiCrashログをPOSIX `0600`、新規内部directoryを`0700`で作成し、既存の危険なpermissionを警告するよう強化。
- checksum無効化、公開管理コマンド、復旧中mutation許可を明示的なセキュリティ警告として表示。
- ASK timerのoverflow、過大なlog rotation世代数、過大なinterval/ticket保持値を安全な上限で拒否。
- Supervisorのsymbolic-link workdirを拒否し、dry-runのpassword/token/secret引数を伏せ字に変更。
- controlled restart markerはUUIDと作成時刻を持つ正規形式だけを受理し、破損・偽markerを保持したままFail-closedに変更。
- 前回の異常終了で残った`cp-XXXXXX.tmp` checkpoint、`.tmp-<uuid>` crash-raw、`restore-work` stagingを起動時に破棄するよう修正。retentionはこれらを対象外にするため、異常終了のたびに未完了コピーがディスクを消費し続けていた。
- checkpoint commit時にcheckpoint tree全体を2回走査してmanifest payload checksumとchecksum listを別々に生成し、manifest.jsonを2回書き出していた重複を廃止。走査とmanifest書き込みを各1回に統合。
- `retention_max_gb = 0`（既定）でも毎commitで保持中checkpoint全世代の全fileをwalk/statしてサイズ合計を計算していた問題を修正。サイズ軸が有効なときだけ計測し、削除時の再walkも廃止。
- `AUTO` backend選択が確定済みbackendへ`isSupported`を再実行し、btrfs環境で`btrfs subvolume show`を毎checkpoint余分に起動していた問題を修正。
- `CRIU_EXPERIMENTAL`の`isSupported`がsymbolic linkを追う`Files.isDirectory`で判定し、`JAVA_INCREMENTAL`が拒否するWorld pathを受理し得た不整合を修正。
- `/ac recovery continue`がASK promptのreminder timerとprompt flagを解除しないまま復旧を継続していた不整合を修正。
- Packet mixinのtransaction記録がfreeze判定と異なりcontrollerのnull検査を行っていなかった不整合を修正。
- 未使用のimport、重複したcheckpoint一覧API、二重に生成していた`CheckpointStore`、参照されないmessage keyを整理。
- `WAITING_FOR_CHUNKS`で必要Chunkがentity-ticking readinessへ到達しない場合にrecoveryが無期限に凍結し続ける問題を修正。`recovery.chunk_ready_timeout_seconds`（既定120秒、`0`で無効）を追加し、準備がまったく進まなくなった場合だけticketを解放してfail closedにする。進捗がある限り発動しないため大きなpreload計画は打ち切られない。
- 親checkpointへのhard link（＝commit時にfsync済みのinode）まで毎回fsyncしていた問題を修正。manifestへ`reusedFromParent`を追加し、新規に書いたfileだけをforceするよう変更。
- checkpoint復元時、checkpointに存在しないdirectoryが空のままLive Worldに残っていた問題を修正。checkpoint後に増えたdirectoryを、中身がなくなった時点で削除するよう変更。
- 到達しない`stop` cancel分岐（`interceptStop`を`onStopRequested`へ変更）、`captureFinal`のfinal job bookkeeping、`MarkResult.transientChunks`、書き込み専用の`CheckpointManager.lastError`、`verifyInternal`の重複path判定、`recovery.ask_prompt_no_timeout`が描画しない引数を削除。
- 呼び出し元のない公開API（`AntiCrashController.worldRoot/logger/server`、`CheckpointManager.worldRoot/lastError/listValid`、`ChunkStateSnapshot.requiredCount`、`PathSafety.isSymlink`、`CarpetIntegration.available`、`ChunkRecoveryManager.begin(server, capsule)`、`AntiCrashLogger.fatal/verbose/trace/traceKey`）と空のtest packageを削除。
- 意図的なWorld凍結中に画面上の手掛かりが何もなく、Playerには原因不明の停止に見えていた問題に対応。`[notifications]`を追加し、`frozen_action_bar`（既定true）でcheckpoint作成中・必要Chunk `n/m`・`/ac start`待ち・復旧失敗などの現在phaseをaction barへ表示し、凍結解除時に消去するようにした。`checkpoint_action_bar_delay_ticks`（既定10）未満の短いcheckpointでは表示しない。
- ASKの質問がconsoleにしか出ず、ゲーム内の管理者は凍結理由を知る手段がなかった問題に対応。`ask_prompt_in_game`（既定true）で回答権限を持つPlayerへprompt・reminder・`RECOVERY_DECISION`中の参加時にも表示し、`/ac y` / `/ac n`を案内するようにした。
- `/ac start`の拒否理由が「statusとlogを確認してください」だけだった問題を修正。必要Chunk未準備（READY n/m）、ASK回答待ち、FakePlayer未準備、`--force`が必要、state永続化失敗などの具体的な原因を応答へ含める。`checkStartValidation`は同じ判定から導出するため、booleanと理由が食い違わない。
- `/ac status`にrecovery中の開始阻害理由、次回checkpointまでの残り時間、ASKの残り時間を追加。
- `/ac checkpoint list` / `/ac list`がIDだけを並べ、どれがいつのものか判断できなかった問題を修正。manifestを読まずに済むディレクトリ時刻からcommit時刻を併記する。
- Checkpoint barrierがdirty chunkを1 tickあたり4件しか保存せず、そのたびにIO drainを待っていた問題を修正。server tick loopは凍結中も20 tick/sで進むため、1,011 dirty chunkが13.4秒のWorld凍結になっていた。`checkpoint.save_batch_chunks`（既定256）件ごとに1回drainし、1 tickの時間予算内は同じtickで保存を続けるよう変更。heap上限は据え置きのままdrain回数を1/64にした。
- Session最初のcheckpointが、変更のないWorld file全体をSHA-256で読み直す処理を`CHECKPOINTING`凍結中に行い、約10 GBのWorldで25.8秒Worldを停止していた問題を修正。`LiveWorldScan`を追加し、incremental base検証の完了後・最初のcheckpointの前にその処理を凍結なしで実施する。自身のhash前後でmetadataが変わらなかったfileだけをcacheし、backendはsize/mtime/ctimeが完全一致する場合だけ信頼するため、走査中に書き換わったfileは従来どおり凍結中にhashする。cacheはcommit成功時に破棄する。
- 上記の事前hashが完了するまで最初の定期checkpointを開始しないようにした（`/stop`時の最終checkpointは待たずにcancelする）。
- checkpoint barrierとRecovery Freezeで拒否したpacketに訂正を返さず、ゴーストブロックが多発していた問題を修正。Vanillaは`onPlayerAction`に`PlayerActionResponseS2CPacket`、`onPlayerInteractBlock`に影響block 2件の`BlockUpdateS2CPacket`、`onClickSlot`に`ScreenHandler.syncState()`を必ず返し、Clientはそれを受けて先行描画を取り消すが、HEADでcancelするだけではこの応答が失われClient側に破壊・設置が残り続けていた。拒否時に同じ訂正を送るよう変更し、inventory/container系の拒否でも`syncState()`を返すようにした。
- 訂正の送信とWorld読み取りを`MinecraftServer.execute`でServer threadへ載せるよう修正。HEAD injectionはpacketがServer threadへ移る前のNetty threadでも実行されるため、そのままではWorldをoff-threadで読むことになる。
- `onPlayerInteractBlock`のcontainer判定が、Netty threadから`World.getBlockEntity`を呼び、`ServerChunkManager.getChunk(create=true)`経由でServer threadへの同期待ちを発生させ得た問題を修正。判定結果が拒否可否を変える場合だけ、Server threadでの2回目の実行時に読むよう変更。
- checkpoint作成中に接続したPlayerが即kickされていた問題を修正。`[login] retry_timeout_seconds`（既定5秒、`0`で従来動作、上限20）を追加し、AntiCrashがloginを拒否する間は接続を保留して毎tick受け入れを再試行する。Vanillaは`READY_TO_ACCEPT`のときだけ`ServerLoginNetworkHandler.tick`から受け入れを呼びそこでstateを変えないため、tickを見送るだけで次のtickに再試行される。slow-login timeoutの加算も同じmethod末尾にあるため保留中は停止する。
- 再試行の対象をAntiCrashの拒否だけに限定。ban・whitelist・満員などVanillaの理由は`PlayerManager.checkCanJoin`が返すもので、保留中のloginはそこへ到達せず、保留終了後・非凍結時はVanillaが従来どおり判定する。拒否条件は`AntiCrashController.isLoginBlocked()`に集約し、login gateと保留判定が食い違わないようにした。
- 凍結中のsnapshotがper-file statに支配されていた問題を修正。`Files.isDirectory`/`isSymbolicLink`/`isRegularFile`/`readAttributes`/`getAttribute("unix:ctime")`/`getAttribute("unix:nlink")`はそれぞれ個別にstatを発行するため、変更が11 fileでも10,000 file分のprobeを払っていた。`FileFacts`で`unix` viewから1回のstatにまとめ（実測10.2 us → 3.6 us/file）、`createDirectories`の重複呼び出しもcacheした。
- 凍結中のpoint-in-time copyを複数threadへ分散し、`snapshot.io_threads`（既定`0`＝`min(4, コア数)`、`1`で従来の逐次copy）を追加。10,175 fileのall-reused captureで1 thread 125 ms → 4 thread 75 ms。上記のstat削減と合わせ、同条件で約190 ms → 75 msになった。
- 並列copyで、`createDirectories`完了前に別threadが同じdirectoryへ書き込み得た競合を修正（claimと生成を同一critical sectionにまとめた）。ベンチマークで`NoSuchFileException`として再現・修正済み。
- checkpointごとにVanillaが出力していた`Saving chunks for level ...`3行を抑止（`server.save`のsuppressLogsを有効化）。AntiCrash自身の確定ログは従来どおり出力する。
- checkpoint復元を予約してstopするとき、`MinecraftServer.shutdown`のreturn（AntiCrashが出力できる最後の地点）で`復元を予約しました。次回起動時にWorldへ適用されます。起動してください。`を出力するよう追加。この時点ではWorldはまだ置き換わっておらず、次回起動の初期化中に適用されることを明示する。復元を予約したstopでのみ出力し、pending requestを消費済みの通常stopでは出力しない。
- 復元予約のたびにrestart helperプロセスを起動していた挙動を既定で無効化。`recovery.spawn_restart_helper`（既定`false`）を追加した。helperは`ProcessHandle.Info`から起動commandを再構成するため、start script経由で起動している構成では環境を再現できず再起動に失敗するうえ、無駄なJVMが毎回起動していた。既定では**プロセスを起動せず、消費者のいないmarkerも書かない**。復元は`recovery-state.json`のpending restoreで駆動されるため、手動起動でそのまま適用される。Supervisor前面起動時はhandshakeが優先され、この設定に関係なく従来どおり自動再起動する。
- Carpetの`creativeNoClip`などが効かなくなることがあった問題を修正。checkpoint barrierが`onUpdatePlayerAbilities`を破棄していたが、Vanillaのこのhandlerは`player.getAbilities().flying`へ代入するだけでWorldに触れないため、barrierで守るものが何もない一方、server側の`abilities.flying`だけが古いまま残っていた。barrierでは拒否せず、Recovery lockが拒否する場合は`sendAbilitiesUpdate`で訂正するよう変更。
- checkpoint barrier中のコマンドが無言で握り潰され、機能が壊れているように見えていた問題を修正。理由を返すようにし、チャット経由のコマンドも同じpolicy・同じ応答を通るようにした（barrier中のチャット自体はWorldを変更しないため素通し）。
- Carpetのtick rate・`process_entities`・freeze状態をRuntime Capsule v5へ保存し、復元時に適用するよう追加。Carpetはこれらを永続化しないため、従来は復元後に必ず20 TPSへ戻っていた。取得・適用できない場合はnull扱いで復旧を止めない。
