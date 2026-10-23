package app.cadence.core;

import static org.junit.Assert.*;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class CatalogPodcastTest {
  private JsonObject fixture(String name) throws Exception {
    try (var in = getClass().getResourceAsStream("/" + name + ".json")) {
      return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  @Test
  public void musicUsesArtistMetadataWithoutVideoClutter() throws Exception {
    var song = MusicCatalog.parseEntries(fixture("song")).get(0);
    assertEquals("song", song.kind);
    assertEquals("Netru Ponavai", song.title);
    assertEquals("Anirudh Ravichander, Shankar Mahadevan & Vignesh Shivan", song.artist);
    assertEquals(265, song.track.durationSeconds);
    assertTrue(song.artwork.contains("w544-h544"));
    assertFalse(song.artist.contains("plays"));
    var album = MusicCatalog.parseEntries(fixture("album")).get(0);
    assertEquals("album", album.kind);
    assertEquals("Nobin Paul", album.artist);
    assertTrue(album.browseId.startsWith("MPRE"));
    var artist = MusicCatalog.parseEntries(fixture("artist")).get(0);
    assertEquals("artist", artist.kind);
    assertEquals("", artist.artist);
  }

  @Test
  public void chartsUseLiveKindsAndChosenLanguages() throws Exception {
    var home = MusicCatalog.parseCharts(fixture("charts"));
    assertTrue(home.shelves.stream().anyMatch(s -> s.title.startsWith("Trending now")));
    var languages =
        home.shelves.stream()
            .filter(s -> s.title.equals("Your languages"))
            .findFirst()
            .orElseThrow();
    assertTrue(languages.entries.size() >= 2);
    assertTrue(
        languages.entries.stream()
            .allMatch(
                e ->
                    e.title.contains("Tamil")
                        || e.title.contains("Telugu")
                        || e.title.contains("Malayalam")
                        || e.title.contains("English")));
    assertEquals(
        "Tamil",
        languages.entries.get(0).title.substring(languages.entries.get(0).title.length() - 5));
    var cached = MusicCatalog.deserialize(MusicCatalog.serialize(home));
    assertEquals(home.shelves.size(), cached.shelves.size());
  }

  @Test
  public void cleanupPreservesMeaningfulVersionsAndUnicode() {
    assertEquals(
        "Veezha Veera (Tamil)",
        MusicText.videoTitle(
            "Veezha Veera Lyrical (Tamil) | Film credits | Artist credits", "Label"));
    assertEquals(
        "Song (Live Remix)", MusicText.videoTitle("Song (Live Remix) | Official Video", "Artist"));
    assertEquals("தமிழ் (Live Remix)", MusicText.title("தமிழ் (Live Remix) [Official Video]"));
    assertEquals("Artist", MusicText.artist("Artist - Topic"));
    assertEquals("Song (Acoustic)", MusicText.title("Song (Acoustic) | Official Audio"));
  }

  private byte[] bytes(String xml) {
    return xml.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void rssParsesArtworkDurationAndStableEpisodeIdentity() throws Exception {
    String xml =
        "<rss xmlns:itunes='http://www.itunes.com/dtds/podcast-1.0.dtd'><channel><title>Tamil"
            + " voices</title><itunes:author>Creator</itunes:author><itunes:image"
            + " href='https://example.com/cover.jpg'/><item><title>Episode &amp;"
            + " One</title><guid>one</guid><enclosure url='https://example.com/one.mp3'"
            + " type='audio/mpeg'/><itunes:duration>1:02:03</itunes:duration></item><item><title>Duplicate</title><guid>one</guid><enclosure"
            + " url='https://example.com/one.mp3'/></item><item><title>No"
            + " audio</title></item></channel></rss>";
    var show = Podcasts.parseFeed("https://example.com/feed", bytes(xml));
    assertEquals(1, show.episodes.size());
    var e = show.episodes.get(0);
    assertEquals("Episode & One", e.title);
    assertEquals(3723, e.duration);
    assertEquals(show.artwork, e.artwork);
    assertTrue(e.track().podcast);
    assertEquals("https://example.com/feed#one", e.track().episodeId);
    assertEquals("Creator", show.author);
  }

  @Test
  public void atomAndNestedOpmlWork() throws Exception {
    var show =
        Podcasts.parseFeed(
            "https://example.com/feed",
            bytes(
                "<feed"
                    + " xmlns='http://www.w3.org/2005/Atom'><title>Show</title><entry><id>ep</id><title>Hello</title><link"
                    + " rel='enclosure' type='audio/mp4' href='/episode.m4a'/></entry></feed>"));
    assertEquals("https://example.com/episode.m4a", show.episodes.get(0).audioUrl);
    var urls =
        Podcasts.opml(
            bytes(
                "<opml><body><outline text='Folder'><outline"
                    + " xmlUrl='https://example.com/rss'/><outline"
                    + " xmlUrl='https://example.com/rss'/><outline"
                    + " xmlUrl='http://example.org/feed'/></outline></body></opml>"));
    assertEquals(2, urls.size());
    assertEquals("https://example.org/feed", urls.get(1));
  }

  @Test
  public void rejectsExternalEntitiesInUtf8AndUtf16() throws Exception {
    String xml =
        "<!DOCTYPE rss [<!ENTITY x SYSTEM"
            + " 'file:///etc/passwd'>]><rss><channel><title>&x;</title></channel></rss>";
    for (byte[] data : new byte[][] {bytes(xml), xml.getBytes(StandardCharsets.UTF_16)})
      try {
        Podcasts.parseFeed("https://example.com/feed", data);
        fail("DTD accepted");
      } catch (java.io.IOException expected) {
        assertTrue(expected.getMessage().contains("document type"));
      }
  }

  @Test
  public void spotifyUsesShowMetadataInsteadOfLatestEpisode() throws Exception {
    assertEquals(
        "Radiolab & Friends",
        Podcasts.spotifyTitleHtml(
            "<meta property='og:title' content='Radiolab &amp; Friends'/><title>Latest"
                + " episode</title>"));
    try {
      Podcasts.spotifyTitleHtml("<html>Sign in</html>");
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void boundedImportsAndSchemesAreChecked() throws Exception {
    try {
      Podcasts.https("file:///tmp/feed");
      fail();
    } catch (java.io.IOException expected) {
    }
    try {
      Podcasts.readLimited(new java.io.ByteArrayInputStream(new byte[20]), 10);
      fail();
    } catch (java.io.IOException expected) {
    }
    try {
      Podcasts.opml(bytes("<rss><channel/></rss>"));
      fail();
    } catch (java.io.IOException expected) {
    }
  }
}
