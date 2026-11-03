package androidx.preference;
public class Preference {
  public interface OnPreferenceChangeListener { boolean onPreferenceChange(Preference p, Object v); }
  public android.content.Context getContext() { return null; }
  public void setKey(String k) {} public void setTitle(CharSequence t) {} public void setSummary(CharSequence s) {}
  public void setPersistent(boolean b) {} public void setOnPreferenceChangeListener(OnPreferenceChangeListener l) {}
}
