package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.os.Bundle;
import androidx.media3.common.MediaItem;
import androidx.media3.session.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class)
public final class AutoBrowseIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private JSONObject before;private byte[] discovery;private String languages,selected;
  private interface Checked{void run()throws Exception;}
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});}
  @Before public void setup()throws Exception{
    java.io.File file=new java.io.File(context.getFilesDir(),"discovery.json");discovery=file.exists()?java.nio.file.Files.readAllBytes(file.toPath()):null;
    languages=Preferences.get(context).getString("languages","");selected=Preferences.get(context).getString("homeLanguage","All");
    main(()->{before=CloudData.localSnapshot(context);Preferences.get(context).edit().putString("homeLanguage","All").putString("languages","English").commit();Library l=new Library(context);l.playlists.removeIf(p->p.id.equals("car-probe"));Library.Playlist p=new Library.Playlist("Car playlist");p.id="car-probe";
      MusicCatalog.Home home=new MusicCatalog.Home();MusicCatalog.Shelf shelf=new MusicCatalog.Shelf("Car recommendations");
      for(int i=0;i<3;i++){Track t=new Track("Car song "+i,"Cadence test","https://www.youtube.com/watch?v=car_test_0"+i,"",180);p.tracks.add(t);MusicCatalog.Entry e=new MusicCatalog.Entry();e.track=t;e.title=t.title;e.artist=t.artist;e.kind="song";shelf.entries.add(e);}l.playlists.add(p);l.save();home.shelves.add(shelf);java.nio.file.Files.write(file.toPath(),MusicCatalog.serialize(home).getBytes(StandardCharsets.UTF_8));
    });
  }
  @After public void cleanup()throws Exception{main(()->{CloudData.applyLocal(context,before);Preferences.get(context).edit().putString("languages",languages).putString("homeLanguage",selected).commit();});java.io.File file=new java.io.File(context.getFilesDir(),"discovery.json");if(discovery!=null)java.nio.file.Files.write(file.toPath(),discovery);else file.delete();}
  @Test public void media3UnboundedRequestsReturnRootAndSameCachedRecommendations()throws Exception{
    com.google.common.util.concurrent.ListenableFuture<MediaBrowser>[] f=new com.google.common.util.concurrent.ListenableFuture[1];main(()->f[0]=new MediaBrowser.Builder(context,new SessionToken(context,new ComponentName(context,PlaybackService.class))).buildAsync());MediaBrowser b=f[0].get(20,TimeUnit.SECONDS);
    try{com.google.common.util.concurrent.ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>>[] result=new com.google.common.util.concurrent.ListenableFuture[1];main(()->result[0]=b.getChildren("root",0,Integer.MAX_VALUE,null));var root=result[0].get(10,TimeUnit.SECONDS);assertEquals(0,root.resultCode);assertEquals(List.of("home","library","podcasts","continue"),root.value.stream().map(x->x.mediaId).collect(java.util.stream.Collectors.toList()));
      main(()->result[0]=b.getChildren("home",0,Integer.MAX_VALUE,null));String shelf=result[0].get(10,TimeUnit.SECONDS).value.stream().filter(x->x.mediaMetadata.title.toString().equals("Car recommendations")).findFirst().orElseThrow().mediaId;
      main(()->result[0]=b.getChildren(shelf,0,Integer.MAX_VALUE,null));var songs=result[0].get(10,TimeUnit.SECONDS).value;assertEquals(3,songs.size());assertEquals("Car song 1",songs.get(1).mediaMetadata.title.toString());assertTrue(songs.get(1).mediaMetadata.isPlayable);
      AutoLibrary.Selection queue=new AutoLibrary(context).selection(songs.get(1).mediaId);assertEquals(3,queue.items.size());assertEquals(1,queue.index);
    }finally{main(()->MediaController.releaseFuture(f[0]));}
  }
  @Test public void externalLegacyBrowserLoadsHomeLibraryAndSelectedPlaylist()throws Exception{
    shell("run-as app.cadence.carprobe rm -f files/legacy-result.json");shell("am start -n app.cadence.carprobe/.LegacyProbeActivity");
    try{String last="";for(int i=0;i<180;i++){last=shell("run-as app.cadence.carprobe cat files/legacy-result.json");if(last.startsWith("{")){JSONObject result=new JSONObject(last);assertTrue(result.optString("message"),result.getBoolean("passed"));return;}Thread.sleep(250);}fail("External legacy probe did not finish: "+last);}
    finally{shell("am force-stop app.cadence.carprobe");}
  }
  @Test public void offlineHomeAndLargePageNumbersStayUsable()throws Exception{
    java.io.File cache=new java.io.File(context.getFilesDir(),"discovery.json");cache.delete();AutoLibrary library=new AutoLibrary(context);assertTrue(library.children("home").stream().anyMatch(x->x.mediaId.equals("library")));assertTrue(library.children("library").stream().anyMatch(x->x.mediaId.equals("playlist:"+SongDownloads.PLAYLIST_ID)));
    assertEquals(4,AutoLibrary.page(library.children("root"),0,Integer.MAX_VALUE).size());assertTrue(AutoLibrary.page(List.of(1,2,3),Integer.MAX_VALUE,Integer.MAX_VALUE).isEmpty());assertEquals(List.of(3),AutoLibrary.page(List.of(1,2,3),1,2));
    try{library.resolve("recommend:not-a-known-song");fail("Unknown external ID was accepted");}catch(IllegalArgumentException expected){}
  }
  @Test public void cachedAlbumAndPodcastPositionsExposePlayableDetails()throws Exception{
    MusicCatalog.Home home=new MusicCatalog.Home();MusicCatalog.Shelf shelf=new MusicCatalog.Shelf("Albums");MusicCatalog.Entry album=new MusicCatalog.Entry();album.title="Car album";album.browseId="MPRE_synthetic";album.kind="album";shelf.entries.add(album);home.shelves.add(shelf);java.nio.file.Files.write(new java.io.File(context.getFilesDir(),"discovery.json").toPath(),MusicCatalog.serialize(home).getBytes(StandardCharsets.UTF_8));
    String id="collection:"+JsonDisk.hash(album.browseId);JSONArray tracks=new JSONArray();for(Track t:new Library(context).playlists.stream().filter(p->p.id.equals("car-probe")).findFirst().orElseThrow().tracks)tracks.put(Library.encode(t));JsonDisk.write(context,"car-collections.json",new JSONObject().put(id,new JSONObject().put("savedAt",System.currentTimeMillis()).put("tracks",tracks)));
    AutoLibrary library=new AutoLibrary(context);List<MediaItem> items=library.children(id);assertEquals(3,items.size());assertEquals(1,library.selection(items.get(1).mediaId).index);
    main(()->{Podcasts.Show show=new Podcasts.Show();show.title="Car podcast";show.feedUrl="https://example.com/car-progress";Podcasts.Episode e=new Podcasts.Episode();e.id="car-progress-episode";e.title="Episode 3";e.show=show.title;e.audioUrl="https://example.com/synthetic.mp3";e.duration=3600;show.episodes.add(e);PodcastStore.get(context).put(show);PodcastStore.get(context).progress(e.id,1200000,false);});
    var episode=library.children("continue").stream().filter(x->x.mediaMetadata.title.toString().equals("Episode 3")).findFirst().orElseThrow();assertTrue(episode.mediaMetadata.subtitle.toString().contains("20 min played"));assertEquals(1,episode.mediaMetadata.extras.getInt("android.media.extra.PLAYBACK_STATUS"));assertEquals(1d/3,episode.mediaMetadata.extras.getDouble("androidx.media.MediaItem.Extras.COMPLETION_PERCENTAGE"),.001);
  }
  private static byte[] read(java.io.InputStream in)throws Exception{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toByteArray();}
  private String shell(String command)throws Exception{try(var p=instrumentation.getUiAutomation().executeShellCommand(command);var in=new java.io.FileInputStream(p.getFileDescriptor())){return new String(read(in),StandardCharsets.UTF_8).trim();}}
}
