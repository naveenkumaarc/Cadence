package app.cadence.core;

import java.io.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.*;
import okhttp3.*;

/** Streams to a partial file. Range responses must identify the exact bytes being appended. */
public final class ResumableDownload {
  public interface Progress {void update(long bytes,long total,String validator) throws IOException;}
  public static final class HttpFailure extends IOException {
    public final int code;
    HttpFailure(int code){super("Audio source returned "+code);this.code=code;}
  }
  public static boolean transfer(OkHttpClient client,String address,File partial,String validator,
      BooleanSupplier stopped,Progress progress,Consumer<Call> active) throws IOException {
    if(stopped.getAsBoolean())return false;
    long offset=partial.isFile()?partial.length():0;
    // Without a validator, requesting a fresh representation avoids splicing different audio.
    if(validator.isEmpty())offset=0;
    Request.Builder request=new Request.Builder().url(address).header("User-Agent",HttpDownloader.USER_AGENT)
        .header("Accept-Encoding","identity");
    if(offset>0)request.header("Range","bytes="+offset+"-").header("If-Range",validator);
    Call call=client.newCall(request.build());active.accept(call);
    try(Response response=call.execute()) {
      if(stopped.getAsBoolean())return false;
      if(response.code()==416&&offset>0) {
        String range=response.header("Content-Range","");
        if(range.equals("bytes */"+offset)){progress.update(offset,offset,validator);return true;}
        // A changed source can be shorter. Discard its partial representation and retry cleanly.
        try(FileOutputStream out=new FileOutputStream(partial)){out.getFD().sync();}
        throw new IOException("Audio length changed; restarting download");
      }
      if(!response.isSuccessful()||response.body()==null)throw new HttpFailure(response.code());
      long total=response.body().contentLength();
      if(response.code()==206) {
        Matcher range=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(response.header("Content-Range",""));
        if(!range.matches()||Long.parseLong(range.group(1))!=offset)throw new IOException("Invalid audio byte range");
        total=Long.parseLong(range.group(3));
        if(Long.parseLong(range.group(2))<offset||Long.parseLong(range.group(2))>=total)throw new IOException("Invalid audio range length");
      } else offset=0;
      String nextValidator=response.header("ETag","");
      if(nextValidator.startsWith("W/"))nextValidator="";
      if(nextValidator.isEmpty())nextValidator=response.header("Last-Modified","");
      if(response.code()==206&&nextValidator.isEmpty())nextValidator=validator;
      if(response.code()==206&&offset>0&&!validator.equals(nextValidator)) {
        try(FileOutputStream output=new FileOutputStream(partial)){output.getFD().sync();}
        throw new IOException("Audio representation changed during resume");
      }
      progress.update(offset,total,nextValidator);
      try(InputStream input=response.body().byteStream();FileOutputStream output=new FileOutputStream(partial,offset>0)) {
        byte[] buffer=new byte[32*1024];long done=offset;int count;
        while(!stopped.getAsBoolean()&&(count=input.read(buffer))!=-1) {
          if(stopped.getAsBoolean())return false;
          output.write(buffer,0,count);done+=count;progress.update(done,total,nextValidator);
        }
        output.getFD().sync();
        if(stopped.getAsBoolean())return false;
        if(total>=0&&done!=total)throw new IOException("Audio transfer was interrupted");
        return true;
      }
    } finally {active.accept(null);}
  }
  private ResumableDownload() {}
}
