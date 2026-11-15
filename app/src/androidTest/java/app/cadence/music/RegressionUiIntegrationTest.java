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
import app.cadence.core.MusicCatalog;
import app.cadence.core.SearchRanker;
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
  private void call(String name,Class<?> type,Object value)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name,type);method.setAccessible(true);method.invoke(activity,value);}
  private List<Button> buttons(View root){List<Button> found=new ArrayList<>();collectButtons(root,found);return found;}
  private void collectButtons(View view,List<Button> found){if(view instanceof Button button)found.add(button);if(view instanceof ViewGroup group)for(int index=0;index<group.getChildCount();index++)collectButtons(group.getChildAt(index),found);}
  private Button button(String label){return buttons(activity.getWindow().getDecorView()).stream().filter(candidate->label.contentEquals(candidate.getText())).findFirst().orElse(null);}
  private View text(View view,String label){if(view instanceof android.widget.TextView value&&!(view instanceof EditText)&&label.contentEquals(value.getText()))return view;if(view instanceof ViewGroup group)for(int index=0;index<group.getChildCount();index++){View found=text(group.getChildAt(index),label);if(found!=null)return found;}return null;}
  private SearchViewModel.Payload payload(String query){return payload(query,1);}
  private SearchViewModel.Payload payload(String query,int count){List<MusicCatalog.Entry> entries=new ArrayList<>();List<SearchViewModel.Item> items=new ArrayList<>();for(int index=1;index<=count;index++){MusicCatalog.Entry entry=new MusicCatalog.Entry();entry.kind="song";entry.title=count==1?query:query+" "+index;entry.artist="Ilaiyaraaja";entries.add(entry);SearchRanker.Document document=new SearchRanker.Document(query+index,SearchRanker.Type.SONG,entry.title,entry.artist,"","","","",0,false,false,false,false);items.add(new SearchViewModel.Item(new SearchRanker.Ranked(document,100-index,true),entry,null));}return new SearchViewModel.Payload(entries,List.of(),items,"");}
  private void fakeSearch()throws Exception{SearchViewModel model=(SearchViewModel)field("searchViewModel");model.onQueryChanged("");model.attach(new SearchViewModel.Repository(){public SearchViewModel.Payload search(String query,String filter){return payload(query);}public List<String> suggestions(String query){return List.of(query,query+" songs",query+" songs",query+" hits");}});}
  @Before public void launch(){activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
  @After public void close(){if(activity!=null)main(activity::finish);}

  @Test public void multiWordSearchKeepsOneFocusedImeOwner()throws Exception{
    fakeSearch();
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
    for(int attempt=0;attempt<50&&text(activity.getWindow().getDecorView(),phrase)==null;attempt++)Thread.sleep(50);
    assertNotNull("Results must render without leaving Search",text(activity.getWindow().getDecorView(),phrase));
    androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(query);assertNotNull(insets);
    if(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())){
      LinearLayout results=(LinearLayout)field("searchResultsBody");assertTrue(results.getPaddingBottom()>0);
      assertEquals(View.GONE,((View)field("navigationShell")).getVisibility());
    }
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

  @Test public void suggestionsAreRowsAndTappingOneSubmitsSearch()throws Exception{
    fakeSearch();main(()->call("showSearch"));EditText query=(EditText)field("searchQueryView");
    main(()->{query.requestFocus();query.setText("ilayaraja");query.setSelection(query.length());});
    for(int attempt=0;attempt<20&&text(activity.getWindow().getDecorView(),"ilayaraja songs")==null;attempt++)Thread.sleep(25);
    assertNotNull(text(activity.getWindow().getDecorView(),"ilayaraja songs"));
    assertNull("The typed query must not be repeated as a suggestion",text(activity.getWindow().getDecorView(),"ilayaraja"));
    View suggestion=text(activity.getWindow().getDecorView(),"ilayaraja songs");assertNotNull(suggestion);
    main(()->((View)suggestion.getParent()).performClick());
    SearchViewModel model=(SearchViewModel)field("searchViewModel");
    for(int attempt=0;attempt<40&&model.state().getValue().phase()!=SearchViewModel.Phase.RESULTS;attempt++)Thread.sleep(25);
    SearchViewModel.UiState state=model.state().getValue();
    assertTrue(state.submitted());assertEquals(SearchViewModel.Phase.RESULTS,state.phase());
  }

  @Test public void leavingSearchAndReturningRestoresQueryChipAndResults()throws Exception{
    fakeSearch();main(()->call("showSearch"));EditText query=(EditText)field("searchQueryView");
    main(()->{query.setText("ilayaraja");query.setSelection(query.length());});Thread.sleep(500);
    main(()->button("Songs").performClick());Thread.sleep(100);
    main(()->call("navigate",String.class,"Home"));main(()->call("navigate",String.class,"Search"));
    EditText restored=(EditText)field("searchQueryView");assertEquals("ilayaraja",restored.getText().toString());
    assertTrue(button("Songs").isSelected());assertNotNull(text(activity.getWindow().getDecorView(),"ilayaraja"));
  }

  @Test public void lastResultScrollsAboveVisibleIme()throws Exception{
    SearchViewModel model=(SearchViewModel)field("searchViewModel");model.onQueryChanged("");model.attach(new SearchViewModel.Repository(){public SearchViewModel.Payload search(String query,String filter){return payload(query,12);}public List<String> suggestions(String query){return List.of();}});
    main(()->call("showSearch"));EditText query=(EditText)field("searchQueryView");
    main(()->{query.requestFocus();query.setText("visible row");query.setSelection(query.length());((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);model.submit("visible row");});
    for(int attempt=0;attempt<80&&model.state().getValue().phase()!=SearchViewModel.Phase.RESULTS;attempt++)Thread.sleep(25);
    android.widget.ScrollView scroll=(android.widget.ScrollView)field("mainScroll");main(()->scroll.fullScroll(View.FOCUS_DOWN));instrumentation.waitForIdleSync();
    View last=text(activity.getWindow().getDecorView(),"visible row 12");assertNotNull(last);android.graphics.Rect visible=new android.graphics.Rect();assertTrue(last.getGlobalVisibleRect(visible));
    androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(query);assertNotNull(insets);
    if(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())){int imeTop=activity.getWindow().getDecorView().getHeight()-insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom;assertTrue("Last row must remain above IME",visible.bottom<=imeTop+2);}
  }
}
