package eu.kanade.tachiyomi.animesource.online;
import eu.kanade.tachiyomi.animesource.model.*;
import java.util.List;
import okhttp3.*;
public abstract class AnimeHttpSource {
  public AnimeHttpSource() {}
  public abstract String getName();
  public abstract String getLang();
  public abstract String getBaseUrl();
  public abstract boolean getSupportsLatest();
  public long getId() { return 0; }
  public Headers getHeaders() { return null; }
  public OkHttpClient getClient() { return null; }
  protected Headers.Builder headersBuilder() { return null; }
  public AnimeFilterList getFilterList() { return null; }
  protected abstract Request popularAnimeRequest(int page);
  protected abstract AnimesPage popularAnimeParse(Response r);
  protected abstract Request latestUpdatesRequest(int page);
  protected abstract AnimesPage latestUpdatesParse(Response r);
  protected abstract Request searchAnimeRequest(int page, String q, AnimeFilterList f);
  protected abstract AnimesPage searchAnimeParse(Response r);
  public Request animeDetailsRequest(SAnime a) { return null; }
  protected abstract SAnime animeDetailsParse(Response r);
  protected Request episodeListRequest(SAnime a) { return null; }
  protected abstract List<SEpisode> episodeListParse(Response r);
  protected List<SAnime> seasonListParse(Response r) { return null; }
  protected Request videoListRequest(SEpisode e) { return null; }
  protected List<Video> videoListParse(Response r) { return null; }
  protected Request hosterListRequest(SEpisode e) { return null; }
  protected List<Hoster> hosterListParse(Response r) { return null; }
  protected String videoUrlParse(Response r) { return null; }
}
