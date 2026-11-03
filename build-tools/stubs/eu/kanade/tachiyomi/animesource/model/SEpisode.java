package eu.kanade.tachiyomi.animesource.model;
public interface SEpisode {
  Companion Companion = null;
  final class Companion { public SEpisode create() { return null; } }
  String getUrl(); void setUrl(String s); void setName(String s);
  void setEpisode_number(float f); void setDate_upload(long l);
}
