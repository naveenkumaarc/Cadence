package app.cadence.core;

/** Stores stable source IDs, never temporary signed stream URLs. */
public final class Track {
  public String title = "", artist = "", sourceUrl = "", artwork = "";
  public String spotifyId = "", query = "", matchState = "original";
  public long durationSeconds;
  public boolean podcast = false;
  public String episodeId = "";

  public Track() {}

  public Track(String title, String artist, String url, String artwork, long duration) {
    this.title = title;
    this.artist = artist;
    sourceUrl = url;
    this.artwork = artwork;
    durationSeconds = duration;
  }

  public String searchQuery() {
    return title + " " + artist + " audio";
  }
}
