#!/usr/bin/env python3
"""Format canonical Java while preserving Stonecutter conditions and inactive imports."""
import argparse
import hashlib
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request

FORMATTER = "2.0.0"
SHA256 = "c78feb48c549fa7114b7ea11db3b456808f7b371ffe84e6643a44dd2f9588639"
URL = f"https://github.com/google/google-java-format/releases/download/v{FORMATTER}/google-java-format-{FORMATTER}-all-deps.jar"


def preserve_directives(text):
    text = re.sub(r'(?m)^([ \t]*(?:\*/|\^/)?//) \?', r'\1?', text)
    # A split ghost terminator becomes an extra source line on each native switch.
    # Keep Stonecutter's compact terminator, trimming only non-executable trailing
    # whitespace before it. Nested inactive comments use the escaped ^/ form.
    return re.sub(r'\n(?:[ \t]*\n)*[ \t]*(\*/|\^/)[ \t]*\n[ \t]*(//\?\})',
                  r'\n\1\2', text)


def java_sources(root):
    roots = [root / name for name in ["src", "supervisor/src", "scripts"]]
    return sorted({path for base in roots for path in base.rglob("*.java")})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify without editing sources")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    cache = root / ".toolchains/formatters"
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
    # Formatting a Stonecutter directive as "// ?" silently disables it. Work on
    # temporary files, then restore this exact token before comparing/writing.
    sources = java_sources(root)
    with tempfile.TemporaryDirectory(prefix="anticrash-format-") as temporary:
        staged = []
        for index, source in enumerate(sources):
            destination = Path(temporary) / str(index) / source.name
            destination.parent.mkdir()
            destination.write_bytes(source.read_bytes())
            staged.append(destination)
        subprocess.run([str(java), "-Xmx1024m", "-XX:ActiveProcessorCount=2", "-jar", str(jar),
                        "--aosp", "--skip-sorting-imports", "--skip-removing-unused-imports",
                        "--skip-javadoc-formatting", "--skip-reordering-modifiers", "--cleanup=false",
                        "--replace", *map(str, staged)], cwd=root, check=True)
        changed = []
        for source, formatted in zip(sources, staged):
            content = preserve_directives(formatted.read_text(encoding='utf-8'))
            if content != source.read_text(encoding='utf-8'):
                changed.append(source)
                if not args.check:
                    source.write_text(content, encoding='utf-8')
        if args.check and changed:
            for source in changed:
                print(source.relative_to(root))
            raise SystemExit(1)



if __name__ == "__main__":
    main()
