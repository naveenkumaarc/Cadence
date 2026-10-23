package app.cadence.music;

import android.content.Context;
import app.cadence.core.Track;
import java.util.*;
import org.json.*;

/** Desired downloads sync; paths, transfer IDs and completed flags never leave the device. */
final class DownloadList {
  static final String PLAYLIST_ID = "cadence:download-list";
  static synchronized JSONObject snapshot(Context c) throws Exception { return JsonDisk.read(c,"download-list.json"); }
  static synchronized void replace(Context c, JSONObject list) throws Exception { JsonDisk.write(c,"download-list.json",list); }
  static synchronized void remember(Context c, Track track) throws Exception {
    if (!track.sourceUrl.startsWith("https://")) return;
    JSONObject list = snapshot(c); list.put(id(track), Library.encode(track)); replace(c,list); CloudSync.changed(c);
  }
  static synchronized void forget(Context c, Track track) throws Exception {
    JSONObject list=snapshot(c); list.remove(id(track)); replace(c,list); CloudSync.changed(c);
  }
  static String id(Track track) { return JsonDisk.hash((track.podcast?"podcast:":"song:")+SongDownloads.key(track.sourceUrl)); }
  static synchronized Library.Playlist playlist(Context c) throws Exception {
    Library.Playlist out = new Library.Playlist("Saved downloads"); out.id=PLAYLIST_ID;
    JSONObject list=snapshot(c);
    for (Iterator<String> it=list.keys();it.hasNext();) { Track t=Library.decode(list.getJSONObject(it.next())); out.tracks.add(t); }
    return out;
  }
}
