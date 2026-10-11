# AntiCrash — Minecraft 1.21.11

状態: **BUILD_VERIFIED**。

main 285 / supervisor 13 tests、failure/error/skip 0。
Minecraft起動・Mixin適用・Carpet・TMC復旧の実機検証は未実施です。

Java target: 21、Loader: 0.18.4、Yarn: 1.21.11+build.6。
この版専用のsource/test/buildを持ちます。Minecraft版が異なるcheckpointの直接復元は禁止です。

```sh
./gradlew test build --no-daemon --max-workers=1
```

配布名: `AntiCrash-1.0.0+mc1.21.11.jar` / `AntiCrash-Supervisor-1.0.0+mc1.21.11.jar`。
成功したbuildだけを共通`../build/`へ集約します。

[全版共通README](../README.md)、[版別内部実装](../docs/PORT-ADAPTERS.md)、[詳細状態](target.json)を参照してください。

Production server modified: NO
