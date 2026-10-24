package app.cadence.music;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.util.AtomicFile;
import app.cadence.core.Podcasts;
import app.cadence.core.Track;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** One process-wide store shared by the UI and playback service. */
final class PodcastStore {
  private static PodcastStore instance;

  static synchronized PodcastStore get(Context c) throws Exception {
    if (instance == null) instance = new PodcastStore(c.getApplicationContext());
    return instance;
  }

  final List<Podcasts.Show> shows = new ArrayList<>();
  private final List<Podcasts.Show> previews = new ArrayList<>();
  private final Map<String, Long> jobs = new HashMap<>();
  private final Map<String, String> checkedAudio = new HashMap<>();
  private final Context context;
  private final AtomicFile file;

  private PodcastStore(Context c) throws Exception {
    context = c;
    file = new AtomicFile(new File(c.getFilesDir(), "podcasts.json"));
    if (!file.getBaseFile().exists()) return;
    JSONObject root = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
    JSONArray subscriptions = root.optJSONArray("shows");
    JSONArray cachedPreviews = root.optJSONArray("previews");
    int subscribedCount = subscriptions == null ? 0 : subscriptions.length();
    if (subscriptions == null) subscriptions = new JSONArray();
    if (cachedPreviews != null) for (int i = 0; i < cachedPreviews.length(); i++) subscriptions.put(cachedPreviews.get(i));
    if (subscriptions != null)
      for (int i = 0; i < subscriptions.length(); i++) {
        JSONObject j = subscriptions.getJSONObject(i);
        Podcasts.Show s = new Podcasts.Show();
        s.title = j.optString("title");
        s.author = j.optString("author");
        s.artwork = j.optString("artwork");
        s.feedUrl = j.getString("feed");
        JSONArray eps = j.getJSONArray("episodes");
        for (int k = 0; k < eps.length(); k++) {
          JSONObject p = eps.getJSONObject(k);
          Podcasts.Episode e = new Podcasts.Episode();
          e.id = p.getString("id");
          e.title = p.optString("title");
          e.show = s.title;
          e.artwork = p.optString("artwork", s.artwork);
          e.audioUrl = p.getString("audio");
          e.duration = p.optLong("duration");
          e.published = p.optString("published");
          s.episodes.add(e);
        }
        if (i < subscribedCount) shows.add(s); else previews.add(s);
      }
    JSONObject j = root.optJSONObject("downloads");
    if (j != null)
      for (Iterator<String> it = j.keys(); it.hasNext(); ) {
        String key = it.next();
        jobs.put(key, j.getLong(key));
      }
    migrateDownloads();
  }

  synchronized void migrateDownloads() throws Exception {
    var prefs=context.getSharedPreferences("download-migration",0);
    if(prefs.getBoolean("podcasts",false)) return;
    JSONObject wishes=DownloadList.snapshot(context);
    for(Podcasts.Show show:knownShows()) for(Podcasts.Episode episode:show.episodes)
      if(jobs.containsKey(episode.id) && episode.audioUrl.startsWith("https://")) {
        Track track=episode.track();wishes.put(DownloadList.id(track),Library.encode(track));
      }
    DownloadList.replace(context,wishes);prefs.edit().putBoolean("podcasts",true).commit();
  }

  synchronized void put(Podcasts.Show show) throws Exception {
    int at = -1;
    for (int i = 0; i < shows.size(); i++)
      if (shows.get(i).feedUrl.equals(show.feedUrl)) {
        at = i;
        break;
      }
    if (at < 0) shows.add(show);
    else {
      Podcasts.Show previous = shows.get(at);
      java.util.Set<String> ids = new java.util.HashSet<>();
      for (Podcasts.Episode e : show.episodes) ids.add(e.id);
      for (Podcasts.Episode e : previous.episodes)
        if (!ids.contains(e.id) && (jobs.containsKey(e.id) || progress(e.id) > 0))
          show.episodes.add(e);
      shows.set(at, show);
    }
    previews.removeIf(s -> s.feedUrl.equals(show.feedUrl));
    save();
    CloudSync.changed(context);
  }

  synchronized List<Podcasts.Show> subscriptions() { return new ArrayList<>(shows); }

  synchronized boolean subscribed(String feed) { return shows.stream().anyMatch(s -> s.feedUrl.equals(feed)); }
  synchronized List<Podcasts.Show> knownShows() {
    List<Podcasts.Show> all = new ArrayList<>(shows); all.addAll(previews); return all;
  }
  synchronized void preview(Podcasts.Show show) throws Exception {
    if (subscribed(show.feedUrl)) return;
    previews.removeIf(s -> s.feedUrl.equals(show.feedUrl)); previews.add(show); save(); CloudSync.changed(context);
  }
  synchronized void remove(Podcasts.Show s) throws Exception {
    shows.removeIf(existing -> existing.feedUrl.equals(s.feedUrl)); preview(s); save();
  }

