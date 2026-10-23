package app.cadence.music;

import android.content.Context;
import app.cadence.core.Track;
import java.util.*;
import org.json.*;

/** Portable metadata only. Local audio references and download completion are device-specific. */
final class CloudData {
  static final Set<String> PREFS=Set.of("languages","homeLanguage","crossfade","skipBack","skipForward","podcastSpeed","autoplay");
  static boolean remote(Track t) { return t.sourceUrl.isEmpty() || t.sourceUrl.startsWith("https://"); }
  static String trackKey(Track t) {
    return JsonDisk.hash(!t.spotifyId.isEmpty()?"spotify:"+t.spotifyId:!t.sourceUrl.isEmpty()?SongDownloads.key(t.sourceUrl):t.title+"\n"+t.artist+"\n"+t.query);
  }
  static JSONObject snapshot(Context c) throws Exception {
    JSONObject out=new JSONObject(); Library library=new Library(c);
    if(library.playlists.size()>1) { JSONArray order=new JSONArray(); for(Library.Playlist p:library.playlists) order.put(p.id); out.put("library/order",order); }
    for(Library.Playlist p:library.playlists) {
      String prefix="p/"+JsonDisk.hash(p.id)+"/";
      out.put(prefix+"meta",new JSONObject().put("id",p.id).put("name",p.name).put("origin",p.origin));
      JSONArray order=new JSONArray(); Map<String,Integer> counts=new HashMap<>();
      for(Track t:p.tracks) if(remote(t)) {
        String key=trackKey(t); key += ":"+counts.merge(key,1,Integer::sum);
        order.put(key); out.put(prefix+"t/"+key,Library.encode(t));
      }
      out.put(prefix+"order",order);
    }
    JSONObject shows=PodcastStore.get(c).backup();
    for(String group:List.of("shows","previews")) {
      JSONArray list=shows.optJSONArray(group); if(list==null) continue;
      for(int i=0;i<list.length();i++) {
        JSONObject show=new JSONObject(list.getJSONObject(i).toString());
        show.put("subscribed",group.equals("shows")); out.put("show/"+JsonDisk.hash(show.getString("feed")),show);
      }
    }
    var prefs=Preferences.get(c);
    for(String key:PREFS) if(prefs.contains(key)) out.put("pref/"+key,prefs.getAll().get(key));
    var progress=c.getSharedPreferences("podcast-progress",0).getAll(); Set<String> ids=new HashSet<>();
    for(String key:progress.keySet()) ids.add(key.endsWith(".done")?key.substring(0,key.length()-5):key);
    for(String id:ids) out.put("progress/"+JsonDisk.hash(id),new JSONObject().put("id",id)
        .put("position",PodcastStore.get(c).progress(id)).put("done",PodcastStore.get(c).completed(id)));
    JSONObject wishes=DownloadList.snapshot(c);
    for(Iterator<String> it=wishes.keys();it.hasNext();) { String key=it.next(); out.put("download/"+key,wishes.getJSONObject(key)); }
    JSONObject profile=JsonDisk.read(c,"cloud-profile.json");
    if(profile.has("name")) out.put("profile/name",profile.optString("name"));
    if(profile.has("photo")) out.put("profile/photo",profile.optString("photo"));
    JSONObject queue=JsonDisk.read(c,"queue.json");
    if(queue.has("items") && queue.getJSONArray("items").length()>0) {
      JSONArray items=queue.getJSONArray("items"); boolean portable=true;
      for(int i=0;i<items.length();i++) {
        Track track=Library.decode(items.getJSONObject(i));
        if(!remote(track) && track.podcast) {
          for(var show:PodcastStore.get(c).knownShows()) for(var episode:show.episodes) if(episode.id.equals(track.episodeId)) {
            boolean manual=items.getJSONObject(i).optBoolean("manualQueue"); items.put(i,Library.encode(episode.track()).put("manualQueue",manual)); track=episode.track();
          }
        }
        if(!remote(track)) portable=false;
      }
      if(portable) out.put("resume",queue);
    }
    return out;
  }

