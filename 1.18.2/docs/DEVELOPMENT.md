# Development

## Isolated checkpoint incident qualification

`qualification/checkpoint_incident.py`は`build/checkpoint-incident-*`に新規Worldを作り、
loopback/port zero/1GB heapで連続checkpointを実行する。既存のqualification launcher dependenciesが必要。
`checkpoint_inode_audit.py`はchecksum/inode/memory graphを、`checkpoint_incident_startup.py`はclean/Nとdirty/Yを検査する。
systemdや運用serverを使わない。対象directoryとchild PIDを固定し、そのchildだけを停止する。
低heap純Java回帰、OOM fault gate、parent mutation/verification concurrencyもunit testsに含む。
PASSの範囲と元incidentのINCONCLUSIVE事項は[CHECKPOINT-INCIDENT](CHECKPOINT-INCIDENT.md)に記載。

## Build

Project rootは`/home/atsuki/server/dev/mods/AntiCrash`です。Gradle Wrapperを使います。

```bash
cd /home/atsuki/server/dev/mods/AntiCrash
./gradlew test
./gradlew build
```

build時にはFabric LoomでMinecraft 1.18.2 / Yarn build.4へcompileし、root jarとremapped jarを生成します。Supervisorは依存なしのJava 17 subprojectです。

## Project structure

```text
src/main/java/com/atsukimc/anticrash/
├─ AntiCrashMod.java / AntiCrashController.java
├─ config/                 # User Config
├─ core/                   # State, marker, crash classification
├─ checkpoint/             # tick barrier and commit coordination
├─ snapshot/               # backend, manifest, checksum, raw world
├─ runtime/                # v6 Runtime Capsule / tick domain / fingerprints
├─ recovery/               # pre-world restore and tickets
├─ player/                 # selective Real Player NBT
├─ integration/carpet/     # optional FakePlayer / ActionPack
├─ consistency/             # WARN/JOURNAL/SPI
├─ command/                # Brigadier /ac
├─ logging/                # levels and rotation
├─ mixin/                   # lifecycle, tick, packet, accessors
└─ util/                    # atomic file, hash, path, reflection
supervisor/                 # foreground process Supervisor
src/test/                   # pure filesystem/config/codec/journal tests
docs/                       # design and operator documents
```

## Mixin and API boundaries

`MinecraftServerMixin`はWorld load、tick end、shutdownをControllerへ通知し、通常停止時は`checkpoint.shutdown_timeout_seconds`の範囲内だけ進行中/final checkpointを待ちます。timeout後はVanilla停止を続けます。`PistonBlockEntityMixin`はmoving pistonのtick後に所有chunkだけをdirty化し、比較器更新や別のtick順序変更は行いません。`ServerWorldMixin`/`ServerPlayerEntityMixin`はCheckpoint/Recovery Freeze中だけtickをcancelし、通常運転時には触りません。`ServerPlayNetworkHandlerMixin`はCheckpoint中のmutation packetを無条件に止め、Recovery中はlock設定に従います。`CommandManagerMixin`はCheckpoint中の変更系Commandを止めます。Accessor Mixinは1.18.2のprivate queue/chunk storageをread/writeするために限定しています。

外部Modは`AntiCrashController.registerExternalBridge(ExternalPlayerDataBridge)`でcheckpoint、transaction、recovery通知を受け取れます。特定MySQL schemaやVelocity APIを参照しないでください。

## Tests

JUnitは次を検証します。

- Config defaults、nested section、invalid value、User/Internal separation
- atomic checkpoint、READY、checksum corruption、incomplete rejection、retention
- Java incrementalのhash reuseとLive World hardlink禁止、symlink rejection
- ActionPackのJSON round-trip、`next`/timing、`BlockPos currentBlock`、必須field validation
- clean/dirty marker、state persistence、bounded JOURNAL JSONL
- version 2 checkpoint lineageとlegacy fallback branch
- Vanilla chunk ticket level `33`/`32`/`31`からAPI radius `0`/`1`/`2`への変換、重複排除plan、CRITICAL/ENTITY_TICKING/ALLのpreload選択と上限
- Runtime Capsule v6のexact Chunk domain serialization、dimension分離、fingerprintのdeterminism/order/count mismatch、STRICTのFail Closed判定
- v6 manifest/capsule counter cross-check、旧v1〜v5の`MISSING_FROM_OLD_FORMAT`、BlockEntity ticker/Entity/Torch/Ticket/Carpet state validation
- Future-based Chunk recoveryのbatch上限、Expected Chunk不足時の拒否、Real Player readiness設定、FakePlayer ActionPack timing
- restart markerのpath/claimとSupervisor handshake

実Server、Carpet、Proxy、MySQL、TMC装置のRuntime Reviewは自動Testの範囲外です。変更時はまず`./gradlew test`、次に`./gradlew build`、最後にJar内容とMixin JSONを検査します。Futureの完了を確認するためにMain Threadで`.join()`や同期`getChunk`へ戻してはいけません。実Minecraftを起動するテストタスクは本プロジェクトの検証手順に含めません。

## Supervisor（標準起動）

