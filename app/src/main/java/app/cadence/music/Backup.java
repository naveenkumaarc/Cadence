package app.cadence.music;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Portable metadata backup. Does not include audio, account tokens or file-access grants. */
final class Backup {
  static JSONObject create(Context c, Library library, PodcastStore podcasts) throws Exception {
    library.save();
    JSONObject out = new JSONObject().put("format", "cadence.backup").put("version", 2)
        .put("createdAt", System.currentTimeMillis())
        .put("playlists", new JSONArray(new String(java.nio.file.Files.readAllBytes(new java.io.File(c.getFilesDir(), "library.json").toPath()), StandardCharsets.UTF_8)))
        .put("podcasts", podcasts.backup());
    for (String name : List.of("playback", "podcast-progress")) {
      JSONObject prefs = new JSONObject();
      for (var entry : c.getSharedPreferences(name, 0).getAll().entrySet())
        if (entry.getValue() instanceof String || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean)
          prefs.put(entry.getKey(), entry.getValue());
      out.put(name, prefs);
    }
    return out;
  }

  static JSONObject queue(androidx.media3.common.Player player) throws Exception {
    JSONObject out=new JSONObject(); JSONArray items=new JSONArray(),order=new JSONArray();
    for(int i=0;i<player.getMediaItemCount();i++) {
      var extras=player.getMediaItemAt(i).mediaMetadata.extras;
      if(extras==null) throw new java.io.IOException("The current queue has incomplete metadata.");
      items.put(new JSONObject(extras.getString("track","{}")).put("manualQueue",extras.getBoolean("manualQueue",false)));
    }
    var timeline=player.getCurrentTimeline(); int at=timeline.getFirstWindowIndex(player.getShuffleModeEnabled());
    for(int i=0;i<player.getMediaItemCount() && at!=androidx.media3.common.C.INDEX_UNSET;i++) {
      order.put(at); at=timeline.getNextWindowIndex(at,androidx.media3.common.Player.REPEAT_MODE_OFF,player.getShuffleModeEnabled());
    }
    return out.put("items",items).put("order",order).put("index",Math.max(0,player.getCurrentMediaItemIndex()))
      .put("position",player.getCurrentPosition()).put("shuffle",player.getShuffleModeEnabled()).put("repeat",player.getRepeatMode());
  }

  static int restore(Context c, JSONObject root, Library library, PodcastStore podcasts) throws Exception {
    if (!root.optString("format").equals("cadence.backup") || root.optInt("version") > 2)
      throw new java.io.IOException("Choose a Cadence backup JSON file.");
    JSONArray all = root.getJSONArray("playlists");
    if (all.length() > 2000) throw new java.io.IOException("This backup has too many playlists.");
    List<Library.Playlist> parsed = new ArrayList<>();
    for (int i = 0; i < all.length(); i++) {
      JSONObject j = all.getJSONObject(i); Library.Playlist playlist = new Library.Playlist(j.getString("name"));
      playlist.id = j.optString("id", playlist.id); playlist.origin = j.optString("origin");
      JSONArray tracks = j.getJSONArray("tracks");
      if (tracks.length() > 20000) throw new java.io.IOException("A playlist is too large.");
      for (int k = 0; k < tracks.length(); k++) playlist.tracks.add(Library.decode(tracks.getJSONObject(k)));
      parsed.add(playlist);
    }
    // Merge by stable ID. Existing songs remain, including songs added since the backup.
    int count = 0;
    for (Library.Playlist incoming : parsed) {
      Library.Playlist existing = library.playlists.stream().filter(p -> p.id.equals(incoming.id)).findFirst().orElse(null);
      if (existing == null) { library.playlists.add(incoming); count++; }
      else {
        Map<String,Integer> present = new HashMap<>(), wanted = new HashMap<>();
        for (var t : existing.tracks) present.merge(key(t), 1, Integer::sum);
        for (var t : incoming.tracks) {
          int n = wanted.merge(key(t), 1, Integer::sum);
          if (n > present.getOrDefault(key(t), 0)) existing.tracks.add(t);
        }
      }
    }
    library.save();
    if (root.has("podcasts")) podcasts.restore(root.getJSONObject("podcasts"));
    for (String name : List.of("podcast-progress", "playback")) {
      JSONObject prefs = root.optJSONObject(name); if (prefs == null) continue;
      var edit = c.getSharedPreferences(name, 0).edit();
      for (Iterator<String> it = prefs.keys(); it.hasNext();) {
        String key = it.next(); Object value = prefs.get(key);
        if (name.equals("playback")) {
          if (key.equals("podcastSpeed") && value instanceof Number) edit.putFloat(key, Math.max(.5f, Math.min(3f, ((Number)value).floatValue())));
          else if (Set.of("crossfade","skipBack","skipForward").contains(key) && value instanceof Number) edit.putInt(key, ((Number)value).intValue());
          else if (Set.of("languages","homeLanguage").contains(key) && value instanceof String) edit.putString(key, (String)value);
          else if (key.equals("autoplay") && value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
        } else if (value instanceof Boolean) edit.putBoolean(key,(Boolean)value);
        else if (value instanceof Number) edit.putLong(key,Math.max(0, ((Number)value).longValue()));
      }
      edit.apply();
    }
    return count;
  }
  private static String key(app.cadence.core.Track t) { return t.sourceUrl + "\n" + t.title + "\n" + t.artist; }
}
