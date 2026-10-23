package app.cadence.music;

import android.content.*;
import android.os.*;
import androidx.work.*;
import app.cadence.core.SyncState;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

final class CloudSync {
  static final String DRIVE="https://www.googleapis.com/auth/drive.appdata";
  static final List<Scope> SCOPES=List.of(new Scope(DRIVE),new Scope("openid"),new Scope("https://www.googleapis.com/auth/userinfo.profile"),new Scope("https://www.googleapis.com/auth/userinfo.email"));
  private static final Handler MAIN=new Handler(Looper.getMainLooper());
  private static final java.util.concurrent.locks.ReentrantLock SYNC=new java.util.concurrent.locks.ReentrantLock();
  private static boolean applying;
  private static Runnable pending;
  private static long lastProgress;
  static Runnable onApplied;
  private static SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
  static SharedPreferences prefs(Context c) { return c.getSharedPreferences("cloud-account",0); }
  static String subject(Context c) { return prefs(c).getString("subject",""); }
  static boolean enabled(Context c) { return !subject(c).isEmpty() && prefs(c).getBoolean("enabled",false); }
  static boolean automatic(Context c) { return enabled(c) && prefs(c).getBoolean("auto",true); }
  static void opened(Context c) { if(automatic(c)) schedule(c); }
  static AuthorizationRequest authorization(Context c) {
    return AuthorizationRequest.builder().setAccount(new android.accounts.Account(prefs(c).getString("email",""),"com.google"))
        .setRequestedScopes(SCOPES).build();
  }
  static void init(Context c) {
    Context app=c.getApplicationContext();
    if(preferencesListener==null) {
      preferencesListener=(p,key)->changed(app); Preferences.get(app).registerOnSharedPreferenceChangeListener(preferencesListener);
    }
    if(automatic(app)) { periodic(app); schedule(app); }
  }
  static void changed(Context c) {
    Context app=c.getApplicationContext();
    if(!automatic(app) || applying) return;
    MAIN.post(()-> { if(pending!=null) MAIN.removeCallbacks(pending); pending=()->schedule(app); MAIN.postDelayed(pending,2000); });
  }
  static void progressChanged(Context c) {
    long now=android.os.SystemClock.elapsedRealtime(); if(now-lastProgress>30000) { lastProgress=now; changed(c); }
  }
  private static Constraints constraints() { return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(); }
  static void schedule(Context c) {
    if(!enabled(c)) return;
    WorkManager.getInstance(c).enqueueUniqueWork("cadence-cloud",ExistingWorkPolicy.APPEND_OR_REPLACE,
        new OneTimeWorkRequest.Builder(CloudWorker.class).setConstraints(constraints())
            .setInputData(new Data.Builder().putString("subject",subject(c)).build()).build());
  }
  static void periodic(Context c) {
    if(!automatic(c)) return;
    WorkManager.getInstance(c).enqueueUniquePeriodicWork("cadence-cloud-periodic",ExistingPeriodicWorkPolicy.UPDATE,
        new PeriodicWorkRequest.Builder(CloudWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints())
            .setInputData(new Data.Builder().putString("subject",subject(c)).build()).build());
  }
  static <T> T main(Callable<T> task) throws Exception {
    if(Looper.myLooper()==Looper.getMainLooper()) return task.call();
    FutureTask<T> future=new FutureTask<>(task); MAIN.post(future);
    try { return future.get(30,TimeUnit.SECONDS); }
    catch(Exception failure) { future.cancel(false); throw failure; }
  }
  private static String actor(Context c) {
    String actor=prefs(c).getString("device","");
    if(actor.isEmpty()) { actor=UUID.randomUUID().toString(); prefs(c).edit().putString("device",actor).commit(); }
    return actor;
  }
  private static String stateName(String owner) { return "cloud-state-"+JsonDisk.hash(owner)+".json"; }
  private static SyncState state(Context c,String owner) throws Exception {
    JSONObject file=JsonDisk.read(c,stateName(owner));
    return file.length()==0?new SyncState(actor(c)):SyncState.parse(file.toString(),actor(c));
  }
  private static void saveState(Context c,String owner,SyncState state) throws Exception { JsonDisk.write(c,stateName(owner),new JSONObject(state.document(true))); }
  private static void requireOwner(Context c,String owner) throws Exception {
    if(!enabled(c) || !subject(c).equals(owner)) throw new CancellationException("Account changed");
  }
  static String accessToken(Context c) throws Exception {
    AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(c).authorize(authorization(c)),45,TimeUnit.SECONDS);
    if(auth.hasResolution() || auth.getAccessToken()==null) throw new DriveStore.ConsentException();
    return auth.getAccessToken();
  }
  static void sync(Context c,String owner) throws Exception {
    if(!SYNC.tryLock()) throw new java.io.IOException("Another sync is finishing.");
    try {
      requireOwner(c,owner); prefs(c).edit().putString("status","Syncing…").apply();
      String device=actor(c);
      SyncState state=main(()-> {
        requireOwner(c,owner); SyncState s=state(c,owner); applying=true;
        try { s.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,s); return s; }
        finally { applying=false; }
      });
      DriveStore drive=new DriveStore(accessToken(c)); drive.verifyAccount(owner); requireOwner(c,owner);
      List<JSONObject> files=drive.list("state"); String own=null,previous=null; int total=0;
      List<SyncState> remote=new ArrayList<>();
      for(JSONObject file:files) {
        requireOwner(c,owner); String payload=drive.read(file.getString("id")); total+=payload.length();
        if(total>80*1024*1024) throw new java.io.IOException("The cloud library is too large.");
        remote.add(SyncState.parse(payload,device));
        if(device.equals(file.optJSONObject("appProperties").optString("device"))) { own=file.getString("id"); previous=payload; }
      }
      String document=main(()-> {
        requireOwner(c,owner); applying=true;
        try {
          state.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject());
          saveState(c,owner,state); // Persist unsent local changes before touching the live library.
          for(SyncState other:remote) state.merge(other);
          CloudData.apply(c,new JSONObject(state.materialize().toString()));
          state.applied(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,state);
          if(onApplied!=null) onApplied.run();
          return state.document(false);
        } finally { applying=false; }
      });
      requireOwner(c,owner);
      if(previous!=null && !previous.equals(document) && System.currentTimeMillis()-prefs(c).getLong("lastSnapshot",0)>3600000) {
        drive.write(null,device,"history",previous); prefs(c).edit().putLong("lastSnapshot",System.currentTimeMillis()).apply();
        List<JSONObject> history=new ArrayList<>();
        for(JSONObject f:drive.list("history")) if(device.equals(f.getJSONObject("appProperties").optString("device"))) history.add(f);
        history.sort(Comparator.comparing(f->f.optString("modifiedTime")));
        while(history.size()>5) { requireOwner(c,owner); drive.delete(history.remove(0).getString("id")); }
      }
      requireOwner(c,owner); drive.write(own,device,"state",document);
      if(subject(c).equals(owner)) prefs(c).edit().putString("status","Up to date").putBoolean("needsConsent",false)
          .putLong("lastSync",System.currentTimeMillis()).apply();
    } finally { SYNC.unlock(); }
  }
  static void connected(Context c) { prefs(c).edit().putBoolean("enabled",true).putBoolean("needsConsent",false).apply(); periodic(c); schedule(c); }
  static void activate(Context c,String sub,String email,String name,String photo,boolean merge) throws Exception {
    activate(c,sub,email,name,photo,merge,CloudData.localSnapshot(c));
  }
  static void activate(Context c,String sub,String email,String name,String photo,boolean merge,JSONObject current) throws Exception {
    applying=true;
    try {
      String old=prefs(c).getString("libraryOwner","");
      JsonDisk.write(c,"cloud-library-"+JsonDisk.hash(old.isEmpty()?"guest":old)+".json",current);
      JSONObject saved=JsonDisk.read(c,"cloud-library-"+JsonDisk.hash(sub)+".json");
      JSONObject use=saved.length()>0?saved:merge?current:new JSONObject();
      if(saved.length()==0 && !old.equals(sub)) { use.remove("profile/name"); use.remove("profile/photo"); }
      if(use.has("_localQueue")) use.getJSONObject("_localQueue").put("owner",sub);
      prefs(c).edit().putString("subject",sub).putString("email",email).putString("googleName",name).putString("googlePhoto",photo)
          .putString("libraryOwner",sub).putBoolean("enabled",false).remove("lastSync").putString("status","Connect Drive to sync").commit();
      if(saved.length()>0 || merge) CloudData.applyLocal(c,use); else CloudData.apply(c,use,false);
      if(onApplied!=null) onApplied.run();
    } finally { applying=false; }
  }
  static void signOut(Context c) throws Exception {
    String owner=subject(c);
    if(!owner.isEmpty()) JsonDisk.write(c,"cloud-library-"+JsonDisk.hash(owner)+".json",CloudData.localSnapshot(c));
    prefs(c).edit().remove("subject").remove("email").remove("googleName").remove("googlePhoto").putBoolean("enabled",false).putString("status","Saved on this phone").commit();
    WorkManager.getInstance(c).cancelUniqueWork("cadence-cloud"); WorkManager.getInstance(c).cancelUniqueWork("cadence-cloud-periodic");
    if(pending!=null) MAIN.removeCallbacks(pending);
  }
  static List<JSONObject> history(Context c) throws Exception {
    String owner=subject(c); DriveStore drive=new DriveStore(accessToken(c)); drive.verifyAccount(owner);
    List<JSONObject> list=drive.list("history"); requireOwner(c,owner);
    list.sort((a,b)->b.optString("modifiedTime").compareTo(a.optString("modifiedTime"))); return list;
  }
  static void restore(Context c,String id) throws Exception {
    String owner=subject(c); DriveStore drive=new DriveStore(accessToken(c)); drive.verifyAccount(owner);
    SyncState remote=SyncState.parse(drive.read(id),actor(c));
    main(()-> {
      requireOwner(c,owner); applying=true;
      try {
        SyncState local=state(c,owner);
        local.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject());
        CloudData.apply(c,new JSONObject(remote.materialize().toString()));
        local.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,local);
        if(onApplied!=null) onApplied.run(); return null;
      } finally { applying=false; }
    }); schedule(c);
  }
}
