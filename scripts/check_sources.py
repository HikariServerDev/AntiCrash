#!/usr/bin/env python3
"""Check source membership, duplicate classes/resources, and optional migration parity."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile


def collect(root, version, groups):
    files = {}
    source_roots = [root / "versions" / version / "src"]
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
        ("adapter-core/src/", root / "versions" / version / "adapter-core" / "src"),
    ]:
        for path in source_root.rglob("*"):
            if path.is_file():
                files[prefix + path.relative_to(source_root).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return files


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
    versions = [v["minecraft"] for v in json.loads((root / "versions.json").read_text())["versions"]]
    groups = json.loads((root / "gradle/source-groups.json").read_text())
    for group, members in groups.items():
        if not members or len(members) != len(set(members)) or not set(members) <= set(versions):
            raise ValueError(f"Invalid members for {group}")
        if not (root / group / "src").is_dir():
            raise ValueError(f"Missing source group {group}")
    if set(groups.get("common", [])) != set(versions):
        raise ValueError("common must apply to every target")
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
