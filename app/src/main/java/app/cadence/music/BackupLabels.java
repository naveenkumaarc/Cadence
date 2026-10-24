package app.cadence.music;

import android.content.Context;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

final class BackupLabels {
  static String deviceName(Context c) {
    String saved=CloudSync.prefs(c).getString("deviceName","").trim();
    if(!saved.isEmpty()) return saved;
    String model=Build.MODEL==null?"Android device":Build.MODEL.trim(), maker=Build.MANUFACTURER==null?"":Build.MANUFACTURER.trim();
    return model.toLowerCase(Locale.ROOT).startsWith(maker.toLowerCase(Locale.ROOT))?model:(maker+" "+model).trim();
  }
  static String date(long time) { return time>0?new SimpleDateFormat("dd MMM yyyy, h:mm:ss a z",Locale.getDefault()).format(new Date(time)):"Date unavailable"; }
  static long savedAt(JSONObject file) {
    JSONObject props=file.optJSONObject("appProperties");long saved=props==null?0:props.optLong("savedAt",0);
    if(saved>0) return saved;
    try { return java.time.Instant.parse(file.optString("modifiedTime")).toEpochMilli(); } catch(Exception ignored) { return 0; }
  }
  static String cloud(JSONObject file) {
    JSONObject props=file.optJSONObject("appProperties");String name=props==null?"":props.optString("deviceName").trim();
    if(name.isEmpty()) {
      String id=props==null?"":props.optString("device");
      name="Earlier Cadence device"+(id.isEmpty()?"":" · "+id.substring(0,Math.min(8,id.length())));
    }
    boolean earlier=props!=null && props.optString("cadenceKind").equals("history");
    return name+"\n"+date(savedAt(file))+"\n"+(file.optBoolean("local")?"Before last restore · On this phone":earlier?"Earlier recovery copy":"Latest device backup");
  }
  static String local(JSONObject backup) {
    return backup.optString("deviceName","Earlier Cadence backup")+"\n"+date(backup.optLong("createdAt"));
  }
  static String filename(Context c) {
    String name=deviceName(c).replaceAll("[^A-Za-z0-9_-]","_");if(name.isEmpty())name="Android";
    return "Cadence-"+name.substring(0,Math.min(45,name.length()))+"-"+new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss",Locale.ROOT).format(new Date())+".json";
  }
  static String cloudName(String name) {
    StringBuilder out=new StringBuilder();int bytes=0;
    for(int at=0;at<name.length();) { int point=name.codePointAt(at);String part=new String(Character.toChars(point));int size=part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(bytes+size>100)break;out.append(part);bytes+=size;at+=Character.charCount(point); }
    return out.toString();
  }
}
