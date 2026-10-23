package app.cadence.music;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.AtomicFile;
import app.cadence.core.HttpDownloader;
import app.cadence.core.Links;
import app.cadence.core.Track;
import app.cadence.core.YouTube;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Durable song downloads owned by Android's DownloadManager; stable source URLs stay in playlists. */
final class SongDownloads {
  static final String PLAYLIST_ID = "cadence:downloaded";
  private static SongDownloads instance;
  static synchronized SongDownloads get(Context context) throws Exception {
    if (instance == null) instance = new SongDownloads(context.getApplicationContext());
    return instance;
  }

  private final Context context;
  private final AtomicFile index;
  private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
  private static final class Entry {
    Track track; String name; long job; boolean complete;
  }
  static final class State {
    String label = "Download song", uri = "";
    boolean complete, active;
  }

  SongDownloads(Context context) throws Exception {
    this.context = context.getApplicationContext();
    index = new AtomicFile(new File(context.getFilesDir(), "song-downloads.json"));
    if (index.getBaseFile().exists()) {
      JSONArray list = new JSONArray(new String(index.readFully(), StandardCharsets.UTF_8));
      for (int i=0; i<list.length(); i++) {
        JSONObject record = list.getJSONObject(i);
        Entry entry = new Entry(); entry.track = Library.decode(record.getJSONObject("track"));
        entry.name = record.getString("file"); entry.job = record.getLong("job");
        entry.complete = record.optBoolean("complete");
        // The index never grants access outside our song download directory.
        if (!entry.name.matches("[a-f0-9]{64}\\.audio")) continue;
        entries.put(key(entry.track.sourceUrl), entry);
      }
    }
  }

  static String key(String source) {
    try { return Links.youtubeVideo(source); } catch (Exception ignored) { return source; }
  }
  private File file(Entry entry) {
    File directory = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
    return directory == null ? null : new File(directory, entry.name);
  }
  private DownloadManager manager() { return (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE); }

  synchronized State state(Track track) { return state(track.sourceUrl); }
  synchronized State state(String source) {
    State state = new State(); Entry entry = entries.get(key(source));
    if (entry == null) return state;
    File target = file(entry);
    if (entry.complete) {
      if (target != null && target.isFile() && target.length() > 16) {
        state.complete = true; state.label = "Downloaded"; state.uri = Uri.fromFile(target).toString();
      } else state.label = "Download again";
      return state;
    }
    try (var cursor = manager().query(new DownloadManager.Query().setFilterById(entry.job))) {
      if (cursor == null || !cursor.moveToFirst()) { state.label = "Download again"; return state; }
      int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
      if (status == DownloadManager.STATUS_SUCCESSFUL) {
        if (validAudio(target)) {
          entry.complete = true; save();
          state.complete = true; state.label = "Downloaded"; state.uri = Uri.fromFile(target).toString();
        } else state.label = "Download failed · Retry";
      } else if (status == DownloadManager.STATUS_FAILED) state.label = "Download failed · Retry";
      else {
        state.active = true;
        long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
        long done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        state.label = status == DownloadManager.STATUS_PAUSED ? "Download waiting" :
            total > 0 ? "Downloading · " + Math.min(100, done * 100 / total) + "%" : "Downloading…";
      }
    } catch (Exception failure) { state.label = "Download unavailable · Retry"; }
    return state;
  }

  synchronized String localUri(String source) {
    State state = state(source); return state.complete ? state.uri : null;
  }
  synchronized List<Track> tracks() {
    List<Track> tracks = new ArrayList<>();
    for (Entry entry : entries.values()) if (state(entry.track).complete) tracks.add(entry.track);
    return tracks;
  }
  synchronized Library.Playlist playlist() {
    Library.Playlist playlist = new Library.Playlist("Downloaded"); playlist.id = PLAYLIST_ID;
    playlist.tracks.addAll(tracks()); return playlist;
  }

  void start(Track track) throws Exception {
    synchronized (this) { State state = state(track); if (state.active || state.complete) return; }
    if (track.podcast) throw new IOException("Use the episode download control for podcasts.");
    YouTube.Resolved resolved = YouTube.resolve(track.sourceUrl);
    Track copy = Library.decode(Library.encode(track));
    if (resolved.track.durationSeconds > 0) copy.durationSeconds = resolved.track.durationSeconds;
    startResolved(copy, resolved.streamUrl, resolved.mimeType);
  }

  // Separate resolution from transfer so the real DownloadManager can be tested with public synthetic audio.
  synchronized void startResolved(Track track, String address, String mimeType) throws Exception {
    Uri uri = Uri.parse(address);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null) throw new IOException("A secure audio address is required.");
    State state = state(track); if (state.complete || state.active) return;
    String key = key(track.sourceUrl); Entry old = entries.get(key);
    if (old != null) { manager().remove(old.job); File previous = file(old); if (previous != null && previous.exists() && !previous.delete()) throw new IOException("Cannot replace the old download."); }
    Entry entry = new Entry(); entry.track = Library.decode(Library.encode(track));
    StringBuilder name = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))
      name.append(String.format(Locale.ROOT, "%02x", b & 255));
    entry.name = name + ".audio";
    File target = file(entry);
    if (target == null) throw new IOException("Phone storage is unavailable.");
    if (target.exists() && !target.delete()) throw new IOException("Cannot replace the old download.");
    var request = new DownloadManager.Request(uri).setTitle(track.title).setDescription(track.artist)
        .setMimeType(mimeType).addRequestHeader("User-Agent", HttpDownloader.USER_AGENT)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, entry.name);
    entry.job = manager().enqueue(request); entries.put(key, entry);
    try { save(); } catch (Exception failure) { manager().remove(entry.job); entries.remove(key); throw failure; }
  }

  synchronized void remove(Track track) throws Exception {
    String key = key(track.sourceUrl); Entry entry = entries.get(key); if (entry == null) return;
    manager().remove(entry.job);
    File target = file(entry);
    if (target != null && target.exists() && !target.delete()) throw new IOException("Cannot remove this download.");
    entries.remove(key); save();
  }

  private void save() throws Exception {
    JSONArray list = new JSONArray();
    for (Entry entry : entries.values()) list.put(new JSONObject().put("track", Library.encode(entry.track))
        .put("file", entry.name).put("job", entry.job).put("complete", entry.complete));
    FileOutputStream stream = null;
    try { stream = index.startWrite(); stream.write(list.toString().getBytes(StandardCharsets.UTF_8)); index.finishWrite(stream); }
    catch (IOException failure) { if (stream != null) index.failWrite(stream); throw failure; }
  }

  private static boolean validAudio(File file) {
    if (file == null || !file.isFile() || file.length() < 1024) return false;
    try (InputStream input = new FileInputStream(file)) {
      byte[] bytes = new byte[16]; if (input.read(bytes) < 12) return false;
      String first = new String(bytes, 0, 4, StandardCharsets.ISO_8859_1);
      return first.startsWith("ID3") || first.equals("OggS") || first.equals("fLaC") || first.equals("RIFF")
          || (bytes[0] & 255) == 255 && (bytes[1] & 224) == 224
          || new String(bytes, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
          || (bytes[0] == 0x1a && bytes[1] == 0x45 && (bytes[2] & 255) == 0xdf && (bytes[3] & 255) == 0xa3);
    } catch (IOException failure) { return false; }
  }
}
