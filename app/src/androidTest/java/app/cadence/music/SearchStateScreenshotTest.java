package app.cadence.music;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import app.cadence.core.MusicCatalog;
import app.cadence.core.SearchRanker;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Deterministic Light/Dark references for every Search state. */
@RunWith(AndroidJUnit4.class)
public final class SearchStateScreenshotTest {
  private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
  private final Context context=instrumentation.getTargetContext();
  private MainActivity activity;
  private interface Checked{void run()throws Exception;}
  private void main(Checked task){instrumentation.runOnMainSync(()->{try{task.run();}catch(Exception error){throw new RuntimeException(error);}});}
  private Object field(String name)throws Exception{Field value=MainActivity.class.getDeclaredField(name);value.setAccessible(true);return value.get(activity);}
  private void call(String name)throws Exception{Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(activity);}
  private void shell(String command)throws Exception{try(ParcelFileDescriptor descriptor=instrumentation.getUiAutomation().executeShellCommand(command);FileInputStream input=new FileInputStream(descriptor.getFileDescriptor())){byte[] buffer=new byte[4096];while(input.read(buffer)!=-1){}}}
  private void capture(String theme,String state)throws Exception{instrumentation.waitForIdleSync();Thread.sleep(120);shell("screencap -p /data/local/tmp/cadence-search-"+theme.toLowerCase(Locale.ROOT)+"-"+state+".png");}
  private void await(SearchViewModel model,SearchViewModel.Phase phase)throws Exception{for(int attempt=0;attempt<200&&model.state().getValue().phase()!=phase;attempt++)Thread.sleep(25);assertEquals(phase,model.state().getValue().phase());instrumentation.waitForIdleSync();}
  private SearchViewModel.Payload results(String query){List<MusicCatalog.Entry> music=new ArrayList<>();List<SearchViewModel.Item> ranked=new ArrayList<>();for(int index=1;index<=8;index++){MusicCatalog.Entry entry=new MusicCatalog.Entry();entry.kind="song";entry.title=(index==1?"Ilaiyaraaja essentials":query+" result "+index);entry.artist="Ilaiyaraaja";music.add(entry);SearchRanker.Document document=new SearchRanker.Document(query+index,SearchRanker.Type.SONG,entry.title,entry.artist,"","","","",0,false,false,false,false);ranked.add(new SearchViewModel.Item(new SearchRanker.Ranked(document,100-index,true),entry,null));}return new SearchViewModel.Payload(music,List.of(),ranked,"");}

  @Test public void everySearchStateRendersInLightAndDark()throws Exception{
    String original=UiStyle.theme(context);
    try{for(String theme:List.of("Light","Dark")){
      AppearanceStore.setBlocking(context,theme);activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      SearchViewModel model=(SearchViewModel)field("searchViewModel");CountDownLatch[] loading={new CountDownLatch(0)};
      model.attach(new SearchViewModel.Repository(){public SearchViewModel.Payload search(String query,String filter)throws Exception{if(query.equals("loading"))loading[0].await(5,TimeUnit.SECONDS);if(query.equals("error"))throw new IllegalStateException("offline");if(query.equals("empty"))return SearchViewModel.Payload.empty();return results(query);}public List<String> suggestions(String query){return List.of(query+" melody songs",query+" tamil hits",query+" songs",query+" classics");}});
      model.onQueryChanged("");main(()->call("showSearch"));await(model,SearchViewModel.Phase.IDLE);capture(theme,"idle");
      EditText query=(EditText)field("searchQueryView");main(()->{query.requestFocus();query.setText("ilayaraja");query.setSelection(query.length());((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(query,InputMethodManager.SHOW_IMPLICIT);});
      for(int attempt=0;attempt<20&&model.state().getValue().suggestions().isEmpty();attempt++)Thread.sleep(20);assertFalse(model.state().getValue().suggestions().isEmpty());capture(theme,"typing");
      loading[0]=new CountDownLatch(1);main(()->{query.setText("loading");query.setSelection(query.length());model.submit("loading");});await(model,SearchViewModel.Phase.LOADING);capture(theme,"loading");loading[0].countDown();await(model,SearchViewModel.Phase.RESULTS);capture(theme,"results");
      main(()->{query.setText("empty");model.submit("empty");});await(model,SearchViewModel.Phase.EMPTY);capture(theme,"empty");
      main(()->{query.setText("error");model.submit("error");});await(model,SearchViewModel.Phase.ERROR);capture(theme,"error");
      main(activity::finish);instrumentation.waitForIdleSync();activity=null;
    }}finally{if(activity!=null)main(activity::finish);AppearanceStore.setBlocking(context,original);}
  }
}
