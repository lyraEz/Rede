package eu.kanade.tachiyomi.animesource.model;
public interface SAnime {
  Companion Companion = null;
  final class Companion { public SAnime create() { return null; } }
  String getUrl(); void setUrl(String s); String getTitle(); void setTitle(String s);
  void setThumbnail_url(String s); void setDescription(String s); void setGenre(String s);
  void setStatus(int s); void setAuthor(String s); void setInitialized(boolean b);
}
