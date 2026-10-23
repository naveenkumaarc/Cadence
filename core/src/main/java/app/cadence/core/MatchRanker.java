package app.cadence.core;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Suggestions only. A person confirms the selected version before it is saved. */
public final class MatchRanker {
  private MatchRanker() {}

  public static void sort(Track wanted, List<Track> candidates) {
    candidates.sort((a, b) -> Double.compare(score(wanted, b), score(wanted, a)));
  }

  static double score(Track wanted, Track candidate) {
    Set<String> desired = words(wanted.title + " " + wanted.artist);
    Set<String> found = words(candidate.title + " " + candidate.artist);
    int overlap = 0;
    for (String token : desired) if (found.contains(token)) overlap++;
    double score = desired.isEmpty() ? 0 : (double) overlap / desired.size();
    if (wanted.durationSeconds > 0 && candidate.durationSeconds > 0)
      score -= Math.min(0.7, Math.abs(wanted.durationSeconds - candidate.durationSeconds) / 120.0);
    String title = candidate.title.toLowerCase(Locale.ROOT);
    for (String modifier : List.of("cover", "karaoke", "remix", "sped up", "slowed", "live"))
      if (title.contains(modifier) && !wanted.title.toLowerCase(Locale.ROOT).contains(modifier))
        score -= 0.35;
    return score;
  }

  private static Set<String> words(String input) {
    String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    Set<String> out = new HashSet<>();
    for (String word : normalized.split("[^\\p{L}\\p{N}]+")) if (!word.isEmpty()) out.add(word);
    return out;
  }
}
