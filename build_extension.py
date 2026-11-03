#!/usr/bin/env python3
"""Build and sign the RedeToons extension APK from src/.

Usage:
  REDE_KEY_PASS=... python build_extension.py --keystore rede.p12 \
      --version 1000-14 --template apk/aniyomi-pt.redetoons-v1000-13.apk

Needs Java 17+. The build jars are downloaded into build-tools/.cache.
The manifest, icon and resources come from the template APK; the code is
compiled from src/ against the stubs in build-tools/stubs.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CACHE = ROOT / "build-tools" / ".cache"
JARS = {
    "apktool.jar": "https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar",
    "r8.jar": "https://dl.google.com/android/maven2/com/android/tools/r8/8.5.35/r8-8.5.35.jar",
    "apksig.jar": "https://dl.google.com/android/maven2/com/android/tools/build/apksig/8.5.2/apksig-8.5.2.jar",
    "okhttp.jar": "https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.jar",
    "json.jar": "https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar",
    "kotlin-stdlib.jar": "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.24/kotlin-stdlib-1.9.24.jar",
}
PACKAGE = "eu.kanade.tachiyomi.animeextension.pt.redetoons"
LIB_VERSION = "14"


def run(*cmd):
    subprocess.run([str(c) for c in cmd], check=True)


def jar(name):
    path = CACHE / name
    if not path.is_file():
        CACHE.mkdir(parents=True, exist_ok=True)
        print(f"Downloading {name}")
        urllib.request.urlretrieve(JARS[name], path)
    return path


def compile_dex(work):
    stub_classes = work / "stub-classes"
    classes = work / "classes"
    dex = work / "dex"
    for d in (stub_classes, classes, dex):
        d.mkdir()
    deps = os.pathsep.join(str(jar(n)) for n in ("okhttp.jar", "json.jar", "kotlin-stdlib.jar"))
    stubs = sorted((ROOT / "build-tools" / "stubs").rglob("*.java"))
    sources = sorted((ROOT / "src").rglob("*.java"))
    run("javac", "-nowarn", "--release", "8", "-Xlint:-options", "-d", stub_classes, "-cp", deps, *stubs)
    run("javac", "-nowarn", "--release", "8", "-Xlint:-options", "-d", classes,
        "-cp", f"{stub_classes}{os.pathsep}{deps}", *sources)
    run("java", "-cp", jar("r8.jar"), "com.android.tools.r8.D8", "--release", "--min-api", "24",
        "--lib", jar("kotlin-stdlib.jar"), "--output", dex, *sorted(classes.rglob("*.class")))
    return dex / "classes.dex"


def build_resources(work, template, version_code, version_name):
    decoded = work / "decoded"
    run("java", "-jar", jar("apktool.jar"), "d", "-f", "-s", template, "-o", decoded)
    for path in decoded.iterdir():
        if path.name.startswith("classes") and path.suffix == ".dex":
            path.unlink()
    config = decoded / "apktool.yml"
    text = config.read_text()
    text = re.sub(r"versionCode: .*", f"versionCode: {version_code}", text)
    text = re.sub(r"versionName: .*", f"versionName: {version_name}", text)
    config.write_text(text)
    manifest = (decoded / "AndroidManifest.xml").read_text()
    if f'android:value=".RedeToons"' not in manifest or "aniyomix.extensionLib" not in manifest:
        raise RuntimeError("Template manifest is not a RedeToons extension")
    out = work / "resources.apk"
    run("java", "-jar", jar("apktool.jar"), "b", "-f", decoded, "-o", out)
    return out


def write_aligned(resources_apk, dex, out):
    """Adds the dex and stores resources.arsc and images uncompressed on a 4-byte boundary."""
    with zipfile.ZipFile(resources_apk) as src, zipfile.ZipFile(out, "w") as dst:
        entries = [(i, src.read(i.filename)) for i in src.infolist()
                   if not i.filename.startswith("META-INF/") and not i.filename.endswith(".dex")]
        entries.insert(0, (zipfile.ZipInfo("classes.dex", (2026, 1, 1, 0, 0, 0)), dex.read_bytes()))
        for info, data in entries:
            stored = info.filename == "resources.arsc" or info.filename.endswith(".png")
            entry = zipfile.ZipInfo(info.filename, info.date_time)
            entry.compress_type = zipfile.ZIP_STORED if stored else zipfile.ZIP_DEFLATED
            if stored:
                header = 30 + len(info.filename.encode())
                pad = (4 - (dst.fp.tell() + header) % 4) % 4
                entry.extra = b"\0" * pad
            dst.writestr(entry, data)


def sign(unsigned, signed, keystore, alias):
    if not os.environ.get("REDE_KEY_PASS"):
        sys.exit("Set REDE_KEY_PASS to the keystore password")
    classes = unsigned.parent / "signer"
    classes.mkdir()
    run("javac", "-nowarn", "-d", classes, "-cp", jar("apksig.jar"), ROOT / "build-tools" / "SignApk.java")
    run("java", "-cp", f"{classes}{os.pathsep}{jar('apksig.jar')}", "SignApk", keystore, alias, unsigned, signed)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keystore", required=True, type=Path)
    parser.add_argument("--alias", default="rede")
    parser.add_argument("--version", required=True, help="Rede version, e.g. 1000-14")
    parser.add_argument("--template", required=True, type=Path)
    args = parser.parse_args()

    major, minor = args.version.split("-")
    version_code = int(major) * 1000 + int(minor)
    version_name = f"{LIB_VERSION}.{args.version}"
    out = ROOT / "apk" / f"aniyomi-pt.redetoons-v{args.version}.apk"

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        dex = compile_dex(work)
        resources = build_resources(work, args.template.resolve(), version_code, version_name)
        unsigned = work / "unsigned.apk"
        write_aligned(resources, dex, unsigned)
        sign(unsigned, out, args.keystore.resolve(), args.alias)
    print(f"Built {out} ({version_name}, code {version_code})")


if __name__ == "__main__":
    main()