  static void apply(Context c, JSONObject data) throws Exception {
    apply(c,data,true);
  }
  static void apply(Context c, JSONObject data, boolean preserveLocal) throws Exception {
    if(data.length()>100000) throw new java.io.IOException("This library is too large to sync.");
    Library existing=new Library(c); JSONArray playlists=new JSONArray(), shows=new JSONArray(), previews=new JSONArray();
    JSONObject wishes=new JSONObject(); Set<String> keys=new TreeSet<>(); data.keys().forEachRemaining(keys::add);
    for(String key:keys) if(key.startsWith("p/") && key.endsWith("/meta")) {
      JSONObject meta=data.getJSONObject(key); String prefix=key.substring(0,key.length()-4);
      List<String> order=new ArrayList<>(); JSONArray ids=data.optJSONArray(prefix+"order");
      if(ids!=null) for(int i=0;i<ids.length();i++) if(!order.contains(ids.getString(i))) order.add(ids.getString(i));
      for(String tkey:keys) if(tkey.startsWith(prefix+"t/") && !order.contains(tkey.substring((prefix+"t/").length())))
        order.add(tkey.substring((prefix+"t/").length()));
      JSONArray tracks=new JSONArray();
      for(String id:order) {
        JSONObject t=data.optJSONObject(prefix+"t/"+id);
        if(t!=null && remote(Library.decode(t))) tracks.put(t);
      }
      // A different phone cannot grant this phone's content/file URI access.
      for(Library.Playlist local:existing.playlists) if(preserveLocal && local.id.equals(meta.getString("id")))
        for(Track t:local.tracks) if(!remote(t)) tracks.put(Library.encode(t));
      playlists.put(new JSONObject().put("id",meta.getString("id")).put("name",meta.optString("name","Playlist"))
          .put("origin",meta.optString("origin")).put("tracks",tracks));
    }
    if(playlists.length()>2000) throw new java.io.IOException("Too many playlists to sync.");
    JSONArray requestedOrder=data.optJSONArray("library/order");
    if(requestedOrder!=null) {
      JSONArray ordered=new JSONArray(); Set<String> used=new HashSet<>();
      for(int i=0;i<requestedOrder.length();i++) for(int j=0;j<playlists.length();j++) {
        JSONObject p=playlists.getJSONObject(j); if(p.getString("id").equals(requestedOrder.optString(i)) && used.add(p.getString("id"))) ordered.put(p);
      }
      for(int j=0;j<playlists.length();j++) if(used.add(playlists.getJSONObject(j).getString("id"))) ordered.put(playlists.getJSONObject(j));
      playlists=ordered;
    }
    for(String key:keys) if(key.startsWith("show/")) {
      JSONObject show=new JSONObject(data.getJSONObject(key).toString());
      boolean subscribed=show.optBoolean("subscribed"); show.remove("subscribed"); (subscribed?shows:previews).put(show);
    } else if(key.startsWith("download/")) {
      Track t=Library.decode(data.getJSONObject(key)); if(t.sourceUrl.startsWith("https://")) wishes.put(DownloadList.id(t),Library.encode(t));
    }
    // Validate the complete incoming document before changing any live library files.
    for(int i=0;i<playlists.length();i++) {
      JSONArray tracks=playlists.getJSONObject(i).getJSONArray("tracks");
      if(tracks.length()>20000) throw new java.io.IOException("Too many songs in a playlist.");
      for(int j=0;j<tracks.length();j++) Library.decode(tracks.getJSONObject(j));
    }
    PodcastStore.validateShows(shows); PodcastStore.validateShows(previews);
    String name=data.optString("profile/name"), photo=data.optString("profile/photo");
    if(name.length()>100 || photo.length()>200000) throw new java.io.IOException("Invalid profile data.");
    if(!photo.isEmpty()) {
      byte[] image;
      try { image=android.util.Base64.decode(photo,android.util.Base64.DEFAULT); } catch(IllegalArgumentException e) { throw new java.io.IOException("Invalid profile photo."); }
      android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
      android.graphics.BitmapFactory.decodeByteArray(image,0,image.length,bounds);
      if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>512 || bounds.outHeight>512) throw new java.io.IOException("Invalid profile photo dimensions.");
    }
    for(String key:PREFS) if(data.has("pref/"+key)) {
      Object value=data.get("pref/"+key);
      boolean valid=key.equals("autoplay")?value instanceof Boolean:Set.of("languages","homeLanguage").contains(key)?value instanceof String:value instanceof Number;
      if(!valid) throw new java.io.IOException("Invalid synced preferences.");
    }
    for(String key:keys) if(key.startsWith("progress/")) {
      JSONObject p=data.getJSONObject(key); if(p.getString("id").isEmpty() || p.getString("id").length()>2048) throw new java.io.IOException("Invalid episode progress.");
    }
    JsonDisk.write(c,"cloud-recovery-local.json",snapshot(c));
    android.util.AtomicFile libraryFile=new android.util.AtomicFile(new java.io.File(c.getFilesDir(),"library.json"));
    java.io.FileOutputStream stream=null;
    try { stream=libraryFile.startWrite(); stream.write(playlists.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); libraryFile.finishWrite(stream); }
    catch(Exception e) { if(stream!=null) libraryFile.failWrite(stream); throw e; }
    PodcastStore.get(c).replaceCloud(shows,previews);
    DownloadList.replace(c,wishes);
    JSONObject nextProfile=new JSONObject();
    if(data.has("profile/name")) nextProfile.put("name",name);
    if(data.has("profile/photo")) nextProfile.put("photo",photo);
    JsonDisk.write(c,"cloud-profile.json",nextProfile);
    var edit=Preferences.get(c).edit(); for(String key:PREFS) edit.remove(key);
    for(String key:PREFS) {
      Object value=data.opt("pref/"+key);
      if(value instanceof Boolean b) edit.putBoolean(key,b);
      else if(value instanceof Number n) { if(key.equals("podcastSpeed")) edit.putFloat(key,Math.max(.5f,Math.min(3,n.floatValue()))); else edit.putInt(key,n.intValue()); }
      else if(value instanceof String s) edit.putString(key,s);
    } edit.apply();
    var progress=c.getSharedPreferences("podcast-progress",0).edit().clear();
    for(String key:keys) if(key.startsWith("progress/")) {
      JSONObject p=data.getJSONObject(key); String id=p.getString("id");
      progress.putLong(id,Math.max(0,p.optLong("position"))).putBoolean(id+".done",p.optBoolean("done"));
    } progress.apply();
    if(data.has("resume")) JsonDisk.write(c,"cloud-resume.json",data.getJSONObject("resume"));
  }
}
