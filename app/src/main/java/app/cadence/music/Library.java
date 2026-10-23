package app.cadence.music;

import android.content.Context;
import android.util.AtomicFile;
import app.cadence.core.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class Library {
  final List<Playlist> playlists = new ArrayList<>();
  private final AtomicFile file;
  private final Context context;

  Library(Context context) throws Exception {
    this.context=context.getApplicationContext();
    file = new AtomicFile(new File(context.getFilesDir(), "library.json"));
    if (file.getBaseFile().exists()) {
      JSONArray data = new JSONArray(new String(file.readFully(), StandardCharsets.UTF_8));
      for (int i = 0; i < data.length(); i++) {
        JSONObject p = data.getJSONObject(i);
        Playlist playlist = new Playlist(p.getString("name"));
        playlist.id = p.getString("id");
        playlist.origin = p.optString("origin");
        JSONArray tracks = p.getJSONArray("tracks");
        for (int j = 0; j < tracks.length(); j++)
          playlist.tracks.add(decode(tracks.getJSONObject(j)));
        playlists.add(playlist);
      }
    }
    if (playlists.stream().noneMatch(p -> p.id.equals("favourites"))) {
      Playlist likes = new Playlist("Favourites");
      likes.id = "favourites";
      playlists.add(0, likes);
    }
  }

  void save() throws Exception {
    JSONArray all = new JSONArray();
    for (Playlist playlist : playlists) {
      JSONArray tracks = new JSONArray();
      for (Track track : playlist.tracks) tracks.put(encode(track));
      all.put(
          new JSONObject()
              .put("id", playlist.id)
              .put("name", playlist.name)
              .put("origin", playlist.origin)
              .put("tracks", tracks));
    }
    FileOutputStream stream = null;
    try {
      stream = file.startWrite();
      stream.write(all.toString().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(stream);
      CloudSync.changed(context);
    } catch (IOException e) {
      if (stream != null) file.failWrite(stream);
      throw e;
    }
  }

  static JSONObject encode(Track t) throws Exception {
    return new JSONObject()
        .put("title", t.title)
        .put("artist", t.artist)
        .put("url", t.sourceUrl)
        .put("art", t.artwork)
        .put("duration", t.durationSeconds)
        .put("spotify", t.spotifyId)
        .put("query", t.query)
        .put("match", t.matchState)
        .put("podcast", t.podcast)
        .put("episodeId", t.episodeId);
  }

  static Track decode(JSONObject j) {
    Track t =
        new Track(
            j.optString("title"),
            j.optString("artist"),
            j.optString("url"),
            j.optString("art"),
            j.optLong("duration"));
    t.podcast = j.optBoolean("podcast");
    t.episodeId = j.optString("episodeId");
    t.spotifyId = j.optString("spotify");
    t.query = j.optString("query");
    t.matchState = j.optString("match", "original");
    return t;
  }

  static final class Playlist {
    String id = UUID.randomUUID().toString(), name, origin = "";
    final List<Track> tracks = new ArrayList<>();

    Playlist(String name) {
      this.name = name;
    }
  }
}
