# Changelog

## 1.0.10 — consistent side checks

- Use `Level.isClientSide()` directly across supported Minecraft versions, following the review of PR #17.
- Remove the redundant `VersionLevels.isClientSide` adapter without changing checkpoint or recovery formats.

## 1.0.9 — world access compatibility

- Integrate PR #17: centralize version-specific entity and player world access in `VersionLevels`.
- Preserve player packet resynchronization, FakePlayer restoration, logical mutation tracking and Mixin contracts across supported Minecraft versions.
- Keep checkpoint and recovery formats unchanged.

## 1.0.8 — storage, messaging and permission compatibility

- Integrate PR #15: centralize version-specific storage, message and permission APIs in the existing adapters.
- Preserve entity-save refusal, abilities and Ender Chest restore timing, message destinations and command permission checks.
- Add cross-version regression coverage for literal/chat text and player ability flags/speeds.
- Preserve checkpoint formats, Mixin injection contracts and recovery validation.

## 1.0.7 — NBT compatibility adapters

- Integrate PR #13: centralize version-specific NBT reads and item/effect serialization in `VersionNbt`.
- Preserve legacy defaults and newer Optional-based validation, including explicit tolerant reads.
- Add six cross-version regression tests for typed values, missing/wrong types, nested lists, UUID layout and effect timing/flags.
- Bound formatter download retries, retain checksum enforcement, and use the identical Maven Central artifact.
- Support cold Gradle caches through the checksum-pinned official release download URL; extend the timeout to 60 seconds and accept byte-identical cached library copies.
- Preserve checkpoint formats, Mixin injection contracts and recovery validation.

## 1.0.6 — chunk coordinate compatibility

- Integrate PR #11: centralize ChunkPos coordinate access, packing, unpacking and BlockPos conversion in `VersionChunkPos`.
- Simplify version conditions while preserving ticket handling, chunk readiness and NBT write paths.
- Add cross-version regression tests for the persisted packed-coordinate layout and negative block boundaries.
- Preserve checkpoint formats and recovery validation.

## 1.0.5 — consistent formatting and identifier compatibility

- Integrate PR #8: format active and inactive Stonecutter branches consistently across Minecraft versions.
- Integrate PR #9: centralize identifier, dimension-name and registry API differences in `VersionIds`.
- Add cross-version tests for identifier parsing, persisted dimension keys, six registry adapters and rejection of malformed or unknown identifiers.
- Preserve checkpoint formats and recovery validation.

## 1.0.4 — build and source maintenance

- Use the latest supported Minecraft version as the Stonecutter VCS baseline.
- Consolidate mutually exclusive version conditions and verify their expanded Java tokens across all 30 targets.
- Run formatting, source ownership checks, and script regression tests through Gradle and CI.
- Integrate the Mojang mappings update from PR #5 while retaining version-specific runtime code and Mixin contracts.

## 1.0.3 — source/build structure

- Consolidate Minecraft implementation and unit tests into one Stonecutter-managed `src` tree.
- Centralize target properties, dependency pins and build tasks; keep ordered Mixin profiles and field-audit fixtures explicit.
- Keep Supervisor independent and separate isolated qualification probes from the distributed Mod. Repair stale probe-only API calls without changing distributed runtime code.
- Add sequential all-target builds, bounded resources, candidate artifact collection and executable/resource parity checks.
- Preserve Stonecutter directives and inactive comment bodies through formatting; prevent indentation growth on repeated runs. Use the existing AST-based save-scope audit consistently in 1.21.5.
- Preserve checkpoint formats and runtime class identities. Publish new versioned JARs because build metadata and debugging information changed; keep older releases unchanged.

## 1.0.2

Fix chunk-storage Mixin injection and BASE checkpoint chunk-domain validation; improve recovery validation, diagnostics, configuration coverage and Carpet factory contracts. Existing release tags preserve their original artifacts.
