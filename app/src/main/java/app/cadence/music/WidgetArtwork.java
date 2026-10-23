package app.cadence.music;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;
import app.cadence.core.HttpDownloader;
import app.cadence.core.Podcasts;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.Executors;
import okhttp3.Request;

/** One small persistent widget cover; decoding and network work stay off the playback thread. */
final class WidgetArtwork {
  private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
  private static String wanted="", memoryKey="";
  private static Bitmap memory;
  private static boolean running;
  private static String failedKey="";
  private static long retryAt;

  static synchronized Bitmap cached(Context c,String url) {
    if(url.isEmpty()) return null;
    if(url.equals(memoryKey) && memory!=null) return memory;
    if(url.equals(c.getSharedPreferences("widget",0).getString("coverKey",""))) {
      Bitmap image=BitmapFactory.decodeFile(new File(c.getCacheDir(),"widget-cover.png").getPath());
      if(image!=null) {memory=image;memoryKey=url;return image;}
    }
    return null;
  }
  static synchronized void request(Context context,String url) {
    wanted=url;
    if(!url.startsWith("https://") || cached(context,url)!=null || running || url.equals(failedKey) && System.currentTimeMillis()<retryAt) return;
    running=true; Context app=context.getApplicationContext();
    worker.execute(() -> {
      while(true) {
        String target;
        synchronized(WidgetArtwork.class) {target=wanted;}
        Bitmap cover=null;
        try(var response=HttpDownloader.CLIENT.newCall(new Request.Builder().url(target).build()).execute()) {
          if(response.isSuccessful() && response.body()!=null) {
            byte[] bytes=Podcasts.readLimited(response.body().byteStream(),4*1024*1024);
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
            int sample=1;while(bounds.outWidth/sample>512 || bounds.outHeight/sample>512) sample*=2;
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
            Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(decoded!=null) {
              int size=Math.min(decoded.getWidth(),decoded.getHeight());
              Bitmap square=Bitmap.createBitmap(decoded,(decoded.getWidth()-size)/2,(decoded.getHeight()-size)/2,size,size);
              cover=Bitmap.createScaledBitmap(square,192,192,true);
            }
          }
        } catch(Exception ignored) {}
        synchronized(WidgetArtwork.class) {
          if(!target.equals(wanted)) {
            if(!wanted.startsWith("https://")) {running=false;return;}
            continue;
          }
          if(cover!=null) {
            AtomicFile file=new AtomicFile(new File(app.getCacheDir(),"widget-cover.png"));FileOutputStream stream=null;
            try {
              stream=file.startWrite();cover.compress(Bitmap.CompressFormat.PNG,100,stream);file.finishWrite(stream);
              memory=cover;memoryKey=target;
              app.getSharedPreferences("widget",0).edit().putString("coverKey",target).apply();
            } catch(Exception e) {if(stream!=null)file.failWrite(stream);}
          }
          if(cover==null) {failedKey=target;retryAt=System.currentTimeMillis()+60_000;} else {failedKey="";retryAt=0;}
          running=false;
        }
        if(cover!=null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> CadenceWidget.updateAll(app));
        return;
      }
    });
  }
}
