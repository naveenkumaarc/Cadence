package app.cadence.music;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.MusicCatalog;
import app.cadence.core.Podcasts;
import app.cadence.core.Track;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real native views, Media3 seek behaviour and compatible local history. */
@RunWith(AndroidJUnit4.class)
public class RedesignIntegrationTest {
  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();

  private Object field(MainActivity activity, String name) throws Exception {
    Field f = MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity);
  }
  private void call(MainActivity activity, String name) throws Exception {
    Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity);
  }
  private TextView findText(View view, String value) {
    if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
    if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
      TextView found = findText(((ViewGroup)view).getChildAt(i), value); if(found!=null)return found;
    }
    return null;
  }
  private void onMain(Checked action) {
    instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Exception e) { throw new RuntimeException(e); } });
  }
  private interface Checked { void run() throws Exception; }
  private void screenshot(String name) throws Exception {
    instrumentation.waitForIdleSync(); Thread.sleep(300);
    Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
    File file = new File(context.getFilesDir(), name + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    }
    bitmap.recycle();
  }

  @Test public void historyPersistsDeduplicatesAndRemovesWithoutChangingLibrary() throws Exception {
    SearchHistory history = new SearchHistory(context); history.clear();
    Library before = new Library(context); int playlists = before.playlists.size();
    Track first = new Track("A song", "An artist", "https://music.youtube.com/watch?v=abcdefghijk", "", 200);
    history.remember(first); history.remember(first);
    assertEquals(1, new SearchHistory(context).entries().size());
    for(int i=0;i<35;i++) history.remember(new Track("Song " + i, "Artist", "file:///track"+i, "", 60));
    assertEquals(30, history.entries().size()); assertEquals("Song 34", history.entries().get(0).title);
    history.remove(history.entries().get(0)); assertEquals("Song 33", history.entries().get(0).title);
    history.clear(); assertTrue(history.entries().isEmpty());
    assertEquals(playlists, new Library(context).playlists.size());
  }

  @Test public void nativeScreensAndPlayerSeekKeepElapsedAndRemainingInSync() throws Exception {
    if (android.os.Build.VERSION.SDK_INT >= 33)
      instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
    // Public CI uses synthetic data. Optional private migration inputs stay on the test device.
    String migration = InstrumentationRegistry.getArguments().getString("migrationBackup");
    String expectedPlaylist = InstrumentationRegistry.getArguments().getString("migrationPlaylist", "Migration fixture");
    if (migration == null) {
      try (java.io.InputStream in = instrumentation.getContext().getAssets().open("synthetic-backup-v2.json")) {
        migration = new String(Podcasts.readLimited(in, 1024*1024), StandardCharsets.UTF_8);
      }
    }
    Backup.restore(context, new org.json.JSONObject(migration), new Library(context), PodcastStore.get(context));
    assertTrue(new Library(context).playlists.stream().anyMatch(p -> p.name.equals(expectedPlaylist)));
    // Offline fixtures are written only to the disposable test installation.
    MusicCatalog.Home home = new MusicCatalog.Home();
    java.nio.file.Files.write(new File(context.getFilesDir(), "discovery.json").toPath(), MusicCatalog.serialize(home).getBytes(StandardCharsets.UTF_8));
    java.nio.file.Files.write(new File(context.getFilesDir(), "discovery-English.json").toPath(), MusicCatalog.serialize(home).getBytes(StandardCharsets.UTF_8));
    Preferences.get(context).edit().putString("languages", "English").putBoolean("autoplay", false).commit();
    SearchHistory history = new SearchHistory(context); history.clear();
    for (String title : List.of("Right Round", "Uyire Un Uyirena", "Pandiyannin Rajyathil", "A Little Taste"))
      history.remember(new Track(title, "Your recent music", "file:///"+title, "", 210));
    MusicCatalog.Entry album = new MusicCatalog.Entry(); album.kind="album"; album.title="Manmadhan"; album.artist="Yuvan Shankar Raja"; album.browseId="test-album"; history.remember(album);
    MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    try {
      MediaController[] controller = {null};
      for(int i=0;i<150 && controller[0]==null;i++) {
        onMain(() -> controller[0]=(MediaController)field(activity,"controller")); Thread.sleep(100);
      }
      assertNotNull(controller[0]);
      onMain(() -> {
        call(activity,"showSearch"); View root=activity.getWindow().getDecorView();
        assertNotNull(findText(root,"Recents")); assertNotNull(findText(root,"Songs"));
        assertNotNull(findText(root,"Albums")); assertNotNull(findText(root,"Artists"));
      });
      screenshot("search");
      onMain(() -> {
        call(activity,"settings"); View root=activity.getWindow().getDecorView();
        assertNotNull(findText(root,"Save backup")); assertNotNull(findText(root,"Restore backup"));
        TextView autoplay=findText(root,"Autoplay"); assertNotNull(autoplay);
        ViewGroup row=(ViewGroup)autoplay.getParent().getParent(); row.performClick();
        assertTrue(Preferences.get(context).getBoolean("autoplay",false));
        row.performClick(); assertFalse(Preferences.get(context).getBoolean("autoplay",true));
      });
      screenshot("settings");
      File wave = new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS), "redesign-test.wav");
      wave.getParentFile().mkdirs();
      int samples = 44100*20;
      ByteBuffer buffer=ByteBuffer.allocate(44+samples*2).order(ByteOrder.LITTLE_ENDIAN);
      buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36+samples*2)
          .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1).putShort((short)1)
          .putInt(44100).putInt(88200).putShort((short)2).putShort((short)16)
          .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples*2);
      java.nio.file.Files.write(wave.toPath(),buffer.array());
      Track track=new Track("A moment to unwind", "Cadence · Your music", wave.toURI().toString(), "",20);
      Library library=(Library)field(activity,"library");
      library.playlists.get(0).tracks.add(new Track("Evening light",track.artist,"file:///another.wav","",180));
      onMain(() -> {
        Method mediaItem=MainActivity.class.getDeclaredMethod("mediaItem", Track.class);mediaItem.setAccessible(true);
        controller[0].setMediaItem((androidx.media3.common.MediaItem)mediaItem.invoke(activity,track));controller[0].prepare();
      });
      boolean[] ready={false};
      for(int i=0;i<150 && !ready[0];i++) {
        onMain(() -> ready[0]=controller[0].getPlaybackState()==Player.STATE_READY);Thread.sleep(100);
      }
      assertTrue("Local audio must be seekable",ready[0]);
      onMain(() -> { controller[0].seekTo(5000); call(activity,"openPlayer"); });
      Thread.sleep(300);
      onMain(() -> {
        call(activity,"updatePlayer");
        assertEquals("0:05", ((TextView)field(activity,"playerTime")).getText().toString());
        assertEquals("−0:15", ((TextView)field(activity,"playerRemaining")).getText().toString());
        assertTrue(((android.widget.SeekBar)field(activity,"playerSeek")).isEnabled());
        assertNotNull(findText((View)field(activity,"playerRelated"),"Related songs"));
        assertNotNull(findText((View)field(activity,"playerRelated"),"Evening light"));
      });
      screenshot("player");
      Podcasts.Show show=new Podcasts.Show();show.title="Wild South Raiders Podcast";show.feedUrl="https://example.org/test-feed";
      for(int i=0;i<3;i++) {
        File audio=new File(wave.getParentFile(),"redesign-podcast-"+i+".wav");
        java.nio.file.Files.copy(wave.toPath(),audio.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Podcasts.Episode e=new Podcasts.Episode();e.id="redesign-episode-"+i;
        e.title=i==0?"A conversation worth listening to":"Another story · Episode "+i;
        e.show=show.title;e.audioUrl="https://example.org/redesign-podcast-"+i+".wav";e.duration=20;show.episodes.add(e);
      }
      PodcastStore.get(context).preview(show);
      onMain(() -> {
        Method mediaItem=MainActivity.class.getDeclaredMethod("mediaItem",Track.class);mediaItem.setAccessible(true);
        androidx.media3.common.MediaItem podcastItem=(androidx.media3.common.MediaItem)mediaItem.invoke(activity,show.episodes.get(0).track());
        controller[0].setMediaItem(podcastItem.buildUpon().setUri(new File(wave.getParentFile(),"redesign-podcast-0.wav").toURI().toString()).build());
        controller[0].prepare();
      });
      boolean[] podcastReady={false};
      for(int i=0;i<300 && !podcastReady[0];i++) {
        onMain(() -> {
          call(activity,"updatePlayer");
          podcastReady[0]="PLAYING FROM PODCAST".contentEquals(((TextView)field(activity,"playerContext")).getText());
        });
        Thread.sleep(100);
      }
      assertTrue("Podcast metadata must reach the controller",podcastReady[0]);
      onMain(() -> {
        assertEquals("PLAYING FROM PODCAST",((TextView)field(activity,"playerContext")).getText().toString());
        assertNotNull(findText((View)field(activity,"playerRelated"),"More episodes & podcasts"));
        assertNotNull(findText((View)field(activity,"playerRelated"),"Another story · Episode 1"));
        assertEquals(View.GONE,((View)field(activity,"playerPrevious")).getVisibility());
      });
      screenshot("podcast-player");
    } finally { onMain(activity::finish); instrumentation.waitForIdleSync(); }
  }
}
