# AntiCrash — Minecraft 1.16.1

状態: **PORT_IN_PROGRESS**。対応版として配布できるJARはまだありません。

`adapter-core`に1.16.1専用予定tickの値モデル・streaming serialization・順序/境界検証を実装しています。
read-only native capture adapterも独立compileを検証しました。coreテスト14件はPASSです。
復元hook、chunk内entity IO、完全なModへの接続は未実装です。
この小さなcoreのテスト成功をMod移植完了とは数えず、root buildは明示的に拒否します。
本体のmigration sourceは旧版にないclass/APIの調査用で、現在の本体compileは未完了です。

開発用coreのテスト（Java 17以上、実Minecraft起動不要）:

```sh
./gradlew :adapter-core:test compileLegacyIntegrationJava --no-daemon --max-workers=1 -PlegacyGameJar=/absolute/path/to/minecraft-merged-1.16.1-Yarn-build.21.jar
```

調査・残工程は[PORTING-INVESTIGATION.md](PORTING-INVESTIGATION.md)を参照してください。

対象の公式release / Java要件 / Yarn情報は [target.json](target.json) を参照してください。
Minecraftの最低Java要件は 8 ですが、AntiCrashの移植に使うコンパイラ要件と同一ではありません。
設定値やフォルダ名だけでは互換性を保証できません。1.18.2のJARをこの版へコピーして使わないでください。

共通の使用方法・対応状況は [共通README](../README.md) を参照してください。
