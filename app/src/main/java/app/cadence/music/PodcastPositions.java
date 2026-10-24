package app.cadence.music;

import android.content.Context;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Account-wide latest position plus independent listening records for each installation. */
final class PodcastPositions {
  static final String FILE="podcast-positions.json";
  private static JSONObject cached;
  private static String cachedPath;
  private static JSONObject load(Context c) throws Exception {
    String path=c.getFilesDir().getAbsolutePath();
    if(cached==null || !path.equals(cachedPath)) {cached=JsonDisk.read(c,FILE);cachedPath=path;}
    return cached;
  }
  private static void store(Context c,JSONObject value) throws Exception {JsonDisk.write(c,FILE,value);cached=value;cachedPath=c.getFilesDir().getAbsolutePath();}
  record Position(String id,String device,String name,long position,boolean done,long updated) {
    JSONObject json() throws JSONException { return new JSONObject().put("id",id).put("device",device).put("name",name).put("position",position).put("done",done).put("updated",updated); }
  }
  static boolean thisDeviceMode(Context c) { return c.getSharedPreferences("podcast-resume",0).getBoolean("thisDevice",false); }
  static void mode(Context c,boolean local) { c.getSharedPreferences("podcast-resume",0).edit().putBoolean("thisDevice",local).apply(); }
  private static JSONObject group(JSONObject data,String key) { JSONObject value=data.optJSONObject(key);return value==null?new JSONObject():value; }
  private static Position parse(JSONObject j) throws Exception {
    String id=j.getString("id"),device=j.optString("device"),name=j.optString("name","Earlier Cadence progress");
    long position=j.getLong("position"),updated=j.optLong("updated");
    if(id.isEmpty() || id.length()>2048 || !device.matches("[A-Za-z0-9-]{0,100}") || name.length()>160 || position<0 || updated<0 || updated>=Long.MAX_VALUE-1) throw new IOException("Invalid saved episode position.");
    return new Position(id,device,name,position,j.optBoolean("done"),updated);
  }
  static synchronized Position latest(Context c,String id) {
    var prefs=c.getSharedPreferences("podcast-progress",0);long position=prefs.getLong(id,0);boolean done=prefs.getBoolean(id+".done",false);
    try {
      JSONObject value=group(load(c),"latest").optJSONObject(JsonDisk.hash(id));
      if(value!=null) { Position p=parse(value);if(p.position==position && p.done==done)return p; }
    } catch(Exception ignored) {}
    return new Position(id,"","Earlier Cadence progress",position,done,0);
  }
  static synchronized Position device(Context c,String id) {
    String actor=CloudSync.device(c);
    try {
      JSONObject row=group(load(c),"devices").optJSONObject(JsonDisk.hash(id)+"/"+actor);
      if(row!=null) return parse(row);
    } catch(Exception ignored) {}
    return new Position(id,actor,BackupLabels.deviceName(c),0,false,0);
  }
  static Position selected(Context c,String id) { return thisDeviceMode(c)?device(c,id):latest(c,id); }
  static synchronized List<Position> choices(Context c,String id) {
    List<Position> out=new ArrayList<>();
    try { JSONObject devices=group(load(c),"devices");for(Iterator<String> it=devices.keys();it.hasNext();) {Position p=parse(devices.getJSONObject(it.next()));if(p.id.equals(id))out.add(p);} }
    catch(Exception ignored) {}
    out.sort(Comparator.comparingLong(Position::updated).reversed().thenComparing(Position::device));return out;
  }
  static synchronized boolean record(Context c,String id,long position,boolean done) throws Exception { return record(c,id,position,done,false); }
  static synchronized boolean record(Context c,String id,long position,boolean done,boolean force) throws Exception {
    if(id.isEmpty()) return false;
    JSONObject data=new JSONObject(load(c).toString()),devices=group(data,"devices"),latest=group(data,"latest");
    String actor=CloudSync.device(c),key=JsonDisk.hash(id)+"/"+actor;JSONObject previous=devices.optJSONObject(key);
    position=Math.max(0,position);
    if(!force && previous!=null && previous.optLong("position")==position && previous.optBoolean("done")==done) return false;
    long stamp=Math.max(System.currentTimeMillis(),Math.max(previous==null?0:previous.optLong("updated")+1,latest(c,id).updated+1));
    Position value=new Position(id,actor,BackupLabels.deviceName(c),position,done,stamp);
    devices.put(key,value.json());latest.put(JsonDisk.hash(id),value.json());
    store(c,data.put("devices",devices).put("latest",latest));
    c.getSharedPreferences("podcast-progress",0).edit().putLong(id,position).putBoolean(id+".done",done).apply();return true;
  }
  static synchronized void capture(Context c,JSONObject out) throws Exception {
    var prefs=c.getSharedPreferences("podcast-progress",0).getAll();Set<String> ids=new TreeSet<>();
    for(String key:prefs.keySet()) ids.add(key.endsWith(".done")?key.substring(0,key.length()-5):key);
    for(String id:ids) out.put("progress/"+JsonDisk.hash(id),latest(c,id).json());
    JSONObject devices=group(load(c),"devices");
    for(Iterator<String> it=devices.keys();it.hasNext();) { Position p=parse(devices.getJSONObject(it.next()));out.put("device-progress/"+JsonDisk.hash(p.id)+"/"+p.device,p.json()); }
  }
  static JSONObject prepare(JSONObject snapshot) throws Exception {
    JSONObject latest=new JSONObject(),devices=new JSONObject();List<Position> records=new ArrayList<>();
    for(Iterator<String> it=snapshot.keys();it.hasNext();) {
      String key=it.next();
      if(key.startsWith("progress/")) { Position p=parse(snapshot.getJSONObject(key));latest.put(JsonDisk.hash(p.id),p.json()); }
      else if(key.startsWith("device-progress/")) {Position p=parse(snapshot.getJSONObject(key));if(p.device.isEmpty())throw new IOException("Missing episode device.");devices.put(JsonDisk.hash(p.id)+"/"+p.device,p.json());records.add(p);}
    }
    for(Position p:records) {
      JSONObject current=latest.optJSONObject(JsonDisk.hash(p.id));Position old=current==null?null:parse(current);
      if(old==null || p.updated>old.updated || p.updated==old.updated && p.device.compareTo(old.device)>0) latest.put(JsonDisk.hash(p.id),p.json());
    }
    for(Iterator<String> it=latest.keys();it.hasNext();) {
      Position p=parse(latest.getJSONObject(it.next()));String key=JsonDisk.hash(p.id)+"/"+p.device;
      if(!p.device.isEmpty() && p.updated>0 && !devices.has(key))devices.put(key,p.json());
    }
    return new JSONObject().put("latest",latest).put("devices",devices);
  }
  static synchronized void install(Context c,JSONObject data) throws Exception {
    store(c,data);var edit=c.getSharedPreferences("podcast-progress",0).edit().clear();JSONObject latest=group(data,"latest");
    for(Iterator<String> it=latest.keys();it.hasNext();) {Position p=parse(latest.getJSONObject(it.next()));edit.putLong(p.id,p.position).putBoolean(p.id+".done",p.done);}edit.apply();
  }
  static synchronized JSONObject backup(Context c) throws Exception { JSONObject out=new JSONObject();capture(c,out);return out; }
  static JSONObject restoring(JSONObject saved,JSONObject current) throws Exception {
    JSONObject result=new JSONObject(saved.toString());
    for(Iterator<String> it=current.keys();it.hasNext();) {String key=it.next();if(key.startsWith("device-progress/") && !result.has(key))result.put(key,current.get(key));}
    long now=System.currentTimeMillis();
    for(Iterator<String> it=current.keys();it.hasNext();) {String key=it.next();if(key.startsWith("progress/") || key.startsWith("device-progress/")) now=Math.max(now,current.getJSONObject(key).optLong("updated")+1);}
    for(Iterator<String> it=result.keys();it.hasNext();) {String key=it.next();if(key.startsWith("progress/")) {JSONObject p=new JSONObject(result.getJSONObject(key).toString());p.put("updated",now);result.put(key,p);}}
    prepare(result);return result;
  }
  static synchronized void restoreBackup(Context c,JSONObject saved,JSONObject legacy) throws Exception {
    if(saved==null && legacy==null)return;
    JSONObject incoming=saved==null?new JSONObject():new JSONObject(saved.toString());
    if(legacy!=null) for(Iterator<String> it=legacy.keys();it.hasNext();) {
      String id=it.next();if(id.endsWith(".done"))continue;
      if(!incoming.has("progress/"+JsonDisk.hash(id))) incoming.put("progress/"+JsonDisk.hash(id),new Position(id,"","Earlier Cadence backup",legacy.optLong(id),legacy.optBoolean(id+".done"),0).json());
    }
    JSONObject current=backup(c);
    for(Iterator<String> it=current.keys();it.hasNext();) {String key=it.next();if(key.startsWith("progress/") && !incoming.has(key))incoming.put(key,current.get(key));}
    install(c,prepare(restoring(incoming,current)));
  }
}
