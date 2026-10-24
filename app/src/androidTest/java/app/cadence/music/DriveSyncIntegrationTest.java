package app.cadence.music;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

/** Real TLS/HTTP uploads and repeated sync, using a private synthetic Drive protocol server. */
@RunWith(AndroidJUnit4.class)
public final class DriveSyncIntegrationTest {
  private Context context;private JSONObject before;private Map<String,?> account;private MockWebServer server;private DriveStore drive;
  private final Map<String,JSONObject> files=new LinkedHashMap<>();private int next=1;private boolean failHistory;
  private CountDownLatch gate;
  private interface Checked {void run() throws Exception;}
  private void main(Checked action) {InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});}
  @Before public void setup() throws Exception {
    context=InstrumentationRegistry.getInstrumentation().getTargetContext();main(()-> {
      account=CloudSync.prefs(context).getAll();CloudSync.prefs(context).edit().clear().commit();before=CloudData.localSnapshot(context);CloudData.apply(context,new JSONObject(),false);
      CloudSync.prefs(context).edit().putString("subject","protocol-account").putString("libraryOwner","protocol-account").putString("device","protocol-phone").putString("deviceName","Protocol phone").putBoolean("enabled",true).putBoolean("auto",false).commit();
    });
    HeldCertificate certificate=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
    HandshakeCertificates serverCertificates=new HandshakeCertificates.Builder().heldCertificate(certificate).build();
    HandshakeCertificates clientCertificates=new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
    server=new MockWebServer();server.useHttps(serverCertificates.sslSocketFactory(),false);
    server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {@Override public MockResponse dispatch(RecordedRequest request) {
      try {return reply(request);}catch(Exception error){return new MockResponse().setResponseCode(500).setBody("{}");}
    }});server.start();
    OkHttpClient client=new OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(),clientCertificates.trustManager()).callTimeout(15,TimeUnit.SECONDS).followRedirects(false).build();
    drive=new DriveStore("synthetic-test-token",client,server.url("/drive").toString(),server.url("/upload").toString(),server.url("/userinfo").toString());
  }
  @After public void cleanup() throws Exception {
    if(gate!=null)gate.countDown();for(int i=0;i<200 && CloudSync.manualRunning();i++)Thread.sleep(25);
    WorkManager.getInstance(context).cancelUniqueWork("cadence-cloud").getResult().get(10,TimeUnit.SECONDS);WorkManager.getInstance(context).cancelUniqueWork("cadence-cloud-v2").getResult().get(10,TimeUnit.SECONDS);
    if(server!=null)server.shutdown();main(()-> {
      CloudSync.prefs(context).edit().clear().commit();CloudData.applyLocal(context,before);var edit=CloudSync.prefs(context).edit();
      for(var entry:account.entrySet()){Object v=entry.getValue();if(v instanceof String x)edit.putString(entry.getKey(),x);else if(v instanceof Boolean x)edit.putBoolean(entry.getKey(),x);else if(v instanceof Long x)edit.putLong(entry.getKey(),x);else if(v instanceof Integer x)edit.putInt(entry.getKey(),x);}edit.commit();
    });
  }
  private MockResponse json(Object body) {return new MockResponse().addHeader("Content-Type","application/json").setBody(body.toString());}
  private MockResponse error(int code,String reason) throws Exception {return json(new JSONObject().put("error",new JSONObject().put("message","private-user@example.com token-secret-library-details").put("errors",new JSONArray().put(new JSONObject().put("reason",reason))))).setResponseCode(code);}
  private synchronized MockResponse reply(RecordedRequest request) throws Exception {
    HttpUrl url=request.getRequestUrl();String path=url.encodedPath(),method=request.getMethod();
    if(path.equals("/userinfo")) {if(gate!=null)gate.await(5,TimeUnit.SECONDS);return json(new JSONObject().put("sub","protocol-account"));}
    if(path.equals("/drive")) {
      boolean history=url.queryParameter("q").contains("'history'");JSONArray list=new JSONArray();
      for(var entry:files.entrySet()) {JSONObject item=entry.getValue();if(item.getJSONObject("appProperties").optString("cadenceKind").equals(history?"history":"state")) {JSONObject meta=new JSONObject(item.toString());meta.remove("body");list.put(meta);}}
      return json(new JSONObject().put("files",list));
    }
    if(path.startsWith("/drive/")) {
      String id=path.substring("/drive/".length());JSONObject item=files.get(id);if(item==null)return error(404,"notFound");
      if(method.equals("DELETE")) {files.remove(id);return new MockResponse().setResponseCode(204);}
      return json(item.getString("body"));
    }
    if(path.startsWith("/upload")) {
      String contentType=request.getHeader("Content-Type");assertTrue(contentType.startsWith("multipart/related"));
      String boundary=contentType.substring(contentType.indexOf("boundary=")+9).replace("\"","");List<String> parts=new ArrayList<>();
      for(String part:request.getBody().readUtf8().split(Pattern.quote("--"+boundary))) {int at=part.indexOf("\r\n\r\n");if(at>=0)parts.add(part.substring(at+4).trim());}
      JSONObject metadata=new JSONObject(parts.get(0));JSONObject properties=metadata.getJSONObject("appProperties");
      if(failHistory && properties.optString("cadenceKind").equals("history"))return error(503,"backendError");
      String id=method.equals("PATCH")?path.substring("/upload/".length()):"file-"+next++;
      if(method.equals("PATCH")){assertFalse(metadata.has("parents"));assertTrue(files.containsKey(id));}else assertEquals("appDataFolder",metadata.getJSONArray("parents").getString(0));
      files.put(id,new JSONObject().put("id",id).put("appProperties",properties).put("modifiedTime",java.time.Instant.now().toString()).put("body",parts.get(1)));
      return json(new JSONObject().put("id",id));
    }
    return error(404,"notFound");
  }
  @Test public void repeatedSyncUpdatesExistingFileAndDeviceLabelOverRealHttps() throws Exception {
    main(()-> {Library library=new Library(context);library.playlists.add(new Library.Playlist("First saved library"));library.save();PodcastStore.get(context).progress("episode",1200000,false);});
    CloudSync.sync(context,"protocol-account",false,()->drive);assertTrue(CloudSync.prefs(context).getLong("lastSync",0)>0);
    main(()-> {Library library=new Library(context);library.playlists.get(1).name="Second saved library";library.save();CloudSync.prefs(context).edit().putString("deviceName","Renamed protocol phone").commit();PodcastStore.get(context).progress("episode",2700000,false);});
    CloudSync.sync(context,"protocol-account",false,()->drive);
    JSONObject state=files.values().stream().filter(x->x.optJSONObject("appProperties").optString("cadenceKind").equals("state")).findFirst().orElseThrow();
    assertEquals("Renamed protocol phone",state.getJSONObject("appProperties").getString("deviceName"));assertTrue(state.getString("body").contains("Second saved library"));assertTrue(state.getString("body").contains("2700000"));
    assertEquals(1,files.values().stream().filter(x->x.optJSONObject("appProperties").optString("cadenceKind").equals("state")).count());
  }
  @Test public void failedRecoveryArchiveDoesNotBlockCurrentCloudSave() throws Exception {
    CloudSync.sync(context,"protocol-account",false,()->drive);long previous=CloudSync.prefs(context).getLong("lastSync",0);failHistory=true;
    main(()->PodcastStore.get(context).progress("episode",600000,false));CloudSync.sync(context,"protocol-account",false,()->drive);
    assertTrue(CloudSync.prefs(context).getLong("lastSync",0)>=previous);assertTrue(CloudSync.prefs(context).getString("status","").startsWith("Up to date"));
    assertTrue(files.values().stream().anyMatch(x->x.optString("body").contains("600000")));
  }
  @Test public void manualSyncBypassesDelayedOldWorkAndDuplicateTapsAreCoalesced() throws Exception {
    WorkManager.getInstance(context).enqueueUniqueWork("cadence-cloud",ExistingWorkPolicy.REPLACE,new OneTimeWorkRequest.Builder(CloudWorker.class).setInitialDelay(1,TimeUnit.DAYS).build()).getResult().get(10,TimeUnit.SECONDS);
    gate=new CountDownLatch(1);boolean[] accepted={false,false};main(()-> {accepted[0]=CloudSync.syncNow(context,()->drive);accepted[1]=CloudSync.syncNow(context,()->drive);});assertTrue(accepted[0]);assertFalse(accepted[1]);
    gate.countDown();for(int i=0;i<400 && CloudSync.manualRunning();i++)Thread.sleep(25);
    assertFalse(CloudSync.manualRunning());assertTrue(CloudSync.prefs(context).getLong("lastSync",0)>0);
  }
  @Test public void failureCategoriesKeepUsefulCodesWithoutExposingPrivateServerMessages() throws Exception {
    // Error classification itself never includes the HTTP response message or account information.
    var quota=SyncErrors.describe(new DriveStore.ApiFailure(403,"storageQuotaExceeded"));assertFalse(quota.consent());assertFalse(quota.retry());assertTrue(quota.description().contains("storage"));
    var rate=SyncErrors.describe(new DriveStore.ApiFailure(403,"rateLimitExceeded"));assertFalse(rate.consent());assertTrue(rate.retry());
    var auth=SyncErrors.describe(new ExecutionException(new DriveStore.ConsentException()));assertTrue(auth.consent());assertFalse(auth.description().contains("@"));
  }
  @Test public void pickerRestoresTheSelectedOlderBackupAndKeepsBeforeRestoreCopy() throws Exception {
    main(()-> {Library library=new Library(context);library.playlists.add(new Library.Playlist("Selected old library"));library.save();PodcastStore.get(context).progress("episode",600000,false);});
    CloudSync.sync(context,"protocol-account",false,()->drive);
    main(()-> {Library library=new Library(context);library.playlists.get(1).name="Newest library";library.save();PodcastStore.get(context).progress("episode",2700000,false);});
    CloudSync.sync(context,"protocol-account",false,()->drive);
    var copies=CloudSync.history(context,()->drive);JSONObject earlier=copies.stream().filter(x->x.optJSONObject("appProperties").optString("cadenceKind").equals("history")).findFirst().orElseThrow();
    assertTrue(BackupLabels.cloud(earlier).contains("Protocol phone"));CloudSync.restore(context,earlier.getString("id"),()->drive);
    for(int i=0;i<400 && CloudSync.manualRunning();i++)Thread.sleep(25);
    main(()-> {assertEquals("Selected old library",new Library(context).playlists.get(1).name);assertEquals(600000,PodcastPositions.latest(context,"episode").position());});
    var after=CloudSync.history(context,()->drive);assertTrue(after.stream().anyMatch(x->x.optBoolean("local") && BackupLabels.cloud(x).contains("Before last restore")));
  }
  @Test public void pendingBackupSelectionBlocksAutomaticAndManualUpload() throws Exception {
    CloudSync.prefs(context).edit().putBoolean("auto",true).putBoolean("chooseBackup",true).commit();
    assertFalse(CloudSync.automatic(context));assertFalse(CloudSync.syncNow(context,()->drive));
    try {CloudSync.sync(context,"protocol-account",false,()->drive);fail("Upload started before choosing backup");}catch(CancellationException expected){}
    assertTrue(files.isEmpty());
  }
}
