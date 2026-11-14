package app.cadence.music;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Process;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaLibraryService;
import androidx.media3.session.LibraryResult;
import com.google.common.collect.ImmutableList;
import app.cadence.core.HttpDownloader;
import app.cadence.core.Links;
import app.cadence.core.YouTube;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaLibraryService {
  private MediaLibrarySession session;
  private ExoPlayer player;
  private PodcastStore podcasts;
  private final java.util.concurrent.ExecutorService networkTasks = java.util.concurrent.Executors.newFixedThreadPool(2);
  private final java.util.concurrent.ExecutorService libraryTasks = java.util.concurrent.Executors.newSingleThreadExecutor();
  private boolean radioLoading;
  private long sleepAt;
  private boolean sleepEndOfTrack;
  private boolean disposed;
  private String radioRequested = "";
  private String radioContextSeed = "";
  private long radioRetryAt;
  private app.cadence.core.Track sessionFirstTrack;
  private final java.util.ArrayDeque<app.cadence.core.MoodAutoplay.Feedback> sessionFeedback = new java.util.ArrayDeque<>();
  private final java.util.ArrayDeque<String> recentSongs = new java.util.ArrayDeque<>();
  private final java.util.Map<String,Integer> completedSongs = new java.util.HashMap<>(), skippedSongs = new java.util.HashMap<>();
  private final java.util.Map<String,app.cadence.core.Track> heardSongs = new java.util.LinkedHashMap<>();
  private final java.util.Map<String,RadioCache> moodCache = new java.util.HashMap<>();
  private AutoLibrary autoLibrary;
  private final java.util.concurrent.atomic.AtomicBoolean carDiscoveryLoading=new java.util.concurrent.atomic.AtomicBoolean();
  private long carDiscoveryRetryAt;
  private DefaultMediaSourceFactory sourceFactory;
  private Crossfade crossfade;
  private android.media.AudioManager audioManager;
  private android.media.AudioFocusRequest focusRequest;
  private boolean focusHeld, resumeOnFocusGain;
  private long focusDeadline;
  private final Runnable focusAfterForeground = () -> {
    if (!disposed && player != null && player.getPlayWhenReady()) ensureFocus();
  };
  private float duck = 1f;
  private final Runnable playbackTick = new Runnable() {
    public void run() {
      if (sleepAt > 0 && android.os.SystemClock.elapsedRealtime() >= sleepAt) {
        sleepAt=0;sleepEndOfTrack=false;
        if(player!=null)player.pause();
      }
      if (focusHeld && crossfade != null && !sleepEndOfTrack) crossfade.tick();
      extendRadio();
      handler.postDelayed(this, 100);
    }
  };
  private boolean arranging = false;
  static final String ENQUEUE = "app.cadence.ENQUEUE",
      ARRANGE = "app.cadence.ARRANGE",
      MOVE = "app.cadence.MOVE",
      RESTORE = "app.cadence.RESTORE",
      SLEEP_TIMER = "app.cadence.SLEEP_TIMER",
      EQUALIZER = "app.cadence.EQUALIZER";
  private final android.os.Handler handler =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final java.util.concurrent.ExecutorService disk =
      java.util.concurrent.Executors.newSingleThreadExecutor();
  private android.util.AtomicFile savedQueue;
  private final Runnable checkpoint =
      new Runnable() {
        public void run() {
          saveProgress(false);
          saveQueue();
          publishWidget();
          handler.postDelayed(this, 5000);
        }
      };
  private final Map<String, Cached> urls = new ConcurrentHashMap<>();

  @Override
  public void onCreate() {
    super.onCreate();
    // Keep paused playback eligible for foreground resumption for Media3's bounded grace period.
    setForegroundServiceTimeoutMs(DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS);
    savedQueue = new android.util.AtomicFile(new java.io.File(getFilesDir(), "queue.json"));
    try {
      podcasts = PodcastStore.get(this);
    } catch (Exception ignored) {
    }
    var network =
        new OkHttpDataSource.Factory(HttpDownloader.MEDIA_CLIENT)
            .setDefaultRequestProperties(Map.of("User-Agent", HttpDownloader.USER_AGENT))
            .setContentTypePredicate(
                type ->
                    type == null || !type.toLowerCase(java.util.Locale.ROOT).contains("text/html"));
    var resolver =
        new ResolvingDataSource.Factory(
            new DefaultDataSource.Factory(this, network),
            spec -> {
              String url = spec.uri.toString();
              try {
                String local = SongDownloads.get(this).localUri(url);
                if (local != null) return spec.withUri(android.net.Uri.parse(local));
              } catch (Exception unavailable) { /* Keep the original source available. */ }
              if (!url.startsWith("https://www.youtube.com/watch?v=")) return spec;
              String key = Links.youtubeVideo(url);
              Cached cached = urls.get(key);
              if (cached == null || System.currentTimeMillis() - cached.at > 10 * 60_000) {
                try {
                  cached = new Cached(YouTube.resolve(key).streamUrl);
                  urls.put(key, cached);
                } catch (Exception e) {
                  throw new IOException("YouTube audio could not be loaded: " + e.getMessage(), e);
                }
              }
              return spec.withUri(android.net.Uri.parse(cached.url));
            });
    sourceFactory = new DefaultMediaSourceFactory(resolver);
    audioManager = (android.media.AudioManager)getSystemService(AUDIO_SERVICE);
    focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setOnAudioFocusChangeListener(change -> {
        if (player == null) return;
        if (change == android.media.AudioManager.AUDIOFOCUS_GAIN) {
          focusHeld = true; duck = 1f;
          if (resumeOnFocusGain) { resumeOnFocusGain = false; player.play(); }
          if (crossfade == null || crossfade.started == 0) player.setVolume(duck);
        } else if (change == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && !trackInfo(player.getCurrentMediaItem()).podcast) {
          duck = .2f; if (crossfade == null || crossfade.started == 0) player.setVolume(duck);
        } else {
          resumeOnFocusGain = change != android.media.AudioManager.AUDIOFOCUS_LOSS && player.getPlayWhenReady();
          player.pause(); if (crossfade != null) crossfade.cancel();
          if (!resumeOnFocusGain) abandonFocus();
        }
      }, handler).build();
    autoLibrary = new AutoLibrary(this);
    player = buildPlayer(true);
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        false);
    player.setHandleAudioBecomingNoisy(true);
    player.setWakeMode(C.WAKE_MODE_LOCAL);
    restoreQueue();
    attachListener(player);
    refreshRadioContext();
    crossfade = new Crossfade();
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    session =
        new MediaLibrarySession.Builder(this, new TransportPlayer(this,player),
                new MediaLibrarySession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession mediaSession, MediaSession.ControllerInfo controller) {
                    if (controller.getUid() != Process.myUid())
                      return new MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                          .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS).build();
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(mediaSession)
                        .setAvailableSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                                .buildUpon()
                                .add(
                                    new androidx.media3.session.SessionCommand(
                                        ENQUEUE, android.os.Bundle.EMPTY))
                                .add(
                                    new androidx.media3.session.SessionCommand(
                                        ARRANGE, android.os.Bundle.EMPTY))
                                .add(new androidx.media3.session.SessionCommand(RESTORE,android.os.Bundle.EMPTY))
                                .add(new androidx.media3.session.SessionCommand(SLEEP_TIMER,android.os.Bundle.EMPTY))
                                .add(new androidx.media3.session.SessionCommand(EQUALIZER,android.os.Bundle.EMPTY))
                                .add(
                                    new androidx.media3.session.SessionCommand(
                                        MOVE, android.os.Bundle.EMPTY))
                                .build())
                        .build();
                  }

                  @Override
                  public ListenableFuture<androidx.media3.session.SessionResult> onCustomCommand(
                      MediaSession mediaSession,
                      MediaSession.ControllerInfo controller,
                      androidx.media3.session.SessionCommand command,
                      android.os.Bundle args) {
                    if (controller.getUid() != Process.myUid())
                      return Futures.immediateFuture(
                          new androidx.media3.session.SessionResult(
                              androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED));
                    try {
                      if (crossfade != null) crossfade.cancel();
                      if (command.customAction.equals(RESTORE)) {
                        if (player.getMediaItemCount() != 0) throw new IllegalArgumentException("Queue already exists");
                        String json = args.getString("queue");
                        if (json == null) json = new String(new android.util.AtomicFile(new java.io.File(getFilesDir(),"restore-queue.json")).readFully(), java.nio.charset.StandardCharsets.UTF_8);
                        restoreQueue(new org.json.JSONObject(json));
                        new java.io.File(getFilesDir(),"restore-queue.json").delete();
                        saveQueue();
                      } else if (command.customAction.equals(ENQUEUE)) {
                        org.json.JSONObject json =
                            new org.json.JSONObject(args.getString("track", "{}"));
                        app.cadence.core.Track t = Library.decode(json);
                        android.os.Bundle extras = new android.os.Bundle();
                        extras.putString("track", json.toString());
                        extras.putBoolean("manualQueue", true);
                        var metadata =
                            new androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist)
                                .setExtras(extras);
                        if (!t.artwork.isEmpty())
                          metadata.setArtworkUri(android.net.Uri.parse(t.artwork));
                        MediaItem item =
                            new MediaItem.Builder()
                                .setMediaId(t.sourceUrl)
                                .setUri(t.sourceUrl)
                                .setMediaMetadata(metadata.build())
                                .build();
                        if (!allowed(item)) throw new IllegalArgumentException();
                        boolean empty = player.getMediaItemCount() == 0;
                        int at = empty ? 0 : player.getCurrentMediaItemIndex() + 1;
                        if (!args.getBoolean("next"))
                          while (at < player.getMediaItemCount()
                              && manual(player.getMediaItemAt(at))) at++;
                        player.addMediaItem(at, item);
                        prioritizeManual();
                        if (empty) {
                          if (t.podcast && podcasts != null)
                            player.seekTo(podcasts.progress(t.episodeId));
                          player.prepare();
                          player.play();
                        }
                        saveQueue();
                      } else if (command.customAction.equals(MOVE)) {
                        int from = args.getInt("from", -1), to = args.getInt("to", -1);
                        if (from < 0
                            || to < 0
                            || from >= player.getMediaItemCount()
                            || to >= player.getMediaItemCount()
                            || from == player.getCurrentMediaItemIndex()
                            || to == player.getCurrentMediaItemIndex())
                          throw new IllegalArgumentException();
                        java.util.ArrayList<Integer> order = playbackOrder();
                        int target = order.indexOf(to);
                        order.remove(Integer.valueOf(from));
                        order.add(target, from);
                        // An explicit drag defines the desired order, replacing earlier priority
                        // marks.
                        for (int i = 0; i < player.getMediaItemCount(); i++) {
                          MediaItem old = player.getMediaItemAt(i);
                          if (manual(old)) {
                            android.os.Bundle extras =
                                new android.os.Bundle(old.mediaMetadata.extras);
                            extras.putBoolean("manualQueue", false);
                            player.replaceMediaItem(
                                i,
                                old.buildUpon()
                                    .setMediaMetadata(
                                        old.mediaMetadata.buildUpon().setExtras(extras).build())
                                    .build());
                          }
                        }
                        if (player.getShuffleModeEnabled()) {
                          int[] indices = new int[order.size()];
                          for (int i = 0; i < indices.length; i++) indices[i] = order.get(i);
                          player.setShuffleOrder(
                              new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                                  indices, System.nanoTime()));
                        } else player.moveMediaItem(from, to);
                        saveQueue();
                      } else if (command.customAction.equals(ARRANGE)) {
                        int[] requested=args.getIntArray("order");
                        if(requested!=null) {
                          if(!app.cadence.core.QueueShuffle.valid(requested,player.getMediaItemCount()))
                            throw new IllegalArgumentException("Invalid queue order");
                          arranging=true;
                          try {
                            player.setShuffleOrder(new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(requested,System.nanoTime()));
                            player.setShuffleModeEnabled(args.getBoolean("shuffle",true));
                          } finally {arranging=false;}
                        }
                        prioritizeManual();
                        saveQueue();
                      } else if (command.customAction.equals(SLEEP_TIMER)) {
                        int minutes=args.getInt("minutes",0);
                        sleepAt=minutes>0?android.os.SystemClock.elapsedRealtime()+minutes*60_000L:0;
                        sleepEndOfTrack=args.getBoolean("endOfTrack",false);
                        if(crossfade!=null)crossfade.cancel();
                      } else if (command.customAction.equals(EQUALIZER)) {
                        int audioSession=player.getAudioSessionId();
                        if(audioSession==C.AUDIO_SESSION_ID_UNSET)throw new IllegalStateException("Audio session unavailable");
                        android.os.Bundle result=new android.os.Bundle();result.putInt("audioSession",audioSession);
                        return Futures.immediateFuture(new androidx.media3.session.SessionResult(
                            androidx.media3.session.SessionResult.RESULT_SUCCESS,result));
                      } else
                        return MediaLibrarySession.Callback.super.onCustomCommand(
                            mediaSession, controller, command, args);
                      return Futures.immediateFuture(
                          new androidx.media3.session.SessionResult(
                              androidx.media3.session.SessionResult.RESULT_SUCCESS));
                    } catch (Exception e) {
                      return Futures.immediateFuture(
                          new androidx.media3.session.SessionResult(
                              androidx.media3.session.SessionError.ERROR_BAD_VALUE));
                    }
                  }

                  @Override
                  public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onPlaybackResumption(
                      MediaSession mediaSession, MediaSession.ControllerInfo controller) {
                    // onCreate restores the saved queue, position, shuffle, repeat and podcast speed.
                    // Also serve this snapshot to Android's media resumption controls without playing
                    // until Media3 receives an explicit Play command.
                    java.util.ArrayList<MediaItem> items = new java.util.ArrayList<>();
                    for (int i = 0; i < player.getMediaItemCount(); i++) items.add(player.getMediaItemAt(i));
                    if (items.isEmpty()) return Futures.immediateFailedFuture(
                        new IllegalStateException("No saved playback to resume"));
                    return Futures.immediateFuture(new MediaSession.MediaItemsWithStartPosition(
                        items, player.getCurrentMediaItemIndex(), Math.max(0, player.getCurrentPosition())));
                  }

                  @Override
                  public ListenableFuture<LibraryResult<MediaItem>> onGetLibraryRoot(MediaLibrarySession s, MediaSession.ControllerInfo c, LibraryParams params) {
                    return Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary.folder("root","Cadence"),new LibraryParams.Builder().setExtras(AutoLibrary.styles()).build()));
                  }
                  @Override
                  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetChildren(MediaLibrarySession s, MediaSession.ControllerInfo c, String parent, int page, int pageSize, LibraryParams params) {
                    if(parent.equals("home"))requestCarDiscovery();
                    return libraryWork(() -> LibraryResult.ofItemList(AutoLibrary.page(autoLibrary.children(parent),page,pageSize),params));
                  }
                  @Override
                  public ListenableFuture<LibraryResult<Void>> onSubscribe(MediaLibrarySession s,MediaSession.ControllerInfo c,String parent,LibraryParams params) {
                    if(parent.equals("home"))requestCarDiscovery();
                    return libraryWork(()->{
                      if(!Boolean.TRUE.equals(autoLibrary.resolve(parent).mediaMetadata.isBrowsable))return LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE);
                      int count=autoLibrary.children(parent).size();handler.post(()->{if(!disposed)s.notifyChildrenChanged(c,parent,count,params);});return LibraryResult.ofVoid();
                    });
                  }
                  @Override
                  public ListenableFuture<LibraryResult<Void>> onUnsubscribe(MediaLibrarySession s,MediaSession.ControllerInfo c,String parent) {return Futures.immediateFuture(LibraryResult.ofVoid());}
                  @Override
                  public ListenableFuture<LibraryResult<MediaItem>> onGetItem(MediaLibrarySession s, MediaSession.ControllerInfo c, String id) {
                    return libraryWork(() -> LibraryResult.ofItem(autoLibrary.resolve(id),null));
                  }
                  @Override
                  public ListenableFuture<LibraryResult<Void>> onSearch(MediaLibrarySession s, MediaSession.ControllerInfo c, String query, LibraryParams params) {
                    var future=com.google.common.util.concurrent.SettableFuture.<LibraryResult<Void>>create();
                    libraryTasks.execute(() -> {
                      try {
                        int count=autoLibrary.search(query).size();
                        handler.post(() -> {
                          if(disposed) {future.cancel(false);return;}
                          s.notifySearchResultChanged(c,query,count,params);future.set(LibraryResult.ofVoid());
                        });
                      } catch(Exception e) {future.set(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE));}
                    });
                    return future;
                  }
                  @Override
                  public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> onGetSearchResult(MediaLibrarySession s, MediaSession.ControllerInfo c, String query, int page, int size, LibraryParams params) {
                    return libraryWork(() -> {
                      return LibraryResult.ofItemList(AutoLibrary.page(autoLibrary.search(query),page,size),params);
                    });
                  }
                  @Override
                  public ListenableFuture<MediaSession.MediaItemsWithStartPosition> onSetMediaItems(
                      MediaSession session,MediaSession.ControllerInfo controller,List<MediaItem> items,int startIndex,long startPositionMs) {
                    int selected=startIndex==C.INDEX_UNSET?0:startIndex;
                    if(controller.getUid()!=Process.myUid() && selected>=0 && selected<items.size()) {
                      String id=items.get(selected).mediaId;
                      if(items.get(selected).requestMetadata.searchQuery==null && (id.startsWith("track:") || id.startsWith("episode:") || id.startsWith("recommend:") || id.startsWith("collection-track:"))) {
                        if(crossfade!=null) crossfade.cancel();
                        radioRequested="";radioRetryAt=0;
                        var future=com.google.common.util.concurrent.SettableFuture.<MediaSession.MediaItemsWithStartPosition>create();
                        libraryTasks.execute(() -> {
                          try {
                            AutoLibrary.Selection selection=autoLibrary.selection(id);
                            for(MediaItem item:selection.items) if(!allowed(item)) throw new SecurityException("Unsupported library source");
                            future.set(new MediaSession.MediaItemsWithStartPosition(selection.items,selection.index,
                                startPositionMs==C.TIME_UNSET?selection.position:Math.max(0,startPositionMs)));
                          } catch(Exception e) {future.setException(e);}
                        });
                        return future;
                      }
                    }
                    return MediaLibrarySession.Callback.super.onSetMediaItems(session,controller,items,startIndex,startPositionMs);
                  }

                  @Override
                  public ListenableFuture<List<MediaItem>> onAddMediaItems(
                      MediaSession mediaSession,
                      MediaSession.ControllerInfo controller,
                      List<MediaItem> items) {
                    if (crossfade != null) crossfade.cancel();
                    radioRequested = "";
                    radioRetryAt = 0;
                    if (controller.getUid() != Process.myUid()) {
                      if (!items.isEmpty() && items.get(0).requestMetadata.searchQuery != null) {
                        String query = items.get(0).requestMetadata.searchQuery.trim();
                        if (query.isEmpty() || query.length() > 200) return Futures.immediateFailedFuture(new IllegalArgumentException("Enter a song or artist"));
                        var future = com.google.common.util.concurrent.SettableFuture.<List<MediaItem>>create();
                        networkTasks.execute(() -> {
                          try {
                            List<MediaItem> found = autoLibrary.search(query);
                            if (!found.isEmpty() && Boolean.TRUE.equals(found.get(0).mediaMetadata.isBrowsable)) found = autoLibrary.children(found.get(0).mediaId);
                            if (found.isEmpty()) {
                              List<app.cadence.core.Track> songs = app.cadence.core.MusicCatalog.songs(query);
                              if (songs.isEmpty()) throw new IllegalArgumentException("No songs found");
                              MediaItem item = AutoLibrary.item(songs.get(0),songs.get(0).sourceUrl);
                              android.os.Bundle extras = new android.os.Bundle(item.mediaMetadata.extras);
                              extras.putString("track",Library.encode(songs.get(0)).put("radio",true).toString());
                              found = List.of(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build());
                            }
                            future.set(found);
                          } catch (Exception e) { future.setException(e); }
                        });
                        return future;
                      }
                      var resolvedFuture=com.google.common.util.concurrent.SettableFuture.<List<MediaItem>>create();
                      libraryTasks.execute(()->{try{java.util.ArrayList<MediaItem> resolved=new java.util.ArrayList<>();for(MediaItem item:items){MediaItem known=autoLibrary.resolve(item.mediaId);if(!Boolean.TRUE.equals(known.mediaMetadata.isPlayable)||!allowed(known))throw new SecurityException();resolved.add(known);}resolvedFuture.set(resolved);}catch(Exception e){resolvedFuture.setException(new SecurityException("Unknown library item",e));}});return resolvedFuture;
                    }
                    for (MediaItem item : items) {
                      if (item.localConfiguration == null)
                        return Futures.immediateFailedFuture(
                            new IllegalArgumentException("Missing media source"));
                      String uri = item.localConfiguration.uri.toString();
                      if (!allowed(item))
                        return Futures.immediateFailedFuture(
                            new IllegalArgumentException("Unsupported media source"));
                    }
                    return Futures.immediateFuture(items);
                  }
                }).setSessionActivity(open)
            .build();
    handler.postDelayed(checkpoint, 5000);
    handler.post(playbackTick);
    publishWidget();
  }

  private void requestCarDiscovery() {
    if(disposed||!autoLibrary.refreshNeeded()||System.currentTimeMillis()<carDiscoveryRetryAt||!carDiscoveryLoading.compareAndSet(false,true))return;
    carDiscoveryRetryAt=System.currentTimeMillis()+60_000;
    networkTasks.execute(()->{try{autoLibrary.refreshDiscovery();int count=autoLibrary.children("home").size();handler.post(()->{if(!disposed&&session!=null)session.notifyChildrenChanged("home",count,null);});}catch(Exception ignored){/* Cached shelves and the library remain available offline. */}finally{carDiscoveryLoading.set(false);}});
  }

  private <T> ListenableFuture<LibraryResult<T>> libraryWork(java.util.concurrent.Callable<LibraryResult<T>> task) {
    var future=com.google.common.util.concurrent.SettableFuture.<LibraryResult<T>>create();
    libraryTasks.execute(() -> {
      try {if(disposed) future.cancel(false); else future.set(task.call());}
      catch(Exception e) {future.set(LibraryResult.ofError(androidx.media3.session.SessionError.ERROR_BAD_VALUE));}
    });
    return future;
  }

  private ExoPlayer buildPlayer(boolean focus) {
    ExoPlayer out = new ExoPlayer.Builder(this).setMediaSourceFactory(sourceFactory)
        .setSeekBackIncrementMs(Preferences.skip(this,false)*1000L)
        .setSeekForwardIncrementMs(Preferences.skip(this,true)*1000L).build();
    out.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),false);
    out.setHandleAudioBecomingNoisy(true); out.setWakeMode(C.WAKE_MODE_LOCAL); return out;
  }

  private void attachListener(ExoPlayer owner) {
    owner.addListener(
        new Player.Listener() {
          @Override public void onPlayWhenReadyChanged(boolean ready, int reason) {
            if (owner != player) return;
            if (ready) {
              focusDeadline = android.os.SystemClock.elapsedRealtime() + 5000;
              ensureFocus();
            } else {
              handler.removeCallbacks(focusAfterForeground);
              if (!resumeOnFocusGain) abandonFocus();
            }
          }
          @Override
          public void onMediaItemTransition(MediaItem item, int reason) {
            if (owner != player) return;
            app.cadence.core.Track selected=track(item);
            if(selected!=null&&!selected.podcast) {
              if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                sessionFirstTrack=selected;sessionFeedback.clear();radioRequested="";radioRetryAt=0;
              } else if(sessionFirstTrack==null) sessionFirstTrack=selected;
              heardSongs.put(selected.sourceUrl,selected);
            }
            if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO&&sleepEndOfTrack){
              sleepEndOfTrack=false;sleepAt=0;player.pause();player.seekTo(0);
            }
            if (item != null
                && item.mediaMetadata.extras != null
                && item.mediaMetadata.extras.getBoolean("manualQueue", false)) {
              android.os.Bundle extras = new android.os.Bundle(item.mediaMetadata.extras);
              extras.putBoolean("manualQueue", false);
              player.replaceMediaItem(
                  player.getCurrentMediaItemIndex(),
                  item.buildUpon()
                      .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
                      .build());
            }
            if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                &&player.getShuffleModeEnabled()&&player.getRepeatMode()==Player.REPEAT_MODE_ALL) {
              java.util.ArrayList<Integer> cycle=playbackOrder();
              if(!cycle.isEmpty()&&cycle.get(0)==player.getCurrentMediaItemIndex())reshuffleAfterCurrent();
            }
            prioritizeManual();
            TrackInfo t = trackInfo(item);
            player.setPlaybackSpeed(
                t.podcast ? getSharedPreferences("playback", 0).getFloat("podcastSpeed", 1f) : 1f);
            if (t.podcast && podcasts != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
              player.seekTo(podcasts.progress(t.id));
            saveQueue();
            publishWidget();
          }

          @Override
          public void onPositionDiscontinuity(
              Player.PositionInfo oldPos, Player.PositionInfo newPos, int reason) {
            if (owner != player) return;
            if (crossfade != null && reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) crossfade.cancel();
            TrackInfo old = trackInfo(oldPos.mediaItem);
            if (old.podcast && podcasts != null) {
              boolean complete = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
              podcasts.progress(old.id, complete ? 0 : oldPos.positionMs, complete);
            }
            if(!old.podcast && oldPos.mediaItem!=null
                && (reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    || reason==Player.DISCONTINUITY_REASON_SEEK
                    || reason==Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT))
              rememberSongSignal(oldPos.mediaItem,oldPos.positionMs,
                  reason==Player.DISCONTINUITY_REASON_AUTO_TRANSITION);
            saveQueue();
          }

          @Override
          public void onIsPlayingChanged(boolean playing) {
            if (owner != player) return;
            if (playing) ensureFocus();
            if (!playing && crossfade != null) crossfade.cancel();
            saveProgress(false);
            saveQueue();
            publishWidget();
          }

          @Override
          public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
            if (owner != player) return;
            if (crossfade != null) crossfade.queueChanged();
            refreshRadioContext();
            saveQueue();
          }

          @Override
          public void onShuffleModeEnabledChanged(boolean enabled) {
            if (owner != player) return;
            if (crossfade != null) crossfade.cancel();
            prioritizeManual();
            refreshRadioContext();
            saveQueue();
          }

          @Override public void onRepeatModeChanged(int repeatMode) {
            if (owner != player) return;
            if (crossfade != null) crossfade.cancel();
            saveQueue();
          }

          @Override
          public void onPlaybackStateChanged(int state) {
            if (owner != player) return;
            if (state == Player.STATE_ENDED) { saveProgress(true); saveQueue(); extendRadio(); abandonFocus(); }
          }

          @Override
          public void onPlaybackParametersChanged(
              androidx.media3.common.PlaybackParameters parameters) {
            if (owner != player) return;
            if (trackInfo(player.getCurrentMediaItem()).podcast)
              getSharedPreferences("playback", 0)
                  .edit()
                  .putFloat("podcastSpeed", parameters.speed)
                  .apply();
          }

          @Override
          public void onPlayerError(androidx.media3.common.PlaybackException error) {
            if (owner != player) return;
            MediaItem item = player.getCurrentMediaItem();
            if (item != null && item.localConfiguration != null)
              urls.remove(item.localConfiguration.uri.toString());
          }
        });
  }

  @Override
  public MediaLibrarySession onGetSession(MediaSession.ControllerInfo controllerInfo) {
    return session;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    saveProgress(false); saveQueue();
    if (!isPlaybackOngoing()
        || player.getMediaItemCount() == 0
        || player.getPlaybackState() == Player.STATE_ENDED) pauseAllPlayersAndStopSelf();
  }

  @Override
  public void onDestroy() {
    disposed = true;
    handler.removeCallbacksAndMessages(null);
    saveProgress(false);
    saveQueue();
    disk.shutdown();
    networkTasks.shutdownNow();
    libraryTasks.shutdownNow();
    resumeOnFocusGain = false;
    abandonFocus();
    if (crossfade != null) crossfade.cancel();
    if (session != null) {
      session.getPlayer().release();
      session.release();
      session = null;
    }
    getSharedPreferences("widget",0).edit().putBoolean("playing",false).apply();
    CadenceWidget.updateAll(this);
    urls.clear();
    super.onDestroy();
  }

  private java.util.ArrayList<Integer> playbackOrder() {
    java.util.ArrayList<Integer> order = new java.util.ArrayList<>();
    androidx.media3.common.Timeline t = player.getCurrentTimeline();
    int at = t.getFirstWindowIndex(player.getShuffleModeEnabled());
    for (int i = 0; i < player.getMediaItemCount() && at != C.INDEX_UNSET; i++) {
      order.add(at);
      at = t.getNextWindowIndex(at, Player.REPEAT_MODE_OFF, player.getShuffleModeEnabled());
    }
    return order;
  }

  private static boolean manual(MediaItem item) {
    return item.mediaMetadata.extras != null
        && item.mediaMetadata.extras.getBoolean("manualQueue", false);
  }

  private void prioritizeManual() {
    if (arranging || !player.getShuffleModeEnabled() || player.getMediaItemCount() < 2) return;
    androidx.media3.common.Timeline timeline = player.getCurrentTimeline();
    if (timeline.isEmpty()) return;
    int current = player.getCurrentMediaItemIndex();
    java.util.ArrayList<Integer> order = new java.util.ArrayList<>(),
        priority = new java.util.ArrayList<>();
    for (int i = 0; i < player.getMediaItemCount(); i++)
      if (i != current && manual(player.getMediaItemAt(i))) priority.add(i);
    if (priority.isEmpty()) return;
    int next = timeline.getFirstWindowIndex(true);
    for (int i = 0; i < player.getMediaItemCount() && next != C.INDEX_UNSET; i++) {
      if (!priority.contains(next)) order.add(next);
      next = timeline.getNextWindowIndex(next, Player.REPEAT_MODE_OFF, true);
    }
    int at = order.indexOf(current);
    if (at < 0) return;
    order.addAll(at + 1, priority);
    int[] indices = new int[order.size()];
    for (int i = 0; i < indices.length; i++) indices[i] = order.get(i);
    arranging = true;
    try {
      player.setShuffleOrder(
          new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
              indices, System.nanoTime()));
    } finally {
      arranging = false;
    }
  }

  private void reshuffleAfterCurrent() {
    int count=player.getMediaItemCount(),current=player.getCurrentMediaItemIndex();if(count<3||current<0)return;
    int[] random=app.cadence.core.QueueShuffle.fisherYates(count-1,System.nanoTime()),order=new int[count];order[0]=current;
    for(int i=0;i<random.length;i++){int value=random[i];order[i+1]=value>=current?value+1:value;}
    arranging=true;
    try {player.setShuffleOrder(new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order,System.nanoTime()));}
    finally {arranging=false;}
  }

  private boolean allowed(MediaItem item) {
    if (item.localConfiguration == null) return false;
    android.net.Uri uri = item.localConfiguration.uri;
    String url = uri.toString();
    if ("content".equals(uri.getScheme())) return true;
    if (url.startsWith("https://www.youtube.com/watch?v="))
      try {
        Links.youtubeVideo(url);
        return true;
      } catch (Exception e) {
        return false;
      }
    if (trackInfo(item).podcast && "https".equals(uri.getScheme()) && uri.getHost() != null) return true;
    if ("file".equals(uri.getScheme()))
      try {
        java.io.File f = new java.io.File(uri.getPath());
        java.io.File base = getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS);
        return base != null
            && f.getCanonicalPath().startsWith(base.getCanonicalPath() + java.io.File.separator);
      } catch (Exception ignored) {
      }
    return false;
  }

  private static final class TrackInfo {
    boolean podcast;
    String id = "";
  }

  private static TrackInfo trackInfo(MediaItem item) {
    TrackInfo t = new TrackInfo();
    try {
      android.os.Bundle e = item == null ? null : item.mediaMetadata.extras;
      if (e != null) {
        org.json.JSONObject j = new org.json.JSONObject(e.getString("track", "{}"));
        t.podcast = j.optBoolean("podcast");
        t.id = j.optString("episodeId");
      }
    } catch (Exception ignored) {
    }
    return t;
  }

  private static app.cadence.core.Track track(MediaItem item) {
    try {
      android.os.Bundle extras=item==null?null:item.mediaMetadata.extras;
      if(extras==null)return null;
      return Library.decode(new org.json.JSONObject(extras.getString("track","{}")));
    } catch(Exception ignored) { return null; }
  }

  private void rememberSongSignal(MediaItem item,long position,boolean automatic) {
    app.cadence.core.Track song=track(item);if(song==null||song.podcast||song.sourceUrl.isBlank())return;
    long duration=song.durationSeconds>0?song.durationSeconds*1000L:0;
    boolean complete=automatic||duration>0&&position>=duration*.85;
    boolean quick=!complete&&(position<30_000||duration>0&&position<duration*.2);
    if(complete)completedSongs.merge(song.sourceUrl,1,Integer::sum);
    if(quick)skippedSongs.merge(song.sourceUrl,1,Integer::sum);
    recentSongs.remove(song.sourceUrl);recentSongs.addFirst(song.sourceUrl);
    while(recentSongs.size()>50)recentSongs.removeLast();
    heardSongs.put(song.sourceUrl,song);
    sessionFeedback.addLast(new app.cadence.core.MoodAutoplay.Feedback(
        moodSignals(song,false,false),false,complete,quick));
    while(sessionFeedback.size()>3)sessionFeedback.removeFirst();
    radioRequested="";
  }

  private app.cadence.core.MoodAutoplay.Signals moodSignals(
      app.cadence.core.Track song,boolean liked,boolean downloaded) {
    java.util.Set<String> artists=new java.util.LinkedHashSet<>();
    for(String artist:song.artist.split("(?i)\\s*(?:,|&| feat\\.? | featuring | x )\\s*"))
      if(!artist.isBlank())artists.add(artist);
    String fallback=Preferences.get(this).getString("homeLanguage","All");
    if(fallback.equals("All"))fallback="";
    String language=app.cadence.core.MoodAutoplay.language(song.title+" "+song.artist,fallback);
    int year=app.cadence.core.MoodAutoplay.year(song.title+" "+song.query);
    java.util.Set<String> tags=new java.util.LinkedHashSet<>();String words=(song.title+" "+song.query).toLowerCase(java.util.Locale.ROOT);
    for(String tag:java.util.List.of("melody","acoustic","dance","rock","lofi","devotional","live","romantic","instrumental"))
      if(words.contains(tag))tags.add(tag);
    int rank=-1,index=0;for(String recent:recentSongs){if(recent.equals(song.sourceUrl)){rank=index;break;}index++;}
    return new app.cadence.core.MoodAutoplay.Signals(song.sourceUrl,song.title,artists,artists,"",language,year,tags,
        song.durationSeconds,liked,downloaded,completedSongs.getOrDefault(song.sourceUrl,0),
        skippedSongs.getOrDefault(song.sourceUrl,0),rank);
  }

  private boolean mixesLanguages() {
    java.util.Set<String> languages=new java.util.HashSet<>();int checked=0;
    for(String source:recentSongs){app.cadence.core.Track song=heardSongs.get(source);if(song==null)continue;
      String language=moodSignals(song,false,false).language();if(!language.isBlank())languages.add(language);
      if(++checked==20)break;}
    return checked>=6&&languages.size()>1;
  }

  private void saveProgress(boolean completed) {
    if (player == null || podcasts == null) return;
    TrackInfo t = trackInfo(player.getCurrentMediaItem());
    if (t.podcast) {
      completed = completed || player.getPlaybackState() == Player.STATE_ENDED;
      podcasts.progress(t.id, completed ? 0 : player.getCurrentPosition(), completed);
    }
  }

  private void saveQueue() {
    if (player == null || savedQueue == null || disk.isShutdown()) return;
    try {
      org.json.JSONArray items = new org.json.JSONArray();
      for (int i = 0; i < player.getMediaItemCount(); i++) {
        MediaItem item = player.getMediaItemAt(i);
        android.os.Bundle extras = item.mediaMetadata.extras;
        if (extras != null)
          items.put(
              new org.json.JSONObject(extras.getString("track", "{}"))
                  .put("manualQueue", extras.getBoolean("manualQueue", false)));
      }
      org.json.JSONArray order = new org.json.JSONArray();
      for (int index : playbackOrder()) order.put(index);
      byte[] bytes =
          new org.json.JSONObject()
              .put("order", order)
              .put("items", items)
              .put("index", Math.max(0, player.getCurrentMediaItemIndex()))
              .put("position", player.getCurrentPosition())
              .put("shuffle", player.getShuffleModeEnabled())
              .put("repeat", player.getRepeatMode())
              .put("owner", CloudSync.prefs(this).getString("libraryOwner",""))
              .toString()
              .getBytes(java.nio.charset.StandardCharsets.UTF_8);
      disk.execute(
          () -> {
            java.io.FileOutputStream out = null;
            try {
              out = savedQueue.startWrite();
              out.write(bytes);
              savedQueue.finishWrite(out);
              CloudSync.progressChanged(this);
            } catch (Exception e) {
              if (out != null) savedQueue.failWrite(out);
            }
          });
    } catch (Exception ignored) {
    }
  }

  private void restoreQueue() {
    if (!savedQueue.getBaseFile().exists()) return;
    try {
      org.json.JSONObject root =
          new org.json.JSONObject(
              new String(savedQueue.readFully(), java.nio.charset.StandardCharsets.UTF_8));
      restoreQueue(root);
    } catch (Exception ignored) { /* Keep the saved queue available for recovery. */ }
  }

  private void restoreQueue(org.json.JSONObject root) throws Exception {
      org.json.JSONArray list = root.getJSONArray("items");
      java.util.ArrayList<MediaItem> items = new java.util.ArrayList<>();
      int restoredIndex=0; boolean restoredCurrent=false;
      for (int i = 0; i < list.length(); i++) {
        org.json.JSONObject j = list.getJSONObject(i);
        app.cadence.core.Track t = Library.decode(j);
        android.os.Bundle extras = new android.os.Bundle();
        extras.putString("track", j.toString());
        extras.putBoolean("manualQueue", j.optBoolean("manualQueue"));
        var metadata =
            new androidx.media3.common.MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)
                .setExtras(extras);
        if (!t.artwork.isEmpty()) metadata.setArtworkUri(android.net.Uri.parse(t.artwork));
        MediaItem item =
            new MediaItem.Builder()
                .setMediaId(t.sourceUrl)
                .setUri(t.sourceUrl)
                .setMediaMetadata(metadata.build())
                .build();
        if (allowed(item)) {
          if (i <= root.optInt("index")) restoredIndex = items.size();
          if (i == root.optInt("index")) restoredCurrent = true;
          items.add(item);
        }
      }
      if (!items.isEmpty()) {
        player.setMediaItems(
            items,
            Math.min(restoredIndex, items.size() - 1),
            restoredCurrent ? Math.max(0, root.optLong("position")) : 0);
        org.json.JSONArray order = root.optJSONArray("order");
        if (order != null && order.length() == items.size()) {
          int[] indices = new int[order.length()];
          java.util.HashSet<Integer> used = new java.util.HashSet<>();
          for (int i = 0; i < indices.length; i++) {
            indices[i] = order.getInt(i);
            if (indices[i] < 0 || indices[i] >= indices.length || !used.add(indices[i]))
              throw new IllegalArgumentException();
          }
          player.setShuffleOrder(
              new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(
                  indices, System.nanoTime()));
        }
        player.setShuffleModeEnabled(root.optBoolean("shuffle"));
        player.setRepeatMode(root.optInt("repeat", Player.REPEAT_MODE_OFF));
        TrackInfo t = trackInfo(player.getCurrentMediaItem());
        player.setPlaybackSpeed(
            t.podcast ? getSharedPreferences("playback", 0).getFloat("podcastSpeed", 1f) : 1f);
      }
  }

  private void ensureFocus() {
    if (focusHeld || audioManager == null) return;
    if (android.os.Build.VERSION.SDK_INT >= 35 && !isPlaybackOngoing()) {
      // Media3 promotes the service in response to Play. Android 15 rejects
      // focus before that asynchronous notification update completes. Keep
      // the renderer silent until we have both foreground status and focus.
      player.setVolume(0f);
      handler.removeCallbacks(focusAfterForeground);
      if (android.os.SystemClock.elapsedRealtime() < focusDeadline)
        handler.postDelayed(focusAfterForeground, 25);
      else player.pause();
      return;
    }
    resumeOnFocusGain = false;
    focusHeld = audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    if (!focusHeld) { player.pause(); if (crossfade != null) crossfade.cancel(); }
    else if (crossfade == null || crossfade.started == 0) player.setVolume(duck);
  }
  private void abandonFocus() {
    if (audioManager != null && focusHeld) audioManager.abandonAudioFocusRequest(focusRequest);
    focusHeld = false; duck = 1f;
    if (player != null && (crossfade == null || crossfade.started == 0)) player.setVolume(duck);
  }

  private boolean radio(MediaItem item) {
    try { return item != null && item.mediaMetadata.extras != null && new org.json.JSONObject(item.mediaMetadata.extras.getString("track","{}")).optBoolean("radio"); }
    catch(Exception e) { return false; }
  }

  private void refreshRadioContext() {
    radioContextSeed="";
    for(int index:playbackOrder()) {
      MediaItem item=player.getMediaItemAt(index);
      try {
        var extras=item.mediaMetadata.extras;
        if(extras==null || item.localConfiguration==null) continue;
        var track=new org.json.JSONObject(extras.getString("track","{}"));
        if(!track.optBoolean("podcast")) {
          if(sessionFirstTrack==null)sessionFirstTrack=Library.decode(track);
          if(track.optBoolean("radio"))radioContextSeed=item.localConfiguration.uri.toString();
        }
      } catch(Exception ignored) {}
    }
  }

  private int upcomingAutoplay() {
    if(player.getCurrentTimeline().isEmpty())return 0;
    int count=0,at=player.getCurrentMediaItemIndex();
    for(int n=0;n<player.getMediaItemCount();n++) {
      at=player.getCurrentTimeline().getNextWindowIndex(at,Player.REPEAT_MODE_OFF,player.getShuffleModeEnabled());
      if(at==C.INDEX_UNSET)break;if(radio(player.getMediaItemAt(at)))count++;
    }
    return count;
  }

  private void extendRadio() {
    if (player == null || radioLoading || !Preferences.get(this).getBoolean("autoplay",true)
        || player.getMediaItemCount()==0 || player.getRepeatMode()!=Player.REPEAT_MODE_OFF
        || System.currentTimeMillis()<radioRetryAt) return;
    int runway=upcomingAutoplay();if(runway>=10)return;
    app.cadence.core.Track seed=sessionFirstTrack!=null?sessionFirstTrack:track(player.getCurrentMediaItem());
    if(seed==null||seed.podcast||!seed.sourceUrl.startsWith("https://www.youtube.com/watch?v="))return;
    String request=seed.sourceUrl+"|"+runway+"|"+player.getMediaItemCount()+"|"+sessionFeedback.size();
    if(request.equals(radioRequested))return;
    radioLoading=true;radioRequested=request;
    networkTasks.execute(() -> {
      List<app.cadence.core.Track> found=java.util.Collections.emptyList();
      try {
        RadioCache cached=moodCache.get(seed.sourceUrl);
        if(cached!=null&&System.currentTimeMillis()-cached.at()<15*60_000)found=cached.tracks();
        else {found=app.cadence.core.MusicCatalog.related(seed.sourceUrl);moodCache.put(seed.sourceUrl,new RadioCache(System.currentTimeMillis(),List.copyOf(found)));}
      } catch(Exception ignored) {}
      List<app.cadence.core.Track> related=found;
      List<AutoLibrary.AutoplayTrack> local=java.util.Collections.emptyList();
      try {local=autoLibrary.autoplayCandidates();}catch(Exception ignored){}
      List<AutoLibrary.AutoplayTrack> saved=local;
      handler.post(() -> {
        radioLoading=false;
        if (disposed) return;
        if(player==null || player.getMediaItemCount()==0 || !Preferences.get(this).getBoolean("autoplay",true)) return;
        if(sessionFirstTrack==null||!seed.sourceUrl.equals(sessionFirstTrack.sourceUrl)) {radioRequested="";return;}
        java.util.HashSet<String> used=new java.util.HashSet<>();
        for(int i=0;i<player.getMediaItemCount();i++) {
          MediaItem m=player.getMediaItemAt(i); if(m.localConfiguration!=null) used.add(m.localConfiguration.uri.toString());
        }
        java.util.LinkedHashMap<String,app.cadence.core.Track> tracks=new java.util.LinkedHashMap<>();
        java.util.HashMap<String,AutoLibrary.AutoplayTrack> localFlags=new java.util.HashMap<>();
        for(app.cadence.core.Track value:related)tracks.putIfAbsent(value.sourceUrl,value);
        for(AutoLibrary.AutoplayTrack value:saved){tracks.putIfAbsent(value.track().sourceUrl,value.track());localFlags.put(value.track().sourceUrl,value);}
        for(app.cadence.core.Track value:heardSongs.values())tracks.putIfAbsent(value.sourceUrl,value);
        java.util.ArrayList<app.cadence.core.MoodAutoplay.Signals> candidates=new java.util.ArrayList<>();
        for(app.cadence.core.Track value:tracks.values()) {AutoLibrary.AutoplayTrack flags=localFlags.get(value.sourceUrl);
          candidates.add(moodSignals(value,flags!=null&&flags.liked(),flags!=null&&flags.downloaded()));}
        app.cadence.core.MoodAutoplay.Profile profile=app.cadence.core.MoodAutoplay.profile(
            moodSignals(seed,localFlags.containsKey(seed.sourceUrl)&&localFlags.get(seed.sourceUrl).liked(),
                localFlags.containsKey(seed.sourceUrl)&&localFlags.get(seed.sourceUrl).downloaded()),
            new java.util.ArrayList<>(sessionFeedback));
        List<app.cadence.core.MoodAutoplay.Ranked> ranked=app.cadence.core.MoodAutoplay.rank(profile,candidates,used,mixesLanguages());
        java.util.ArrayList<MediaItem> additions=new java.util.ArrayList<>();
        int needed=Math.max(0,12-upcomingAutoplay());
        for(var pick:ranked) {app.cadence.core.Track t=tracks.get(pick.song().source());if(t==null||t.podcast||!used.add(t.sourceUrl))continue;try {
          MediaItem item=AutoLibrary.item(t,t.sourceUrl);
          android.os.Bundle extras=new android.os.Bundle(item.mediaMetadata.extras);
          extras.putString("track",Library.encode(t).put("radio",true).toString());
          additions.add(item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build());
          if(additions.size()>=needed)break;
        }catch(Exception ignored){}}
        if(additions.isEmpty()) { radioRequested=""; radioRetryAt=System.currentTimeMillis()+60_000; return; }
        boolean ended=player.getPlaybackState()==Player.STATE_ENDED && player.getPlayWhenReady();
        java.util.ArrayList<Integer> existingOrder=playbackOrder();
        int next=player.getMediaItemCount();
        java.util.ArrayList<Integer> incomingOrder=new java.util.ArrayList<>();
        for(int i=0;i<additions.size();i++) incomingOrder.add(next+i);
        if(player.getShuffleModeEnabled()) java.util.Collections.shuffle(incomingOrder);
        player.addMediaItems(additions);
        if(player.getShuffleModeEnabled()) {
          existingOrder.addAll(incomingOrder);
          player.setShuffleOrder(new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(existingOrder.stream().mapToInt(Integer::intValue).toArray(),System.nanoTime()));
        }
        prioritizeManual();
        if(ended) { player.seekTo(incomingOrder.get(0),0); player.prepare(); player.play(); }
        radioRequested="";
        saveQueue();
      });
    });
  }

  private void publishWidget() {
    if (player == null) return;
    var m=player.getMediaMetadata(); TrackInfo t=trackInfo(player.getCurrentMediaItem());
    getSharedPreferences("widget",0).edit().putString("title",m.title==null?"Cadence":m.title.toString())
      .putString("artist",m.artist==null?"Choose something to play":m.artist.toString())
      .putString("artwork",m.artworkUri==null?"":m.artworkUri.toString())
      .putBoolean("playing",player.getPlayWhenReady() && player.getPlaybackState()!=Player.STATE_ENDED && player.getPlayerError()==null).putBoolean("podcast",t.podcast)
      .putInt("progress",player.getDuration()>0?(int)Math.min(1000,1000*player.getCurrentPosition()/player.getDuration()):0).apply();
    CadenceWidget.updateAll(this);
  }

  /** Two real audio renderers. The session moves to the incoming song halfway through overlap. */
  private final class Crossfade {
    private ExoPlayer standby, outgoing;
    private String stamp="";
    private long started, length;
    private boolean swapping;
    void queueChanged() { if (!swapping) cancel(); }
    void cancel() {
      if(swapping) return;
      if(outgoing!=null) {
        outgoing.release(); outgoing=null;
      }
      if(standby!=null) { standby.release(); standby=null; }
      if(player!=null) player.setVolume(duck);
      started=0; stamp="";
    }
    void tick() {
      if(player==null) return;
      int seconds=Preferences.crossfade(PlaybackService.this);
      if(seconds==0 || !player.getPlayWhenReady() || player.getPlayerError()!=null) { cancel(); return; }
      if(started>0) {
        ExoPlayer incoming=outgoing==null?standby:player;
        if(incoming==null || incoming.getPlaybackState()!=Player.STATE_READY || !player.isPlaying()) { cancel(); return; }
        float fraction=Math.min(1f,(android.os.SystemClock.elapsedRealtime()-started)/(float)length);
        ExoPlayer old=outgoing==null?player:outgoing;
        incoming.setVolume(fraction * duck); old.setVolume((1f-fraction) * duck);
        if(fraction>=.5f && outgoing==null) {
          swapping=true;
          outgoing=player; player=standby; standby=null;
          // Both renderers share the service's single audio-focus request.
          MediaItem current=player.getCurrentMediaItem();
          if(current!=null && manual(current)) {
            android.os.Bundle extras=new android.os.Bundle(current.mediaMetadata.extras);
            extras.putBoolean("manualQueue",false);
            player.replaceMediaItem(player.getCurrentMediaItemIndex(),current.buildUpon()
                .setMediaMetadata(current.mediaMetadata.buildUpon().setExtras(extras).build()).build());
          }
          prioritizeManual();
          session.setPlayer(new TransportPlayer(PlaybackService.this,player));
          swapping=false;
          saveQueue(); publishWidget();
        }
        if(fraction>=1) { cancel(); }
        return;
      }
      if(!player.isPlaying() || trackInfo(player.getCurrentMediaItem()).podcast || player.getRepeatMode()==Player.REPEAT_MODE_ONE) { cancel(); return; }
      long duration=player.getDuration(),remaining=duration-player.getCurrentPosition();
      int next=player.getNextMediaItemIndex();
      if(duration<=seconds*1000L+1000 || next==C.INDEX_UNSET || next==player.getCurrentMediaItemIndex()
          || trackInfo(player.getMediaItemAt(next)).podcast || !player.isCurrentMediaItemSeekable()) { cancel(); return; }
      if(remaining>(seconds+8)*1000L) return;
      String wanted=player.getCurrentMediaItemIndex()+":"+next+":"+player.getMediaItemCount()+":"+player.getMediaItemAt(next).mediaId;
      if(!stamp.equals(wanted)) {
        cancel(); stamp=wanted;
        standby=buildPlayer(false); attachListener(standby); standby.setVolume(0f);
        java.util.ArrayList<MediaItem> items=new java.util.ArrayList<>();
        for(int i=0;i<player.getMediaItemCount();i++) items.add(player.getMediaItemAt(i));
        standby.setMediaItems(items,next,0);
        int[] order=playbackOrder().stream().mapToInt(Integer::intValue).toArray();
        standby.setShuffleOrder(new androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order,System.nanoTime()));
        standby.setShuffleModeEnabled(player.getShuffleModeEnabled()); standby.setRepeatMode(player.getRepeatMode()); standby.prepare();
      }
      if(remaining<=seconds*1000L && remaining>600 && standby!=null && standby.getPlaybackState()==Player.STATE_READY) {
        length=remaining; started=android.os.SystemClock.elapsedRealtime(); standby.play();
      }
    }
  }

  private static final class Cached {
    final String url;
    final long at = System.currentTimeMillis();

    Cached(String url) {
      this.url = url;
    }
  }
  private record RadioCache(long at,List<app.cadence.core.Track> tracks) {}
}
