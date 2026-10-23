package app.cadence.core;

/** Conservative display cleanup; preserves version, remix and language information. */
public final class MusicText {
  private MusicText() {}

  public static String title(String s) {
    if (s == null) return "";
    return s.replaceAll(
            "(?i)[\\[(]\\s*(official\\s+)?(music\\s+)?(video|audio|lyric(s|"
                + " video)?|visuali[sz]er)(\\s+video)?\\s*[\\])]",
            "")
        .replaceAll(
            "(?i)\\s*[|–-]\\s*(official\\s+)?(music video|lyric video|official audio|official"
                + " video)\\s*$",
            "")
        .replaceAll("\\s{2,}", " ")
        .trim();
  }

  public static String title(String value, String artist) {
    String title = title(value);
    String name = artist(artist);
    if (!name.isEmpty()) {
      String prefix = name + " - ";
      if (title.regionMatches(true, 0, prefix, 0, prefix.length()))
        title = title.substring(prefix.length()).trim();
    }
    return title;
  }

  /** Video credits are only stripped for video entries; release catalogue titles stay intact. */
  public static String videoTitle(String value, String artist) {
    String title = title(value.split("[|¦]", 2)[0], artist);
    return title
        .replaceAll("(?i)\\s*\\(song\\)\\s*$", "")
        .replaceAll("(?i)\\b(official\\s+)?(lyrical(\\s+video)?|music\\s+video)\\b", "")
        .replaceAll("\\s{2,}", " ")
        .trim();
  }

  public static String artist(String s) {
    return s == null
        ? ""
        : s.replaceFirst("(?i)\\s*-\\s*Topic$", "").replaceAll("\\s{2,}", " ").trim();
  }
}
