package app.cadence.music;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.widget.RemoteViews;
import androidx.media3.session.*;
import androidx.media3.common.Player;

/** Widget controls connect to the same foreground playback session as the app and car. */
public final class CadenceWidget extends AppWidgetProvider {
  private static final String CONTROL="app.cadence.WIDGET_CONTROL";
  static void updateAll(Context c) {
    AppWidgetManager manager=AppWidgetManager.getInstance(c);
    int[] ids=manager.getAppWidgetIds(new ComponentName(c,CadenceWidget.class));
    for(int id:ids) update(c,manager,id);
    if(ids.length>0) WidgetArtwork.request(c,c.getSharedPreferences("widget",0).getString("artwork",""));
  }
  private static void update(Context c,AppWidgetManager manager,int id) {
    var p=c.getSharedPreferences("widget",0); boolean podcast=p.getBoolean("podcast",false);
    RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_player);
    android.graphics.Bitmap cover=WidgetArtwork.cached(c,p.getString("artwork",""));
    if(cover!=null) v.setImageViewBitmap(R.id.widget_art,cover); else v.setImageViewResource(R.id.widget_art,R.drawable.ic_cadence);
    v.setContentDescription(R.id.widget_back,podcast?"Skip back "+Preferences.skip(c,false)+" seconds":"Previous song");
    v.setContentDescription(R.id.widget_next,podcast?"Skip forward "+Preferences.skip(c,true)+" seconds":"Next song");
    v.setTextViewText(R.id.widget_title,p.getString("title","Cadence"));
    v.setTextViewText(R.id.widget_artist,p.getString("artist","Choose something to play"));
    v.setImageViewResource(R.id.widget_play,p.getBoolean("playing",false)?R.drawable.widget_pause:R.drawable.widget_play);
    v.setImageViewResource(R.id.widget_back,podcast?R.drawable.widget_back15:R.drawable.widget_previous);
    v.setImageViewResource(R.id.widget_next,podcast?R.drawable.widget_forward15:R.drawable.widget_next);
    v.setProgressBar(R.id.widget_progress,1000,p.getInt("progress",0),false);
    v.setOnClickPendingIntent(R.id.widget_open,PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    for(int view:new int[]{R.id.widget_back,R.id.widget_play,R.id.widget_next}) {
      String action=view==R.id.widget_back?"back":view==R.id.widget_next?"next":"play";
      Intent i=new Intent(c,CadenceWidget.class).setAction(CONTROL).putExtra("control",action);
      v.setOnClickPendingIntent(view,PendingIntent.getBroadcast(c,view,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
    }
    manager.updateAppWidget(id,v);
  }
  @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids) { updateAll(c); }
  @Override public void onReceive(Context c,Intent intent) {
    super.onReceive(c,intent);
    if(!CONTROL.equals(intent.getAction())) return;
    PendingResult pending=goAsync();
    Context app=c.getApplicationContext();
    final com.google.common.util.concurrent.ListenableFuture<MediaController> future;
    try { future=new MediaController.Builder(app,new SessionToken(app,new ComponentName(app,PlaybackService.class))).buildAsync(); }
    catch(Exception e) { pending.finish(); return; }
    Handler handler=new Handler(Looper.getMainLooper());
    java.util.concurrent.atomic.AtomicBoolean finished=new java.util.concurrent.atomic.AtomicBoolean();
    Runnable finish=()-> { if(finished.compareAndSet(false,true)) { MediaController.releaseFuture(future); pending.finish(); } };
    handler.postDelayed(finish,8000);
    future.addListener(()-> {
      if(finished.get()) return;
      try {
        MediaController controller=future.get();
        boolean podcast=false;
        try {var extras=controller.getMediaMetadata().extras;podcast=extras!=null && new org.json.JSONObject(extras.getString("track","{}")).optBoolean("podcast");} catch(Exception ignored) {}
        switch(intent.getStringExtra("control")) {
          case "play":
            if(controller.getPlayWhenReady() && controller.getPlaybackState()!=Player.STATE_ENDED && controller.getPlayerError()==null) controller.pause();
            else {
              if(controller.getPlaybackState()==Player.STATE_ENDED) controller.seekToDefaultPosition();
              if(controller.getPlaybackState()==Player.STATE_IDLE || controller.getPlayerError()!=null) controller.prepare();controller.play();
            }
            break;
          case "back": if(podcast) controller.seekBack(); else controller.seekToPrevious(); break;
          case "next": if(podcast) controller.seekForward(); else controller.seekToNextMediaItem(); break;
        }
        handler.postDelayed(finish,500);
      } catch(Exception e) { finish.run(); }
    },handler::post);
  }
}
