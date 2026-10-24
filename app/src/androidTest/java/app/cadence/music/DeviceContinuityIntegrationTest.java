package app.cadence.music;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class DeviceContinuityIntegrationTest {
  private Context context;private JSONObject before;private Map<String,?> account;private boolean mode;
  private interface Checked {void run() throws Exception;}
  private void main(Checked task) {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{task.run();}catch(Exception e){throw new RuntimeException(e);}});}
  @Before public void setup() {context=InstrumentationRegistry.getInstrumentation().getTargetContext();main(()-> {
    account=CloudSync.prefs(context).getAll();CloudSync.prefs(context).edit().clear().commit();mode=PodcastPositions.thisDeviceMode(context);PodcastPositions.mode(context,false);
    before=CloudData.localSnapshot(context);CloudData.apply(context,new JSONObject(),false);
  });}
  @After public void cleanup() {main(()-> {
    CloudSync.prefs(context).edit().clear().commit();CloudData.applyLocal(context,before);PodcastPositions.mode(context,mode);
    var edit=CloudSync.prefs(context).edit();for(var entry:account.entrySet()) {Object v=entry.getValue();if(v instanceof String x)edit.putString(entry.getKey(),x);else if(v instanceof Boolean x)edit.putBoolean(entry.getKey(),x);else if(v instanceof Long x)edit.putLong(entry.getKey(),x);else if(v instanceof Integer x)edit.putInt(entry.getKey(),x);}edit.commit();
  });}
  private JSONObject row(String id,String device,String name,long position,long updated) throws Exception {
    return new JSONObject().put("id",id).put("device",device).put("name",name).put("position",position).put("done",false).put("updated",updated);
  }
  @Test public void latestAndThisDeviceKeepSeparatePositionsAndLaterRewindWins() {main(()-> {
    CloudSync.prefs(context).edit().putString("device","phone-test").putString("deviceName","Naveen phone").commit();
    PodcastStore.get(context).progress("episode-3",1200000,false);JSONObject snapshot=CloudData.snapshot(context);
    long stamp=PodcastPositions.latest(context,"episode-3").updated()+1000;
    snapshot.put("device-progress/"+JsonDisk.hash("episode-3")+"/tablet-test",row("episode-3","tablet-test","Living room tablet",2700000,stamp));
    CloudData.apply(context,snapshot);assertEquals(2700000,PodcastStore.get(context).progress("episode-3"));
    PodcastPositions.mode(context,true);assertEquals(1200000,PodcastStore.get(context).progress("episode-3"));
    PodcastPositions.mode(context,false);PodcastStore.get(context).progress("episode-3",600000,false);
    assertEquals(600000,PodcastStore.get(context).progress("episode-3"));assertEquals(2,PodcastPositions.choices(context,"episode-3").size());
    assertTrue(PodcastPositions.latest(context,"episode-3").updated()>stamp);
  });}
  @Test public void pausedCheckpointDoesNotStealLatestActivityFromOtherDevice() {main(()-> {
    CloudSync.prefs(context).edit().putString("device","phone-test").commit();PodcastStore.get(context).progress("episode-3",1200000,false);
    JSONObject snapshot=CloudData.snapshot(context);long newer=PodcastPositions.latest(context,"episode-3").updated()+1000;
    snapshot.put("device-progress/"+JsonDisk.hash("episode-3")+"/tablet-test",row("episode-3","tablet-test","Tablet",2700000,newer));CloudData.apply(context,snapshot);
    PodcastStore.get(context).progress("episode-3",1200000,false);
    assertEquals(2700000,PodcastPositions.latest(context,"episode-3").position());assertEquals("tablet-test",PodcastPositions.latest(context,"episode-3").device());
  });}
  @Test public void fullCloudRoundTripRetainsIndependentEpisodesAndDevices() {main(()-> {
    CloudSync.prefs(context).edit().putString("device","phone-test").commit();PodcastStore.get(context).progress("episode-3",1200000,false);PodcastStore.get(context).progress("episode-4",2700000,false);
    JSONObject saved=CloudData.snapshot(context);CloudData.apply(context,new JSONObject(),false);CloudData.apply(context,saved);
    PodcastPositions.mode(context,true);assertEquals(1200000,PodcastStore.get(context).progress("episode-3"));assertEquals(2700000,PodcastStore.get(context).progress("episode-4"));
    CloudSync.prefs(context).edit().putString("device","new-installation").commit();assertEquals(0,PodcastStore.get(context).progress("episode-3"));
    PodcastPositions.mode(context,false);assertEquals(1200000,PodcastStore.get(context).progress("episode-3"));
  });}
  @Test public void legacyProgressRemainsAvailableWithoutInventingADevice() {main(()-> {
    JSONObject data=new JSONObject().put("progress/"+JsonDisk.hash("old-episode"),new JSONObject().put("id","old-episode").put("position",1200000).put("done",false));
    CloudData.apply(context,data);assertEquals(1200000,PodcastStore.get(context).progress("old-episode"));assertEquals(0,PodcastPositions.choices(context,"old-episode").size());
    PodcastPositions.mode(context,true);assertEquals(0,PodcastStore.get(context).progress("old-episode"));
  });}
  @Test public void selectedRestoreKeepsOtherDeviceHistoryAndMakesChosenPositionLatest() {main(()-> {
    CloudSync.prefs(context).edit().putString("device","phone-test").commit();PodcastStore.get(context).progress("episode",600000,false);JSONObject saved=CloudData.snapshot(context);
    PodcastStore.get(context).progress("episode",1800000,false);JSONObject current=CloudData.snapshot(context);
    current.put("device-progress/"+JsonDisk.hash("episode")+"/tablet",row("episode","tablet","Tablet",2700000,System.currentTimeMillis()+10000));
    JSONObject restored=PodcastPositions.restoring(saved,current);CloudData.apply(context,restored);
    assertEquals(600000,PodcastPositions.latest(context,"episode").position());assertEquals(2,PodcastPositions.choices(context,"episode").size());
  });}
  @Test public void localBackupCarriesDeviceMetadataAndRestoresPerDeviceHistory() {main(()-> {
    CloudSync.prefs(context).edit().putString("device","phone-test").putString("deviceName","Naveen phone").commit();PodcastStore.get(context).progress("episode",1200000,false);
    JSONObject backup=Backup.create(context,new Library(context),PodcastStore.get(context));assertEquals("Naveen phone",backup.getString("deviceName"));assertTrue(backup.getLong("createdAt")>0);assertTrue(backup.has("podcastPositions"));
    PodcastStore.get(context).progress("episode",2700000,false);Backup.restore(context,backup,new Library(context),PodcastStore.get(context));
    assertEquals(1200000,PodcastPositions.latest(context,"episode").position());assertEquals(1200000,PodcastPositions.device(context,"episode").position());
    assertTrue(BackupLabels.filename(context).startsWith("Cadence-Naveen_phone-"));
  });}
  @Test public void backupLabelsUseOriginalSnapshotTimeAndIdentifyLegacyDevices() {main(()-> {
    JSONObject known=new JSONObject().put("modifiedTime","2026-10-24T12:00:00Z").put("appProperties",new JSONObject().put("cadenceKind","history").put("deviceName","Bedroom tablet").put("savedAt","1700000000000"));
    assertEquals(1700000000000L,BackupLabels.savedAt(known));String label=BackupLabels.cloud(known);assertTrue(label.contains("Bedroom tablet"));assertTrue(label.contains(BackupLabels.date(1700000000000L)));assertTrue(label.contains("Earlier recovery copy"));
    JSONObject old=new JSONObject().put("modifiedTime","2026-10-24T12:00:00Z").put("appProperties",new JSONObject().put("device","legacy-device-123"));assertTrue(BackupLabels.cloud(old).contains("Earlier Cadence device"));assertTrue(BackupLabels.savedAt(old)>0);
    assertTrue(BackupLabels.cloudName("தமிழ்".repeat(50)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=100);
  });}
  @Test public void invalidDeviceProgressRollsBackWithoutChangingLibrary() {main(()-> {
    JSONObject before=CloudData.snapshot(context),bad=new JSONObject(before.toString());bad.put("device-progress/bad",row("episode","invalid/device","Bad",123,100));
    try {CloudData.apply(context,bad);fail("Invalid device accepted");}catch(java.io.IOException expected){}
    assertEquals(before.toString(),CloudData.snapshot(context).toString());
  });}
}
