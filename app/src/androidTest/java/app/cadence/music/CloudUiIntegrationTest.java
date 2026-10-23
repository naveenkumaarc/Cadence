package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;
import androidx.media3.common.*;
import androidx.media3.session.MediaController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class CloudUiIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private interface Checked { void run() throws Exception; }
  private void main(Checked action) { instrumentation.runOnMainSync(()-> { try { action.run(); } catch(Exception e) { throw new RuntimeException(e); } }); }
  private View described(View view,String description) {
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) { View found=described(group.getChildAt(i),description);if(found!=null)return found; } return null;
  }
  private boolean text(View view,String text) {
    if(view instanceof TextView label && text.contentEquals(label.getText())) return true;
    if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) if(text(group.getChildAt(i),text)) return true;return false;
  }
  private void until(Callable<Boolean> check) throws Exception {
    for(int i=0;i<300;i++) { boolean[] ready={false};main(()->ready[0]=check.call());if(ready[0])return;Thread.sleep(50); }fail("Native UI did not reach the requested playback state");
  }
  @Test public void episodeRowsShowIndividualProgressAndRoundControlTogglesRealPlayback() throws Exception {
    Context context=instrumentation.getTargetContext();
    if(android.os.Build.VERSION.SDK_INT>=33) instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);
    MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    MediaController[] controller={null};Field field=MainActivity.class.getDeclaredField("controller");field.setAccessible(true);
    until(()-> {controller[0]=(MediaController)field.get(activity);return controller[0]!=null;});
    org.json.JSONObject[] before={null};main(()->before[0]=CloudData.localSnapshot(context));
    File audio=new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS),"episode-ui-fixture.wav");
    try(InputStream input=instrumentation.getContext().getAssets().open("offline-fixture.wav");OutputStream output=new FileOutputStream(audio)) { byte[] bytes=new byte[8192];int count;while((count=input.read(bytes))!=-1)output.write(bytes,0,count); }
    Podcasts.Show show=new Podcasts.Show();show.title="Episode progress test";show.feedUrl="https://example.com/synthetic-feed.xml";
    for(int i=3;i<=4;i++) { Podcasts.Episode e=new Podcasts.Episode();e.id="ui-episode-"+i;e.title="Episode "+i;e.show=show.title;e.audioUrl="https://example.com/episode-"+i+".mp3";e.duration=3600;show.episodes.add(e); }
    try {
      main(()-> {
        PodcastStore.get(context).progress("ui-episode-3",1200000,false);PodcastStore.get(context).progress("ui-episode-4",2700000,false);
        Method render=MainActivity.class.getDeclaredMethod("showPodcast",Podcasts.Show.class);render.setAccessible(true);render.invoke(activity,show);
        View root=activity.getWindow().getDecorView();
        assertTrue(text(root,"20 min played · 40 min remaining"));assertTrue(text(root,"45 min played · 15 min remaining"));
        View play=described(root,"Resume Episode 3");assertTrue(play instanceof TransportButton);assertEquals(play.getLayoutParams().width,play.getLayoutParams().height);
        PodcastStore.get(context).progress("ui-episode-3",500,false);
        Track track=new Track("Episode 3",show.title,android.net.Uri.fromFile(audio).toString(),"",3);track.podcast=true;track.episodeId="ui-episode-3";
        Bundle extras=new Bundle();extras.putString("track",Library.encode(track).toString());
        controller[0].setMediaItem(new MediaItem.Builder().setMediaId(track.sourceUrl).setUri(track.sourceUrl)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setExtras(extras).build()).build());
        controller[0].prepare();controller[0].play();
      });
      until(()->controller[0].isPlaying() && described(activity.getWindow().getDecorView(),"Pause Episode 3")!=null);
      main(()->described(activity.getWindow().getDecorView(),"Pause Episode 3").performClick());
      until(()->!controller[0].getPlayWhenReady());
      main(()-> {
        assertNotNull(described(activity.getWindow().getDecorView(),"Resume Episode 3"));
        Method profile=MainActivity.class.getDeclaredMethod("showProfile");profile.setAccessible(true);profile.invoke(activity);
        assertTrue(text(activity.getWindow().getDecorView(),"Your profile"));assertNotNull(described(activity.getWindow().getDecorView(),"Open your profile"));
      });
    } finally {
      main(()-> { if(controller[0]!=null && controller[0].isConnected()) {controller[0].pause();controller[0].clearMediaItems();} activity.finish();CloudData.applyLocal(context,before[0]); });
      if(audio.exists()) assertTrue(audio.delete());
    }
  }
}
