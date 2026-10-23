package app.cadence.probe;

import app.cadence.core.*;
import java.util.concurrent.*;
import okhttp3.Request;

/** Optional network diagnostics. Never logs signed music URLs or tokens. */
public final class CatalogProbe {
  public static void main(String[] args) throws Exception {
    var home = MusicCatalog.home();
    System.out.println("CHARTS: " + home.shelves.size() + " shelves");
    for (var shelf : home.shelves)
      System.out.println(
          shelf.title
              + ": "
              + shelf.entries.size()
              + " entries, art="
              + shelf.entries.stream().filter(e -> !e.artwork.isBlank()).count());
    var workers = Executors.newFixedThreadPool(4);
    var futures = new java.util.ArrayList<Future<?>>();
    for (String lang : MusicCatalog.LANGUAGES)
      futures.add(
          workers.submit(
              () -> {
                try {
                  var songs = MusicCatalog.picks(lang);
                  if (songs.isEmpty()) throw new IllegalStateException("No songs");
                  System.out.println(
                      "MUSIC "
                          + lang
                          + ": "
                          + songs.size()
                          + " songs; first="
                          + songs.get(0).title
                          + " / "
                          + songs.get(0).artist);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    for (var future : futures) future.get();
    workers.shutdown();
    var albums = MusicCatalog.search("Tamil", "Albums");
    if (albums.isEmpty() || !albums.get(0).kind.equals("album"))
      throw new IllegalStateException("No albums");
    System.out.println("ALBUMS: " + albums.size());
    var tracks = MusicCatalog.browse(albums.get(0).browseId);
    System.out.println(
        "ALBUM BROWSE: " + tracks.stream().filter(e -> e.track != null).count() + " songs");
    String title =
        Podcasts.spotifyShowTitle("https://open.spotify.com/show/2hmkzUtix0qTqvtpPcMzEL");
    System.out.println("SPOTIFY SHOW: " + title);
    var shows = Podcasts.search(title);
    var match =
        shows.stream().filter(s -> s.title.equalsIgnoreCase(title)).findFirst().orElseThrow();
    var show = Podcasts.feed(match.feedUrl);
    if (show.episodes.isEmpty()) throw new IllegalStateException("No episodes");
    System.out.println("PODCAST: " + show.title + "; " + show.episodes.size() + " audio episodes");
    try (var r =
        HttpDownloader.MEDIA_CLIENT
            .newCall(
                new Request.Builder()
                    .url(show.episodes.get(0).audioUrl)
                    .header("Range", "bytes=0-131071")
                    .build())
            .execute()) {
      if (!r.isSuccessful() || r.body() == null)
        throw new IllegalStateException("Podcast media HTTP " + r.code());
      byte[] data = r.body().byteStream().readNBytes(131072);
      System.out.println(
          "PODCAST MEDIA: HTTP "
              + r.code()
              + "; "
              + data.length
              + " bytes; "
              + r.header("Content-Type"));
      if (data.length < 4096 || r.header("Content-Type", "").contains("text/html"))
        throw new IllegalStateException("Not audio");
      java.nio.file.Files.write(
          java.nio.file.Path.of("/workspace/scratch/2a06d7beda18/podcast-sample.audio"), data);
    }
    System.out.println(
        "Catalogue and public podcast host checks passed. Android playback needs separate"
            + " testing.");
    System.exit(0);
  }
}
