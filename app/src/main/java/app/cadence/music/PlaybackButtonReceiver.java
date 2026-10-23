package app.cadence.music;

import android.content.Context;
import android.content.Intent;
import androidx.media3.session.MediaButtonReceiver;

/** Lets Android restart saved playback from a headset after the player service has been removed. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackButtonReceiver extends MediaButtonReceiver {
  @Override
  protected boolean shouldStartForegroundService(Context context, Intent intent) {
    // With no saved queue, there is nothing to play. Starting a foreground service
    // in that case would miss Android's deadline for beginning media playback.
    try {
      var file = new android.util.AtomicFile(new java.io.File(context.getFilesDir(), "queue.json"));
      var queue = new org.json.JSONObject(new String(file.readFully(), java.nio.charset.StandardCharsets.UTF_8));
      return queue.getJSONArray("items").length() > 0;
    } catch (Exception unavailable) {
      return false;
    }
  }
}
