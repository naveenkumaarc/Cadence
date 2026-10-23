package app.cadence.music;

import android.app.DownloadManager;
import android.content.*;

/** Records completed song transfers even when no Cadence screen is open. */
public final class SongDownloadReceiver extends BroadcastReceiver {
  @Override public void onReceive(Context context, Intent intent) {
    if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
    long job = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
    if (job < 0) return;
    PendingResult pending = goAsync();
    new Thread(() -> {
      try { SongDownloads.get(context).completed(job); }
      catch (Exception unavailable) { /* The app retries the system status on its next launch. */ }
      finally { pending.finish(); }
    }, "song-download-complete").start();
  }
}
