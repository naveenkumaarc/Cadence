package app.cadence.core;

import java.util.*;

/** Pure, deterministic ranking for a listening session. Network and storage stay outside this class. */
public final class MoodAutoplay {
  public record Signals(
      String source,
      String title,
      Set<String> artists,
      Set<String> composers,
      String album,
      String language,
      int year,
      Set<String> tags,
      long durationSeconds,
      boolean liked,
      boolean downloaded,
      int completedPlays,
      int quickSkips,
      int recentRank) {
    public Signals {
      source = clean(source);
      title = clean(title);
      artists = normalized(artists);
      composers = normalized(composers);
      album = normalize(album);
      language = normalize(language);
      tags = normalized(tags);
      durationSeconds = Math.max(0, durationSeconds);
      completedPlays = Math.max(0, completedPlays);
      quickSkips = Math.max(0, quickSkips);
      recentRank = Math.max(-1, recentRank);
    }
  }

  public record Feedback(Signals song, boolean liked, boolean completed, boolean quickSkip) {}

  public static final class Profile {
    private final Map<String, Double> artists = new HashMap<>(), composers = new HashMap<>(),
        languages = new HashMap<>(), albums = new HashMap<>(), tags = new HashMap<>();
    private double duration;
    private double durationWeight;
    private double year;
    private double yearWeight;

    public String language() { return strongest(languages); }
    public int year() { return yearWeight == 0 ? 0 : (int)Math.round(year / yearWeight); }
    public long durationSeconds() { return durationWeight == 0 ? 0 : Math.round(duration / durationWeight); }
  }

  public record Ranked(Signals song, double score, boolean close) {}

  private MoodAutoplay() {}

  /** The first song anchors the session; the latest three accepted/played songs bend it gradually. */
  public static Profile profile(Signals first, List<Feedback> latest) {
    Profile profile = new Profile();
    add(profile, first, 4.0);
    int start = Math.max(0, latest.size() - 3);
    for (int i = start; i < latest.size(); i++) {
      Feedback feedback = latest.get(i);
      double weight = feedback.quickSkip ? -1.25 : feedback.liked || feedback.completed ? 2.0 : .75;
      add(profile, feedback.song, weight);
    }
    return profile;
  }

  /** Ranks unique candidates, then interleaves roughly seven close picks with three adjacent picks. */
  public static List<Ranked> rank(
      Profile profile, Collection<Signals> candidates, Set<String> excluded, boolean mixesLanguages) {
    Map<String, Ranked> unique = new LinkedHashMap<>();
    for (Signals candidate : candidates) {
      if (candidate.source.isBlank() || excluded.contains(candidate.source)) continue;
      double score = score(profile, candidate, mixesLanguages);
      boolean close = close(profile, candidate, mixesLanguages);
      Ranked ranked = new Ranked(candidate, score, close);
      Ranked old = unique.get(candidate.source);
      if (old == null || ranked.score > old.score) unique.put(candidate.source, ranked);
    }
    Comparator<Ranked> order = Comparator.comparingDouble(Ranked::score).reversed()
        .thenComparing(value -> value.song.source);
    List<Ranked> close = unique.values().stream().filter(Ranked::close).sorted(order).toList();
    List<Ranked> adjacent = unique.values().stream().filter(value -> !value.close).sorted(order).toList();
    List<Ranked> result = new ArrayList<>(unique.size());
    int near = 0, away = 0;
    while (near < close.size() || away < adjacent.size()) {
      for (int i = 0; i < 7 && near < close.size(); i++) result.add(close.get(near++));
      for (int i = 0; i < 3 && away < adjacent.size(); i++) result.add(adjacent.get(away++));
      if (near >= close.size() && away < adjacent.size()) result.add(adjacent.get(away++));
      if (away >= adjacent.size() && near < close.size()) result.add(close.get(near++));
    }
    return result;
  }

