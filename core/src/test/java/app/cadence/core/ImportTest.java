package app.cadence.core;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ImportTest {
  @Test
  public void quotedCsvPreservesOrderDuplicatesAndUnicode() throws Exception {
    var tracks =
        PlaylistCsv.read(
            new StringReader(
                "\ufeffTrack Name,Artist Name(s),Duration (ms),Track URI\r\n"
                    + "\"Song, One\",\"Artist \"\"A\"\"\",185000,spotify:track:abc\r\n"
                    + "தமிழ் பாடல்,கலைஞர்,240000,\r\n"
                    + "\"Song, One\",\"Artist \"\"A\"\"\",185000,\r\n"));
    assertEquals(3, tracks.size());
    assertEquals("Song, One", tracks.get(0).title);
    assertEquals("Artist \"A\"", tracks.get(0).artist);
    assertEquals(185, tracks.get(0).durationSeconds);
    assertEquals("தமிழ் பாடல்", tracks.get(1).title);
    assertEquals("", tracks.get(0).sourceUrl);
    assertEquals("abc", tracks.get(0).spotifyId);
    assertEquals("unmatched", tracks.get(0).matchState);
  }

  @Test
  public void quotedNewlinesAreOneSong() throws Exception {
    var tracks = PlaylistCsv.read(new StringReader("Title,Artist\n\"Two\nLines\",Artist\n"));
    assertEquals(1, tracks.size());
    assertEquals("Two\nLines", tracks.get(0).title);
  }

  @Test
  public void malformedAndMissingHeadersFailClearly() {
    assertThrows(
        java.io.IOException.class,
        () -> PlaylistCsv.read(new StringReader("Track Name,Artist\n\"oops,x")));
    assertThrows(
        java.io.IOException.class,
        () -> PlaylistCsv.read(new StringReader("Album,Year\nTest,2000")));
  }

  @Test
  public void standardYoutubeLinksCanonicalize() {
    String expected = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";
    assertEquals(expected, Links.youtubeVideo("https://youtu.be/dQw4w9WgXcQ?si=example"));
    assertEquals(
        expected, Links.youtubeVideo("https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=abc"));
    assertEquals(expected, Links.youtubeVideo("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
  }

  @Test
  public void youtubeLookalikeHostsAndNonVideosAreRejected() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Links.youtubeVideo("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Links.youtubeVideo("https://youtube.com/playlist?list=abc"));
    assertThrows(IllegalArgumentException.class, () -> Links.youtubeVideo("file:///etc/passwd"));
  }

  @Test
  public void spotifyLinksAcceptLocaleAndRejectTracks() {
    String id = "37i9dQZF1DXcBWIGoYBM5M";
    assertEquals(
        id, Links.spotifyPlaylist("https://open.spotify.com/intl-en/playlist/" + id + "?si=test"));
    assertEquals(id, Links.spotifyPlaylist("spotify:playlist:" + id));
    assertThrows(
        IllegalArgumentException.class,
        () -> Links.spotifyPlaylist("https://open.spotify.com/track/" + id));
    assertThrows(
        IllegalArgumentException.class,
        () -> Links.spotifyPlaylist("https://open.spotify.com.evil.test/playlist/" + id));
  }

  @Test
  public void matchingPenalizesUnrequestedVersionsAndWrongDuration() {
    Track wanted = new Track("Example Song", "Singer", "", "", 180);
    Track original = new Track("Example Song official audio", "Singer", "a", "", 181);
    Track cover = new Track("Example Song cover", "Singer", "b", "", 180);
    Track extended = new Track("Example Song official audio", "Singer", "c", "", 600);
    var matches = new ArrayList<>(List.of(cover, extended, original));
    MatchRanker.sort(wanted, matches);
    assertSame(original, matches.get(0));
  }
}
