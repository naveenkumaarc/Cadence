package app.cadence.carprobe;

import android.app.Activity;
import android.os.*;
import android.content.ComponentName;
import androidx.media3.common.MediaItem;
import androidx.media3.session.*;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Separate UID exercises the same catalogue contract used by Android Auto. Never shipped in Cadence. */
public final class MainActivity extends Activity {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private ListenableFuture<MediaBrowser> pending,observerPending;
  private MediaBrowser browser,observer;
  private long deadline;
  private boolean completed;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);android.widget.TextView text=new android.widget.TextView(this);text.setText("Checking Cadence media-library integration…");setContentView(text);
    pending=new MediaBrowser.Builder(this,new SessionToken(this,new ComponentName("app.cadence.player","app.cadence.music.PlaybackService"))).buildAsync();
    pending.addListener(()->{
      try {
        browser=pending.get();
        var root=browser.getChildren("root",0,20,null);
        root.addListener(()->{
          try {
            if(root.get().value==null || root.get().value.size()!=4) throw new IllegalStateException("Invalid library root");
            browser.setMediaItem(new MediaItem.Builder().setMediaId("track:car-probe:1").build());
            await(()->browser.getMediaItemCount()==3 && browser.getCurrentMediaItemIndex()==1,()->{
              browser.setMediaItem(new MediaItem.Builder().setMediaId("unknown-source").setUri("https://example.com/arbitrary.mp3").build());
              handler.postDelayed(this::observeServer,700);
            });
          } catch(Exception e) {finishResult(false,e.toString());}
        },handler::post);
      } catch(Exception e) {finishResult(false,e.toString());}
    },handler::post);
    handler.postDelayed(()->finishResult(false,"Probe timed out"),45000);
  }
  private void observeServer() {
    observerPending=new MediaBrowser.Builder(this,new SessionToken(this,new ComponentName("app.cadence.player","app.cadence.music.PlaybackService"))).buildAsync();
    observerPending.addListener(()->{
      try {
        observer=observerPending.get();
        if(observer.getMediaItemCount()!=3 || observer.getCurrentMediaItemIndex()!=1) throw new IllegalStateException("Foreign URI changed the server queue");
        observer.seekToNextMediaItem();
        await(()->observer.getCurrentMediaItemIndex()==2,()->finishResult(true,"External UID: root, selected-song playlist expansion, foreign-URI rejection, and Next transport passed"));
      } catch(Exception e) {finishResult(false,e.toString());}
    },handler::post);
  }
  private void await(java.util.concurrent.Callable<Boolean> condition,Runnable success) {
    deadline=System.currentTimeMillis()+15000;poll(condition,success);
  }
  private void poll(java.util.concurrent.Callable<Boolean> condition,Runnable success) {
    if(completed) return;
    try {
      if(condition.call()) {success.run();return;}
      if(System.currentTimeMillis()>deadline) throw new IllegalStateException("Timed out waiting for media state");
      handler.postDelayed(()->poll(condition,success),100);
    } catch(Exception e) {finishResult(false,e.toString());}
  }
  private void finishResult(boolean passed,String message) {
    if(completed)return;completed=true;
    try {var result=new JSONObject().put("passed",passed).put("message",message);try(var out=openFileOutput("result.json",0)){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}}
    catch(Exception ignored) {}
    android.util.Log.i("CadenceCarProbe",passed+": "+message);
    if(pending!=null) MediaController.releaseFuture(pending);if(observerPending!=null) MediaController.releaseFuture(observerPending);
    finish();
  }
}
