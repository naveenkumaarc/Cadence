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
import app.cadence.core.ResumableDownload;
import androidx.work.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Durable resumable song transfers, with compatibility for existing DownloadManager files. */
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
    Track track; String name, address="", mime="audio/*", validator="", generation="", status="queued";
    long job, bytes, total=-1; boolean complete, paused;
    volatile okhttp3.Call call;
  }
  static final class State {
    String label = "Download song", uri = "";
    boolean complete, active, paused;
    int percent=-1;
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
        entry.complete = record.optBoolean("complete");entry.paused=record.optBoolean("paused");
        entry.address=record.optString("address");entry.mime=record.optString("mime","audio/*");entry.validator=record.optString("validator");
        entry.generation=record.optString("generation");entry.status=record.optString("status","queued");
        entry.bytes=record.optLong("bytes");entry.total=record.optLong("total",-1);
        // The index never grants access outside our song download directory.
        if (!entry.name.matches("[a-f0-9]{64}\\.audio")) continue;
        entries.put(key(entry.track.sourceUrl), entry);
      }
    }
    if(!context.getSharedPreferences("download-migration",0).getBoolean("songs",false)) {
      JSONObject wishes=DownloadList.snapshot(context);
      for(Entry entry:entries.values()) if(entry.track.sourceUrl.startsWith("https://")) wishes.put(DownloadList.id(entry.track),Library.encode(entry.track));
      DownloadList.replace(context,wishes); context.getSharedPreferences("download-migration",0).edit().putBoolean("songs",true).commit();
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
    if(entry.job==0) {
      state.paused=entry.paused;state.active=!entry.status.equals("failed");
      state.percent=entry.total>0?(int)Math.min(100,entry.bytes*100/entry.total):-1;
      state.label=entry.paused?"Paused":entry.status.equals("failed")?"Download failed · Retry":
          !DownloadPolicy.allowed(context)?(DownloadPolicy.wifiOnly(context)?"Waiting for Wi-Fi":"Waiting for connection"):
          entry.status.equals("preparing")?"Preparing audio…":entry.status.equals("queued")?"Queued":
          entry.status.equals("retry")?"Waiting to retry":state.percent>=0?"Downloading · "+state.percent+"%":"Downloading…";
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
        state.percent=total>0?(int)Math.min(100,done*100/total):-1;
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
  synchronized void completed(long job) {
    for (Entry entry : entries.values()) if (entry.job == job) { state(entry.track); break; }
  }
  synchronized Library.Playlist playlist() {
    Library.Playlist playlist = new Library.Playlist("Downloaded"); playlist.id = PLAYLIST_ID;
    try {
      JSONObject wanted=DownloadList.snapshot(context);
      for(Track track:tracks()) if(wanted.has(DownloadList.id(track))) playlist.tracks.add(track);
    } catch(Exception ignored) { playlist.tracks.addAll(tracks()); }
    return playlist;
  }

  synchronized List<Track> requests() {return entries.values().stream().map(e->e.track).collect(java.util.stream.Collectors.toList());}
  private static final java.util.concurrent.ConcurrentHashMap<String,Object> LOCKS=new java.util.concurrent.ConcurrentHashMap<>();
  private Object transferLock(String source){return LOCKS.computeIfAbsent(key(source),ignored->new Object());}
  private String workName(Entry entry){return "cadence-song-"+entry.name;}
  private void schedule(Entry entry) {
    OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(SongDownloadWorker.class)
        .setInputData(new Data.Builder().putString("source",entry.track.sourceUrl).putString("generation",entry.generation).build())
        .setConstraints(DownloadPolicy.constraints(context)).build();
    WorkManager.getInstance(context).enqueueUniqueWork(workName(entry),ExistingWorkPolicy.REPLACE,request);
  }
  private Entry create(Track track,String address,String mime) throws Exception {
    DownloadList.remember(context,track);
    Entry entry=new Entry();entry.track=Library.decode(Library.encode(track));entry.address=address;entry.mime=mime;
    StringBuilder name=new StringBuilder();
    for(byte b:MessageDigest.getInstance("SHA-256").digest(key(track.sourceUrl).getBytes(StandardCharsets.UTF_8)))name.append(String.format(Locale.ROOT,"%02x",b&255));
    entry.name=name+".audio";entry.generation=UUID.randomUUID().toString();
    entry.paused=Preferences.get(context).getBoolean("downloadsPaused",false);
    if(file(entry)==null)throw new IOException("Phone storage is unavailable.");
    entries.put(key(track.sourceUrl),entry);save();if(!entry.paused)schedule(entry);return entry;
  }
  synchronized void start(Track track) throws Exception {
    if(track.podcast)throw new IOException("Use the episode download control for podcasts.");
    State current=state(track);if(current.complete||current.active)return;
    Entry entry=entries.get(key(track.sourceUrl));
    if(entry!=null&&entry.job==0){resume(track);return;}
    create(track,"","audio/*");
  }
  synchronized void startResolved(Track track,String address,String mimeType) throws Exception {
    Uri uri=Uri.parse(address);
    if(!"https".equals(uri.getScheme())||uri.getHost()==null)throw new IOException("A secure audio address is required.");
    State current=state(track);if(current.complete||current.active)return;
    Entry old=entries.get(key(track.sourceUrl));
    if(old!=null&&old.job==0){old.address=address;old.mime=mimeType;resume(track);return;}
    if(old!=null)manager().remove(old.job);
    create(track,address,mimeType);
  }
  synchronized void pause(Track track) throws Exception {
    Entry entry=entries.get(key(track.sourceUrl));if(entry==null||state(track).complete)return;
    entry.paused=true;entry.generation=UUID.randomUUID().toString();
    if(entry.call!=null)entry.call.cancel();
    if(entry.job!=0){manager().remove(entry.job);entry.job=0;entry.bytes=0;entry.total=-1;}
    WorkManager.getInstance(context).cancelUniqueWork(workName(entry));save();
  }
  synchronized void resume(Track track) throws Exception {
    Entry entry=entries.get(key(track.sourceUrl));if(entry==null||state(track).complete)return;
    Preferences.get(context).edit().putBoolean("downloadsPaused",false).apply();
    entry.paused=false;entry.status="queued";entry.generation=UUID.randomUUID().toString();save();schedule(entry);
  }
  synchronized void pauseAll(boolean paused) throws Exception {
    Preferences.get(context).edit().putBoolean("downloadsPaused",paused).apply();
    for(Track track:requests())if(!state(track).complete){if(paused)pause(track);else resume(track);}
  }
  synchronized void connectionChanged() throws Exception {
    for(Entry entry:entries.values())if(!state(entry.track).complete&&!entry.paused) {
      if(entry.call!=null)entry.call.cancel();
      if(entry.job!=0){manager().remove(entry.job);entry.job=0;entry.bytes=0;entry.total=-1;}
      entry.generation=UUID.randomUUID().toString();save();schedule(entry);
    }
  }
  private synchronized boolean current(Entry entry,String generation) {
    return entries.get(key(entry.track.sourceUrl))==entry&&entry.generation.equals(generation)&&!entry.paused;
  }
  boolean transfer(String source,String generation,java.util.function.BooleanSupplier stopped,java.util.function.Consumer<okhttp3.Call> active) throws Exception {
    Entry entry;synchronized(this){entry=entries.get(key(source));if(entry==null||!current(entry,generation)||entry.complete)return true;}
    synchronized(transferLock(source)) {
      if(stopped.getAsBoolean()||!current(entry,generation))return true;
      try {
        if(entry.address.isEmpty()) {
          synchronized(this){entry.status="preparing";save();}
          YouTube.Resolved resolved=YouTube.resolve(source);
          synchronized(this){if(!current(entry,generation))return true;entry.address=resolved.streamUrl;entry.mime=resolved.mimeType;entry.track.durationSeconds=resolved.track.durationSeconds;save();}
        }
        File target=file(entry),partial=new File(target.getPath()+".part");
        long[] lastSave={0};
        boolean done=ResumableDownload.transfer(HttpDownloader.MEDIA_CLIENT,entry.address,partial,entry.validator,
            ()->stopped.getAsBoolean()||!current(entry,generation),(bytes,total,validator)->{
              synchronized(this){if(!current(entry,generation))return;entry.bytes=bytes;entry.total=total;entry.validator=validator;entry.status="downloading";
                if(System.currentTimeMillis()-lastSave[0]>1000){try{save();}catch(Exception e){throw new IOException(e);}lastSave[0]=System.currentTimeMillis();}}
            },call->{entry.call=call;active.accept(call);});
        synchronized(this) {
          if(!current(entry,generation))return true;
          if(!done||stopped.getAsBoolean()){entry.status="queued";save();return false;}
          if(!validAudio(partial)){partial.delete();entry.bytes=0;entry.validator="";throw new IOException("The downloaded file is not playable audio");}
          if(!partial.renameTo(target))throw new IOException("Cannot save downloaded audio");
          entry.complete=true;entry.status="complete";entry.bytes=target.length();entry.total=entry.bytes;save();return true;
        }
      } catch(Exception failure) {
        synchronized(this){if(current(entry,generation)){entry.status="retry";
          if(failure instanceof ResumableDownload.HttpFailure http&&(http.code==401||http.code==403))entry.address="";
          save();}}
        if(!current(entry,generation))return true;throw failure;
      }
    }
  }
  synchronized void failed(String source,String generation) throws Exception {Entry entry=entries.get(key(source));if(entry!=null&&current(entry,generation)){entry.status="failed";save();}}

  synchronized void remove(Track track) throws Exception {
    cancelTransfer(track); DownloadList.forget(context,track);
  }
  synchronized void cancelTransfer(Track track) throws Exception {
    String key = key(track.sourceUrl); Entry entry = entries.get(key); if (entry == null) return;
    entry.generation=UUID.randomUUID().toString();if(entry.call!=null)entry.call.cancel();
    if(entry.job!=0)manager().remove(entry.job);
    WorkManager.getInstance(context).cancelUniqueWork(workName(entry));
    File target = file(entry);
    if (target != null && target.exists() && !target.delete()) throw new IOException("Cannot remove this download.");
    File partial=target==null?null:new File(target.getPath()+".part");
    if(partial!=null&&partial.exists()&&!partial.delete())throw new IOException("Cannot remove this partial download.");
    entries.remove(key); save();
  }

  private void save() throws Exception {
    JSONArray list = new JSONArray();
    for (Entry entry : entries.values()) list.put(new JSONObject().put("track", Library.encode(entry.track))
        .put("file", entry.name).put("job", entry.job).put("complete", entry.complete)
        .put("paused",entry.paused).put("address",entry.address).put("mime",entry.mime).put("validator",entry.validator)
        .put("generation",entry.generation).put("status",entry.status).put("bytes",entry.bytes).put("total",entry.total));
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
