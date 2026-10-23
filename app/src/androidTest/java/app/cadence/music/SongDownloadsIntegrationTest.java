package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.TextView;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.io.*;
import java.lang.reflect.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real HTTPS DownloadManager transfer, native library indicators, persistence and file-backed decoding. */
@RunWith(AndroidJUnit4.class)
public class SongDownloadsIntegrationTest {
  private final android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private final Context context = instrumentation.getTargetContext();
  private interface Checked { void run() throws Exception; }
  private void main(Checked action) {
    instrumentation.runOnMainSync(() -> { try { action.run(); } catch (Exception e) {throw new RuntimeException(e);} });
  }
  private Object field(MainActivity activity, String name) throws Exception {
    Field field = MainActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(activity);
  }
  private void call(MainActivity activity, String name) throws Exception {
    Method method = MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity);
  }
  private TextView text(View view, String value) {
    if (view instanceof TextView && value.contentEquals(((TextView)view).getText())) return (TextView)view;
    if (view instanceof ViewGroup) for (int i=0; i<((ViewGroup)view).getChildCount(); i++) {
      TextView found = text(((ViewGroup)view).getChildAt(i), value); if (found != null) return found;
    }
    return null;
  }
  private void tapRow(MainActivity activity, String title) {
    TextView view = text(activity.getWindow().getDecorView(), title); assertNotNull(view);
    ((View)view.getParent().getParent()).performClick();
  }

  @Test public void downloadedSongPersistsShowsAcrossPlaylistsAndDecodesWithoutRemoteSource() throws Exception {
    Track song = new Track("Offline fixture", "Cadence test", "https://www.youtube.com/watch?v=offline0001", "", 3);
    SongDownloads downloads = SongDownloads.get(context); downloads.remove(song);
    Library library = new Library(context); library.playlists.removeIf(p -> p.id.equals("offline-csv"));
    Library.Playlist imported = new Library.Playlist("Imported duration test"); imported.id = "offline-csv";
    Track csv = PlaylistCsv.read(new java.io.StringReader("Track Name,Artist Name(s)\nImported title,Imported artist\n")).get(0);
    csv.confirmVersion(song); csv.durationSeconds = 0; // Simulates an already-matched 2.0 song.
    imported.tracks.add(csv); library.playlists.add(imported); library.save();
    MainActivity activity = null;
    MediaController[] controller = {null};
    try {
      String fixture = "https://raw.githubusercontent.com/naveenkumaarc/Cadence/main/app/src/androidTest/assets/offline-fixture.wav";
      downloads.startResolved(song, fixture, "audio/wav");
      long deadline = System.currentTimeMillis() + 90000;
      while (!downloads.state(song).complete && System.currentTimeMillis() < deadline) Thread.sleep(250);
      assertTrue(downloads.state(song).label, downloads.state(song).complete);
      String local = new SongDownloads(context).localUri(song.sourceUrl); assertNotNull(local);
      byte[] audio = java.nio.file.Files.readAllBytes(new File(android.net.Uri.parse(local).getPath()).toPath());
      StringBuilder hash = new StringBuilder();
      for (byte value : java.security.MessageDigest.getInstance("SHA-256").digest(audio)) hash.append(String.format(java.util.Locale.ROOT,"%02x",value & 255));
      assertEquals("12cbd6a4c10fb45c460722e6a0c4c33d2330d383336dccae38fa59b03edc3b7f",hash.toString());
      downloads.startResolved(song, fixture, "audio/wav"); assertEquals(1, downloads.tracks().size());
      assertTrue(new AutoLibrary(context).children("playlists").stream().anyMatch(i -> i.mediaId.equals("playlist:"+SongDownloads.PLAYLIST_ID)));
      assertEquals(1, new AutoLibrary(context).children("playlist:"+SongDownloads.PLAYLIST_ID).size());

      activity = (MainActivity)instrumentation.startActivitySync(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      MainActivity screen = activity;
      for(int i=0;i<200 && controller[0]==null;i++) {main(() -> controller[0]=(MediaController)field(screen,"controller")); Thread.sleep(100);}
      assertNotNull(controller[0]);
      main(() -> {
        call(screen,"showLibrary"); tapRow(screen,"Downloaded");
        assertNotNull(text(screen.getWindow().getDecorView(),"Offline fixture"));
        assertNotNull(text(screen.getWindow().getDecorView(),"✓"));
        call(screen,"showLibrary"); tapRow(screen,"Imported duration test");
        TextView badge = text(screen.getWindow().getDecorView(),"✓"); assertNotNull(badge);
        assertTrue(badge.getContentDescription().toString().startsWith("Available offline:"));
        tapRow(screen,"Imported title");
      });
      boolean[] ready = {false};
      for(int i=0;i<200 && !ready[0];i++) {main(() -> ready[0]=controller[0].isPlaying() && controller[0].getDuration()>0);Thread.sleep(50);}
      assertTrue("The synthetic YouTube ID can play only through the local download",ready[0]);
      main(() -> {controller[0].pause(); call(screen,"updatePlayer");});
      Track restored = new Library(context).playlists.stream().filter(p -> p.id.equals("offline-csv")).findFirst().orElseThrow().tracks.get(0);
      assertEquals(3,restored.durationSeconds); assertEquals("Imported title",restored.title);
      assertEquals(song.sourceUrl,restored.sourceUrl);
      main(() -> {call(screen,"showLibrary");tapRow(screen,"Imported duration test");assertNotNull(text(screen.getWindow().getDecorView(),"0:03"));});
      downloads.remove(song);
      assertNull(new SongDownloads(context).localUri(song.sourceUrl));
      assertEquals(song.sourceUrl,new Library(context).playlists.stream().filter(p -> p.id.equals("offline-csv")).findFirst().orElseThrow().tracks.get(0).sourceUrl);
      main(() -> {call(screen,"refreshSongDownloads");assertNull(text(screen.getWindow().getDecorView(),"✓"));});
    } finally {
      MainActivity screen=activity;
      if(screen!=null) main(() -> {if(controller[0]!=null && controller[0].isConnected()){controller[0].pause();controller[0].clearMediaItems();}screen.finish();});
      downloads.remove(song);
      Library after = new Library(context); after.playlists.removeIf(p -> p.id.equals("offline-csv")); after.save();
    }
  }
}
