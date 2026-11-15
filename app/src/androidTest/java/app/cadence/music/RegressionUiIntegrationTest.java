package app.cadence.music;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class RegressionUiIntegrationTest {
  private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;
  private interface Checked { void run() throws Exception; }
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception error){throw new RuntimeException(error);}});}
  private Object field(String name)throws Exception{Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);}
  private void call(String name)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
  private List<Button> buttons(View root){List<Button> found=new ArrayList<>();collectButtons(root,found);return found;}
  private void collectButtons(View view,List<Button> found){if(view instanceof Button button)found.add(button);if(view instanceof ViewGroup group)for(int index=0;index<group.getChildCount();index++)collectButtons(group.getChildAt(index),found);}
  private Button button(String label){return buttons(activity.getWindow().getDecorView()).stream().filter(candidate->label.contentEquals(candidate.getText())).findFirst().orElse(null);}
  @Before public void launch(){activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  @After public void close(){if(activity!=null)main(activity::finish);}

  @Test public void multiWordSearchKeepsOneFocusedImeOwner()throws Exception{
    main(()->call("showSearch"));
    EditText query=(EditText)field("searchQueryView");
    assertNotNull(query);
    main(()->{query.requestFocus();((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);});
    instrumentation.waitForIdleSync();
    String phrase="rajini intro sing";
    for(int index=0;index<phrase.length();index++){
      char value=phrase.charAt(index);
      if(value==' ')instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_SPACE);
      else instrumentation.sendStringSync(String.valueOf(value));
      instrumentation.waitForIdleSync();
      assertSame("Search field was recreated",query,field("searchQueryView"));
      assertTrue("Search focus was lost after character "+index,query.hasFocus());
    }
    Thread.sleep(650);instrumentation.waitForIdleSync();
    assertEquals(phrase,query.getText().toString());
    assertSame(query,field("searchQueryView"));assertTrue(query.hasFocus());
    assertTrue(((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).isActive(query));
  }

  @Test public void libraryChipTapSwipeAndRestoreUseOneSelection()throws Exception{
    main(()->call("showLibrary"));
    for(String tab:List.of("Playlists","Songs","Podcasts","Downloaded")){
      Button control=button(tab);assertNotNull(control);main(control::performClick);
      assertEquals(tab,field("libraryFilter"));
      for(String candidate:List.of("Playlists","Songs","Podcasts","Downloaded")){
        Button visible=button(candidate);assertNotNull(visible);assertEquals(candidate.equals(tab),visible.isSelected());
      }
    }
    main(()->button("Playlists").performClick());
    LinearLayout pages=(LinearLayout)field("libraryResults");
    main(()->{long now=android.os.SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,300,40,0);MotionEvent up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,20,42,0);pages.dispatchTouchEvent(down);pages.dispatchTouchEvent(up);down.recycle();up.recycle();});
    assertEquals("Songs",field("libraryFilter"));assertTrue(button("Songs").isSelected());
    Bundle state=new Bundle();main(()->activity.onSaveInstanceState(state));assertEquals("Songs",state.getString("libraryFilter"));
  }
}
