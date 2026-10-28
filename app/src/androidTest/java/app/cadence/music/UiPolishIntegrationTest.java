package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class UiPolishIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;private JSONObject before;private String theme;
  private interface Checked{void run()throws Exception;}
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});}
  private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
  private View described(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=described(group.getChildAt(i),value);if(found!=null)return found;}return null;}
  private TextView text(View view,String value){if(view instanceof TextView t&&value.contentEquals(t.getText()))return t;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){TextView found=text(group.getChildAt(i),value);if(found!=null)return found;}return null;}
  @Before public void setup(){theme=UiStyle.theme(context);main(()->{before=CloudData.localSnapshot(context);});}
  private void launch(String chosen){UiStyle.preferences(context).edit().putString("theme",chosen).commit();if(android.os.Build.VERSION.SDK_INT>=33)instrumentation.getUiAutomation().grantRuntimePermission(context.getPackageName(),android.Manifest.permission.POST_NOTIFICATIONS);activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  @After public void cleanup(){if(activity!=null)main(()->activity.finish());UiStyle.preferences(context).edit().putString("theme",theme).commit();main(()->CloudData.applyLocal(context,before));}
  @Test public void lightThemeHasReadableRowsAndActionableEmptyStates()throws Exception{
    launch("Light");Track song=new Track("A very long song title that should stay readable across two lines","A long artist name with several collaborators","https://www.youtube.com/watch?v=abcdefghijk","",213);Library.Playlist playlist=new Library.Playlist("UI polish playlist");playlist.tracks.add(song);
    main(()->{Method m=MainActivity.class.getDeclaredMethod("showPlaylist",Library.Playlist.class);m.setAccessible(true);m.invoke(activity,playlist);View root=activity.getWindow().getDecorView();View more=described(root,"Options for "+song.title);assertTrue(more instanceof AppIcon);assertTrue(more.getLayoutParams().width>=UiStyle.dp(context,48));assertNotNull(text(root,"3:33"));TextView title=text(root,song.title);assertEquals(UiStyle.LIGHT.ink(),title.getCurrentTextColor());assertEquals(2,title.getMaxLines());});screenshot("polish-library");
    main(()->{Method m=MainActivity.class.getDeclaredMethod("showPlaylist",Library.Playlist.class);m.setAccessible(true);m.invoke(activity,new Library.Playlist("New playlist"));assertNotNull(text(activity.getWindow().getDecorView(),"Find songs"));assertNotNull(text(activity.getWindow().getDecorView(),"Import a playlist"));});screenshot("polish-empty");
    main(()->{call("settings");View root=activity.getWindow().getDecorView();assertNotNull(text(root,"Theme"));assertNotNull(text(root,"Light"));assertNotNull(text(root,"Motion"));assertNotNull(text(root,"Touch feedback"));});screenshot("polish-settings");
  }
  @Test public void miniPlayerProgressFollowsActualSeekWithoutStartingPlayback()throws Exception{
    launch("Dark");androidx.media3.session.MediaController[] controller={null};for(int i=0;i<200;i++){main(()->controller[0]=(androidx.media3.session.MediaController)field("controller"));if(controller[0]!=null)break;Thread.sleep(50);}assertNotNull(controller[0]);
    File audio=new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PODCASTS),"polish-progress.wav");try(var in=instrumentation.getContext().getAssets().open("offline-fixture.wav");var out=new FileOutputStream(audio)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
    Track track=new Track("Mini-player progress","Cadence test",android.net.Uri.fromFile(audio).toString(),"",3);track.podcast=true;track.episodeId="polish-progress";
    main(()->{controller[0].pause();controller[0].setMediaItem(new androidx.media3.common.MediaItem.Builder().setMediaId(track.sourceUrl).setUri(track.sourceUrl).setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder().setTitle(track.title).setArtist(track.artist).setExtras(new android.os.Bundle(){{putString("track",Library.encode(track).toString());}}).build()).build());controller[0].prepare();});
    boolean[] ready={false};for(int i=0;i<200;i++){main(()->ready[0]=controller[0].getDuration()>0);if(ready[0])break;Thread.sleep(50);}assertTrue(ready[0]);
    main(()->{controller[0].seekTo(1000);call("updatePlayer");MiniProgress bar=(MiniProgress)field("miniProgress");assertEquals(1000f/controller[0].getDuration(),bar.fraction(),.05f);assertFalse(bar.isClickable());assertFalse(controller[0].getPlayWhenReady());});screenshot("polish-player");main(()->{controller[0].clearMediaItems();});
  }
  @Test public void refreshGesturePreservesHorizontalScrollingAndTriggersOnlyAtTop()throws Exception{
    launch("Dark");main(()->{RefreshScrollView scroll=(RefreshScrollView)field("mainScroll");int[] calls={0};scroll.refreshAction(()->true,()->calls[0]++);long now=android.os.SystemClock.uptimeMillis();
      MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,10,10,0),horizontal=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_MOVE,300,30,0);scroll.onInterceptTouchEvent(down);assertFalse(scroll.onInterceptTouchEvent(horizontal));assertEquals(0,calls[0]);down.recycle();horizontal.recycle();
      down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,100,10,0);MotionEvent move=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_MOVE,100,UiStyle.dp(context,150),0),up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,100,UiStyle.dp(context,150),0);scroll.onInterceptTouchEvent(down);assertTrue(scroll.onInterceptTouchEvent(move));scroll.onTouchEvent(move);scroll.onTouchEvent(up);assertEquals(1,calls[0]);down.recycle();move.recycle();up.recycle();
    });
  }
  private void screenshot(String name)throws Exception{instrumentation.waitForIdleSync();try(var p=instrumentation.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/"+name+".png");var input=new FileInputStream(p.getFileDescriptor())){byte[] buffer=new byte[8192];while(input.read(buffer)!=-1){}}}
}
