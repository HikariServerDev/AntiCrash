#!/usr/bin/env bash
set -euo pipefail
mod=$(sed -n 's/^mod_version=\([^[:space:]]*\)[[:space:]]*$/\1/p' gradle.properties)
if [[ ! "$mod" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo 'Exactly one numeric mod_version is required' >&2
  exit 1
fi
if [[ "${GITHUB_REF_TYPE:-}" == tag && "${GITHUB_REF_NAME:-}" != "$mod" ]]; then
  echo "Tag ${GITHUB_REF_NAME:-} does not match mod_version=$mod" >&2
  exit 1
fi
if [[ "${PUBLISH:-false}" == true && "${GITHUB_REF_TYPE:-}" != tag ]]; then
  echo 'Draft publication requires the matching release tag, not a branch' >&2
  exit 1
fi
printf 'mod=%s\n' "$mod" >> "$GITHUB_OUTPUT"
