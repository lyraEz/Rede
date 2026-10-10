#!/usr/bin/env python3
"""Apply the RedeToons 1000-8 changes to an Apktool decoded 14.2 APK.

Usage: python patch_rede.py PATH_TO_APKTOOL_DECODED_DIRECTORY
"""

from pathlib import Path
import sys


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label} match; found {count}")
    return text.replace(old, new, 1)


root = Path(sys.argv[1])
source = root / "smali/eu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons.smali"
data = source.read_text()
data = replace_once(data, 'const-string v0, "https://redetoons.win"',
                    'const-string v0, "https://redetoons.gay"', "base URL")

old_catalog_poster = '''    if-eqz p1, :cond_2

    .line 67
    .line 68
    const-string v0, "https://image.tmdb.org/t/p/w500"'''
new_catalog_poster = '''    if-eqz p1, :cond_2

    const-string v0, "http"
    invoke-virtual {p1, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :goto_0

    .line 67
    .line 68
    const-string v0, "https://image.tmdb.org/t/p/w500"'''
data = replace_once(data, old_catalog_poster, new_catalog_poster, "catalog poster")

old_details_poster = '''    if-eqz p2, :cond_1

    .line 51
    .line 52
    const-string v1, "https://image.tmdb.org/t/p/w500"'''
new_details_poster = '''    if-eqz p2, :cond_1

    const-string v1, "http"
    invoke-virtual {p2, v1}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z
    move-result v1
    if-nez v1, :goto_0

    .line 51
    .line 52
    const-string v1, "https://image.tmdb.org/t/p/w500"'''
data = replace_once(data, old_details_poster, new_details_poster, "details poster")

old_details_url = '''    const/16 v2, 0x2f

    .line 47
    .line 48
    const/4 v3, 0x2

    .line 49
    invoke-static {p1, v2, v1, v3, v1}, Lkotlin/text/StringsKt;->substringAfterLast$default(Ljava/lang/String;CLjava/lang/String;ILjava/lang/Object;)Ljava/lang/String;'''
new_details_url = '''    const-string v2, "/api/tmdb/"

    .line 47
    .line 48
    const/4 v3, 0x2

    .line 49
    invoke-static {p1, v2, v1, v3, v1}, Lkotlin/text/StringsKt;->substringAfter$default(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/String;'''
data = replace_once(data, old_details_url, new_details_url, "details media type")
source.write_text(data)

config = root / "apktool.yml"
data = config.read_text()
data = replace_once(data, "  versionCode: 2\n  versionName: 14.2",
                    "  versionCode: 1000008\n  versionName: 1000-8", "version")
config.write_text(data)

manifest = root / "AndroidManifest.xml"
data = manifest.read_text()
data = replace_once(
    data,
    '<meta-data android:name="tachiyomi.animeextension.class" android:value=".RedeToons"/>',
    '<meta-data android:name="tachiyomi.animeextension.class" android:value=".RedeToons"/>\n'
    '        <meta-data android:name="aniyomix.extensionLib" android:value="14"/>',
    "Aniyomi library metadata",
)
manifest.write_text(data)
print("Patched RedeToons to 1000-8")
