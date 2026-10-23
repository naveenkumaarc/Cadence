package app.cadence.music;

import android.content.Context;
import androidx.work.*;

public final class CloudWorker extends Worker {
  public CloudWorker(Context context,WorkerParameters parameters) { super(context,parameters); }
  @Override public Result doWork() {
    Context c=getApplicationContext(); String owner=getInputData().getString("subject");
    if(owner==null || !CloudSync.enabled(c) || !owner.equals(CloudSync.subject(c))) return Result.success();
    try { CloudSync.sync(c,owner); return Result.success(); }
    catch(java.util.concurrent.CancellationException e) { return Result.success(); }
    catch(Exception e) {
      if(!owner.equals(CloudSync.subject(c))) return Result.success();
      boolean consent=e instanceof DriveStore.ConsentException;
      CloudSync.prefs(c).edit().putString("status",consent?"Reconnect Drive to sync":"Changes saved on this phone · Sync will retry")
          .putBoolean("needsConsent",consent).apply();
      return consent?Result.failure():Result.retry();
    }
  }
}
