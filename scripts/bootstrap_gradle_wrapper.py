#!/usr/bin/env python3
"""Restore gradle-wrapper.jar from the configured Gradle distribution.

The standard wrapper is committed. This is only a recovery tool for archives
that omit binary files; it verifies the distribution before extracting the
standalone wrapper embedded in Gradle's wrapper-main JAR.
"""
from __future__ import annotations
from pathlib import Path
import io
import hashlib
import re
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROPS = ROOT / "gradle/wrapper/gradle-wrapper.properties"
TARGET = ROOT / "gradle/wrapper/gradle-wrapper.jar"

def main() -> None:
    if TARGET.is_file():
        print(f"Wrapper already present: {TARGET}")
        return
    text = PROPS.read_text("utf-8")
    m = re.search(r"^distributionUrl=(.+)$", text, re.M)
    if not m:
        raise SystemExit("distributionUrl not found")
    checksum = re.search(r"^distributionSha256Sum=([a-fA-F0-9]{64})$", text, re.M)
    if not checksum:
        raise SystemExit("distributionSha256Sum not found")
    url = m.group(1).replace("\\:", ":")
    print(f"Downloading {url}")
    with urllib.request.urlopen(url, timeout=60) as r:
        data = r.read()
    if hashlib.sha256(data).hexdigest() != checksum.group(1).lower():
        raise SystemExit("Gradle distribution SHA-256 mismatch")
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        candidates = [n for n in z.namelist() if re.search(r"/lib/plugins/gradle-wrapper-main-[^/]+\.jar$", n)]
        if not candidates:
            raise SystemExit("gradle-wrapper-main jar not found in distribution")
        with zipfile.ZipFile(io.BytesIO(z.read(candidates[0]))) as wrapper_main:
            wrapper = wrapper_main.read("gradle-wrapper.jar")
        TARGET.parent.mkdir(parents=True, exist_ok=True)
        TARGET.write_bytes(wrapper)
    print(f"Wrote {TARGET}")

if __name__ == "__main__":
    main()
