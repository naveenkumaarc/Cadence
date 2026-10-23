package app.cadence.music;

import android.content.Context;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import org.json.JSONObject;

/** Standard lock-screen, Bluetooth and car seek commands use the podcast's saved intervals. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class TransportPlayer extends ForwardingPlayer {
  private final Context context;
  TransportPlayer(Context c,Player delegate) { super(delegate); context=c; }
  private boolean podcast() {
    try { var e=getMediaMetadata().extras; return e!=null && new JSONObject(e.getString("track","{}")).optBoolean("podcast"); }
    catch(Exception e) { return false; }
  }
  @Override public long getSeekBackIncrement() { return (podcast()?Preferences.skip(context,false):15)*1000L; }
  @Override public long getSeekForwardIncrement() { return (podcast()?Preferences.skip(context,true):15)*1000L; }
  @Override public void seekBack() { seekTo(Math.max(0,getCurrentPosition()-getSeekBackIncrement())); }
  @Override public void seekForward() {
    long target=getCurrentPosition()+getSeekForwardIncrement();
    if(getDuration()>0) target=Math.min(target,getDuration()); seekTo(target);
  }
}
