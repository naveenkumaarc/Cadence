package app.cadence.music;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

/** HTTPS Drive appDataFolder transport. Tokens remain in memory and are never logged. */
final class DriveStore {
  private static final String API="https://www.googleapis.com/drive/v3/files";
  private static final String UPLOAD="https://www.googleapis.com/upload/drive/v3/files";
  private static final MediaType JSON=MediaType.get("application/json; charset=utf-8");
  private final OkHttpClient client;
  private final String api,upload,userInfo;
  private final String token;
  DriveStore(String token) { this(token,new OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS)
      .readTimeout(40,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS).followRedirects(false).build(),API,UPLOAD,"https://www.googleapis.com/oauth2/v3/userinfo"); }
  DriveStore(String token,OkHttpClient client,String api,String upload,String userInfo) {
    if(token==null || token.isBlank()) throw new IllegalArgumentException("Missing authorization");
    this.token=token;this.client=client;this.api=api;this.upload=upload;this.userInfo=userInfo;
  }
  private String execute(Request request) throws Exception {
    try(Response response=client.newCall(request.newBuilder().header("Authorization","Bearer "+token).build()).execute()) {
      if(response.code()==204) return "";
      if(!response.isSuccessful()) {
        String reason="unknown";
        try {
          String body=response.peekBody(65536).string(); JSONObject error=new JSONObject(body).optJSONObject("error");
          JSONArray details=error==null?null:error.optJSONArray("errors");
          if(details!=null && details.length()>0) reason=details.getJSONObject(0).optString("reason","unknown");
        } catch(Exception ignored) {}
        if(!reason.matches("[A-Za-z][A-Za-z0-9_]{0,79}")) reason="unknown";
        if(response.code()==401 || response.code()==403 && Set.of("authError","insufficientPermissions","appNotAuthorizedToFile").contains(reason)) throw new ConsentException();
        throw new ApiFailure(response.code(),reason);
      }
      if(response.body()==null) throw new IOException("Empty Drive response");
      try(InputStream in=response.body().byteStream(); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
        byte[] buffer=new byte[8192]; int count;
        while((count=in.read(buffer))!=-1) { if(bytes.size()+count>20*1024*1024) throw new IOException("The cloud library is too large."); bytes.write(buffer,0,count); }
        return bytes.toString(java.nio.charset.StandardCharsets.UTF_8.name());
      }
    }
  }
  void verifyAccount(String subject) throws Exception {
    JSONObject info=new JSONObject(execute(new Request.Builder().url(userInfo).build()));
    if(!subject.equals(info.optString("sub"))) throw new IOException("Choose the Google account shown in your Cadence profile.");
  }
  List<JSONObject> list(String kind) throws Exception {
    List<JSONObject> out=new ArrayList<>(); String page="";
    do {
      HttpUrl.Builder url=Objects.requireNonNull(HttpUrl.parse(api)).newBuilder().addQueryParameter("spaces","appDataFolder")
          .addQueryParameter("q","trashed=false and appProperties has { key='cadenceKind' and value='"+kind+"' }")
          .addQueryParameter("pageSize","100").addQueryParameter("fields","nextPageToken,files(id,name,modifiedTime,appProperties)");
      if(!page.isEmpty()) url.addQueryParameter("pageToken",page);
      JSONObject response=new JSONObject(execute(new Request.Builder().url(url.build()).build())); JSONArray files=response.optJSONArray("files");
      if(files!=null) for(int i=0;i<files.length();i++) out.add(files.getJSONObject(i));
      if(out.size()>500) throw new IOException("Too many cloud devices or recovery copies.");
      page=response.optString("nextPageToken");
    } while(!page.isEmpty()); return out;
  }
  private String id(String value) { if(!value.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException("Invalid Drive file"); return value; }
  String read(String file) throws Exception { return execute(new Request.Builder().url(api+"/"+id(file)+"?alt=media").build()); }
  void write(String file, String actor, String kind, String content) throws Exception {
    write(file,actor,kind,content,"",System.currentTimeMillis());
  }
  void write(String file,String actor,String kind,String content,String deviceName,long savedAt) throws Exception {
    if(content.length()>20*1024*1024) throw new IOException("The library is too large to sync.");
    Request.Builder request=new Request.Builder();
    JSONObject properties=new JSONObject().put("cadenceKind",kind).put("device",actor)
        .put("deviceName",BackupLabels.cloudName(deviceName)).put("savedAt",Long.toString(Math.max(0,savedAt)));
    JSONObject metadata=new JSONObject().put("appProperties",properties);
    if(file==null) metadata.put("name","Cadence-"+kind+"-"+actor+".json").put("mimeType","application/json").put("parents",new JSONArray().put("appDataFolder"));
    RequestBody body=new MultipartBody.Builder().setType(MediaType.get("multipart/related"))
          .addPart(RequestBody.create(metadata.toString(),JSON)).addPart(RequestBody.create(content,JSON)).build();
    if(file==null) request.url(upload+"?uploadType=multipart").post(body);
    else request.url(upload+"/"+id(file)+"?uploadType=multipart").patch(body);
    execute(request.build());
  }
  void delete(String file) throws Exception { execute(new Request.Builder().url(api+"/"+id(file)).delete().build()); }
  static final class ApiFailure extends IOException {
    final int http;final String reason;
    ApiFailure(int http,String reason) { super("Drive "+http+" / "+reason);this.http=http;this.reason=reason; }
    boolean retryable() { return http==408 || http==429 || http>=500 || Set.of("rateLimitExceeded","userRateLimitExceeded","dailyLimitExceeded").contains(reason); }
  }
  static final class ConsentException extends IOException { ConsentException() { super("Reconnect Google Drive from your profile to continue syncing."); } }
}
