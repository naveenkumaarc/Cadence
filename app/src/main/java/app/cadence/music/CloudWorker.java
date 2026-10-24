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
      SyncErrors.Failure failure=CloudSync.recordFailure(c,owner,e,true);
      return failure.retry()?Result.retry():Result.failure();
    }
  }
}
