package app.cadence.music;

import android.content.Context;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import io.reactivex.rxjava3.core.Single;

/** Phone-local appearance mode. The legacy preference is imported once and remains untouched. */
final class AppearanceStore {
  private static final Preferences.Key<String> MODE=PreferencesKeys.stringKey("mode");
  private static RxDataStore<Preferences> store;
  private static volatile String cached="System";

  static synchronized void init(Context context) {
    if(store!=null)return;Context app=context.getApplicationContext();
    store=new RxPreferenceDataStoreBuilder(app,"appearance-theme").build();
    Preferences saved=store.data().firstOrError().blockingGet();String existing=saved.get(MODE);
    if(existing!=null)cached=normalize(existing);
    else {
      android.content.SharedPreferences legacy=UiStyle.preferences(app);
      if(legacy.contains("theme"))cached=normalize(legacy.getString("theme","System"));
      else if(hasExistingLibrary(app))cached="Dark";
      else cached="System";
      write(cached).blockingGet();
    }
  }

  static String mode(Context context) {init(context);return cached;}

  static void set(Context context,String value,Runnable applied) {
    init(context);String next=normalize(value);cached=next;
    write(next).subscribe(ignored->{if(applied!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(applied);},
        error->{/* The in-memory choice remains usable; a later update can retry persistence. */});
  }

  static void setBlocking(Context context,String value) {init(context);cached=normalize(value);write(cached).blockingGet();}

  private static Single<Preferences> write(String value) {
    return store.updateDataAsync(current->{MutablePreferences next=current.toMutablePreferences();next.set(MODE,value);return Single.just(next);});
  }

  private static String normalize(String value) {
    if("Light".equalsIgnoreCase(value))return "Light";
    if("Dark".equalsIgnoreCase(value))return "Dark";
    return "System";
  }

  private static boolean hasExistingLibrary(Context context) {
    for(String name:new String[]{"library.json","podcasts.json","queue.json","song-downloads.json"})
      if(new java.io.File(context.getFilesDir(),name).exists())return true;
    return false;
  }

  private AppearanceStore() {}
}
