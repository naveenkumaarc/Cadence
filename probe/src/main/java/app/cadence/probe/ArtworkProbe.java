package app.cadence.probe;
import app.cadence.core.*;
import java.nio.file.*;
import java.util.*;
import okhttp3.*;

/** Fetches public artwork into an ordinary OkHttp cache for repeatable offline UI review. */
public final class ArtworkProbe {
 public static void main(String[] args) throws Exception {
   Path root=Path.of("../verification");
   var home=MusicCatalog.deserialize(Files.readString(root.resolve("discovery.json")));
   Set<String> urls=new LinkedHashSet<>();
   for(var shelf:home.shelves) for(var entry:shelf.entries) if(!entry.artwork.isBlank())urls.add(entry.artwork);
   for(String language:List.of("Tamil","Telugu")) {
     var h=MusicCatalog.deserialize(Files.readString(root.resolve(language+".json")));
     for(var shelf:h.shelves)for(var e:shelf.entries)if(!e.artwork.isBlank())urls.add(e.artwork);
   }
   var cache=new Cache(root.resolve("art-cache").toFile(),40*1024*1024);
   var client=HttpDownloader.CLIENT.newBuilder().cache(cache).build();
   var pool=java.util.concurrent.Executors.newFixedThreadPool(6);
   var tasks=new ArrayList<java.util.concurrent.Future<Boolean>>();
   for(String url:urls)tasks.add(pool.submit(()->{try(var r=client.newCall(new Request.Builder().url(url).build()).execute()){if(r.body()==null || !r.isSuccessful())return false;r.body().bytes();return true;}}));
   int success=0;for(var f:tasks)if(f.get())success++;
   pool.shutdown();cache.close();System.out.println("Public artwork fetched: "+success+"/"+urls.size());System.exit(0);
 }
}
