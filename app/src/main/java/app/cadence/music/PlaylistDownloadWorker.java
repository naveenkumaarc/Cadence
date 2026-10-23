package app.cadence.music;

import android.content.Context;
import androidx.work.*;

public final class PlaylistDownloadWorker extends Worker {
  public PlaylistDownloadWorker(Context context,WorkerParameters parameters) { super(context,parameters); }
  @Override public Result doWork() {
    try {
      Context c=getApplicationContext(); PlaylistDownloads.recover(c);
      for(int i=0;i<8 && !isStopped();i++) if(!PlaylistDownloads.runOne(c,this::isStopped)) break;
      return PlaylistDownloads.hasPending(c)?Result.retry():Result.success();
    } catch(Exception e) { return Result.retry(); }
  }
}
