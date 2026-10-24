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
  private static final String WORK="cadence-cloud-v2";
  private static final java.util.concurrent.atomic.AtomicBoolean MANUAL=new java.util.concurrent.atomic.AtomicBoolean();
  private static final ExecutorService MANUAL_EXECUTOR=Executors.newSingleThreadExecutor();
  interface DriveFactory { DriveStore open() throws Exception; }
  static boolean manualRunning() {return MANUAL.get();}
  static Runnable onApplied;
  private static SharedPreferences.OnSharedPreferenceChangeListener preferencesListener;
  static SharedPreferences prefs(Context c) { return c.getSharedPreferences("cloud-account",0); }
  static String subject(Context c) { return prefs(c).getString("subject",""); }
  static boolean enabled(Context c) { return !subject(c).isEmpty() && prefs(c).getBoolean("enabled",false); }
  static boolean automatic(Context c) { return enabled(c) && prefs(c).getBoolean("auto",true) && !prefs(c).getBoolean("chooseBackup",false); }
  static void opened(Context c) { if(automatic(c)) schedule(c); }
  static AuthorizationRequest authorization(Context c) {
    return AuthorizationRequest.builder().setAccount(new android.accounts.Account(prefs(c).getString("email",""),"com.google"))
        .setRequestedScopes(SCOPES).build();
  }
  static void init(Context c) {
    Context app=c.getApplicationContext();
    if(prefs(app).getInt("schedulerVersion",0)<2) {
      WorkManager.getInstance(app).cancelUniqueWork("cadence-cloud");prefs(app).edit().putInt("schedulerVersion",2).apply();
    }
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
    if(!enabled(c) || prefs(c).getBoolean("chooseBackup",false)) return;
    WorkManager.getInstance(c).enqueueUniqueWork(WORK,ExistingWorkPolicy.KEEP,
        new OneTimeWorkRequest.Builder(CloudWorker.class).setConstraints(constraints())
            .setInputData(new Data.Builder().putString("subject",subject(c)).build()).build());
  }
  static boolean syncNow(Context c) {Context app=c.getApplicationContext();return syncNow(app,()->new DriveStore(accessToken(app)));}
  static boolean syncNow(Context c,DriveFactory factory) {
    if(!enabled(c) || prefs(c).getBoolean("chooseBackup",false) || !MANUAL.compareAndSet(false,true))return false;
    Context app=c.getApplicationContext();String owner=subject(app);
    prefs(app).edit().putString("status","Starting sync…").putLong("lastAttempt",System.currentTimeMillis()).apply();
    MANUAL_EXECUTOR.execute(()-> {
      try {sync(app,owner,true,factory);}
      catch(CancellationException ignored) {}
      catch(Exception failure) {recordFailure(app,owner,failure,false);}
      finally {MANUAL.set(false);}
    });return true;
  }
  static SyncErrors.Failure recordFailure(Context c,String owner,Exception error,boolean background) {
    SyncErrors.Failure failure=SyncErrors.describe(error);
    if(owner.equals(subject(c))) {
      String phase=prefs(c).getString("syncPhase","Sync");
      prefs(c).edit().putString("status",phase+" failed. "+failure.description()+"\nChanges are saved on this phone."+(background && failure.retry()?" Sync will retry.":""))
          .putBoolean("needsConsent",failure.consent()).putString("lastSyncError",failure.description()).apply();
    }return failure;
  }
  private static void phase(Context c,String owner,String value) throws Exception {
    requireOwner(c,owner);prefs(c).edit().putString("syncPhase",value).putString("status",value+"…").apply();
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
  static String device(Context c) { return actor(c); }
  private static synchronized String actor(Context c) {
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
  static JSONObject libraryData(SyncState state) throws Exception {
    JSONObject data=new JSONObject(state.materialize().toString());
    for(Iterator<String> it=data.keys();it.hasNext();) {String key=it.next();if(key.startsWith("progress/")) {
      JSONObject row=data.getJSONObject(key);
      if(row.optLong("updated")==0)row.put("updated",state.changedAt(key)).put("device",state.changedBy(key)).put("name","Earlier Cadence device");
    }}return data;
  }
  private static void requireOwner(Context c,String owner) throws Exception {
    if(Thread.currentThread().isInterrupted() || !enabled(c) || !subject(c).equals(owner) || prefs(c).getBoolean("chooseBackup",false)) throw new CancellationException("Account changed or backup selection pending");
  }
  static String accessToken(Context c) throws Exception {
    AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(c).authorize(authorization(c)),45,TimeUnit.SECONDS);
    if(auth.hasResolution() || auth.getAccessToken()==null) throw new DriveStore.ConsentException();
    return auth.getAccessToken();
  }
  static void sync(Context c,String owner) throws Exception {
    sync(c,owner,false,()->new DriveStore(accessToken(c)));
  }
  static void sync(Context c,String owner,boolean manual,DriveFactory factory) throws Exception {
    if(manual) {prefs(c).edit().putString("status","Waiting for the current sync…").apply();if(!SYNC.tryLock(60,TimeUnit.SECONDS))throw new java.io.IOException("Another sync is finishing.");}
    else if(!SYNC.tryLock()) throw new CancellationException("Another sync is finishing");
    try {
      requireOwner(c,owner);prefs(c).edit().putLong("lastAttempt",System.currentTimeMillis()).apply();phase(c,owner,"Preparing library");
      String device=actor(c);
      SyncState state=main(()-> {
        requireOwner(c,owner); SyncState s=state(c,owner); applying=true;
        try { s.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,s); return s; }
        finally { applying=false; }
      });
      phase(c,owner,"Connecting to Google Drive");DriveStore drive=factory.open();drive.verifyAccount(owner);requireOwner(c,owner);
      phase(c,owner,"Reading device backups");List<JSONObject> files=drive.list("state");String own=null,previous=null;JSONObject ownFile=null;int total=0;
      List<SyncState> remote=new ArrayList<>();
      for(JSONObject file:files) {
        requireOwner(c,owner); String payload=drive.read(file.getString("id")); total+=payload.length();
        if(total>80*1024*1024) throw new java.io.IOException("The cloud library is too large.");
        remote.add(SyncState.parse(payload,device));
        JSONObject properties=file.optJSONObject("appProperties");
        if(properties!=null && device.equals(properties.optString("device"))) {own=file.getString("id");previous=payload;ownFile=file;}
      }
      phase(c,owner,"Combining library changes");
      String document=main(()-> {
        requireOwner(c,owner); applying=true;
        try {
          state.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject());
          saveState(c,owner,state); // Persist unsent local changes before touching the live library.
          for(SyncState other:remote) state.merge(other);
          CloudData.apply(c,libraryData(state));
          state.applied(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,state);
          if(onApplied!=null) onApplied.run();
          return state.document(false);
        } finally { applying=false; }
      });
      phase(c,owner,"Saving this device backup");long savedAt=System.currentTimeMillis();
      drive.write(own,device,"state",document,BackupLabels.deviceName(c),savedAt);
      if(subject(c).equals(owner)) prefs(c).edit().putString("status","Up to date").putBoolean("needsConsent",false)
          .remove("lastSyncError").putLong("lastSync",savedAt).apply();
      if(previous!=null && !previous.equals(document) && savedAt-prefs(c).getLong("lastSnapshot",0)>3600000) {
        try {
          JSONObject props=ownFile==null?null:ownFile.optJSONObject("appProperties");
          String previousName=props==null?"":props.optString("deviceName");
          drive.write(null,device,"history",previous,previousName,ownFile==null?0:BackupLabels.savedAt(ownFile));
          prefs(c).edit().putLong("lastSnapshot",savedAt).apply();List<JSONObject> history=new ArrayList<>();
          for(JSONObject f:drive.list("history")) {JSONObject p=f.optJSONObject("appProperties");if(p!=null && device.equals(p.optString("device")))history.add(f);}
          history.sort(Comparator.comparingLong(BackupLabels::savedAt));
          while(history.size()>5) {requireOwner(c,owner);drive.delete(history.remove(0).getString("id"));}
        } catch(CancellationException cancelled) {throw cancelled;}
        catch(Exception recoveryFailure) {if(subject(c).equals(owner))prefs(c).edit().putString("status","Up to date · Earlier recovery copy could not be saved").apply();}
      }
    } finally { SYNC.unlock(); }
  }
  static void connected(Context c) {
    prefs(c).edit().putBoolean("enabled",true).putBoolean("needsConsent",false).apply();
    if(prefs(c).getBoolean("chooseBackup",false)) prefs(c).edit().putString("status","Choose a cloud backup to finish setup").apply();
    else {periodic(c);syncNow(c);}
  }
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
          .putString("libraryOwner",sub).putBoolean("enabled",false).putBoolean("chooseBackup",false).remove("lastSync").putString("status","Connect Drive to sync").commit();
      if(saved.length()>0 || merge) CloudData.applyLocal(c,use); else CloudData.apply(c,use,false);
      if(onApplied!=null) onApplied.run();
    } finally { applying=false; }
  }
  static void signOut(Context c) throws Exception {
    String owner=subject(c);
    if(!owner.isEmpty()) JsonDisk.write(c,"cloud-library-"+JsonDisk.hash(owner)+".json",CloudData.localSnapshot(c));
    prefs(c).edit().remove("subject").remove("email").remove("googleName").remove("googlePhoto").remove("chooseBackup").putBoolean("enabled",false).putString("status","Saved on this phone").commit();
    WorkManager.getInstance(c).cancelUniqueWork("cadence-cloud");WorkManager.getInstance(c).cancelUniqueWork(WORK);WorkManager.getInstance(c).cancelUniqueWork("cadence-cloud-periodic");
    if(pending!=null) MAIN.removeCallbacks(pending);
  }
  static List<JSONObject> history(Context c) throws Exception {
    return history(c,()->new DriveStore(accessToken(c)));
  }
  static List<JSONObject> history(Context c,DriveFactory factory) throws Exception {
    String owner=subject(c);DriveStore drive=factory.open();drive.verifyAccount(owner);
    List<JSONObject> list=new ArrayList<>(drive.list("state"));list.addAll(drive.list("history"));
    if(!enabled(c) || !subject(c).equals(owner))throw new CancellationException("Account changed");
    JSONObject before=JsonDisk.read(c,"cloud-before-restore-"+JsonDisk.hash(owner)+".json");
    if(before.length()>0)list.add(new JSONObject().put("id","local-before-restore").put("local",true).put("appProperties",new JSONObject()
        .put("device",actor(c)).put("deviceName",before.optString("_backupDevice",BackupLabels.deviceName(c))).put("savedAt",Long.toString(before.optLong("_backupTime"))).put("cadenceKind","history")));
    list.sort(Comparator.comparingLong(BackupLabels::savedAt).reversed());return list;
  }
  static void restore(Context c,String id) throws Exception {
    restore(c,id,()->new DriveStore(accessToken(c)));
  }
  static void restore(Context c,String id,DriveFactory factory) throws Exception {
    SYNC.lockInterruptibly();
    try {
    String owner=subject(c);boolean phoneCopy=id.equals("local-before-restore");
    JSONObject selectedData;
    if(phoneCopy) selectedData=JsonDisk.read(c,"cloud-before-restore-"+JsonDisk.hash(owner)+".json");
    else {DriveStore drive=factory.open();drive.verifyAccount(owner);selectedData=libraryData(SyncState.parse(drive.read(id),actor(c)));}
    if(selectedData.length()==0)throw new java.io.IOException("This backup is no longer available.");
    JSONObject picked=selectedData;
    main(()-> {
      if(!enabled(c) || !subject(c).equals(owner))throw new CancellationException("Account changed");applying=true;
      try {
        SyncState local=state(c,owner);
        JSONObject current=CloudData.snapshot(c);local.capture(JsonParser.parseString(current.toString()).getAsJsonObject());
        JSONObject selected=PodcastPositions.restoring(picked,current);PodcastPositions.prepare(selected);
        JsonDisk.write(c,"cloud-before-restore-"+JsonDisk.hash(owner)+".json",CloudData.localSnapshot(c)
            .put("_backupDevice",BackupLabels.deviceName(c)).put("_backupTime",System.currentTimeMillis()));
        if(phoneCopy)CloudData.applyLocal(c,selected);else CloudData.apply(c,selected);
        prefs(c).edit().putBoolean("chooseBackup",false).apply();
        local.capture(JsonParser.parseString(CloudData.snapshot(c).toString()).getAsJsonObject()); saveState(c,owner,local);
        if(onApplied!=null) onApplied.run(); return null;
      } finally { applying=false; }
    });
    } finally {SYNC.unlock();}
    periodic(c);syncNow(c,factory);
  }
}
