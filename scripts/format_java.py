#!/usr/bin/env python3
"""Use one pinned Java formatter for shared sources and every version adapter."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request

FORMATTER = "2.0.0"
SHA256 = "c78feb48c549fa7114b7ea11db3b456808f7b371ffe84e6643a44dd2f9588639"
URL = f"https://github.com/google/google-java-format/releases/download/v{FORMATTER}/google-java-format-{FORMATTER}-all-deps.jar"


def java_sources(root):
    versions = json.loads((root / "versions.json").read_text())["versions"]
    roots = [root / name for name in ["common", "shared", "supervisor/src", "scripts"]]
    roots += [root / version["minecraft"] / "src" for version in versions]
    roots += [root / "1.16.1/adapter-core/src"]
    return sorted({path for base in roots for path in base.rglob("*.java")})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify without editing sources")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    cache = root / "build/tooling"
    cache.mkdir(parents=True, exist_ok=True)
    jar = cache / f"google-java-format-{FORMATTER}-all-deps.jar"
    if not jar.exists():
        with tempfile.TemporaryDirectory(prefix="formatter-", dir=cache) as temporary:
            downloaded = Path(temporary) / "formatter.jar"
            with urllib.request.urlopen(URL, timeout=60) as response, downloaded.open("wb") as output:
                shutil.copyfileobj(response, output, length=65536)
            if hashlib.sha256(downloaded.read_bytes()).hexdigest() != SHA256:
                raise ValueError("Formatter checksum mismatch")
            downloaded.replace(jar)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Cached formatter checksum mismatch")
    java = Path(os.environ["JAVA_HOME"]) / "bin/java" if os.environ.get("JAVA_HOME") else "java"
    options = ["--dry-run", "--set-exit-if-changed"] if args.check else ["--replace"]
    subprocess.run([str(java), "-Xmx1024m", "-jar", str(jar), "--aosp", *options,
                    *map(str, java_sources(root))], cwd=root, check=True)


if __name__ == "__main__":
    main()
