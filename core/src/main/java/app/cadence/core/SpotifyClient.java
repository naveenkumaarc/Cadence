package app.cadence.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public final class SpotifyClient {
  private final OkHttpClient http;
  private final String base;

  public SpotifyClient() {
    this(HttpDownloader.CLIENT, "https://api.spotify.com/v1/");
  }

  // Separate base URL permits deterministic pagination/error tests without Spotify credentials.
  SpotifyClient(OkHttpClient client, String base) {
    http = client;
    this.base = base;
  }

  public ImportedPlaylist playlist(String token, String playlistId) throws IOException {
    String id = Links.spotifyPlaylist(playlistId);
    JsonObject meta = get(token, base + "playlists/" + id);
    ImportedPlaylist result = new ImportedPlaylist(string(meta, "name"));
    String next = base + "playlists/" + id + "/items?limit=50";
    Set<String> visited = new HashSet<>();
    while (next != null) {
      if (!visited.add(next)) throw new IOException("Spotify returned a repeated playlist page.");
      JsonObject page = get(token, next);
      JsonArray items = page.getAsJsonArray("items");
      if (items == null) throw new IOException("Spotify did not return playlist items.");
      for (JsonElement element : items) {
        JsonObject wrapper = element.getAsJsonObject();
        JsonElement value = wrapper.has("item") ? wrapper.get("item") : wrapper.get("track");
        if (value == null || value.isJsonNull()) {
          result.skipped++;
          continue;
        }
        JsonObject song = value.getAsJsonObject();
        if (!"track".equals(string(song, "type")) || string(song, "name").isEmpty()) {
          result.skipped++;
          continue;
        }
        List<String> artists = new ArrayList<>();
        JsonArray artistArray = song.getAsJsonArray("artists");
        if (artistArray != null)
          for (JsonElement a : artistArray) artists.add(string(a.getAsJsonObject(), "name"));
        Track track = new Track(string(song, "name"), String.join(", ", artists), "", "", 0);
        track.spotifyId = string(song, "id");
        track.matchState = "unmatched";
        track.query = track.searchQuery();
        if (song.has("duration_ms") && !song.get("duration_ms").isJsonNull())
          track.durationSeconds = song.get("duration_ms").getAsLong() / 1000;
        result.tracks.add(track);
      }
      next = string(page, "next");
      if (next.isEmpty()) next = null;
    }
    return result;
  }

  private JsonObject get(String token, String url) throws IOException {
    HttpUrl target = HttpUrl.get(url), origin = HttpUrl.get(base);
    if (!target.scheme().equals(origin.scheme())
        || !target.host().equals(origin.host())
        || target.port() != origin.port()
        || !target.encodedPath().startsWith(origin.encodedPath()))
      throw new IOException("Rejected an unexpected Spotify pagination address.");
    try (var response =
        http.newCall(
                new Request.Builder().url(url).header("Authorization", "Bearer " + token).build())
            .execute()) {
      if (response.code() == 401) throw new IOException("Spotify session expired. Connect again.");
      if (response.code() == 403)
        throw new IOException(
            "Spotify denied access. Check Premium, authorized users, and ownership/collaboration of"
                + " this playlist. CSV import is also available.");
      if (response.code() == 429)
        throw new IOException(
            "Spotify rate limit. Retry after "
                + response.header("Retry-After", "a few")
                + " seconds.");
      if (!response.isSuccessful() || response.body() == null)
        throw new IOException("Spotify HTTP " + response.code());
      try {
        return JsonParser.parseString(response.body().string()).getAsJsonObject();
      } catch (RuntimeException e) {
        throw new IOException("Spotify returned an unreadable response.", e);
      }
    }
  }

  static String string(JsonObject json, String name) {
    return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : "";
  }

  public static final class ImportedPlaylist {
    public final String name;
    public final List<Track> tracks = new ArrayList<>();
    public int skipped;

    ImportedPlaylist(String name) {
      this.name = name;
    }
  }
}
