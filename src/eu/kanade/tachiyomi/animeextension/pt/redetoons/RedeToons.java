package eu.kanade.tachiyomi.animeextension.pt.redetoons;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource;
import eu.kanade.tachiyomi.animesource.model.AnimeFilter;
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList;
import eu.kanade.tachiyomi.animesource.model.AnimesPage;
import eu.kanade.tachiyomi.animesource.model.Hoster;
import eu.kanade.tachiyomi.animesource.model.SAnime;
import eu.kanade.tachiyomi.animesource.model.SEpisode;
import eu.kanade.tachiyomi.animesource.model.Video;
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource;
import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * RedeToons runs a StreamingCommunity-style Laravel/Inertia site. Every page
 * embeds its data as JSON in a {@code <script id="su-page-data">} tag, and the
 * player loads a direct MP4 from {@code /api/watch/{title}/next?e={episode}}.
 * Playback needs a logged-in session that passed the site's security check,
 * which the user does once in the WebView.
 */
public final class RedeToons extends AnimeHttpSource implements ConfigurableAnimeSource {

    private static final String DEFAULT_BASE_URL = "https://redetoons.watch";
    private static final String LOCALE = "/br";
    private static final String PREF_DOMAIN = "domain_override";
    private static final String PREF_USER = "account_user";
    private static final String PREF_PASS = "account_password";

    // Search pages send an empty su-page-data first and the results later in su-final-page.
    private static final Pattern FINAL_PAGE_DATA = Pattern.compile(
            "<script[^>]*id=\"su-final-page\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern PAGE_DATA = Pattern.compile(
            "<script[^>]*id=\"su-page-data\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern DATA_PAGE_ATTR = Pattern.compile("data-page=\"([^\"]*)\"");
    private static final Pattern TMDB_IMAGE = Pattern.compile("^/tmdb/(\\w)/+(.+)$");
    private static final Pattern LEGACY_URL = Pattern.compile("^/?(?:tv|movie)/(\\d+)$");
    private static final Pattern WATCH_URL = Pattern.compile("/watch/(\\d+)(?:\\?e=(\\d+))?");
    private static final Pattern TITLE_URL = Pattern.compile("/titles/(\\d+)(-[^/?#]*)?");

    /** First title id of page 1 for each listing, used to stop when paging is ignored. */
    private final java.util.Map<String, Long> firstIds = new java.util.HashMap<>();

    @Override public String getName() { return "RedeToons"; }
    @Override public String getLang() { return "pt-BR"; }
    @Override public boolean getSupportsLatest() { return true; }

    // ---------------------------------------------------------------- domain

    @Override
    public String getBaseUrl() {
        String custom = normalizeDomain(preferences().getString(PREF_DOMAIN, ""));
        return custom != null ? custom : DEFAULT_BASE_URL;
    }

    /** Keeps only the HTTPS origin; old RedeToons domains map to the current one. */
    public static String normalizeDomain(String value) {
        if (value == null) return null;
        String v = value.trim().replaceFirst("(?i)^(https://[A-Za-z0-9.-]+(:[0-9]{1,5})?)(/.*)?$", "$1");
        if (v.matches("https://redetoons[.](bond|beer|one|win|gay)")) return DEFAULT_BASE_URL;
        return v.matches("https://[A-Za-z0-9.-]+(:[0-9]{1,5})?") ? v : null;
    }

    private SharedPreferences preferences() {
        return appContext().getSharedPreferences("source_" + getId(), 0);
    }

