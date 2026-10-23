package app.cadence.music;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import com.google.gson.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.*;

/** Exercises real Android persistence with synthetic libraries; no Google accounts or private files. */
@RunWith(AndroidJUnit4.class)
public final class CloudIntegrationTest {
  private Context context;
  private JSONObject saved;
  private void main(Runnable task) { InstrumentationRegistry.getInstrumentation().runOnMainSync(task); }
  private void checked(Callable task) { main(()-> { try { task.run(); } catch(Exception e) { throw new RuntimeException(e); } }); }
  private interface Callable { void run() throws Exception; }
  @Before public void before() throws Exception {
    context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    checked(()-> { saved=CloudData.snapshot(context); CloudData.apply(context,new JSONObject(),false); });
  }
  @After public void after() { checked(()->CloudData.apply(context,saved,false)); }
  private Track song(String id) { return new Track("Synthetic "+id,"Synthetic artist","https://www.youtube.com/watch?v="+id,"",180); }

  @Test public void twoDeviceMergePreservesChosenVersionsAndDeletions() {
    checked(()-> {
      Library library=new Library(context); Library.Playlist p=new Library.Playlist("CSV matches");p.id="synthetic-playlist";
      Track chosen=song("abcdefghijk"); chosen.spotifyId="synthetic-csv-id";chosen.matchState="confirmed";p.tracks.add(chosen);library.playlists.add(p);library.save();
      JsonObject initial=JsonParser.parseString(CloudData.snapshot(context).toString()).getAsJsonObject();
      SyncState a=new SyncState("phone-a");a.capture(initial);SyncState b=SyncState.parse(a.document(false),"phone-b");b.applied(initial);
      p.tracks.clear();library.save();a.capture(JsonParser.parseString(CloudData.snapshot(context).toString()).getAsJsonObject());
      b.capture(initial);b.merge(a);CloudData.apply(context,new JSONObject(b.materialize().toString()));
      Library after=new Library(context);assertEquals(0,after.playlists.stream().filter(x->x.id.equals(p.id)).findFirst().orElseThrow().tracks.size());
      assertFalse(CloudData.snapshot(context).toString().contains("abcdefghijk"));
    });
  }
  @Test public void savedDownloadsRestoreWithoutFalseOfflineCompletion() {
    checked(()-> {
      Track track=song("lmnopqrstuv");DownloadList.remember(context,track);
      JSONObject portable=CloudData.snapshot(context);String text=portable.toString();
      assertFalse(text.contains("song-downloads.json"));assertFalse(text.contains("\"job\""));assertFalse(text.contains("file://"));
      CloudData.apply(context,new JSONObject(),false);CloudData.apply(context,portable);
      assertEquals(1,DownloadList.playlist(context).tracks.size());assertFalse(SongDownloads.get(context).state(track).complete);
    });
  }
  @Test public void episodeProgressAndProfileSurvivePortableRoundTrip() {
    checked(()-> {
      PodcastStore.get(context).progress("episode-3",20*60*1000,false);PodcastStore.get(context).progress("episode-4",45*60*1000,false);
      JsonDisk.write(context,"cloud-profile.json",new JSONObject().put("name","Synthetic Listener"));
      JSONObject snapshot=CloudData.snapshot(context);CloudData.apply(context,new JSONObject(),false);CloudData.apply(context,snapshot);
      assertEquals(1200000,PodcastStore.get(context).progress("episode-3"));assertEquals(2700000,PodcastStore.get(context).progress("episode-4"));
      assertEquals("20 min played · 40 min remaining",MainActivity.episodeProgressLabel(1200000,3600));
      assertEquals("45 min played · 15 min remaining",MainActivity.episodeProgressLabel(2700000,3600));
      assertEquals("Synthetic Listener",JsonDisk.read(context,"cloud-profile.json").getString("name"));
    });
  }
  @Test public void invalidCloudFeedDoesNotOverwritePhoneLibrary() {
    checked(()-> {
      Library library=new Library(context);Library.Playlist p=new Library.Playlist("Keep this library");library.playlists.add(p);library.save();
      JSONObject before=CloudData.snapshot(context),bad=new JSONObject(before.toString());
      bad.put("show/invalid",new JSONObject().put("feed","file:///private").put("episodes",new JSONArray()));
      try { CloudData.apply(context,bad);fail("Unsafe feed accepted"); } catch(java.io.IOException expected) {}
      assertEquals(before.toString(),CloudData.snapshot(context).toString());
    });
  }

  @Test public void newPhoneDoesNotOverwriteCloudProfileWithBlankDefaults() {
    checked(()-> {
      JsonDisk.write(context,"cloud-profile.json",new JSONObject());
      SyncState first=new SyncState("existing-phone");JsonObject old=new JsonObject();old.addProperty("profile/name","Custom name");first.capture(old);
      SyncState fresh=new SyncState("new-phone");fresh.capture(JsonParser.parseString(CloudData.snapshot(context).toString()).getAsJsonObject());fresh.merge(first);
      CloudData.apply(context,new JSONObject(fresh.materialize().toString()));
      assertEquals("Custom name",JsonDisk.read(context,"cloud-profile.json").getString("name"));
    });
  }
  @Test public void bulkDownloadsSkipDuplicatesFlagUnmatchedAndCanCancelPreparation() {
    checked(()-> {
      Library.Playlist p=new Library.Playlist("Synthetic batch");p.id="synthetic-batch";
      Track match=song("bulk0000001");p.tracks.add(match);p.tracks.add(match);p.tracks.add(new Track("Needs matching","Artist","","",0));
      assertEquals(1,PlaylistDownloads.enqueue(context,p));
      assertEquals(1,PlaylistDownloads.summary(context,p.id).total());
      PlaylistDownloads.cancel(context,p.id);
      assertFalse(PlaylistDownloads.hasPending(context));assertEquals(0,PlaylistDownloads.summary(context,p.id).active());
      assertEquals(1,DownloadList.playlist(context).tracks.size());
      androidx.work.WorkManager.getInstance(context).cancelUniqueWork("cadence-playlist-downloads");
    });
  }
}