  private synchronized void save() throws Exception {
    JSONArray all = new JSONArray();
    JSONArray previewData = new JSONArray();
    for (Podcasts.Show s : knownShows()) {
      JSONArray eps = new JSONArray();
      for (Podcasts.Episode e : s.episodes)
        eps.put(
            new JSONObject()
                .put("id", e.id)
                .put("title", e.title)
                .put("artwork", e.artwork)
                .put("audio", e.audioUrl)
                .put("duration", e.duration)
                .put("published", e.published));
      (subscribed(s.feedUrl) ? all : previewData).put(
          new JSONObject()
              .put("title", s.title)
              .put("author", s.author)
              .put("artwork", s.artwork)
              .put("feed", s.feedUrl)
              .put("episodes", eps));
    }
    JSONObject downloads = new JSONObject();
    for (var kv : jobs.entrySet()) downloads.put(kv.getKey(), kv.getValue());
    byte[] data =
        new JSONObject()
            .put("shows", all)
            .put("previews", previewData)
            .put("downloads", downloads)
            .toString()
            .getBytes(StandardCharsets.UTF_8);
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(data);
      file.finishWrite(out);
    } catch (IOException e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
  }

  synchronized JSONObject backup() throws Exception {
    save(); JSONObject out = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
    out.remove("downloads"); return out;
  }
  synchronized void restore(JSONObject root) throws Exception {
    List<Podcasts.Show> incoming = new ArrayList<>(), cached = new ArrayList<>();
    for (String key : List.of("shows", "previews")) {
      JSONArray array = root.optJSONArray(key); if (array == null) continue;
      if (array.length() > 500) throw new IOException("Too many podcast feeds in this backup.");
      for (int i = 0; i < array.length(); i++) {
        JSONObject j = array.getJSONObject(i); Podcasts.Show show = new Podcasts.Show();
        show.feedUrl = Podcasts.https(j.getString("feed")); show.title = j.optString("title");
        show.author = j.optString("author"); show.artwork = j.optString("artwork");
        JSONArray eps = j.optJSONArray("episodes");
        if (eps != null) for (int k = 0; k < eps.length(); k++) {
          JSONObject e = eps.getJSONObject(k); Podcasts.Episode ep = new Podcasts.Episode();
          ep.id = e.getString("id"); ep.title = e.optString("title"); ep.show = show.title;
          ep.audioUrl = Podcasts.https(e.getString("audio")); ep.artwork = e.optString("artwork", show.artwork);
          ep.duration = Math.max(0, e.optLong("duration")); ep.published = e.optString("published"); show.episodes.add(ep);
        }
        (key.equals("shows") ? incoming : cached).add(show);
      }
    }
    for (Podcasts.Show show : incoming) put(show);
    for (Podcasts.Show show : cached) preview(show);
  }

  synchronized long progress(String episode) {
    return PodcastPositions.selected(context,episode).position();
  }

  synchronized boolean completed(String episode) {
    return PodcastPositions.selected(context,episode).done();
  }

  synchronized void progress(String episode, long position, boolean complete) {
    if (episode.isEmpty()) return;
    try { if(PodcastPositions.record(context,episode,position,complete)) CloudSync.progressChanged(context); }
    catch(Exception failure) { context.getSharedPreferences("podcast-progress",0).edit().putLong(episode,Math.max(0,position)).putBoolean(episode+".done",complete).apply(); }
  }
  synchronized void selectPosition(String episode,long position,boolean complete) throws Exception {
    PodcastPositions.record(context,episode,position,complete,true);CloudSync.changed(context);
  }

  static List<Podcasts.Show> validateShows(JSONArray list) throws Exception {
    if(list.length()>500) throw new IOException("Too many podcast feeds.");
    List<Podcasts.Show> out=new ArrayList<>();
    for(int i=0;i<list.length();i++) {
      JSONObject j=list.getJSONObject(i); Podcasts.Show show=new Podcasts.Show();
      show.feedUrl=Podcasts.https(j.getString("feed")); show.title=j.optString("title"); show.author=j.optString("author"); show.artwork=j.optString("artwork");
      JSONArray episodes=j.optJSONArray("episodes"); if(episodes==null) episodes=new JSONArray();
      if(episodes.length()>10000) throw new IOException("Too many podcast episodes.");
      for(int k=0;k<episodes.length();k++) {
        JSONObject e=episodes.getJSONObject(k); Podcasts.Episode ep=new Podcasts.Episode();
        ep.id=e.getString("id"); ep.title=e.optString("title"); ep.show=show.title; ep.audioUrl=Podcasts.https(e.getString("audio"));
        ep.artwork=e.optString("artwork",show.artwork); ep.duration=Math.max(0,e.optLong("duration")); ep.published=e.optString("published"); show.episodes.add(ep);
      } out.add(show);
    } return out;
  }
  synchronized void replaceCloud(JSONArray subscriptions, JSONArray cached) throws Exception {
    List<Podcasts.Show> next=validateShows(subscriptions), other=validateShows(cached);
    shows.clear(); shows.addAll(next); previews.clear(); previews.addAll(other); save();
  }