    private static Context appContext() {
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void setupPreferenceScreen(PreferenceScreen screen) {
        EditTextPreference domain = new EditTextPreference(screen.getContext());
        domain.setKey(PREF_DOMAIN);
        domain.setTitle("Domínio do site");
        domain.setSummary("URL HTTPS do site; caminhos como /br são opcionais. Em branco: redetoons.watch");
        domain.setPersistent(false);
        domain.setText(preferences().getString(PREF_DOMAIN, ""));
        domain.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference pref, Object newValue) {
                String text = String.valueOf(newValue).trim();
                String stored = text.isEmpty() ? "" : normalizeDomain(text);
                if (stored == null) {
                    Toast.makeText(pref.getContext(), "Use uma URL HTTPS válida, com ou sem caminho", 0).show();
                    return false;
                }
                preferences().edit().putString(PREF_DOMAIN, stored).apply();
                return true;
            }
        });
        screen.addPreference(domain);

        screen.addPreference(accountPreference(screen, PREF_USER, "E-mail ou usuário da RedeToons",
                "Usado para entrar automaticamente quando o site pedir login", false));
        screen.addPreference(accountPreference(screen, PREF_PASS, "Senha da RedeToons",
                "Fica salva só neste aparelho", true));
    }

    private EditTextPreference accountPreference(PreferenceScreen screen, final String key, String title,
                                                 final String summary, final boolean secret) {
        final EditTextPreference pref = new EditTextPreference(screen.getContext());
        pref.setKey(key);
        pref.setTitle(title);
        pref.setPersistent(false);
        String current = preferences().getString(key, "");
        pref.setText(current);
        pref.setSummary(current.isEmpty() ? summary : secret ? "••••••" : current);
        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference p, Object newValue) {
                String value = String.valueOf(newValue).trim();
                preferences().edit().putString(key, value).apply();
                pref.setSummary(value.isEmpty() ? summary : secret ? "••••••" : value);
                return true;
            }
        });
        return pref;
    }

    // --------------------------------------------------------------- helpers

    @Override
    protected Headers.Builder headersBuilder() {
        return super.headersBuilder().set("Referer", getBaseUrl() + "/");
    }

    private Request get(String url) {
        return new Request.Builder().url(url).headers(getHeaders()).build();
    }

    private static String body(Response response) {
        try {
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            response.close();
        }
    }

    /** Reads the Inertia props from a page, or the JSON body of an API call. */
    private static JSONObject props(String html) {
        try {
            String trimmed = html.trim();
            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(trimmed);
                return json.has("props") ? json.getJSONObject("props") : json;
            }
            Matcher m = FINAL_PAGE_DATA.matcher(html);
            if (m.find()) return new JSONObject(m.group(1)).getJSONObject("props");
            m = PAGE_DATA.matcher(html);
            if (m.find()) return new JSONObject(m.group(1)).getJSONObject("props");
            m = DATA_PAGE_ATTR.matcher(html);
            if (m.find()) return new JSONObject(unescapeHtml(m.group(1))).getJSONObject("props");
        } catch (JSONException e) {
            throw new RuntimeException("Resposta inesperada do site", e);
        }
        if (html.contains("challenges.cloudflare.com") || html.contains("Verificação de segurança")) {
            throw new RuntimeException("O site pediu uma verificação. Abra a RedeToons no WebView e tente de novo.");
        }
        throw new RuntimeException("Não foi possível ler a página do site.");
    }

    private static String unescapeHtml(String s) {
        return s.replace("&quot;", "\"").replace("&#039;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
    }

    private String imageUrl(JSONObject image) {
        String file = image.optString("filename", "");
        Matcher m = TMDB_IMAGE.matcher(file);
        if (m.find()) {
            String size;
            switch (m.group(1)) {
                case "p": size = "w342"; break;
                case "b": size = "w1280"; break;
                case "l": size = "w500"; break;
                default: size = "w780";
            }
            return getBaseUrl() + "/img/tmdb/t/p/" + size + "/" + m.group(2);
        }
        if (file.matches("^/[a-zA-Z0-9].*")) return getBaseUrl() + "/img/tmdb/t/p/w342" + file;
        String original = image.optString("original_url_field", "");
        if (original.startsWith("/")) return getBaseUrl() + original;
        return original.startsWith("http") ? original : null;
    }

    private String poster(JSONObject title) {
        JSONArray images = title.optJSONArray("images");
        if (images == null) return null;
        String fallback = null;
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.optJSONObject(i);
            if (image == null) continue;
            String type = image.optString("type");
            if ("poster".equals(type)) return imageUrl(image);
            if (fallback == null && ("cover".equals(type) || "background".equals(type))) fallback = imageUrl(image);
        }
        return fallback;
    }

    private SAnime toAnime(JSONObject title) {
        SAnime anime = SAnime.Companion.create();
        anime.setUrl(LOCALE + "/titles/" + title.optLong("id") + "-" + title.optString("slug", "titulo"));
        anime.setTitle(title.optString("name", String.valueOf(title.optLong("id"))));
        anime.setThumbnail_url(poster(title));
        return anime;
    }

    private void addTitles(JSONArray titles, List<SAnime> out, Set<Long> seen) {
        if (titles == null) return;
        for (int i = 0; i < titles.length(); i++) {
            JSONObject title = titles.optJSONObject(i);
            if (title != null && title.has("id") && seen.add(title.optLong("id"))) out.add(toAnime(title));
        }
    }

    // ------------------------------------------------------------- catalogue

    @Override
    protected Request popularAnimeRequest(int page) {
        return get(getBaseUrl() + LOCALE);
    }

    @Override
    protected AnimesPage popularAnimeParse(Response response) {
        JSONObject props = props(body(response));
        List<SAnime> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        JSONArray sliders = props.optJSONArray("sliders");
        if (sliders != null) {
            for (int i = 0; i < sliders.length(); i++) {
                JSONObject slider = sliders.optJSONObject(i);
                if (slider != null) addTitles(slider.optJSONArray("titles"), list, seen);
            }
        }
        addTitles(props.optJSONArray("titles"), list, seen);
        return new AnimesPage(list, false);
    }

    @Override
    protected Request latestUpdatesRequest(int page) {
        return browseRequest(getBaseUrl() + LOCALE + "/archive", page);
    }

    @Override
    protected AnimesPage latestUpdatesParse(Response response) {
        return browseParse(response);
    }

    @Override
    protected Request searchAnimeRequest(int page, String query, AnimeFilterList filters) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return latestUpdatesRequest(page);
        try {
            return browseRequest(getBaseUrl() + LOCALE + "/search?q=" + URLEncoder.encode(q, "UTF-8"), page);
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected AnimesPage searchAnimeParse(Response response) {
        return browseParse(response);
    }

    private Request browseRequest(String url, int page) {
        if (page <= 1) return get(url);
        return get(url + (url.contains("?") ? "&" : "?") + "offset=" + ((page - 1) * 60));
    }

    private AnimesPage browseParse(Response response) {
        okhttp3.HttpUrl url = response.request().url();
        String offsetParam = url.queryParameter("offset");
        int offset = offsetParam == null ? 0 : Integer.parseInt(offsetParam);
        String key = url.newBuilder().removeAllQueryParameters("offset").build().toString();

        JSONObject props = props(body(response));
        JSONArray titles = props.optJSONArray("titles");
        List<SAnime> list = new ArrayList<>();
        addTitles(titles, list, new HashSet<Long>());
        if (list.isEmpty()) return new AnimesPage(list, false);

        long firstId = titles.optJSONObject(0).optLong("id");
        if (offset == 0) {
            firstIds.put(key, firstId);
        } else if (Long.valueOf(firstId).equals(firstIds.get(key))) {
            // The site ignored the offset and sent page 1 again.
            return new AnimesPage(new ArrayList<SAnime>(), false);
        }
        int total = props.optInt("totalCount", 0);
        return new AnimesPage(list, offset + titles.length() < total);
    }

    @Override
    public AnimeFilterList getFilterList() {
        return new AnimeFilterList(new AnimeFilter[0]);
    }

    // --------------------------------------------------------------- details

    /** Turns URLs saved by 1000-13 and older ("tv/123") into the new title pages. */
    private String titlePath(String url) {
        Matcher legacy = LEGACY_URL.matcher(url);
        if (legacy.find()) return LOCALE + "/titles/" + legacy.group(1) + "-titulo";
        return url.startsWith("/") ? url : "/" + url;
    }

    @Override
    public Request animeDetailsRequest(SAnime anime) {
        return get(getBaseUrl() + titlePath(anime.getUrl()));
    }

    @Override
    protected SAnime animeDetailsParse(Response response) {
        JSONObject title = props(body(response)).optJSONObject("title");
        if (title == null) throw new RuntimeException("Título não encontrado no site.");
        SAnime anime = toAnime(title);
        anime.setDescription(title.optString("plot", ""));

        List<String> genres = new ArrayList<>();
        JSONArray g = title.optJSONArray("genres");
        if (g != null) for (int i = 0; i < g.length(); i++) {
            String name = g.optJSONObject(i) == null ? "" : g.optJSONObject(i).optString("name", "");
            if (!name.isEmpty()) genres.add(name);
        }
        anime.setGenre(join(genres));

        List<String> directors = new ArrayList<>();
        JSONArray d = title.optJSONArray("main_directors");
        if (d != null) for (int i = 0; i < d.length(); i++) {
            String name = d.optJSONObject(i) == null ? "" : d.optJSONObject(i).optString("name", "");
            if (!name.isEmpty()) directors.add(name);
        }
        if (!directors.isEmpty()) anime.setAuthor(join(directors));

        String status = title.optString("status", "");
        if ("movie".equals(title.optString("type"))) {
            anime.setStatus(2);
        } else if (status.equals("Returning Series") || status.equals("In Production")) {
            anime.setStatus(1);
        } else if (status.equals("Ended") || status.equals("Canceled") || status.equals("Released")) {
            anime.setStatus(2);
        } else {
            anime.setStatus(0);
        }
        return anime;
    }

    private static String join(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(p);
        }
        return sb.toString();
    }

    // -------------------------------------------------------------- episodes

    @Override
    protected Request episodeListRequest(SAnime anime) {
        return animeDetailsRequest(anime);
    }

    @Override
    protected List<SEpisode> episodeListParse(Response response) {
        JSONObject props = props(body(response));
        JSONObject title = props.optJSONObject("title");
        if (title == null) throw new RuntimeException("Título não encontrado no site.");
        long titleId = title.optLong("id");
        String slug = title.optString("slug", "titulo");

        List<SEpisode> episodes = new ArrayList<>();
        if ("movie".equals(title.optString("type"))) {
            SEpisode movie = SEpisode.Companion.create();
            movie.setUrl(LOCALE + "/watch/" + titleId);
            movie.setName("Filme");
            movie.setEpisode_number(1f);
            episodes.add(movie);
            return episodes;
        }

        JSONObject loaded = props.optJSONObject("loadedSeason");
        JSONArray seasons = title.optJSONArray("seasons");
        boolean multiSeason = seasons != null && seasons.length() > 1;
        // su_seasons lists the seasons that have video on the site.
        Set<Integer> available = null;
        JSONArray suSeasons = title.optJSONArray("su_seasons");
        if (suSeasons != null) {
            available = new HashSet<>();
            for (int i = 0; i < suSeasons.length(); i++) available.add(suSeasons.optInt(i));
        }
        if (seasons == null || seasons.length() == 0) {
            if (loaded != null) addEpisodes(loaded, titleId, false, episodes);
        } else {
            for (int i = 0; i < seasons.length(); i++) {
                JSONObject season = seasons.optJSONObject(i);
                if (season == null) continue;
                int number = season.optInt("number");
                if (available != null && !available.contains(number)) continue;
                JSONObject data = loaded != null && loaded.optInt("number") == number
                        ? loaded
                        : fetchSeason(titleId, slug, number);
                if (data != null) addEpisodes(data, titleId, multiSeason, episodes);
            }
        }
        Collections.reverse(episodes);
        return episodes;
    }

    private JSONObject fetchSeason(long titleId, String slug, int number) {
        String url = getBaseUrl() + LOCALE + "/titles/" + titleId + "-" + slug + "/season-" + number;
        try {
            Response response = getClient().newCall(get(url)).execute();
            return props(body(response)).optJSONObject("loadedSeason");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addEpisodes(JSONObject season, long titleId, boolean multiSeason, List<SEpisode> out) {
        JSONArray list = season.optJSONArray("episodes");
        if (list == null) return;
        int seasonNumber = season.optInt("number", 1);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        for (int i = 0; i < list.length(); i++) {
            JSONObject ep = list.optJSONObject(i);
            if (ep == null) continue;
            String avail = ep.optString("su_avail", "ok");
            if (!avail.isEmpty() && !"ok".equals(avail)) continue;

            int number = ep.optInt("number", i + 1);
            String name = ep.optString("name", "");
            SEpisode episode = SEpisode.Companion.create();
            episode.setUrl(LOCALE + "/watch/" + titleId + "?e=" + ep.optLong("id"));
            String prefix = multiSeason ? "T" + seasonNumber + " E" + number : "Episódio " + number;
            episode.setName(name.isEmpty() ? prefix : prefix + " - " + name);
            episode.setEpisode_number(multiSeason ? seasonNumber * 1000 + number : number);
            String air = ep.optString("air_date", "");
            if (!air.isEmpty() && !"null".equals(air)) {
                try {
                    episode.setDate_upload(dateFormat.parse(air).getTime());
                } catch (java.text.ParseException ignored) {
                }
            }
            out.add(episode);
        }
    }

    @Override
    protected List<SAnime> seasonListParse(Response response) {
        return new ArrayList<>();
    }

    // ---------------------------------------------------------------- videos

    /**
     * The app fails on any non-2xx answer before parsing, and the watch page or the
     * API may answer with one when a login or security check is due. So request a
     * small static file that always loads, carry the episode path along, and make
     * the API calls in parseVideos.
     */
    private Request playRequest(SEpisode episode) {
        if (!WATCH_URL.matcher(episode.getUrl()).find()) {
            throw new RuntimeException("Episódio salvo por uma versão antiga. Atualize a lista de episódios.");
        }
        okhttp3.HttpUrl url = okhttp3.HttpUrl.get(getBaseUrl() + "/favicon.ico").newBuilder()
                .addQueryParameter("rt", episode.getUrl())
                .build();
        return get(url.toString());
    }

    private Request apiRequest(String titleId, String episodeParam, String watchPath) {
        String url = getBaseUrl() + "/api/watch/" + titleId + "/next?"
                + (episodeParam.isEmpty() ? "" : episodeParam + "&") + "lang=br";
        Headers headers = getHeaders().newBuilder()
                .set("Accept", "application/json")
                .set("Referer", getBaseUrl() + watchPath)
                .build();
        return new Request.Builder().url(url).headers(headers).build();
    }

    /** Videos, or the API error that stopped them. */
    private static final class PlayResult {
        List<Video> videos;
        String error = "";
    }

    private PlayResult fetchPlay(String titleId, String[] params, String watchPath) {
        PlayResult result = new PlayResult();
        for (String param : params) {
            String text;
            try (Response response = getClient().newCall(apiRequest(titleId, param, watchPath)).execute()) {
                text = response.body().string();
            } catch (IOException e) {
                result.error = "rede";
                continue;
            }
            JSONObject json;
            try {
                json = new JSONObject(text.trim());
            } catch (JSONException e) {
                result.error = "verificacao";
                return result;
            }
            String error = json.optString("error", "");
            if (error.isEmpty()) {
                result.videos = videosFrom(json.optJSONObject("play"));
                if (result.videos.isEmpty()) result.error = "sem_video";
                return result;
            }
            result.error = error;
            // Movies: keep trying while the site rejects the parameters.
            if (!"parametros".equals(error)) return result;
        }
        return result;
    }

    @Override
    protected Request videoListRequest(SEpisode episode) {
        return playRequest(episode);
    }

    @Override
    protected Request hosterListRequest(SEpisode episode) {
        return playRequest(episode);
    }

    @Override
    protected List<Video> videoListParse(Response response) {
        return parseVideos(response);
    }

    @Override
    protected List<Hoster> hosterListParse(Response response) {
        return Hoster.Companion.toHosterList(parseVideos(response));
    }

    /** NyanTV still declares this abstract method; it is never called. */
    protected SEpisode episodeVideoParse(Response response) {
        throw new UnsupportedOperationException("Episode video metadata not used");
    }

    @Override
    protected String videoUrlParse(Response response) {
        throw new UnsupportedOperationException();
    }

    private List<Video> parseVideos(Response response) {
        String watchPath = response.request().url().queryParameter("rt");
        response.close();
        if (watchPath == null) throw new RuntimeException("Episódio não encontrado no site.");
        Matcher m = WATCH_URL.matcher(watchPath);
        if (!m.find()) throw new RuntimeException("Episódio não encontrado no site.");
        String titleId = m.group(1);
        // Movies have no episode id; the site has accepted e={title id} for them.
        String[] params = m.group(2) != null
                ? new String[] {"e=" + m.group(2)}
                : new String[] {"e=" + titleId, "e=0", ""};

        PlayResult result = fetchPlay(titleId, params, watchPath);
        if (result.videos != null && !result.videos.isEmpty()) return result.videos;
        if ("parametros".equals(result.error)) {
            throw new RuntimeException("O site recusou o pedido deste filme. Ele ainda não é suportado pela extensão.");
        }

        // The site wants a login or a fresh security check: do both in a hidden browser.
        String user = preferences().getString(PREF_USER, "");
        String pass = preferences().getString(PREF_PASS, "");
        RedeBrowser.Result browser = RedeBrowser.unlock(appContext(), getBaseUrl() + watchPath,
                getHeaders().get("User-Agent"), user, pass);
        if (browser == RedeBrowser.Result.NEEDS_LOGIN) {
            throw new RuntimeException("Para assistir, coloque o e-mail e a senha da sua conta RedeToons nas configurações da extensão.");
        }
        if (browser == RedeBrowser.Result.LOGIN_REJECTED) {
            throw new RuntimeException("O site recusou o login. Confira o e-mail e a senha nas configurações da extensão.");
        }

        result = fetchPlay(titleId, params, watchPath);
        if (result.videos != null && !result.videos.isEmpty()) return result.videos;
        throw new RuntimeException(errorMessage(result.error, browser, user.isEmpty() || pass.isEmpty()));
    }

    private static String errorMessage(String error, RedeBrowser.Result browser, boolean noAccount) {
        switch (error) {
            case "login":
                return noAccount
                        ? "Para assistir, coloque o e-mail e a senha da sua conta RedeToons nas configurações da extensão."
                        : "Não foi possível entrar na sua conta. Confira o e-mail e a senha nas configurações da extensão.";
            case "indisponivel":
                return browser == RedeBrowser.Result.TIMEOUT
                        ? "O site não liberou este vídeo: a verificação de segurança não terminou a tempo. Tente de novo."
                        : "Este título não está disponível para assistir no site agora.";
            case "verificacao":
                return "A verificação de segurança do site não foi concluída. Tente de novo em alguns segundos.";
            case "parametros":
                return "O site recusou o pedido deste filme. Ele ainda não é suportado pela extensão.";
            case "rede":
                return "Sem conexão com o site.";
            case "sem_video":
                return "Nenhum vídeo disponível para este episódio.";
            default:
                return "O site não liberou o vídeo (" + error + ").";
        }
    }

    private List<Video> videosFrom(JSONObject play) {
        List<Video> videos = new ArrayList<>();
        if (play == null) return videos;
        Headers videoHeaders = new Headers.Builder()
                .set("Referer", getBaseUrl() + "/")
                .set("Origin", getBaseUrl())
                .build();
        Set<String> seen = new HashSet<>();
        JSONArray sources = play.optJSONArray("sources");
        if (sources != null) for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.optJSONObject(i);
            if (source == null) continue;
            String src = source.optString("src", "");
            if (src.isEmpty() || !seen.add(src)) continue;
            String label = source.optString("label", source.optString("audio", "Padrão"));
            videos.add(new Video(src, "RedeToons - " + label, src, videoHeaders, null, null, 0x30, null));
        }
        String src = play.optString("src", "");
        if (!src.isEmpty() && seen.add(src)) {
            videos.add(new Video(src, "RedeToons", src, videoHeaders, null, null, 0x30, null));
        }
        return videos;
    }
}
