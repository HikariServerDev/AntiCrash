# AntiCrash — Minecraft 1.18.2

- **Build status:** `BUILD_VERIFIED`
- **Mod JAR:** `AntiCrash-1.0.1+mc1.18.2.jar`
- **Supervisor JAR:** `AntiCrash-Supervisor-1.0.1+mc1.18.2.jar`
- **Requirements:** Minecraft 1.18.2, Fabric Loader 0.17.3 or newer, Java 17 or newer.

AntiCrash is a server-side Fabric mod for checkpoint creation and recovery after unexpected shutdowns. Put the matching Mod JAR in the dedicated server's `mods` directory; players do not need the mod on their clients. The Supervisor is a separate optional Java program and must not be placed in `mods`.

Compilation, unit tests, and artifact checks are recorded as passing for this build. Minecraft startup, compatibility with other mods, and live recovery behavior have not been qualified. Keep independent world backups and test recovery on a copy first. Checkpoints must not be restored across different Minecraft versions.

Licensed under the GNU Lesser General Public License v3.0 only (`LGPL-3.0-only`).

Development layout: clone the complete repository; this target uses shared `common/`, API-family `shared/`, and `supervisor/` sources with its own Minecraft adapters.
