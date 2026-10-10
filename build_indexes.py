#!/usr/bin/env python3
"""Generate Aniyomi and AniZen indexes from extensions.json."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
RAW = "https://raw.githubusercontent.com/lyraEz/Rede/repo"
manifest = json.loads((ROOT / "extensions.json").read_text())
extensions = manifest["extensions"]

modern_extensions = []
legacy_extensions = []
for extension in extensions:
    apk = extension["apk"]
    icon = extension["icon"]
    lib = extension["extensionLib"]
    if not extension["versionName"].startswith(f"{lib}."):
        raise ValueError(f"{extension['name']}: versionName must begin with {lib}.")
    if not (ROOT / "apk" / apk).is_file():
        raise FileNotFoundError(apk)
    if not (ROOT / "icon" / icon).is_file():
        raise FileNotFoundError(icon)

    modern_extensions.append({
        "name": extension["name"],
        "packageName": extension["packageName"],
        "resources": {
            "apkUrl": f"{RAW}/apk/{apk}",
            "iconUrl": f"{RAW}/icon/{icon}",
        },
        "extensionLib": str(lib),
        "versionCode": extension["versionCode"],
        "versionName": extension["versionName"],
        "contentWarning": extension["contentWarning"],
        "isTorrent": False,
        "sources": [{
            "id": int(extension["sourceId"]),
            "name": extension["name"],
            "language": extension["lang"],
            "homeUrl": extension["baseUrl"],
            "mirrorUrls": [],
            "message": None,
        }],
    })
    legacy_extensions.append({
        "name": f"Aniyomi: {extension['name']}",
        "pkg": extension["packageName"],
        "apk": apk,
        "lang": extension["lang"],
        "code": extension["versionCode"],
        "version": extension["versionName"],
        "nsfw": 0 if extension["contentWarning"] == "SAFE" else 1,
        "sources": [{
            "name": extension["name"],
            "lang": extension["lang"],
            "id": extension["sourceId"],
            "baseUrl": extension["baseUrl"],
        }],
    })

modern = {
    "name": manifest["storeName"],
    "badgeLabel": manifest["storeName"],
    "signingKey": manifest["signingKey"],
    "contact": {"website": "https://github.com/lyraEz/Rede", "discord": None},
    "extensionList": {"extensions": modern_extensions},
    "extensionListUrl": None,
}
repo = {
    "index_v2": f"{RAW}/store-v2.json",
    "meta": {
        "name": manifest["storeName"],
        "shortName": manifest["storeName"],
        "website": "https://github.com/lyraEz/Rede",
        "signingKeyFingerprint": manifest["signingKey"],
    },
}

def write(name, data, compact=False):
    (ROOT / name).write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":") if compact else None, indent=None if compact else 2) + "\n"
    )

for name in ("store-v2.json", "store-v2-1000-7.json", "store-v2-1000-8.json", "store-v2-1000-9.json", "store.json"):
    write(name, modern)
write("repo.json", repo)
write("index.json", legacy_extensions)
write("index.min.json", legacy_extensions, compact=True)
