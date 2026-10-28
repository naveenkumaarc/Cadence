package app.cadence.carprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.os.*;
import android.media.browse.MediaBrowser;
import androidx.media3.session.*;
import java.util.*;

/** Separate UID, platform MediaBrowser: exercises the legacy contract used by projected cars. */
public final class LegacyProbeActivity extends Activity {
  private final Handler handler=new Handler(Looper.getMainLooper());
  private MediaBrowser legacy;
  private com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.MediaBrowser> pending;
  private boolean finished;
  private void fail(String message){finishResult(false,message);}
  @Override public void onCreate(Bundle state){super.onCreate(state);android.widget.TextView label=new android.widget.TextView(this);label.setText("Checking the car browse screen…");setContentView(label);
    legacy=new MediaBrowser(this,new ComponentName("app.cadence.player","app.cadence.music.PlaybackService"),new MediaBrowser.ConnectionCallback(){
      @Override public void onConnectionFailed(){fail("Legacy browser connection failed");}
      @Override public void onConnected(){
        if(!legacy.getExtras().getBoolean("android.media.browse.SEARCH_SUPPORTED")){fail("Search/style extras missing");return;}
        children(legacy.getRoot(),root->{
          if(root.size()!=4||root.stream().noneMatch(x->x.getMediaId().equals("home"))||root.stream().noneMatch(x->x.getMediaId().equals("library"))){fail("Root tabs missing");return;}
          children("home",home->{MediaBrowser.MediaItem shelf=home.stream().filter(x->"Car recommendations".contentEquals(x.getDescription().getTitle())).findFirst().orElse(null);if(shelf==null){fail("Recommendations missing");return;}
            children(shelf.getMediaId(),songs->{if(songs.size()!=3||!songs.get(1).isPlayable()){fail("Recommendation songs missing");return;}
              children("library",library->{if(library.stream().noneMatch(x->x.getMediaId().equals("playlists"))){fail("Library missing");return;}
                children("playlist:car-probe",playlist->{if(playlist.size()!=3){fail("Playlist contents missing");return;}
                  new android.media.session.MediaController(LegacyProbeActivity.this,legacy.getSessionToken()).getTransportControls().playFromMediaId(playlist.get(1).getMediaId(),null);observe();
                });
              });
            });
          });
        });
      }
    },null);legacy.connect();handler.postDelayed(()->fail("Legacy browse request timed out"),35000);
  }
  private void children(String id,java.util.function.Consumer<List<MediaBrowser.MediaItem>> next){legacy.subscribe(id,new MediaBrowser.SubscriptionCallback(){@Override public void onChildrenLoaded(String parent,List<MediaBrowser.MediaItem> children){legacy.unsubscribe(id,this);next.accept(children);}@Override public void onError(String parent){fail("Legacy subscribe failed: "+parent);}});}
  private void observe(){pending=new androidx.media3.session.MediaBrowser.Builder(this,new SessionToken(this,new ComponentName("app.cadence.player","app.cadence.music.PlaybackService"))).buildAsync();pending.addListener(()->{try{poll();}catch(Exception e){fail(e.toString());}},handler::post);}
  private void poll(){if(finished)return;try{var browser=pending.get();if(browser.getMediaItemCount()==3&&browser.getCurrentMediaItemIndex()==1){finishResult(true,"External legacy client: root, Home recommendations, Library, playlist contents and selected-song queue expansion passed");return;}handler.postDelayed(this::poll,100);}catch(Exception e){fail(e.toString());}}
  private void finishResult(boolean passed,String message){if(finished)return;finished=true;try(var out=openFileOutput("legacy-result.json",0)){out.write(new org.json.JSONObject().put("passed",passed).put("message",message).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception ignored){}if(legacy!=null)legacy.disconnect();if(pending!=null)MediaController.releaseFuture(pending);finish();}
}
