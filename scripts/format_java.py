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

from build_model import java_home

FORMATTER = "2.0.0"
SHA256 = "c78feb48c549fa7114b7ea11db3b456808f7b371ffe84e6643a44dd2f9588639"
URL = f"https://github.com/google/google-java-format/releases/download/v{FORMATTER}/google-java-format-{FORMATTER}-all-deps.jar"


# Java literals precede comment tokens so comment-shaped text inside a string
# or text block never becomes a placeholder.
JAVA_TRIVIA = re.compile(
    r'"""(?:\\.|(?!""").)*"""|"(?:\\.|[^"\\])*"|'
    r"'(?:\\.|[^'\\])*'|//[^\n]*|/\*.*?\*/", re.DOTALL)
BLOCK_PREFIX = "__ANTICRASH_FORMAT_BLOCK_"


def mask_block_comments(text):
    if BLOCK_PREFIX in text:
        raise ValueError("Formatter placeholder collides with source text")
    blocks = []
    def replace(match):
        token = match.group()
        if not token.startswith("/*"):
            return token
        index = len(blocks)
        blocks.append(token)
        return f"/*{BLOCK_PREFIX}{index}__*/"
    return JAVA_TRIVIA.sub(replace, text), blocks


def restore_block_comments(text, blocks):
    seen = []
    def replace(match):
        index = int(match.group(1))
        if index >= len(blocks):
            raise ValueError("Unknown formatter placeholder")
        seen.append(index)
        return blocks[index]
    pattern = r"/\*\s*" + BLOCK_PREFIX + r"([0-9]+)__\s*\*/"
    text = re.sub(pattern, replace, text)
    if sorted(seen) != list(range(len(blocks))):
        raise ValueError("Formatter lost or duplicated a block comment")
    return text


def restore_formatted(text, blocks):
    def restore_marker(match):
        token = match.group()
        if token.startswith("//"):
            return re.sub(r'^//[ \t]+\?(?= if\b|\})', '//?', token)
        return token
    text = JAVA_TRIVIA.sub(restore_marker, text)
    # Compact only the formatter's placeholder terminator. Nested inactive
    # source and literal contents are restored afterwards, without any edits.
    pattern = r'(/\*\s*' + BLOCK_PREFIX + r'([0-9]+)__\s*\*/)\s*(//\?\})'
    def compact_ghost(match):
        if blocks[int(match.group(2))].startswith('/**'):
            return match.group()
        return match.group(1) + match.group(3)
    text = re.sub(pattern, compact_ghost, text)
    return restore_block_comments(text, blocks)


def preserve_directives(text):
    masked, blocks = mask_block_comments(text)
    return restore_formatted(masked, blocks)


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
    home = java_home(root)
    java = home / "bin/java" if home else "java"
    # Formatting a Stonecutter directive as "// ?" silently disables it. Work on
    # temporary files, then restore this exact token before comparing/writing.
    sources = java_sources(root)
    with tempfile.TemporaryDirectory(prefix="anticrash-format-") as temporary:
        staged = []
        comments = []
        for index, source in enumerate(sources):
            destination = Path(temporary) / str(index) / source.name
            destination.parent.mkdir()
            masked, blocks = mask_block_comments(source.read_text(encoding='utf-8'))
            destination.write_text(masked, encoding='utf-8')
            comments.append(blocks)
            staged.append(destination)
        subprocess.run([str(java), "-Xmx1024m", "-XX:ActiveProcessorCount=2", "-jar", str(jar),
                        "--aosp", "--skip-sorting-imports", "--skip-removing-unused-imports",
                        "--skip-javadoc-formatting", "--skip-reordering-modifiers", "--cleanup=false",
                        "--replace", *map(str, staged)], cwd=root, check=True)
        changed = []
        for source, formatted, blocks in zip(sources, staged, comments):
            content = restore_formatted(formatted.read_text(encoding='utf-8'), blocks)
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
