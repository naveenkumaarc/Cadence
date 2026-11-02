package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RequestedChangesIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;
  private interface Checked{void run()throws Exception;}
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});}
  private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
  private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
  private void call(String name,Class<?> type,Object value)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(activity,value);}
  private TextView text(View view,String value){if(view instanceof TextView t&&value.contentEquals(t.getText()))return t;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){TextView found=text(g.getChildAt(i),value);if(found!=null)return found;}return null;}
  private View described(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=described(g.getChildAt(i),label);if(found!=null)return found;}return null;}
  private EditText edit(View view){if(view instanceof EditText e)return e;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){EditText found=edit(g.getChildAt(i));if(found!=null)return found;}return null;}
  @Before public void launch(){activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  @After public void cleanup(){if(activity!=null)main(()->activity.finish());Preferences.get(context).edit().putBoolean("downloadsPaused",false).commit();UiStyle.preferences(context).edit().remove("colour").commit();}
  @Test public void searchAlbumBackRestoresSearchQueryFilterAndResults()throws Exception {
    main(()->{
      set("searchText","My album");set("searchFilter","Albums");
      MusicCatalog.Entry album=new MusicCatalog.Entry();album.kind="album";album.title="My album";album.browseId="test-album";
      set("catalogResults",new ArrayList<>(List.of(album)));call("showSearch");call("openCollection",MusicCatalog.Entry.class,album);
      assertEquals("Collection",field("page"));activity.onBackPressed();
      assertEquals("Search",field("page"));assertEquals("My album",field("searchText"));assertEquals("Albums",field("searchFilter"));
      assertEquals(View.GONE,((TextView)field("status")).getVisibility());
      assertNotNull(text(activity.getWindow().getDecorView(),"My album"));
    });
    screenshot("search-back");
  }
  @Test public void homeChipsStayOnHomeAndProfilePrecedesBrand()throws Exception {
    main(()->{
      View root=activity.getWindow().getDecorView();text(root,"Podcasts").performClick();
      assertEquals("Home",field("page"));assertEquals("Podcasts",field("homeTab"));
      text(activity.getWindow().getDecorView(),"Music").performClick();assertEquals("Home",field("page"));assertEquals("Music",field("homeTab"));
      LinearLayout header=(LinearLayout)((LinearLayout)field("root")).getChildAt(0);
      assertEquals("Open your profile",header.getChildAt(0).getContentDescription());assertEquals("Cadence",((TextView)header.getChildAt(1)).getText());
    });
    screenshot("home");
  }
  @Test public void allSearchShowsPodcastsAndBothSearchInputsClear()throws Exception {
    main(()->{
      Podcasts.Show show=new Podcasts.Show();show.title="Podcast result fixture";show.author="Test voice";show.feedUrl="https://example.com/test-feed";
      set("searchText","Fixture");set("searchFilter","All");set("podcastResults",new ArrayList<>(List.of(show)));call("showSearch");
      View root=activity.getWindow().getDecorView();assertNotNull(text(root,show.title));
      View clear=described(root,"Clear search");assertEquals(View.VISIBLE,clear.getVisibility());clear.performClick();assertEquals("",field("searchText"));assertEquals(View.GONE,clear.getVisibility());
      call("showPodcasts");root=activity.getWindow().getDecorView();EditText query=edit(root);query.setText("Podcast");clear=described(root,"Clear podcast search");assertEquals(View.VISIBLE,clear.getVisibility());clear.performClick();assertEquals("",query.getText().toString());assertEquals(View.GONE,clear.getVisibility());
      assertNull(text(root,"Continue listening"));
    });
  }
  @Test public void followedShowsSortByLastActivityAndExcludePreviews()throws Exception {
    PodcastStore store=PodcastStore.get(context);org.json.JSONObject before=store.backup();
    try {
      Podcasts.Show old=new Podcasts.Show();old.title="Older show";old.feedUrl="https://example.com/old";
      Podcasts.Episode ep=new Podcasts.Episode();ep.id="cadence-test-old";ep.audioUrl="https://example.com/old.wav";old.episodes.add(ep);
      Podcasts.Show recent=new Podcasts.Show();recent.title="Latest show";recent.feedUrl="https://example.com/recent";
      Podcasts.Episode last=new Podcasts.Episode();last.id="cadence-test-recent";last.audioUrl="https://example.com/recent.wav";recent.episodes.add(last);
      main(()->{store.put(old);store.put(recent);store.selectPosition(ep.id,60000,false);store.selectPosition(last.id,30000,false);assertEquals(recent.feedUrl,store.subscriptions().get(0).feedUrl);store.remove(recent);assertFalse(store.subscriptions().stream().anyMatch(s->s.feedUrl.equals(recent.feedUrl)));});
    } finally {main(()->store.replaceCloud(before.getJSONArray("shows"),before.optJSONArray("previews")));}
  }
  @Test public void pauseStatePersistsAndQueueUsesToggledButtons()throws Exception {
    SongDownloads downloads=SongDownloads.get(context);Track track=new Track("Pause fixture","Cadence test","https://www.youtube.com/watch?v=pausedtest1","",3);
    Preferences.get(context).edit().putBoolean("downloadsPaused",true).commit();
    try {
      downloads.startResolved(track,"https://example.com/fixture.wav","audio/wav");assertTrue(downloads.state(track).paused);assertTrue(new SongDownloads(context).state(track).paused);
      main(()->{call("showDownloadQueue");assertNotNull(text(activity.getWindow().getDecorView(),"Resume all"));assertNotNull(text(activity.getWindow().getDecorView(),"Resume"));
        text(activity.getWindow().getDecorView(),"Resume").performClick();assertFalse(downloads.state(track).paused);
        text(activity.getWindow().getDecorView(),"Pause").performClick();assertTrue(downloads.state(track).paused);assertNotNull(text(activity.getWindow().getDecorView(),"Resume"));});
      screenshot("download-queue");
    } finally {downloads.remove(track);}
  }
  @Test public void customSchemesRetainContrastAndSettingsExposeControls()throws Exception {
    for(int hue=0;hue<360;hue+=15)for(boolean light:new boolean[]{false,true}) {
      UiStyle.Palette palette=UiStyle.scheme(android.graphics.Color.HSVToColor(new float[]{hue,.85f,1}),light);
      assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(palette.onAccent(),palette.accent())>=4.5);
      assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(palette.ink(),palette.surface())>=4.5);
    }
    UiStyle.preferences(context).edit().putInt("colour",0xff9966ee).commit();main(()->activity.finish());launch();
    main(()->{assertEquals(UiStyle.palette(context).accent(),field("accent"));call("settings");View root=activity.getWindow().getDecorView();assertNotNull(text(root,"Colour scheme"));assertNotNull(text(root,"Download connection"));assertNotNull(text(root,"Download queue"));});
    screenshot("settings");main(()->((ScrollView)field("mainScroll")).fullScroll(View.FOCUS_DOWN));screenshot("colour-settings");
  }
  private void screenshot(String name)throws Exception {
    android.os.SystemClock.sleep(350);instrumentation.waitForIdleSync();android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();
    assertNotNull(bitmap);try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"cadence24-"+name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
  }
}
