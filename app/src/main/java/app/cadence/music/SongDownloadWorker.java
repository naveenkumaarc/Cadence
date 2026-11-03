package app.cadence.music;

import android.content.Context;
import androidx.work.*;

/** WorkManager retains requests across process death and stops transfers when constraints change. */
public final class SongDownloadWorker extends Worker {
  private volatile okhttp3.Call active;
  public SongDownloadWorker(Context context,WorkerParameters parameters){super(context,parameters);}
  @Override public Result doWork() {
    try {
      if(!DownloadPolicy.allowed(getApplicationContext()))return Result.retry();
      return SongDownloads.get(getApplicationContext()).transfer(getInputData().getString("source"),
          getInputData().getString("generation"),()->isStopped()||!DownloadPolicy.allowed(getApplicationContext()),
          call->{active=call;if(call!=null&&isStopped())call.cancel();})?Result.success():Result.retry();
    } catch(Exception e) {
      if(getRunAttemptCount()<5)return Result.retry();
      try{SongDownloads.get(getApplicationContext()).failed(getInputData().getString("source"),getInputData().getString("generation"));}catch(Exception ignored){}
      return Result.failure();
    }
  }
  @Override public void onStopped(){okhttp3.Call call=active;if(call!=null)call.cancel();}
}
