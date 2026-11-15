package app.cadence.music;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import androidx.media3.common.MediaItem;
import androidx.media3.session.MediaController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.Track;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Captures the four fixed surfaces in both semantic themes. */
@RunWith(AndroidJUnit4.class)
public final class RegressionScreenshotIntegrationTest {
  private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;
  private interface Checked { void run() throws Exception; }
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception error){throw new RuntimeException(error);}});}
  private Object field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
  private void set(String name,Object value)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);}
  private void call(String name)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
  private void capture(String theme,String screen)throws Exception{instrumentation.waitForIdleSync();String name="cadence26-"+theme.toLowerCase(Locale.ROOT)+"-"+screen;try(var descriptor=instrumentation.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/"+name+".png");var input=new FileInputStream(descriptor.getFileDescriptor())){byte[] buffer=new byte[8192];while(input.read(buffer)!=-1){}}}
  private MediaController awaitController()throws Exception{MediaController[] found={null};for(int attempt=0;attempt<200&&found[0]==null;attempt++){main(()->found[0]=(MediaController)field("controller"));if(found[0]==null)Thread.sleep(50);}assertNotNull(found[0]);return found[0];}
  private void prepareAudio()throws Exception{MediaController controller=awaitController();File audio=new File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),"equalizer-screenshot.wav");try(var input=instrumentation.getContext().getAssets().open("offline-fixture.wav");var output=new FileOutputStream(audio)){byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);}Track track=new Track("Equalizer fixture","Cadence",android.net.Uri.fromFile(audio).toString(),"",3);main(()->{Method item=MainActivity.class.getDeclaredMethod("mediaItem",Track.class);item.setAccessible(true);controller.setMediaItem((MediaItem)item.invoke(activity,track));controller.prepare();controller.play();});Thread.sleep(500);}

  @Test public void fixedSurfacesRenderInLightAndDark()throws Exception{
    String original=UiStyle.theme(context);
    try{
      for(String theme:new String[]{"Light","Dark"}){
        AppearanceStore.setBlocking(context,theme);activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->{set("searchText","rajini intro sing");set("searchLoading",true);call("showSearch");});capture(theme,"search");
        main(()->{set("libraryFilter","Downloaded");call("showLibrary");});capture(theme,"library");
        prepareAudio();main(()->call("openPlayer"));capture(theme,"player");main(()->call("openEqualizer"));
        Dialog[] sheet={null};for(int attempt=0;attempt<200&&sheet[0]==null;attempt++){main(()->sheet[0]=(Dialog)field("equalizerDialog"));if(sheet[0]==null)Thread.sleep(50);}assertNotNull(sheet[0]);capture(theme,"equalizer");
        MediaController controller=awaitController();main(()->{sheet[0].dismiss();controller.pause();controller.clearMediaItems();activity.finish();});instrumentation.waitForIdleSync();activity=null;
      }
    }finally{if(activity!=null)main(activity::finish);AppearanceStore.setBlocking(context,original);}
  }
}
