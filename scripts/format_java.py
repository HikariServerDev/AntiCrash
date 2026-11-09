#!/usr/bin/env python3
"""Format canonical Java so that every Stonecutter target is google-java-format output.

StonecutterFormat.java does the work with the project's own Stonecutter processor: each target's
active code is formatted as that target sees it, inactive branches included, and directives are
indented like the code they open. This wrapper supplies the pinned formatter, the cached processor
and the target matrix.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

from build_model import java_home, load_targets
from check_conditions import cached_libraries, only_jar

FORMATTER = "2.0.0"
SHA256 = "c78feb48c549fa7114b7ea11db3b456808f7b371ffe84e6643a44dd2f9588639"
URL = f"https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/{FORMATTER}/google-java-format-{FORMATTER}-all-deps.jar"
# google-java-format runs inside the same JVM as the processor, so it needs javac's internals.
EXPORTS = [f"--add-exports=jdk.compiler/com.sun.tools.javac.{name}=ALL-UNNAMED"
           for name in ["api", "code", "file", "parser", "tree", "util"]]


def java_sources(root):
    roots = [root / name for name in ["src", "supervisor/src", "scripts"]]
    return sorted({path for base in roots for path in base.rglob("*.java")})


def active_version(root):
    text = (root / "stonecutter.gradle.kts").read_text(encoding="utf-8")
    values = re.findall(r'^stonecutter active "([^"]+)"', text, re.MULTILINE)
    if len(values) != 1:
        raise ValueError("Exactly one Stonecutter active version is required")
    return values[0]


def formatter_jar(root):
    cache = root / ".toolchains/formatters"
    cache.mkdir(parents=True, exist_ok=True)
    jar = cache / f"google-java-format-{FORMATTER}-all-deps.jar"
    if not jar.exists():
        with tempfile.TemporaryDirectory(prefix="formatter-", dir=cache) as temporary:
            downloaded = Path(temporary) / "formatter.jar"
            for attempt in range(3):
                try:
                    with urllib.request.urlopen(URL, timeout=60) as response, downloaded.open("wb") as output:
                        shutil.copyfileobj(response, output, length=65536)
                    break
                except (urllib.error.URLError, TimeoutError) as error:
                    # Retry transient transport failures only; never retry integrity or HTTP 4xx failures.
                    if (isinstance(error, urllib.error.HTTPError) and error.code not in (502, 503, 504)) or attempt == 2:
                        raise
                    time.sleep(attempt + 1)
            if hashlib.sha256(downloaded.read_bytes()).hexdigest() != SHA256:
                raise ValueError("Formatter checksum mismatch")
            downloaded.replace(jar)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != SHA256:
        raise ValueError("Cached formatter checksum mismatch")
    return jar


def command(root, *arguments):
    """The java command line running StonecutterFormat with the formatter and processor."""
    stonecutter, kotlin = cached_libraries(root)
    classpath = [formatter_jar(root), only_jar(stonecutter, "Stonecutter"), only_jar(kotlin, "Kotlin")]
    home = java_home(root)
    java = str(home / "bin/java") if home else "java"
    return [java, "-Xmx2g", "-XX:ActiveProcessorCount=2", *EXPORTS,
            "--class-path", os.pathsep.join(map(str, classpath)),
            str(root / "scripts/StonecutterFormat.java"), *arguments]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify without editing sources")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix="anticrash-format-") as temporary:
        listing = Path(temporary) / "sources.txt"
        listing.write_text("".join(f"{path}\n" for path in java_sources(root)), encoding="utf-8")
        result = subprocess.run(command(root, "--check" if args.check else "--apply", str(root),
                                        active_version(root), ",".join(load_targets(root)), str(listing)),
                                cwd=root)
    if result.returncode:
        raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
