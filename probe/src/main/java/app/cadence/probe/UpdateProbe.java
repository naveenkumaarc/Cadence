package app.cadence.probe;
import app.cadence.core.*;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.*;

/** Read-only live catalogue check, saves public metadata for reproducible Android UI review. */
public final class UpdateProbe {
 public static void main(String[] args) throws Exception {
   Path out=Path.of("../verification");Files.createDirectories(out);
   var suggestions=MusicCatalog.suggestions("anirudh");
   System.out.println("Suggestions: "+suggestions.size()+" "+suggestions);
   var songs=MusicCatalog.songs("anirudh");
   if(songs.isEmpty())throw new IllegalStateException("No songs");
   var related=MusicCatalog.related(songs.get(0).sourceUrl);
   if(related.size()<2) {
     var method=MusicCatalog.class.getDeclaredMethod("request",String.class,com.google.gson.JsonObject.class);method.setAccessible(true);
     var request=new com.google.gson.JsonObject();String id=Links.parameter(java.net.URI.create(Links.youtubeVideo(songs.get(0).sourceUrl)).getRawQuery(),"v");
     request.addProperty("videoId",id);request.addProperty("playlistId","RDAMVM"+id);request.addProperty("params","wAEB");request.addProperty("isAudioOnly",true);request.addProperty("enablePersistentPlaylistPanel",true);request.addProperty("tunerSettingValue","AUTOMIX_SETTING_NORMAL");
     var raw=(com.google.gson.JsonObject)method.invoke(null,"next",request);
     Files.writeString(out.resolve("radio-raw.json"),raw.toString());
     System.out.println("Radio with params: "+MusicCatalog.parseRadio(raw).size());
   }
   System.out.println("Radio: "+related.size()+" songs");
   if(related.size()<2)throw new IllegalStateException("Radio returned no playable recommendations");
   var lists=MusicCatalog.search("Tamil hits","Playlists");
   if(lists.isEmpty())throw new IllegalStateException("No playlists");
   System.out.println("Playlist search: "+lists.size()+" first="+lists.get(0).title+" "+lists.get(0).browseId);
   var first=MusicCatalog.browse(lists.get(0).browseId);
   var all=MusicCatalog.browseAll(lists.get(0).browseId);
   System.out.println("Whole playlist: first="+first.stream().filter(e->e.track!=null).count()+" full="+all.stream().filter(e->e.track!=null).count());
   Files.writeString(out.resolve("playlist.json"),new Gson().toJson(all));
   for(var entry:MusicCatalog.search("Tamil 1000 songs","Playlists").subList(0,3)) {
     var page=MusicCatalog.browse(entry.browseId);
     if(page.stream().filter(e->e.track!=null).count()>=100) {
       var full=MusicCatalog.browseAll(entry.browseId);
       System.out.println("PAGED playlist: first="+page.stream().filter(e->e.track!=null).count()+" full="+full.stream().filter(e->e.track!=null).count());break;
     }
   }
   var en=MusicCatalog.languageHome("Tamil");
   var te=MusicCatalog.languageHome("Telugu");
   System.out.println("Tamil="+en.shelves.size()+" Telugu="+te.shelves.size());
   Files.writeString(out.resolve("Tamil.json"),MusicCatalog.serialize(en));
   Files.writeString(out.resolve("Telugu.json"),MusicCatalog.serialize(te));
   var home=MusicCatalog.home();Files.writeString(out.resolve("discovery.json"),MusicCatalog.serialize(home));
   Files.writeString(out.resolve("songs.json"),new Gson().toJson(songs));
   System.out.println("Live catalogue checks passed");
   System.exit(0);
 }
}