  static final class Download {
    String label = "Download episode", uri = "";
    boolean active = false, complete = false;
    int percent = -1;
  }

  synchronized Download download(String id) {
    Download state = new Download();
    Long job = jobs.get(id);
    if (job == null) return state;
    DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    try (Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(job))) {
      if (cursor == null || !cursor.moveToFirst()) {
        state.label = "Download again";
        return state;
      }
      int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
      if (status == DownloadManager.STATUS_SUCCESSFUL) {
        String uri =
            cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
        if (uri != null
            && uri.startsWith("file:")
            && validAudio(new File(Uri.parse(uri).getPath()))) {
          state.complete = true;
          state.uri = uri;
          state.label = "Downloaded";
        } else state.label = "Download failed · Retry";
      } else if (status == DownloadManager.STATUS_FAILED) state.label = "Download failed · Retry";
      else {
        state.active = true;
        long
            total =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            read =
                cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
        if (total > 0) state.percent = (int) (read * 100 / total);
        state.label =
            status == DownloadManager.STATUS_PAUSED
                ? "Download waiting"
                : state.percent >= 0 ? "Downloading · " + state.percent + "%" : "Downloading…";
      }
    } catch (Exception e) {
      state.label = "Download unavailable · Retry";
    }
    return state;
  }

  private static String hash(String s) throws Exception {
    StringBuilder b = new StringBuilder();
    for (byte x : MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)))
      b.append(String.format(java.util.Locale.ROOT, "%02x", x & 255));
    return b.toString();
  }

  private boolean validAudio(File f) {
    if (!f.isFile() || f.length() < 1024) return false;
    String stamp = f.length() + ":" + f.lastModified();
    if (stamp.equals(checkedAudio.get(f.getPath()))) return true;
    try (FileInputStream in = new FileInputStream(f)) {
      byte[] b = new byte[16];
      int n = in.read(b);
      if (n < 8) return false;
      String first = new String(b, 0, 4, StandardCharsets.ISO_8859_1);
      boolean valid =
          first.startsWith("ID3")
              || first.equals("OggS")
              || first.equals("fLaC")
              || first.equals("RIFF")
              || (b[0] & 255) == 255 && (b[1] & 224) == 224
              || new String(b, 4, 4, StandardCharsets.US_ASCII).equals("ftyp")
              || (b[0] == 0x1a && b[1] == 0x45 && (b[2] & 255) == 0xdf && (b[3] & 255) == 0xa3);
      if (valid) checkedAudio.put(f.getPath(), stamp);
      return valid;
    } catch (IOException e) {
      return false;
    }
  }

  synchronized void startDownload(Podcasts.Episode e) throws Exception {
    DownloadList.remember(context,e.track());
    Download state = download(e.id);
    if (state.complete || state.active) return;
    DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
    Long old = jobs.get(e.id);
    if (old != null) dm.remove(old);
    String name = hash(e.id) + ".audio";
    File dir = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS);
    if (dir == null) throw new IOException("Phone storage is unavailable.");
    File target = new File(dir, name);
    if (target.exists() && !target.delete()) throw new IOException("Cannot replace this download.");
    DownloadManager.Request request =
        new DownloadManager.Request(Uri.parse(Podcasts.https(e.audioUrl)))
            .setTitle(e.title)
            .setDescription(e.show)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, name);
    long id = dm.enqueue(request);
    jobs.put(e.id, id);
    try {
      save();
    } catch (Exception failure) {
      dm.remove(id);
      jobs.remove(e.id);
      throw failure;
    }
  }

  synchronized void deleteDownload(String episode) throws Exception {
    Long job = jobs.remove(episode);
    if (job != null)
      ((DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE)).remove(job);
    save();
  }

  synchronized Track playable(Podcasts.Episode e) {
    Track t = e.track();
    Download d = download(e.id);
    if (d.complete) t.sourceUrl = d.uri;
    return t;
  }
}
