package app.cadence.music;

import android.content.Context;
import app.cadence.core.AccentPalette;

/** Migrates and stores the phone-local accent selection in the existing appearance preferences. */
final class AccentStore {
  private static final String INITIALIZED="accent.initialized",PRESET="accent.preset",ANNOUNCEMENT="accent.announcement";

  static synchronized void init(Context context) {
    var prefs=UiStyle.preferences(context);if(prefs.getBoolean(INITIALIZED,false))return;
    boolean custom=prefs.contains("colour"),existing=existingInstall(context);
    String selected=AccentPalette.initialId(existing,custom);
    prefs.edit().putString(PRESET,selected).putBoolean(INITIALIZED,true)
        .putBoolean(ANNOUNCEMENT,existing&&!custom).commit();
  }

  static String id(Context context){init(context);return UiStyle.preferences(context).getString(PRESET,"rose");}
  static String label(Context context){String id=id(context);return id.equals("custom")?"Custom colour":AccentPalette.preset(id).label();}
  static AccentPalette.Resolved resolved(Context context,boolean light) {
    String id=id(context);if(id.equals("custom"))return AccentPalette.custom(UiStyle.preferences(context).getInt("colour",0xffe5384f),light);
    var preset=AccentPalette.preset(id);return new AccentPalette.Resolved(preset.accent(light),preset.onAccent(light),false);
  }
  static void preset(Context context,String id) {
    AccentPalette.preset(id);UiStyle.preferences(context).edit().putString(PRESET,id).remove("colour").putBoolean(ANNOUNCEMENT,false).apply();
  }
  static void custom(Context context,int colour) {
    UiStyle.preferences(context).edit().putString(PRESET,"custom").putInt("colour",0xff000000|(colour&0xffffff)).putBoolean(ANNOUNCEMENT,false).apply();
  }
  static boolean announcement(Context context){init(context);return UiStyle.preferences(context).getBoolean(ANNOUNCEMENT,false);}
  static void dismissAnnouncement(Context context){UiStyle.preferences(context).edit().putBoolean(ANNOUNCEMENT,false).apply();}

  private static boolean existingInstall(Context context) {
    if(!UiStyle.preferences(context).getAll().isEmpty())return true;
    for(String name:new String[]{"library.json","podcasts.json","queue.json","song-downloads.json","cloud-profile.json"})
      if(new java.io.File(context.getFilesDir(),name).exists())return true;
    return new java.io.File(context.getFilesDir(),"datastore/appearance-theme.preferences_pb").exists();
  }
  private AccentStore(){}
}
