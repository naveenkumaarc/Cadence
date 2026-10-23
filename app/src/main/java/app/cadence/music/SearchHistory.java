package app.cadence.music;

import android.content.Context;
import app.cadence.core.MusicCatalog;
import app.cadence.core.Track;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** A small, local history; removing an entry never removes saved music. */
final class SearchHistory {
  private final android.content.SharedPreferences prefs;
  SearchHistory(Context context) { prefs = context.getSharedPreferences("search-history", 0); }

  List<MusicCatalog.Entry> entries() {
    List<MusicCatalog.Entry> out = new ArrayList<>();
    try {
      JSONArray array = new JSONArray(prefs.getString("entries", "[]"));
      for (int i = 0; i < Math.min(30, array.length()); i++) {
        JSONObject item = array.getJSONObject(i);
        MusicCatalog.Entry entry = new MusicCatalog.Entry();
        entry.kind = item.optString("kind", "song");
        entry.title = item.optString("title"); entry.artist = item.optString("artist");
        entry.artwork = item.optString("artwork"); entry.browseId = item.optString("browseId");
        if (item.has("track")) entry.track = Library.decode(item.getJSONObject("track"));
        out.add(entry);
      }
    } catch (Exception ignored) { /* History cannot prevent access to the library. */ }
    return out;
  }

  void remember(Track track) {
    if (track.podcast) return;
    MusicCatalog.Entry entry = new MusicCatalog.Entry();
    entry.kind = "song"; entry.title = track.title; entry.artist = track.artist;
    entry.artwork = track.artwork; entry.track = track;
    remember(entry);
  }

  void remember(MusicCatalog.Entry entry) {
    List<MusicCatalog.Entry> all = entries();
    all.removeIf(e -> key(e).equals(key(entry))); all.add(0, entry);
    write(all.subList(0, Math.min(30, all.size())));
  }

  void remove(MusicCatalog.Entry entry) {
    List<MusicCatalog.Entry> all = entries();
    all.removeIf(e -> key(e).equals(key(entry))); write(all);
  }
  void clear() { prefs.edit().remove("entries").apply(); }
  private String key(MusicCatalog.Entry entry) {
    return entry.kind + ":" + (entry.track == null ? entry.browseId : entry.track.sourceUrl)
        + ":" + entry.title + ":" + entry.artist;
  }
  private void write(List<MusicCatalog.Entry> all) {
    try {
      JSONArray array = new JSONArray();
      for (MusicCatalog.Entry entry : all) {
        JSONObject item = new JSONObject().put("kind", entry.kind).put("title", entry.title)
            .put("artist", entry.artist).put("artwork", entry.artwork).put("browseId", entry.browseId);
        if (entry.track != null) item.put("track", Library.encode(entry.track));
        array.put(item);
      }
      prefs.edit().putString("entries", array.toString()).apply();
    } catch (Exception ignored) { }
  }
}
