# Isolated Minecraft qualification

Java probes are in `src/qualification/java`; resources are in `src/qualification/resources`. They are a separate source set, never part of the normal AntiCrash JAR. Build a supported probe with `./gradlew -PmcVersion=1.18.2 :1.18.2:probeJar`.

`fixtures/` contains immutable field-classification expectations consumed by unit tests. Identical TSV data is shared by reference from `stonecutter.properties.toml`; version-specific classifications remain separate.

Runtime checks require a dedicated isolated server directory and existing accepted EULA. Never copy a test world into production or test against a production directory. See the development instructions for the acceptance sequence and resource limits. Building the probe does not count as executing a Minecraft runtime test.
