# Changelog

## Unreleased — source/build structure

- Consolidate Minecraft implementation and unit tests into one Stonecutter-managed `src` tree.
- Centralize target properties, dependency pins and build tasks; keep ordered Mixin profiles and field-audit fixtures explicit.
- Keep Supervisor independent and separate isolated qualification probes from the distributed Mod. Repair stale probe-only API calls without changing distributed runtime code.
- Add sequential all-target builds, bounded resources, candidate artifact collection and executable/resource parity checks.
- Preserve Stonecutter directives through formatting and use the existing AST-based save-scope audit consistently in 1.21.5.
- Preserve Mod version 1.0.2, checkpoint formats, runtime class identities and published release artifacts. This migration does not publish a new release.

## 1.0.2

Fix chunk-storage Mixin injection and BASE checkpoint chunk-domain validation; improve recovery validation, diagnostics, configuration coverage and Carpet factory contracts. Existing release tags preserve their original artifacts.
