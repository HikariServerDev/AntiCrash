#!/usr/bin/env python3
"""Check source membership, duplicate classes/resources, and optional migration parity."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import subprocess
import tarfile


def collect(root, version, groups):
    files = {}
    source_roots = [root / version / "src"]
    source_roots += [root / group / "src" for group, members in groups.items() if version in members]
    for source_root in source_roots:
        for path in source_root.rglob("*"):
            if path.is_file():
                name = "src/" + path.relative_to(source_root).as_posix()
                if name in files:
                    raise ValueError(f"{version}: duplicate source {name}")
                files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    for prefix, source_root in [
        ("supervisor/src/", root / "supervisor" / "src"),
        ("adapter-core/src/", root / version / "adapter-core" / "src"),
    ]:
        for path in source_root.rglob("*"):
            if path.is_file():
                files[prefix + path.relative_to(source_root).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return files


def validate_groups(root, versions, groups):
    """A contribution cannot route a target to arbitrary files outside the repository."""
    for group, members in groups.items():
        parts = Path(group).parts
        if (not parts or Path(group).is_absolute() or ".." in parts
                or not (group == "common" or len(parts) == 2 and parts[0] == "shared")):
            raise ValueError(f"Unsafe source group {group}")
        if not members or len(members) != len(set(members)) or not set(members) <= set(versions):
            raise ValueError(f"Invalid members for {group}")
        source = root / group / "src"
        if not source.is_dir() or source.is_symlink():
            raise ValueError(f"Missing or linked source group {group}")
        if not source.resolve().is_relative_to(root.resolve()):
            raise ValueError(f"Source group escapes repository: {group}")
    if set(groups.get("common", [])) != set(versions):
        raise ValueError("common must apply to every target")


def validate_release(root, versions, release):
    for version in versions:
        properties = (root / version / "gradle.properties").read_text()
        matches = re.findall(r"^mod_version\s*=\s*(\S+)\s*$", properties, re.MULTILINE)
        if matches != [release]:
            raise ValueError(f"Release version mismatch in {version}: {matches}")
    implementation = root / "common/src/main/java/com/atsukimc/anticrash"
    source = (implementation / "AntiCrashMod.java").read_text()
    if re.findall(r'\bVERSION\s*=\s*"([^"]+)"', source) != [release]:
        raise ValueError("Runtime VERSION does not match the release matrix")
    if f"# AntiCrash {release} - complete" not in (implementation / "config/AntiCrashConfig.java").read_text():
        raise ValueError("Configuration version header is stale")
    for language in ["ja", "en"]:
        catalog = root / f"common/src/main/resources/anticrash/messages_{language}.properties"
        if f"command.status=AntiCrash {release}\\n" not in catalog.read_text():
            raise ValueError(f"Status version header is stale: {language}")


def baseline(root, ref, versions):
    paths = [f"{v}/{part}" for v in versions for part in ["src", "supervisor/src"]]
    paths.append("1.16.1/adapter-core/src")
    result = {v: {} for v in versions}
    with subprocess.Popen(["git", "archive", ref, "--", *paths], cwd=root, stdout=subprocess.PIPE) as process:
        with tarfile.open(fileobj=process.stdout, mode="r|") as archive:
            for member in archive:
                if member.isfile():
                    version, name = member.name.split("/", 1)
                    result[version][name] = hashlib.sha256(archive.extractfile(member).read()).hexdigest()
        if process.wait() != 0:
            raise ValueError(f"Cannot read baseline {ref}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-ref", help="Compare every target with its original version/src tree at this Git ref")
    parser.add_argument("--production-only", action="store_true", help="Exclude tests adapted to the new source layout from baseline comparison")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    matrix = json.loads((root / "versions.json").read_text())
    versions = [v["minecraft"] for v in matrix["versions"]]
    groups = json.loads((root / "gradle/source-groups.json").read_text())
    validate_groups(root, versions, groups)
    validate_release(root, versions, matrix["mod_version"])
    current = {v: collect(root, v, groups) for v in versions}
    if args.baseline_ref:
        previous = baseline(root, args.baseline_ref, versions)
        for v in versions:
            differences = sorted(name for name in current[v].keys() | previous[v].keys()
                                 if current[v].get(name) != previous[v].get(name)
                                 and not (args.production_only and "/test/" in name))
            if differences:
                raise ValueError(f"{v}: source bytes or membership changed: {differences[:8]}")
        scope = "production sources, probes and resources" if args.production_only else "sources, tests, probes and resources"
        print(f"Byte-identical {scope} for all {len(versions)} targets, including supervisor and legacy adapter.")
    else:
        print(f"No duplicate sources or resources across {len(versions)} targets and {len(groups)} source groups.")


if __name__ == "__main__":
    main()
