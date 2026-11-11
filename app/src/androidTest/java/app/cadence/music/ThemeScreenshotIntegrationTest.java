package app.cadence.music;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.ColorUtils;
import androidx.media3.session.MediaController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.Track;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Captures the four primary 2.5 surfaces and checks theme, scale and accessibility contracts. */
@RunWith(AndroidJUnit4.class)
public final class ThemeScreenshotIntegrationTest {
  private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;
  private interface Checked { void run() throws Exception; }
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});}
  private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
  private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(activity);}
  private void launch(String theme){UiStyle.preferences(context).edit().putString("theme",theme).commit();activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  private void capture(String theme,String screen)throws Exception{
    instrumentation.waitForIdleSync();String name="cadence25-"+theme.toLowerCase(java.util.Locale.ROOT)+"-"+screen;
    try(var descriptor=instrumentation.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/"+name+".png");var input=new FileInputStream(descriptor.getFileDescriptor())){byte[] buffer=new byte[8192];while(input.read(buffer)!=-1){}}
  }
  private void assertIconLabels(View view){
    if(view instanceof AppIcon&&view.getImportantForAccessibility()!=View.IMPORTANT_FOR_ACCESSIBILITY_NO)
      assertTrue("Interactive icon needs a TalkBack label",view.getContentDescription()!=null&&!view.getContentDescription().toString().isBlank());
    if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)assertIconLabels(group.getChildAt(i));
  }
  private MediaController awaitController()throws Exception{
    MediaController[] found={null};for(int i=0;i<200&&found[0]==null;i++){main(()->found[0]=(MediaController)field("controller"));if(found[0]==null)Thread.sleep(50);}assertNotNull(found[0]);return found[0];
  }
  private void preparePlayer()throws Exception{
    MediaController controller=awaitController();
    File audio=new File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC),"theme-screenshot.wav");
    try(var input=instrumentation.getContext().getAssets().open("offline-fixture.wav");var output=new FileOutputStream(audio)){byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);}
    Track track=new Track("Blue hour","Cadence",android.net.Uri.fromFile(audio).toString(),"",3);
    main(()->{Method item=MainActivity.class.getDeclaredMethod("mediaItem",Track.class);item.setAccessible(true);controller.setMediaItem((androidx.media3.common.MediaItem)item.invoke(activity,track));controller.prepare();call("openPlayer");});
  }

  @Test public void coreScreensRenderInLightAndDark()throws Exception{
    String original=UiStyle.theme(context);
    try{
      for(String theme:new String[]{"Light","Dark"}){
        launch(theme);
        main(()->{call("showHome");assertIconLabels(activity.getWindow().getDecorView());});capture(theme,"home");
        main(()->{call("showLibrary");assertIconLabels(activity.getWindow().getDecorView());});capture(theme,"library");
        main(()->{call("settings");assertIconLabels(activity.getWindow().getDecorView());});capture(theme,"settings");
        preparePlayer();main(()->assertIconLabels(((Dialog)field("playerDialog")).getWindow().getDecorView()));capture(theme,"player");
        main(()->{Dialog dialog=(Dialog)field("playerDialog");if(dialog!=null)dialog.dismiss();activity.finish();});instrumentation.waitForIdleSync();activity=null;
      }
    }finally{if(activity!=null)main(activity::finish);UiStyle.preferences(context).edit().putString("theme",original).commit();}
  }

  @Test public void contrastScaleAndReducedMotionRemainAccessible(){
    for(UiStyle.Palette palette:new UiStyle.Palette[]{UiStyle.LIGHT,UiStyle.DARK}){
      assertTrue(ColorUtils.calculateContrast(palette.ink(),palette.background())>=4.5);
      assertTrue(ColorUtils.calculateContrast(palette.muted(),palette.background())>=4.5);
      assertTrue(ColorUtils.calculateContrast(palette.accent(),palette.background())>=4.5);
      assertTrue(ColorUtils.calculateContrast(palette.onAccent(),palette.accent())>=4.5);
    }
    assertTrue(UiStyle.bottomBarHeight(1.3f)>=66);assertTrue(UiStyle.bottomBarHeight(2f)>=80);
    assertEquals(2,UiStyle.libraryColumns(800,1.3f));assertEquals(1,UiStyle.libraryColumns(800,2f));
    boolean original=UiStyle.preferences(context).getBoolean("motion",true);
    UiStyle.preferences(context).edit().putBoolean("motion",false).commit();
    assertFalse(UiStyle.motion(context));UiStyle.preferences(context).edit().putBoolean("motion",original).commit();
  }
}
