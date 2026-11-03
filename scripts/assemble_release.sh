#!/usr/bin/env bash
# CI artifact assembly only; Gradle verifies the contents of the distribution JARs.
set -euo pipefail
export LC_ALL=C
input=${1:-verification}
output=${2:-release}
mapfile -t versions < <(grep -oE '^\["[0-9.]+"\]$' stonecutter.properties.toml | tr -d '[]"')
[[ ${#versions[@]} -gt 0 ]]
mkdir "$output"
expected=$(mktemp)
actual=$(mktemp)
trap 'rm -f "$expected" "$actual"' EXIT
for version in "${versions[@]}"; do
  printf 'AntiCrash-%s+mc%s.jar\nAntiCrash-Supervisor-%s+mc%s.jar\n' "$MOD" "$version" "$MOD" "$version"
done | sort > "$expected"
find "$input" -path '*/build/libs/*' \( -name "AntiCrash-${MOD}+mc*.jar" -o -name "AntiCrash-Supervisor-${MOD}+mc*.jar" \) ! -name '*-sources.jar' -printf '%f\n' | sort > "$actual"
# Exact identity, not just total count: a missing target cannot be replaced by an unrelated JAR.
diff -u "$expected" "$actual"
while IFS= read -r name; do
  find "$input" -path '*/build/libs/*' -name "$name" -exec cp {} "$output/" \;
done < "$expected"
count_tests() {
  local directory=$1
  local reports=("$directory"/TEST-*.xml)
  [[ -f "${reports[0]}" ]] || { echo "Missing JUnit reports: $directory" >&2; return 1; }
  grep -h '<testsuite ' "${reports[@]}" | jq -Rsc --argjson reports "${#reports[@]}" '
    def number($name): capture(" " + $name + "=\"(?<n>[0-9]+)\"").n | tonumber;
    [split("\n")[] | select(length > 0) |
      {tests: number("tests"), failures: number("failures"), errors: number("errors")}] |
    if length != $reports or any(.[]; .failures != 0 or .errors != 0) then
      error("Missing or failed JUnit suites")
    else map(.tests) | add | if . > 0 then . else error("Empty test evidence") end end'
}
main_tests=0
for version in "${versions[@]}"; do
  tests=$(count_tests "$input/versions/$version/build/test-results/test")
  main_tests=$((main_tests + tests))
done
supervisor_tests=$(count_tests "$input/supervisor/build/test-results/test")
(cd "$output" && sha256sum -- *.jar > SHA256SUMS)
run_url="$GITHUB_SERVER_URL/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID"
while read -r sha name; do
  minecraft=${name#*+mc}; minecraft=${minecraft%.jar}
  kind=mod
  [[ "$name" != AntiCrash-Supervisor-* ]] || kind=supervisor
  printf '%s\t%s\t%s\t%s\t%s\n' "$name" "$minecraft" "$kind" "$sha" "$(stat -c %s "$output/$name")"
done < "$output/SHA256SUMS" | jq -R -s --arg mod "$MOD" --arg commit "$GITHUB_SHA" --arg run "$run_url" \
  --argjson main "$main_tests" --argjson supervisor "$supervisor_tests" --argjson targets "${#versions[@]}" '
  {mod_version: $mod, commit: $commit, workflow_run: $run,
   artifacts: [split("\n")[] | select(length > 0) | split("\t") |
     {name: .[0], minecraft: .[1], kind: .[2], sha256: .[3], size: (.[4] | tonumber)}],
   verification: {main_tests: $main, supervisor_tests: $supervisor,
     minecraft_targets: $targets, release_jars: ($targets * 2)}}' > "$output/artifacts.json"
{
  echo "Built from \`$GITHUB_SHA\` by [workflow run]($run_url)."
  echo
  echo "${#versions[@]} Minecraft targets. Tests: $main_tests Mod, $supervisor_tests Supervisor."
  echo 'See `SHA256SUMS` and `artifacts.json`. Minecraft runtime/parity verification is not established by this workflow.'
} > notes.md
