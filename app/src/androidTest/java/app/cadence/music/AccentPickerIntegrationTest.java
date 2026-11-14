package app.cadence.music;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AccentPickerIntegrationTest {
  private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;private String originalAccent,originalTheme;private int originalCustom;
  private void main(Runnable task){instrumentation.runOnMainSync(task);}
  private View described(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=described(group.getChildAt(i),label);if(found!=null)return found;}return null;}

  @Before public void save(){AccentStore.init(context);originalAccent=AccentStore.id(context);originalCustom=UiStyle.preferences(context).getInt("colour",0xffe5384f);originalTheme=UiStyle.theme(context);}
  @After public void restore(){if(activity!=null)main(activity::finish);if(originalAccent.equals("custom"))AccentStore.custom(context,originalCustom);else AccentStore.preset(context,originalAccent);AppearanceStore.setBlocking(context,originalTheme);}

  @Test public void pickerAndHomeCoverThreeAccentsInBothThemes()throws Exception {
    for(String theme:new String[]{"Light","Dark"})for(String accent:new String[]{"rose","teal","monochrome"}) {
      AppearanceStore.setBlocking(context,theme);AccentStore.preset(context,accent);
      activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      capture(theme,accent,"home");Method picker=MainActivity.class.getDeclaredMethod("showAccentPicker",boolean.class);picker.setAccessible(true);main(()->{try{picker.invoke(activity,false);}catch(Exception e){throw new RuntimeException(e);}});
      View selected=described(activity.getWindow().getDecorView(),app.cadence.core.AccentPalette.preset(accent).label()+", selected");
      assertNotNull(selected);assertTrue(selected.getMinimumHeight()>=UiStyle.dp(context,48));assertNotNull(described(activity.getWindow().getDecorView(),"Custom colour hue"));
      capture(theme,accent,"picker");main(activity::finish);activity=null;instrumentation.waitForIdleSync();
    }
    assertTrue(UiStyle.bottomBarHeight(1.3f)>=66);
  }

  private void capture(String theme,String accent,String screen)throws Exception {
    instrumentation.waitForIdleSync();android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
    String name="accent-"+theme.toLowerCase(Locale.ROOT)+"-"+accent+"-"+screen+".png";
    try(FileOutputStream output=new FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}bitmap.recycle();
  }
}
