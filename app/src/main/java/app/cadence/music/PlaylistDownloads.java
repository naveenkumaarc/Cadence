package app.cadence.music;

import android.content.Context;
import androidx.work.*;
import app.cadence.core.*;
import java.util.*;
import org.json.*;

/** Persistent preparation queue; Android DownloadManager owns each actual transfer. */
final class PlaylistDownloads {
  record Summary(int complete,int total,int active,int failed) {
    String label() { return active>0?"Cancel downloads · "+complete+"/"+total:failed>0?"Retry downloads · "+failed+" failed":"Download all"; }
  }
  private static JSONObject read(Context c) throws Exception { return JsonDisk.read(c,"playlist-downloads.json"); }
  private static void write(Context c,JSONObject data) throws Exception { JsonDisk.write(c,"playlist-downloads.json",data); }
  static boolean complete(Context c,Track t) throws Exception { return t.podcast?PodcastStore.get(c).download(t.episodeId).complete:SongDownloads.get(c).state(t).complete; }
  static boolean active(Context c,Track t) throws Exception { return t.podcast?PodcastStore.get(c).download(t.episodeId).active:SongDownloads.get(c).state(t).active; }
  static synchronized int enqueue(Context c,Library.Playlist playlist) throws Exception {
    JSONObject all=read(c),group=new JSONObject().put("generation",UUID.randomUUID().toString()); JSONArray list=new JSONArray(); Set<String> ids=new HashSet<>(); int unmatched=0;
    for(Track t:new ArrayList<>(playlist.tracks)) {
      if(t.sourceUrl.isEmpty()) { unmatched++; continue; }
      if(!t.sourceUrl.startsWith("https://") || !ids.add(DownloadList.id(t))) continue;
      DownloadList.remember(c,t);
      list.put(new JSONObject().put("track",Library.encode(t)).put("status",complete(c,t)?"complete":active(c,t)?"started":"queued"));
    }
    group.put("tracks",list); all.put(playlist.id,group); write(c,all);
    WorkManager.getInstance(c).enqueueUniqueWork("cadence-playlist-downloads",ExistingWorkPolicy.APPEND_OR_REPLACE,
        new OneTimeWorkRequest.Builder(PlaylistDownloadWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build());
    return unmatched;
  }
  static synchronized Summary summary(Context c,String id) throws Exception {
    JSONObject group=read(c).optJSONObject(id); if(group==null) return new Summary(0,0,0,0);
    JSONArray list=group.getJSONArray("tracks"); int done=0,active=0,failed=0;
    for(int i=0;i<list.length();i++) {
      JSONObject row=list.getJSONObject(i); Track t=Library.decode(row.getJSONObject("track"));
      if(complete(c,t)) done++;
      else if(active(c,t) || Set.of("queued","preparing").contains(row.optString("status"))) active++;
      else if(!row.optString("status").equals("cancelled")) failed++;
    } return new Summary(done,list.length(),active,failed);
  }
  static synchronized void cancel(Context c,String id) throws Exception {
    JSONObject all=read(c),group=all.optJSONObject(id); if(group==null) return;
    JSONArray list=group.getJSONArray("tracks"); group.put("generation",UUID.randomUUID().toString());
    for(int i=0;i<list.length();i++) {
      JSONObject row=list.getJSONObject(i); Track t=Library.decode(row.getJSONObject("track")); row.put("status","cancelled");
      if(active(c,t)) { if(t.podcast) PodcastStore.get(c).deleteDownload(t.episodeId); else SongDownloads.get(c).cancelTransfer(t); }
    } write(c,all);
  }
  static synchronized void recover(Context c) throws Exception {
    JSONObject all=read(c);
    for(Iterator<String> it=all.keys();it.hasNext();) {
      JSONArray list=all.getJSONObject(it.next()).getJSONArray("tracks");
      for(int i=0;i<list.length();i++) if(list.getJSONObject(i).optString("status").equals("preparing")) list.getJSONObject(i).put("status","queued");
    } write(c,all);
  }
  private record Pending(String group,String generation,int index,Track track) {}
  private static synchronized Pending next(Context c) throws Exception {
    JSONObject all=read(c);
    for(Iterator<String> it=all.keys();it.hasNext();) {
      String id=it.next(); JSONObject group=all.getJSONObject(id); JSONArray tracks=group.getJSONArray("tracks");
      for(int i=0;i<tracks.length();i++) if(tracks.getJSONObject(i).optString("status").equals("queued")) {
        JSONObject row=tracks.getJSONObject(i); row.put("status","preparing"); write(c,all);
        return new Pending(id,group.getString("generation"),i,Library.decode(row.getJSONObject("track")));
      }
    } return null;
  }
  static boolean runOne(Context c,java.util.function.BooleanSupplier stopped) throws Exception {
    Pending p=next(c); if(p==null) return false;
    YouTube.Resolved resolved=null; Exception failure=null;
    try { if(!p.track.podcast && !complete(c,p.track) && !active(c,p.track)) resolved=YouTube.resolve(p.track.sourceUrl); }
    catch(Exception e) { failure=e; }
    synchronized(PlaylistDownloads.class) {
      JSONObject all=read(c),group=all.optJSONObject(p.group);
      if(group==null || !group.optString("generation").equals(p.generation)) return true;
      JSONObject row=group.getJSONArray("tracks").getJSONObject(p.index);
      if(stopped.getAsBoolean()) { row.put("status","queued"); write(c,all); return false; }
      try {
        if(failure!=null) throw failure;
        if(!complete(c,p.track) && !active(c,p.track)) {
          if(p.track.podcast) {
            Podcasts.Episode e=new Podcasts.Episode(); e.id=p.track.episodeId; e.title=p.track.title; e.show=p.track.artist;
            e.audioUrl=p.track.sourceUrl; e.artwork=p.track.artwork; e.duration=p.track.durationSeconds; PodcastStore.get(c).startDownload(e);
          } else {
            if(resolved==null) throw new java.io.IOException("Audio is unavailable");
            SongDownloads.get(c).startResolved(p.track,resolved.streamUrl,resolved.mimeType);
          }
        } row.put("status","started");
      } catch(Exception e) { row.put("status","failed"); }
      write(c,all);
    } return true;
  }
  static synchronized boolean hasPending(Context c) throws Exception {
    JSONObject all=read(c);
    for(Iterator<String> it=all.keys();it.hasNext();) {
      JSONArray rows=all.getJSONObject(it.next()).getJSONArray("tracks");
      for(int i=0;i<rows.length();i++) if(Set.of("queued","preparing").contains(rows.getJSONObject(i).optString("status"))) return true;
    } return false;
  }
}