  private static double score(Profile profile, Signals song, boolean mixesLanguages) {
    double score = overlap(profile.artists, song.artists) * 7
        + overlap(profile.composers, song.composers) * 8
        + weight(profile.albums, song.album) * 7
        + overlap(profile.tags, song.tags) * 2.5;
    String language = profile.language();
    if (!language.isBlank() && !song.language.isBlank())
      score += language.equals(song.language) ? 8 : mixesLanguages ? -1.5 : -10;
    int era = profile.year();
    if (era > 0 && song.year > 0) {
      int difference = Math.abs(era - song.year);
      score += difference <= 4 ? 6 : difference <= 10 ? 2 : -Math.min(7, difference / 6d);
    }
    long duration = profile.durationSeconds();
    if (duration > 0 && song.durationSeconds > 0) {
      double ratio = Math.abs(duration - song.durationSeconds) / (double)Math.max(duration, 1);
      score += ratio <= .15 ? 2 : ratio <= .35 ? .5 : -1;
    }
    if (song.liked) score += 4;
    if (song.downloaded) score += 3;
    score += Math.min(5, song.completedPlays * 1.2);
    score -= Math.min(30, song.quickSkips * 5.0);
    if (song.recentRank >= 0) score -= Math.max(2, 14 - song.recentRank * .24);
    return score;
  }

  private static boolean close(Profile profile, Signals song, boolean mixesLanguages) {
    boolean language = profile.language().isBlank() || song.language.isBlank()
        || profile.language().equals(song.language) || mixesLanguages;
    boolean identity = overlap(profile.artists, song.artists) > 0
        || overlap(profile.composers, song.composers) > 0
        || weight(profile.albums, song.album) > 0;
    int era = profile.year();
    boolean sameEra = era == 0 || song.year == 0 || Math.abs(era - song.year) <= 10;
    return language && sameEra && (identity || sameEra && profile.language().equals(song.language));
  }

  private static void add(Profile profile, Signals song, double amount) {
    merge(profile.artists, song.artists, amount);
    merge(profile.composers, song.composers, amount);
    merge(profile.tags, song.tags, amount);
    merge(profile.languages, Set.of(song.language), amount);
    merge(profile.albums, Set.of(song.album), amount);
    if (song.durationSeconds > 0) {
      profile.duration += song.durationSeconds * amount;
      profile.durationWeight += amount;
    }
    if (song.year > 0) {
      profile.year += song.year * amount;
      profile.yearWeight += amount;
    }
  }

  private static void merge(Map<String, Double> target, Set<String> values, double amount) {
    for (String value : values) if (!value.isBlank()) {
      target.merge(value, amount, Double::sum);
      if (target.get(value) <= 0) target.remove(value);
    }
  }

  private static double overlap(Map<String, Double> profile, Set<String> values) {
    double best = 0;
    for (String value : values) best = Math.max(best, weight(profile, value));
    return best > 0 ? Math.min(1, best / 4d) : 0;
  }

  private static double weight(Map<String, Double> profile, String value) {
    return value == null || value.isBlank() ? 0 : Math.max(0, profile.getOrDefault(normalize(value), 0d));
  }

  private static String strongest(Map<String, Double> values) {
    return values.entrySet().stream().max(Map.Entry.<String,Double>comparingByValue()
        .thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).orElse("");
  }

  public static String language(String value, String fallback) {
    int tamil = 0, telugu = 0, malayalam = 0, kannada = 0, devanagari = 0, latin = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c >= 0x0b80 && c <= 0x0bff) tamil++;
      else if (c >= 0x0c00 && c <= 0x0c7f) telugu++;
      else if (c >= 0x0d00 && c <= 0x0d7f) malayalam++;
      else if (c >= 0x0c80 && c <= 0x0cff) kannada++;
      else if (c >= 0x0900 && c <= 0x097f) devanagari++;
      else if (Character.UnicodeScript.of(c) == Character.UnicodeScript.LATIN) latin++;
    }
    if (tamil > 0) return "tamil";
    if (telugu > 0) return "telugu";
    if (malayalam > 0) return "malayalam";
    if (kannada > 0) return "kannada";
    if (devanagari > 0) return "hindi";
    return latin > 0 ? normalize(fallback) : "";
  }

  public static int year(String value) {
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:19|20)\\d{2}").matcher(value);
    if (!matcher.find()) return 0;
    int year = Integer.parseInt(matcher.group());
    return year >= 1900 && year <= 2100 ? year : 0;
  }

  private static Set<String> normalized(Collection<String> source) {
    Set<String> out = new LinkedHashSet<>();
    if (source != null) for (String value : source) if (!normalize(value).isBlank()) out.add(normalize(value));
    return Collections.unmodifiableSet(out);
  }

  private static String normalize(String value) {
    return clean(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static String clean(String value) { return value == null ? "" : value.trim(); }
}
