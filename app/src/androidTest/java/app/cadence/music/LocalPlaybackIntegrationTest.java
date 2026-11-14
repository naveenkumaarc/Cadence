package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.os.*;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LocalPlaybackIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MediaController controller;private JSONObject before;private File local;
  private static String hash(String value)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
  private void main(Runnable action){instrumentation.runOnMainSync(action);}
  private void until(java.util.concurrent.Callable<Boolean> condition)throws Exception{for(int i=0;i<240;i++){boolean[] result={false};main(()->{try{result[0]=condition.call();}catch(Exception e){throw new RuntimeException(e);}});if(result[0])return;Thread.sleep(50);}fail("Playback did not become ready");}

  @Before public void setup()throws Exception{
    before=CloudData.localSnapshot(context);var future=new MediaController.Builder(context,new SessionToken(context,new ComponentName(context,PlaybackService.class))).buildAsync();controller=future.get(10,TimeUnit.SECONDS);
  }
  @After public void cleanup()throws Exception{
    if(controller!=null)main(()->{controller.pause();controller.clearMediaItems();});
    if(local!=null&&local.exists())local.delete();CloudData.applyLocal(context,before);
  }

  @Test public void downloadedEpisodeUsesLocalUriAndMissingFileFallsBackToStream()throws Exception{
    Podcasts.Show show=new Podcasts.Show();show.title="Offline fixture";show.feedUrl="https://example.invalid/feed";
    Podcasts.Episode episode=new Podcasts.Episode();episode.id="offline-local-first";episode.title="Offline episode";episode.show=show.title;
    episode.audioUrl="https://example.invalid/offline.mp3";episode.duration=3;show.episodes.add(episode);PodcastStore store=PodcastStore.get(context);store.preview(show);
    local=new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS),hash(episode.id)+".audio");
    try(InputStream input=instrumentation.getContext().getAssets().open("offline-fixture.wav");OutputStream output=new FileOutputStream(local)){input.transferTo(output);}
    assertTrue(store.localUri(episode.audioUrl).startsWith("file:"));
    Track stable=store.playable(episode);assertEquals(episode.audioUrl,stable.sourceUrl);
    android.os.Bundle extras=new android.os.Bundle();extras.putString("track",Library.encode(stable).toString());
    MediaItem item=new MediaItem.Builder().setMediaId(stable.sourceUrl).setUri(stable.sourceUrl).setMediaMetadata(new MediaMetadata.Builder().setTitle(stable.title).setExtras(extras).build()).build();
    main(()->{controller.setMediaItem(item);controller.prepare();controller.play();});
    until(()->controller.isPlaying()&&controller.getDuration()>0);
    main(()->{controller.pause();controller.clearMediaItems();});
    assertTrue(local.delete());assertNull(store.localUri(episode.audioUrl));
    assertEquals("Missing local audio must retain the stream fallback",episode.audioUrl,store.playable(episode).sourceUrl);
  }
}
