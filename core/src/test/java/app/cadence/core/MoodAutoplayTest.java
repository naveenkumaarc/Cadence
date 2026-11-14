package app.cadence.core;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class MoodAutoplayTest {
  private static MoodAutoplay.Signals song(
      String source, String artist, String album, String language, int year, boolean liked,
      boolean downloaded, int completed, int skipped, int recent) {
    return new MoodAutoplay.Signals(source, source, Set.of(artist), Set.of(artist), album,
        language, year, Set.of("melody"), 260, liked, downloaded, completed, skipped, recent);
  }

  @Test public void tamilMelodyKeepsLanguageAndEraFirstWithoutRepeats() {
    var seed = song("seed", "ilaiyaraaja", "mouna ragam", "tamil", 1986, false, false, 0, 0, -1);
    var profile = MoodAutoplay.profile(seed, List.of());
    List<MoodAutoplay.Signals> fakeCatalog = List.of(
        song("tamil-era", "s janaki", "mouna ragam", "tamil", 1987, false, false, 1, 0, -1),
        song("tamil-liked", "ilaiyaraaja", "other", "tamil", 1984, true, true, 3, 0, -1),
        song("english-new", "other", "other", "english", 2025, false, false, 0, 0, -1),
        song("tamil-skipped", "ilaiyaraaja", "mouna ragam", "tamil", 1985, false, false, 0, 6, -1),
        song("tamil-era", "duplicate", "duplicate", "tamil", 1987, false, false, 0, 0, -1));
    List<MoodAutoplay.Ranked> ranked = MoodAutoplay.rank(profile, fakeCatalog, Set.of("seed"), false);
    assertEquals(Set.of("tamil-liked","tamil-era"),Set.of(
        ranked.get(0).song().source(),ranked.get(1).song().source()));
    assertTrue(ranked.indexOf(ranked.stream().filter(v -> v.song().source().equals("english-new")).findFirst().orElseThrow()) > 1);
    assertEquals(ranked.size(), ranked.stream().map(v -> v.song().source()).distinct().count());
  }

  @Test public void fullPlayBendsProfileAndQuickSkipPushesAway() {
    var seed = song("seed", "first", "old", "tamil", 1990, false, false, 0, 0, -1);
    var next = song("next", "second", "new", "tamil", 2000, false, false, 0, 0, -1);
    var profile = MoodAutoplay.profile(seed, List.of(
        new MoodAutoplay.Feedback(next, false, true, false),
        new MoodAutoplay.Feedback(song("skip", "third", "skip", "english", 2024, false, false, 0, 0, -1), false, false, true)));
    var ranked = MoodAutoplay.rank(profile, List.of(
        song("toward", "second", "new", "tamil", 2001, false, false, 0, 0, -1),
        song("away", "third", "skip", "english", 2024, false, false, 0, 0, -1)), Set.of(), false);
    assertEquals("toward", ranked.get(0).song().source());
  }

  @Test public void recentAndSkippedSongsArePenalized() {
    var seed = song("seed", "artist", "album", "tamil", 2010, false, false, 0, 0, -1);
    var ranked = MoodAutoplay.rank(MoodAutoplay.profile(seed, List.of()), List.of(
        song("fresh", "artist", "album", "tamil", 2010, false, false, 0, 0, -1),
        song("recent", "artist", "album", "tamil", 2010, true, true, 5, 2, 0)), Set.of(), false);
    assertEquals("fresh", ranked.get(0).song().source());
  }
}