管理ツールが同じウィンドウ内の1 PIDだけを監視できるよう、ServerはSupervisorを前面起動してください。SupervisorがMinecraft childを起動し、stdin/stdout/stderrを継承します。Supervisor自身とchildのPID、child終了code、起動試行回数、controlled restart受理、異常終了時の再試行を同じconsoleへ出力します。Modが復元用の`.anticrash/state/restart-request`を保存した場合は、childの正常終了でも一度だけ再起動します。通常の`/stop`やmarkerのないexit code 0は再起動しません。非0終了は短時間の連続失敗に対して`--max-restarts`まで再試行し、`--stable-runtime-ms`以上稼働した後の非0終了では再試行回数をリセットします。子processにはOOM終了flagと固定marker絶対pathの両方を渡し、Modは両者が一致する場合だけSupervisor handshakeとして認めます。

```bash
exec java -jar AntiCrash-Supervisor-1.0.0.jar \
  --workdir /absolute/path/to/server \
  --language ja \
  --max-restarts 3 \
  --restart-delay-ms 5000 \
  --stable-runtime-ms 60000 \
  -- java -jar server.jar nogui
```

Supervisorを使わない直接起動は互換用のfallbackです。復元予約時だけMod JAR内のhelperを開始し、旧PID終了後にreplacement JVMを起動しますが、対話shellの`SIGTTIN`停止を避けるためreplacementのstdinはnull deviceです。ASKの再起動後もconsole入力を維持する運用、通常運転中のOOM/JVM crashの監視、管理ツールの1 PID監視にはSupervisorを使用します。Supervisorはsymbolic linkの`--workdir`を拒否し、`--dry-run`では一般的なpassword/token/secret引数を伏せ字にします。

## Runtime investigation boundary

1.18.2の実装依存部分を変更するときは、まずGradle cacheのmapped merged JAR、対象Modの実JAR、`javap -verbose`/bytecodeを確認し、存在しないfield/methodを推測してAccessorを追加しません。確認済みでも、内部Mapを丸ごと置換する実装は避け、意味的なsnapshot・read-only fingerprint・Fail Closedを優先します。lighting queue、worker thread、Futureの内部実行順、CPU schedulingをRuntime Capsuleへ擬似的に保存してTMCの等価性を主張してはいけません。実機でのTMC検証では、`/ac recovery verify --verbose`と`/ac debug equivalence --verbose`の結果をログと一緒に保全してください。

## Checkpoint engine implementation rules

`CheckpointManager`のserver-thread側は、tick boundary、Vanilla save、Runtime capture、portable detach、seal journalまでを担当します。`runCapture`はportable sealがある場合に`context.world == null`でなければならず、Live Worldを再scanしてはいけません。新しいsnapshot providerを追加する場合も、変更fileをLogical Cut内に独立化できないbackendは`LEGACY_SAFE`として明示指定だけにし、`AUTO`へ登録しないでください。

新しいRuntime stateは、まずMinecraft 1.18.2のmapped merged bytecodeで存在とmutation順を確認し、次にdeep immutable representation、canonical ordering、fingerprint、restore可否、Unsupported policyを設計します。内部Mapのreflection置換、旧World objectの参照保存、lighting queueやthread stackの擬似復元は受け入れません。完全復元できない状態は`UNSUPPORTED`/`MISMATCH`としてFail Closedするのが既定です。

Checkpointの自動testは実Serverを起動しません。filesystemは`@TempDir`、Runtimeはfake state、Futureはfake completion、Retentionはmanifestのdependency graphで検証します。実機のTMC装置、Carpet派生版、Proxy、OOM、電源断はユーザーのManual Runtime Review範囲です。

## Logical Delta v2を変更するとき

`LogicalDeltaEngine`はserver thread専用、`LogicalDeltaMaterializer`と`CheckpointCompactor`はimmutable payload専用のworkerです。新しいcapture対象を追加する場合は、(1) mapped merged bytecodeでmutation入口とserialization形式を確認し、(2) primitive/byte配列のdeep immutable representationを定義し、(3) generation・ordering・fingerprint・restore可否を決め、(4) capture中の不確実性をfallbackまたはFail Closedへ接続してください。`World`、`Chunk`、`Entity`、queue objectをworkerへ渡す変更は受け入れません。

最小の検証は`ChunkCandidateCacheTest`、`LogicalDeltaModelTest`、`LogicalDeltaMaterializerTest`、`CheckpointCompactorTest`、`ScheduledTickMutationTracerTest`です。Materializer testではtemporary filesystemにRegionFileを作り、RegionFile引数順、chunk/entity差分、tombstone、level.dat、親hard linkのcopy-on-writeを確認します。実Minecraft起動、実World、Supervisorの本番監視はこのGradle testの範囲外です。

## Runtime State監査の更新

Minecraftのversionを上げたとき、または新しいBlockEntity/Entityを対象にするときは、`src/test/resources/runtime-audit/*.tsv`に全fieldの判断を追加します。`RuntimeStateAuditCoverageTest`は、compile対象のjarから実際のfieldを読み、判断のないfieldがあると失敗します。分類は[RUNTIME-STATE-AUDIT.md](RUNTIME-STATE-AUDIT.md)を参照。Production codeではreflectionによるfield探索を行いません（明示的なaccessor mixinだけ）。
