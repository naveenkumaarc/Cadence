package app.cadence.core;

import static org.junit.Assert.*;
import java.io.StringReader;
import org.junit.Test;

public class TrackVersionTest {
  @Test public void manuallyMatchedCsvSongKeepsIdentityAndStoresChosenDuration() throws Exception {
    Track imported = PlaylistCsv.read(new StringReader("Track Name,Artist Name(s),Track URI\nMy song,My artist,spotify:track:original\n")).get(0);
    Track selected = new Track("Different upload title", "Uploader", "https://www.youtube.com/watch?v=abcdefghijk", "https://example.com/art", 213);
    imported.confirmVersion(selected);
    assertEquals("My song", imported.title); assertEquals("My artist", imported.artist);
    assertEquals("original", imported.spotifyId); assertEquals(213, imported.durationSeconds);
    assertEquals(selected.sourceUrl, imported.sourceUrl); assertEquals("confirmed", imported.matchState);
  }
  @Test public void missingCandidateDurationDoesNotEraseKnownLength() {
    Track imported = new Track("Song", "Artist", "", "", 180);
    imported.confirmVersion(new Track("Upload", "Uploader", "https://www.youtube.com/watch?v=abcdefghijk", "", 0));
    assertEquals(180, imported.durationSeconds);
  }
}
