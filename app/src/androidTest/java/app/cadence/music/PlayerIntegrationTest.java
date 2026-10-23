package app.cadence.music;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.*;
import androidx.media3.session.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Runs against Android's actual XML provider, Media3 service and audio decoder. */
@RunWith(AndroidJUnit4.class)
public class PlayerIntegrationTest {
  private Context context;
  private MediaController controller;
  private com.google.common.util.concurrent.ListenableFuture<MediaController> pending;
  private File wave;

  private void main(Runnable r) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
  }

  private void until(Callable<Boolean> condition) throws Exception {
    long deadline = System.currentTimeMillis() + 30000;
    while (System.currentTimeMillis() < deadline) {
      final boolean[] done = {false};
      main(
          () -> {
            try {
              done[0] = condition.call();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      if (done[0]) return;
      Thread.sleep(100);
    }
    fail("Timed out waiting for the player");
  }

  @Before
  public void connect() throws Exception {
    context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    wave =
        new File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS),
            "integration.wav");
    wave.getParentFile().mkdirs();
    int count = 44100 * 20;
    ByteBuffer buffer = ByteBuffer.allocate(44 + count * 2).order(ByteOrder.LITTLE_ENDIAN);
    buffer
        .put("RIFF".getBytes(StandardCharsets.US_ASCII))
        .putInt(36 + count * 2)
        .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII))
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(44100)
        .putInt(88200)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes(StandardCharsets.US_ASCII))
        .putInt(count * 2);
    for (int i = 0; i < count; i++)
      buffer.putShort((short) (Math.sin(2 * Math.PI * 440 * i / 44100) * 100));
    try (FileOutputStream out = new FileOutputStream(wave)) {
      out.write(buffer.array());
    }
    connectController();
    Preferences.get(context).edit().putInt("crossfade",0).commit();
    main(
        () -> {
          controller.stop();
          controller.clearMediaItems();
          controller.setShuffleModeEnabled(false);
          controller.setRepeatMode(Player.REPEAT_MODE_OFF);
        });
  }

  private void connectController() throws Exception {
    main(() -> pending = new MediaController.Builder(context,
        new SessionToken(context, new ComponentName(context, PlaybackService.class))).buildAsync());
    controller = pending.get(20, TimeUnit.SECONDS);
  }

  @SuppressWarnings("deprecation")
  private android.app.ActivityManager.RunningServiceInfo playbackService() {
    var manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    for (var service : manager.getRunningServices(Integer.MAX_VALUE))
      if (service.service.getClassName().equals(PlaybackService.class.getName())) return service;
    return null;
  }

  private void headsetPlay() {
    var manager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    manager.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY));
    manager.dispatchMediaKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,
        android.view.KeyEvent.KEYCODE_MEDIA_PLAY));
  }

  private void stopDisconnectedService() throws Exception {
    main(() -> MediaController.releaseFuture(pending));
    until(() -> !controller.isConnected());
    pending = null;
    controller = null;
    context.stopService(new android.content.Intent(context, PlaybackService.class));
    until(() -> playbackService() == null);
    // Service bookkeeping disappears before the platform media session has
    // finished releasing. Wait for Android's routing state as well.
    var automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    boolean released = false;
    for (int i=0; i<100 && !released; i++) {
      try (var input = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
          automation.executeShellCommand("dumpsys media_session"))) {
        released = !new String(input.readAllBytes(), StandardCharsets.UTF_8)
            .contains(context.getPackageName() + "/androidx.media3.session.id.");
      }
      if (!released) Thread.sleep(100);
    }
    assertTrue("The platform must release the old session before testing cold resumption", released);
  }

  @After
  public void close() {
    main(
        () -> {
          if (controller != null && controller.isConnected()) {
            controller.pause();
            controller.clearMediaItems();
          }
          if (pending != null) MediaController.releaseFuture(pending);
        });
    if (wave != null) wave.delete();
  }

  private Track track(String title) {
    Track t = new Track(title, "Integration test", Uri.fromFile(wave).toString(), "", 20);
    t.podcast = true;
    t.episodeId = "test:" + title;
    return t;
  }

  private MediaItem item(String title) throws Exception {
    Track t = track(title);
    Bundle extras = new Bundle();
    extras.putString("track", Library.encode(t).toString());
    return new MediaItem.Builder()
        .setUri(t.sourceUrl)
        .setMediaId(title)
        .setMediaMetadata(
            new MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(t.artist)
                .setExtras(extras)
                .build())
        .build();
  }

  private void enqueue(String title, boolean next) throws Exception {
    Bundle args = new Bundle();
    args.putString("track", Library.encode(track(title)).toString());
    args.putBoolean("next", next);
    command(PlaybackService.ENQUEUE, args);
  }

  private void command(String action, Bundle args) throws Exception {
    final com.google.common.util.concurrent.ListenableFuture<SessionResult>[] result =
        new com.google.common.util.concurrent.ListenableFuture[1];
    main(
        () ->
            result[0] =
                controller.sendCustomCommand(new SessionCommand(action, Bundle.EMPTY), args));
    assertEquals(SessionResult.RESULT_SUCCESS, result[0].get(10, TimeUnit.SECONDS).resultCode);
  }

  private String nextTitle() {
    int next = controller.getNextMediaItemIndex();
    return next == C.INDEX_UNSET
        ? ""
        : String.valueOf(controller.getMediaItemAt(next).mediaMetadata.title);
  }

  @Test
  public void queuePriorityShuffleMoveAndRemoveWork() throws Exception {
    List<MediaItem> base = List.of(item("A"), item("B"), item("C"));
    main(
        () -> {
          controller.setMediaItems(base);
          controller.prepare();
          controller.play();
        });
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 300);
    enqueue("Next", true);
    enqueue("Later", false);
    main(
        () -> {
          assertEquals("Next", nextTitle());
          controller.setShuffleModeEnabled(true);
        });
    until(() -> nextTitle().equals("Next"));
    main(() -> controller.seekToNextMediaItem());
    until(() -> String.valueOf(controller.getMediaMetadata().title).equals("Next"));
    main(() -> assertEquals("Later", nextTitle()));
    main(() -> controller.setShuffleModeEnabled(false));
    int[] indices = {-1, -1};
    main(
        () -> {
          for (int i = 0; i < controller.getMediaItemCount(); i++) {
            String title = String.valueOf(controller.getMediaItemAt(i).mediaMetadata.title);
            if (title.equals("B")) indices[0] = i;
            if (title.equals("Later")) indices[1] = i;
          }
        });
    Bundle move = new Bundle();
    move.putInt("from", indices[0]);
    move.putInt("to", indices[1]);
    command(PlaybackService.MOVE, move);
    main(() -> assertEquals("B", nextTitle()));
    main(
        () -> {
          controller.removeMediaItem(controller.getNextMediaItemIndex());
          assertEquals("Later", nextTitle());
        });
  }

  @Test
  public void localAudioDecodesAndPodcastProgressAndSpeedPersist() throws Exception {
    MediaItem item = item("Progress");
    main(
        () -> {
          controller.setMediaItem(item);
          controller.prepare();
          controller.play();
          controller.setPlaybackSpeed(1.5f);
        });
    // The controller estimates position across processes; leave room before
    // checking the decoder's checkpoint at the one-second boundary.
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 1500);
    main(() -> controller.pause());
    until(() -> PodcastStore.get(context).progress("test:Progress") >= 1000);
    assertEquals(
        1.5f, context.getSharedPreferences("playback", 0).getFloat("podcastSpeed", 1f), 0.01);
  }

  @Test
  public void podcastPreviewRequiresExplicitSubscription() throws Exception {
    PodcastStore store=PodcastStore.get(context);
    var show=Podcasts.parseFeed("https://example.com/preview-test",("<rss><channel><title>Preview</title><item><guid>one</guid><title>Episode</title><enclosure url='https://example.com/one.mp3' type='audio/mpeg'/></item></channel></rss>").getBytes(StandardCharsets.UTF_8));
    store.preview(show);
    assertFalse(store.subscribed(show.feedUrl));
    store.progress(show.episodes.get(0).id,63000,false);
    assertTrue(store.knownShows().stream().anyMatch(x->x.feedUrl.equals(show.feedUrl)));
    store.put(show); assertTrue(store.subscribed(show.feedUrl));
    store.remove(show); assertFalse(store.subscribed(show.feedUrl));
    assertEquals(63000,store.progress(show.episodes.get(0).id));
  }

  @Test
  public void backupMergePreservesDuplicatesAndProgressWithoutAddingPreviewToLibrary() throws Exception {
    Library library=new Library(context);
    Library.Playlist p=new Library.Playlist("Backup test"); p.id="backup-test";
    p.tracks.add(track("Duplicate")); p.tracks.add(track("Duplicate")); library.playlists.add(p); library.save();
    var store=PodcastStore.get(context); store.progress("backup-episode",78000,false);
    org.json.JSONObject backup=Backup.create(context,library,store);
    library.playlists.remove(p); library.save(); store.progress("backup-episode",0,false);
    Backup.restore(context,backup,library,store); Backup.restore(context,backup,library,store);
    var restored=library.playlists.stream().filter(x->x.id.equals("backup-test")).findFirst().orElseThrow();
    assertEquals(2,restored.tracks.size()); assertEquals(78000,store.progress("backup-episode"));
  }

  @Test
  public void mediaBrowserExposesSavedPlaylistsAndPodcasts() throws Exception {
    Library lib=new Library(context); Library.Playlist p=new Library.Playlist("Car test");p.id="car-test";p.tracks.add(track("Car song"));lib.playlists.add(p);lib.save();
    var show=Podcasts.parseFeed("https://example.com/car-test","<rss><channel><title>Car show</title><item><guid>a</guid><title>Car episode</title><enclosure url='https://example.com/one.mp3' type='audio/mpeg'/></item></channel></rss>".getBytes(StandardCharsets.UTF_8));
    PodcastStore.get(context).put(show);
    final com.google.common.util.concurrent.ListenableFuture<MediaBrowser>[] browserFuture=new com.google.common.util.concurrent.ListenableFuture[1];
    main(()->browserFuture[0]=new MediaBrowser.Builder(context,new SessionToken(context,new ComponentName(context,PlaybackService.class))).buildAsync());
    MediaBrowser browser=browserFuture[0].get(20,TimeUnit.SECONDS);
    final com.google.common.util.concurrent.ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>[] result=new com.google.common.util.concurrent.ListenableFuture[1];
    main(()->result[0]=browser.getChildren("root",0,20,null));
    assertEquals(4,result[0].get(10,TimeUnit.SECONDS).value.size());
    main(()->result[0]=browser.getChildren("playlist:car-test",0,20,null));
    assertEquals("Car song",String.valueOf(result[0].get(10,TimeUnit.SECONDS).value.get(0).mediaMetadata.title));
    main(()->result[0]=browser.getChildren("podcasts",0,20,null));
    assertTrue(result[0].get(10,TimeUnit.SECONDS).value.stream().anyMatch(x->String.valueOf(x.mediaMetadata.title).equals("Car show")));
    main(()->MediaController.releaseFuture(browserFuture[0]));
  }

  private MediaItem music(String title) throws Exception {
    Track t=track(title); t.podcast=false;
    Bundle extras=new Bundle();extras.putString("track",Library.encode(t).toString());
    return new MediaItem.Builder().setMediaId(title).setUri(t.sourceUrl).setMediaMetadata(new MediaMetadata.Builder().setTitle(title).setExtras(extras).build()).build();
  }

  @Test
  public void crossfadeAdvancesIncomingAudioAndPauseStopsIt() throws Exception {
    Preferences.get(context).edit().putInt("crossfade",10).commit();
    List<MediaItem> songs=List.of(music("Fade A"),music("Fade B"));
    main(()-> { controller.setMediaItems(songs); controller.prepare(); controller.play(); });
    until(()->controller.isPlaying() && controller.getDuration()>0);
    main(()->controller.seekTo(10500));
    until(()->controller.getVolume()<.95f);
    until(()->controller.getCurrentMediaItemIndex()==1);
    main(()-> {
      assertTrue("Incoming decoder must already have advanced during overlap",controller.getCurrentPosition()>2500);
      controller.pause();
    });
    long[] position={0};main(()->position[0]=controller.getCurrentPosition());
    Thread.sleep(600);
    main(()-> { assertFalse(controller.isPlaying()); assertTrue(Math.abs(controller.getCurrentPosition()-position[0])<200); assertEquals(1f,controller.getVolume(),.01f); });
    Preferences.get(context).edit().putInt("crossfade",0).commit();
  }

  @Test
  public void restoringQueueKeepsPositionPausedUntilPlay() throws Exception {
    org.json.JSONArray items=new org.json.JSONArray().put(Library.encode(track("Resume A"))).put(Library.encode(track("Resume B")));
    org.json.JSONObject queue=new org.json.JSONObject().put("items",items).put("index",1).put("position",12345).put("repeat",Player.REPEAT_MODE_OFF).put("shuffle",false).put("order",new org.json.JSONArray().put(0).put(1));
    Bundle args=new Bundle();args.putString("queue",queue.toString());command(PlaybackService.RESTORE,args);
    until(()->controller.getMediaItemCount()==2);
    main(()->{assertEquals(1,controller.getCurrentMediaItemIndex());assertEquals(12345,controller.getCurrentPosition());assertFalse(controller.getPlayWhenReady());controller.prepare();controller.play();});
    until(()->controller.isPlaying() && controller.getCurrentPosition()>12500);
  }

  @Test
  public void widgetUsesPodcastSkipPreferencesAndKeepsPauseState() throws Exception {
    MediaItem episode=item("Widget");
    main(()->{controller.setMediaItem(episode);controller.prepare();});
    until(()->controller.getPlaybackState()==Player.STATE_READY);
    main(()->controller.seekTo(18000));
    until(()->controller.getCurrentPosition()>=18000);
    Preferences.get(context).edit().putInt("skipBack",15).commit();
    context.getSharedPreferences("widget",0).edit().putBoolean("podcast",true).commit();
    context.sendBroadcast(new android.content.Intent(context,CadenceWidget.class).setAction("app.cadence.WIDGET_CONTROL").putExtra("control","back"));
    until(()->controller.getCurrentPosition()<=3100);
    main(()->assertFalse(controller.getPlayWhenReady()));
    assertTrue(android.appwidget.AppWidgetManager.getInstance(context).getInstalledProviders().stream().anyMatch(p->p.provider.getClassName().equals(CadenceWidget.class.getName())));
  }

  @Test
  public void carSelectionKeepsTheRequestedSongAndPlaylist() throws Exception {
    Library library=new Library(context);library.playlists.removeIf(p->p.id.equals("car-probe"));
    Library.Playlist playlist=new Library.Playlist("Car probe");playlist.id="car-probe";
    for(int i=0;i<3;i++) {Track t=track("Auto "+i);t.podcast=false;playlist.tracks.add(t);}
    library.playlists.add(playlist);library.save();
    AutoLibrary.Selection selected=new AutoLibrary(context).selection("track:car-probe:1");
    assertEquals(3,selected.items.size());assertEquals(1,selected.index);
    assertEquals("Auto 1",String.valueOf(selected.items.get(selected.index).mediaMetadata.title));
    assertEquals("Auto 2",String.valueOf(selected.items.get(2).mediaMetadata.title));
  }

  @Test
  public void podcastCarSelectionRetainsProgressAndEpisodeOrder() throws Exception {
    var show=Podcasts.parseFeed("https://example.com/car-resume",("<rss><channel><title>Car resume</title>"+
        "<item><guid>first</guid><title>First</title><enclosure url='https://example.com/first.mp3' type='audio/mpeg'/></item>"+
        "<item><guid>second</guid><title>Second</title><enclosure url='https://example.com/second.mp3' type='audio/mpeg'/></item></channel></rss>").getBytes(StandardCharsets.UTF_8));
    PodcastStore store=PodcastStore.get(context);store.put(show);store.progress(show.episodes.get(1).id,45000,false);
    AutoLibrary auto=new AutoLibrary(context);
    String folder=auto.children("podcasts").stream().filter(item->String.valueOf(item.mediaMetadata.title).equals("Car resume")).findFirst().orElseThrow().mediaId;
    String selected=auto.children(folder).get(1).mediaId;
    AutoLibrary.Selection selection=auto.selection(selected);
    assertEquals(2,selection.items.size());assertEquals(1,selection.index);assertEquals(45000,selection.position);
  }

  @Test
  public void widgetRestartsAnEndedSongWithOnePress() throws Exception {
    MediaItem song=music("Ended widget");
    main(()->{controller.setMediaItem(song);controller.prepare();controller.play();});
    until(()->controller.isPlaying() && controller.getDuration()>0);
    main(()->controller.seekTo(19900));until(()->controller.getPlaybackState()==Player.STATE_ENDED);
    context.sendBroadcast(new android.content.Intent(context,CadenceWidget.class).setAction("app.cadence.WIDGET_CONTROL").putExtra("control","play"));
    until(()->controller.isPlaying() && controller.getCurrentPosition()<10000);
  }

  @Test
  public void audioFocusInterruptionPausesAndResumesPlayback() throws Exception {
    MediaItem song=music("Focus");
    main(()->{controller.setMediaItem(song);controller.prepare();controller.play();});until(()->controller.isPlaying());
    android.media.AudioManager manager=(android.media.AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
    var request=new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
      .setOnAudioFocusChangeListener(change->{},new android.os.Handler(android.os.Looper.getMainLooper())).build();
    try {
      assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED,manager.requestAudioFocus(request));
      until(()->!controller.getPlayWhenReady());
    } finally {manager.abandonAudioFocusRequest(request);}
    until(()->controller.isPlaying());
  }

  @Test
  public void pausedPodcastStaysForegroundBeyondThirtySecondsAndHeadsetResumes() throws Exception {
    MediaItem episode = item("Paused podcast");
    main(() -> {controller.setMediaItem(episode); controller.prepare(); controller.play();});
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 500);
    main(() -> {controller.seekTo(8000); controller.pause();});
    until(() -> playbackService() != null && playbackService().foreground);
    Thread.sleep(35000);
    main(() -> {
      assertTrue("Paused service must retain foreground grace", playbackService().foreground);
      assertFalse(controller.getPlayWhenReady());
      assertEquals(8000, controller.getCurrentPosition(), 200);
    });
    headsetPlay();
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 8200);
  }

  @Test
  public void permanentFocusLossKeepsMusicQueueForManualHeadsetResume() throws Exception {
    MediaItem song = music("Other app audio");
    main(() -> {controller.setMediaItem(song); controller.prepare(); controller.play();});
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 500);
    var manager = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    var request = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(new android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener(change -> {}, new android.os.Handler(android.os.Looper.getMainLooper())).build();
    try {
      assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request));
      until(() -> !controller.getPlayWhenReady());
      Thread.sleep(35000);
      main(() -> {
        assertTrue("Audio focus loss must keep the resume grace", playbackService().foreground);
        assertEquals("Other app audio", String.valueOf(controller.getMediaMetadata().title));
        assertEquals(1, controller.getMediaItemCount());
      });
    } finally { manager.abandonAudioFocusRequest(request); }
    main(() -> assertFalse("Permanent focus loss must not auto-resume over another app", controller.getPlayWhenReady()));
    headsetPlay();
    until(() -> controller.isPlaying());
  }

  @Test
  public void headsetPlayRestartsDestroyedServiceAtSavedPodcastPosition() throws Exception {
    List<MediaItem> queue = List.of(item("Cold A"), item("Cold B"));
    main(() -> {
      controller.setMediaItems(queue, 1, 10000); controller.setRepeatMode(Player.REPEAT_MODE_ALL);
      controller.prepare(); controller.play(); controller.setPlaybackSpeed(1.5f);
    });
    until(() -> controller.isPlaying() && controller.getCurrentPosition() > 10500);
    main(() -> controller.pause());
    File saved = new File(context.getFilesDir(), "queue.json");
    until(() -> {
      var state = new org.json.JSONObject(new String(new android.util.AtomicFile(saved).readFully(), StandardCharsets.UTF_8));
      return state.getInt("index") == 1 && state.getLong("position") >= 10500;
    });
    stopDisconnectedService();
    // Dispatch through Android's media-button routing; no Activity or controller is open.
    headsetPlay();
    until(() -> playbackService() != null && playbackService().foreground);
    connectController();
    until(() -> controller.isPlaying());
    main(() -> {
      assertEquals(2, controller.getMediaItemCount());
      assertEquals(1, controller.getCurrentMediaItemIndex());
      assertEquals("Cold B", String.valueOf(controller.getMediaMetadata().title));
      assertTrue(controller.getCurrentPosition() >= 10500);
      assertEquals(Player.REPEAT_MODE_ALL, controller.getRepeatMode());
      assertEquals(1.5f, controller.getPlaybackParameters().speed, .01f);
    });
  }

  @Test
  public void emptyHistoryDoesNotStartForegroundServiceFromHeadset() throws Exception {
    File saved = new File(context.getFilesDir(), "queue.json");
    until(() -> saved.exists() && new org.json.JSONObject(new String(
        new android.util.AtomicFile(saved).readFully(), StandardCharsets.UTF_8)).getJSONArray("items").length() == 0);
    stopDisconnectedService();
    var event = new android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON)
        .setComponent(new ComponentName(context, PlaybackButtonReceiver.class))
        .putExtra(android.content.Intent.EXTRA_KEY_EVENT, new android.view.KeyEvent(
            android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY));
    context.sendBroadcast(event);
    Thread.sleep(1500);
    main(() -> assertNull(playbackService()));
  }

  @Test
  public void feedsAndOpmlParseOnAndroidXmlProvider() throws Exception {
    var show =
        Podcasts.parseFeed(
            "https://example.com/feed",
            "<rss><channel><title>Show</title><item><guid>x</guid><title>Episode</title><enclosure type='audio/mpeg' url='https://example.com/one.mp3'/></item></channel></rss>"
                .getBytes(StandardCharsets.UTF_8));
    assertEquals(1, show.episodes.size());
    assertEquals(
        1,
        Podcasts.opml(
                "<opml><body><outline xmlUrl='https://example.com/rss'/></body></opml>"
                    .getBytes(StandardCharsets.UTF_8))
            .size());
  }
}
