package app.cadence.music;

import android.content.Context;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder;
import androidx.datastore.rxjava3.RxDataStore;
import io.reactivex.rxjava3.core.Single;

/** App-wide equalizer profile stored independently from backups and cloud sync. */
final class EqualizerStore {
  record Profile(boolean enabled,int preset,String levels) {}
  private static final Preferences.Key<Boolean> ENABLED=PreferencesKeys.booleanKey("enabled");
  private static final Preferences.Key<Integer> PRESET=PreferencesKeys.intKey("preset");
  private static final Preferences.Key<String> LEVELS=PreferencesKeys.stringKey("levels");
  private static RxDataStore<Preferences> store;
  static synchronized Profile read(Context context){if(store==null)store=new RxPreferenceDataStoreBuilder(context.getApplicationContext(),"equalizer").build();Preferences p=store.data().firstOrError().blockingGet();return new Profile(Boolean.TRUE.equals(p.get(ENABLED)),p.get(PRESET)==null?-1:p.get(PRESET),p.get(LEVELS)==null?"":p.get(LEVELS));}
  static synchronized void write(Context context,Profile value){read(context);store.updateDataAsync(current->{MutablePreferences next=current.toMutablePreferences();next.set(ENABLED,value.enabled());next.set(PRESET,value.preset());next.set(LEVELS,value.levels());return Single.just(next);}).subscribe(ignored->{},error->{/* Keep the active in-memory effect usable; a later change can retry. */});}
  static short[] levels(String value,int count){short[] out=new short[count];if(value==null)return out;String[] parts=value.split(",");for(int i=0;i<Math.min(count,parts.length);i++)try{out[i]=Short.parseShort(parts[i]);}catch(Exception ignored){}return out;}
  static String levels(short[] value){StringBuilder out=new StringBuilder();for(short level:value){if(out.length()>0)out.append(',');out.append(level);}return out.toString();}
  private EqualizerStore(){}
}
