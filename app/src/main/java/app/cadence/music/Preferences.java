package app.cadence.music;

import android.content.Context;
import java.util.*;

final class Preferences {
  static final String[] LANGUAGES = {"Tamil", "Telugu", "Malayalam", "English", "Hindi", "Kannada", "Punjabi", "Bengali"};
  static android.content.SharedPreferences get(Context c) { return c.getSharedPreferences("playback", 0); }
  static List<String> languages(Context c) {
    String saved = get(c).getString("languages", "Tamil,Telugu,Malayalam,English");
    List<String> out = new ArrayList<>();
    for (String language : LANGUAGES) if (Arrays.asList(saved.split(",")).contains(language)) out.add(language);
    if (out.isEmpty()) out.add("English");
    return out;
  }
  static int skip(Context c, boolean forward) {
    int v = get(c).getInt(forward ? "skipForward" : "skipBack", 15);
    return v == 30 || v == 60 ? v : 15;
  }
  static int crossfade(Context c) {
    int v = get(c).getInt("crossfade", 0);
    return v >= 5 && v <= 15 ? v : 0;
  }
}
