#!/usr/bin/env python3
"""Apply the RedeToons 1000-12 changes to an Apktool decoded 14.2 APK.

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
                    'const-string v0, "https://redetoons.beer"', "base URL")

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

# Create the default hoster through the source API's common helper. NyanTV has
# the older four-argument Hoster constructor, while Aniyomi/AniZen have a
# newer constructor. The helper exists in all three apps.
method = ".method protected hosterListParse(Lokhttp3/Response;)Ljava/util/List;"
if data.count(method) != 1:
    raise RuntimeError("Expected one hosterListParse method")
start = data.index(method)
end = data.index(".end method", start) + len(".end method")
data = data[:start] + '''.method protected hosterListParse(Lokhttp3/Response;)Ljava/util/List;
    .locals 2

    sget-object v0, Leu/kanade/tachiyomi/animesource/model/Hoster;->Companion:Leu/kanade/tachiyomi/animesource/model/Hoster$Companion;

    invoke-direct {p0, p1}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->w(Lokhttp3/Response;)Ljava/util/List;
    move-result-object v1

    invoke-virtual {v0, v1}, Leu/kanade/tachiyomi/animesource/model/Hoster$Companion;->toHosterList(Ljava/util/List;)Ljava/util/List;
    move-result-object v0

    return-object v0
.end method''' + data[end:]

# NyanTV still uses the legacy video list methods and requires the old
# episodeVideoParse abstract method. Modern Aniyomi uses the hoster methods.
legacy_video_methods = '''.method protected videoListRequest(Leu/kanade/tachiyomi/animesource/model/SEpisode;)Lokhttp3/Request;
    .locals 1

    invoke-virtual {p0, p1}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->hosterListRequest(Leu/kanade/tachiyomi/animesource/model/SEpisode;)Lokhttp3/Request;
    move-result-object v0

    return-object v0
.end method

.method protected videoListParse(Lokhttp3/Response;)Ljava/util/List;
    .locals 1

    invoke-direct {p0, p1}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->w(Lokhttp3/Response;)Ljava/util/List;
    move-result-object v0

    return-object v0
.end method

.method protected episodeVideoParse(Lokhttp3/Response;)Leu/kanade/tachiyomi/animesource/model/SEpisode;
    .locals 2

    new-instance v0, Ljava/lang/UnsupportedOperationException;
    const-string v1, "Episode video metadata not used"
    invoke-direct {v0, v1}, Ljava/lang/UnsupportedOperationException;-><init>(Ljava/lang/String;)V
    throw v0
.end method

'''
data = data.replace(method, legacy_video_methods + method, 1)

# Use the same per-source preferences as the existing content filter. The
# playable API paths are built from getBaseUrl(), so a single origin setting
# covers catalog, episodes, playback, and request headers.
old_base_url = '''.method public getBaseUrl()Ljava/lang/String;
    .locals 0

    .line 1
    iget-object p0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->b:Ljava/lang/String;

    .line 2
    .line 3
    return-object p0
.end method'''
new_base_url = '''.method public getBaseUrl()Ljava/lang/String;
    .locals 3

    invoke-direct {p0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->k()Landroid/content/SharedPreferences;
    move-result-object v0

    const-string v1, "domain_override"
    const-string v2, ""
    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0

    invoke-static {v0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->normalizeDomain(Ljava/lang/String;)Ljava/lang/String;
    move-result-object v0

    if-eqz v0, :default_domain
    return-object v0

    :default_domain
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->b:Ljava/lang/String;
    return-object v0
.end method

.method public static normalizeDomain(Ljava/lang/String;)Ljava/lang/String;
    .locals 2

    if-eqz p0, :invalid
    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;
    move-result-object p0

    const-string v0, "(?i)/browse/?$"
    const-string v1, ""
    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0

    const-string v0, "/$"
    invoke-virtual {p0, v0, v1}, Ljava/lang/String;->replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object p0

    const-string v0, "https://redetoons.bond"
    invoke-virtual {v0, p0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
    move-result v0
    if-eqz v0, :validate
    const-string p0, "https://redetoons.beer"
    return-object p0

    :validate
    const-string v0, "https://[A-Za-z0-9.-]+(:[0-9]{1,5})?"
    invoke-virtual {p0, v0}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
    move-result v0
    if-eqz v0, :invalid
    return-object p0

    :invalid
    const/4 v0, 0x0
    return-object v0
.end method

.method public saveDomain(Ljava/lang/String;)Z
    .locals 3

    invoke-virtual {p1}, Ljava/lang/String;->trim()Ljava/lang/String;
    move-result-object p1
    invoke-virtual {p1}, Ljava/lang/String;->isEmpty()Z
    move-result v0
    if-nez v0, :store

    invoke-static {p1}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->normalizeDomain(Ljava/lang/String;)Ljava/lang/String;
    move-result-object p1
    if-eqz p1, :invalid

    :store
    invoke-direct {p0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->k()Landroid/content/SharedPreferences;
    move-result-object v0
    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;
    move-result-object v0
    const-string v1, "domain_override"
    invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;
    move-result-object v0
    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V
    const/4 v0, 0x1
    return v0

    :invalid
    const/4 v0, 0x0
    return v0
.end method'''
data = replace_once(data, old_base_url, new_base_url, "configurable domain")

browser_client = '''.method public getClient()Lokhttp3/OkHttpClient;
    .locals 4

    invoke-super {p0}, Leu/kanade/tachiyomi/animesource/online/AnimeHttpSource;->getClient()Lokhttp3/OkHttpClient;
    move-result-object v0
    invoke-virtual {v0}, Lokhttp3/OkHttpClient;->newBuilder()Lokhttp3/OkHttpClient$Builder;
    move-result-object v0
    new-instance v1, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;
    invoke-static {}, Lkeiyoushi/utils/a;->a()Landroid/app/Application;
    move-result-object v2
    invoke-virtual {p0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->getBaseUrl()Ljava/lang/String;
    move-result-object v3
    invoke-direct {v1, v2, v3}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeBrowserInterceptor;-><init>(Landroid/content/Context;Ljava/lang/String;)V
    invoke-virtual {v0, v1}, Lokhttp3/OkHttpClient$Builder;->addInterceptor(Lokhttp3/Interceptor;)Lokhttp3/OkHttpClient$Builder;
    move-result-object v0
    invoke-virtual {v0}, Lokhttp3/OkHttpClient$Builder;->build()Lokhttp3/OkHttpClient;
    move-result-object v0
    return-object v0
.end method

'''
data = replace_once(data, '.method public getFilterList()Leu/kanade/tachiyomi/animesource/model/AnimeFilterList;',
                    browser_client + '.method public getFilterList()Leu/kanade/tachiyomi/animesource/model/AnimeFilterList;',
                    "browser-backed play-link client")

old_pref_end = '''    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    .line 147
    .line 148
    .line 149
    return-void
.end method'''
new_pref_end = '''    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    new-instance v0, Landroidx/preference/EditTextPreference;
    invoke-virtual {p1}, Landroidx/preference/PreferenceScreen;->getContext()Landroid/content/Context;
    move-result-object v1
    invoke-direct {v0, v1}, Landroidx/preference/EditTextPreference;-><init>(Landroid/content/Context;)V
    const-string v1, "domain_override"
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setKey(Ljava/lang/String;)V
    const-string v1, "Domínio do site"
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setTitle(Ljava/lang/CharSequence;)V
    const-string v1, "URL HTTPS do site; /browse é opcional. Em branco: redetoons.beer"
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setSummary(Ljava/lang/CharSequence;)V
    const/4 v1, 0x0
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setPersistent(Z)V
    invoke-direct {p0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->k()Landroid/content/SharedPreferences;
    move-result-object v1
    const-string v2, "domain_override"
    const-string v3, ""
    invoke-interface {v1, v2, v3}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
    move-result-object v1
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setText(Ljava/lang/String;)V
    new-instance v1, Leu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener;
    invoke-direct {v1, p0}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener;-><init>(Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;)V
    invoke-virtual {v0, v1}, Landroidx/preference/EditTextPreference;->setOnPreferenceChangeListener(Landroidx/preference/Preference$OnPreferenceChangeListener;)V
    invoke-virtual {p1, v0}, Landroidx/preference/PreferenceScreen;->addPreference(Landroidx/preference/Preference;)Z

    return-void
.end method'''
data = replace_once(data, old_pref_end, new_pref_end, "domain preference")
data = replace_once(data, '.method public setupPreferenceScreen(Landroidx/preference/PreferenceScreen;)V\n    .locals 5',
                    '.method public setupPreferenceScreen(Landroidx/preference/PreferenceScreen;)V\n    .locals 6', "preference registers")
source.write_text(data)

listener = root / "smali/eu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener.smali"
listener.write_text('''.class public final Leu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener;
.super Ljava/lang/Object;
.implements Landroidx/preference/Preference$OnPreferenceChangeListener;

.field private final source:Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;

.method public constructor <init>(Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;)V
    .locals 0
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V
    iput-object p1, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener;->source:Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;
    return-void
.end method

.method public onPreferenceChange(Landroidx/preference/Preference;Ljava/lang/Object;)Z
    .locals 3
    check-cast p2, Ljava/lang/String;
    iget-object v0, p0, Leu/kanade/tachiyomi/animeextension/pt/redetoons/DomainPreferenceListener;->source:Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;
    invoke-virtual {v0, p2}, Leu/kanade/tachiyomi/animeextension/pt/redetoons/RedeToons;->saveDomain(Ljava/lang/String;)Z
    move-result v0
    if-nez v0, :done
    invoke-virtual {p1}, Landroidx/preference/Preference;->getContext()Landroid/content/Context;
    move-result-object v1
    const-string v2, "Use uma URL HTTPS válida, com ou sem /browse"
    const/4 p0, 0x0
    invoke-static {v1, v2, p0}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;
    move-result-object v1
    invoke-virtual {v1}, Landroid/widget/Toast;->show()V
    :done
    return v0
.end method
''')

for name in ("RedeBrowserInterceptor.smali", "RedeBrowserFetcher.smali"):
    (source.parent / name).write_text((Path(__file__).parent / "smali-patches" / name).read_text())

config = root / "apktool.yml"
data = config.read_text()
data = replace_once(data, "  versionCode: 2\n  versionName: 14.2",
                    "  versionCode: 1000012\n  versionName: 14.1000-12", "version")
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
print("Patched RedeToons to 1000-12")
