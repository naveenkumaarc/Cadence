package app.cadence.music;

import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.media3.session.*;
import com.google.common.collect.ImmutableList;

/** Keeps the standard Media3 notification while supplying the active semantic accent. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class AccentNotificationProvider extends DefaultMediaNotificationProvider {
  private final Context context;
  AccentNotificationProvider(Context context){super(context);this.context=context.getApplicationContext();setSmallIcon(R.drawable.ic_notification);}
  @Override protected int[] addNotificationActions(MediaSession session,ImmutableList<CommandButton> buttons,
      NotificationCompat.Builder builder,MediaNotification.ActionFactory actions) {
    builder.setColor(UiStyle.palette(context).accent());builder.setColorized(false);
    return super.addNotificationActions(session,buttons,builder,actions);
  }
}
