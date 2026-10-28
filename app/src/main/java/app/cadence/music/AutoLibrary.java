package app.cadence.music;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.*;
import app.cadence.core.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** The phone catalogue, exposed as a car browse tree. External clients supply known IDs, never URLs. */
final class AutoLibrary {
  private final Context context;
  AutoLibrary(Context c) {context=c.getApplicationContext();}
  // Stable Android media browse extras, also understood by the Media3 legacy bridge.
  static Bundle styles() {Bundle b=new Bundle();b.putBoolean("android.media.browse.SEARCH_SUPPORTED",true);b.putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",2);b.putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",1);return b;}
  static <T> List<T> page(List<T> items,int page,int size) {if(page<0||size<1)throw new IllegalArgumentException("Invalid page");int start=(int)Math.min((long)page*size,items.size()),end=(int)Math.min((long)start+size,items.size());return items.subList(start,end);}
  private Library library() throws Exception {Library l=new Library(context);l.playlists.add(SongDownloads.get(context).playlist());Library.Playlist wishes=DownloadList.playlist(context);if(!wishes.tracks.isEmpty())l.playlists.add(wishes);return l;}
  static MediaItem folder(String id,String title) {return folder(id,title,"","");}
  private static MediaItem folder(String id,String title,String subtitle,String art) {
    var m=new MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).setExtras(styles());
    if(!art.isEmpty())m.setArtworkUri(Uri.parse(art));return new MediaItem.Builder().setMediaId(id).setMediaMetadata(m.build()).build();
  }
  private String icon(String name) {return "android.resource://"+context.getPackageName()+"/drawable/car_"+name;}
  private MediaItem category(String id,String title,String detail,String icon) {return folder(id,title,detail,icon(icon));}
  static MediaItem item(Track t,String id) throws Exception {
    Bundle extra=new Bundle();extra.putString("track",Library.encode(t).toString());var m=new MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setSubtitle(t.artist).setExtras(extra).setIsBrowsable(false).setIsPlayable(true).setMediaType(t.podcast?MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE:MediaMetadata.MEDIA_TYPE_MUSIC);
    if(!t.artwork.isEmpty())m.setArtworkUri(Uri.parse(t.artwork));return new MediaItem.Builder().setMediaId(id).setUri(t.sourceUrl).setMediaMetadata(m.build()).build();
  }
  private MediaItem episode(Podcasts.Episode e) throws Exception {
    PodcastStore p=PodcastStore.get(context);MediaItem item=item(p.playable(e),"episode:"+key(e.id));Bundle extras=new Bundle(item.mediaMetadata.extras);long position=p.progress(e.id);boolean done=p.completed(e.id);
    extras.putInt("android.media.extra.PLAYBACK_STATUS",done?2:position>0?1:0);extras.putDouble("androidx.media.MediaItem.Extras.COMPLETION_PERCENTAGE",done?1:e.duration>0?Math.min(1,position/(e.duration*1000d)):0);
    String progress=done?"Played":position>0?position/60000+" min played"+(e.duration>0?" · "+Math.max(0,(e.duration*1000-position+59999)/60000)+" min left":""):e.show;
    return item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setSubtitle(progress).setExtras(extras).build()).build();
  }
  private Map<String,MusicCatalog.Shelf> shelves() {
    Map<String,MusicCatalog.Shelf> out=new LinkedHashMap<>();String selected=Preferences.get(context).getString("homeLanguage","All");
    if(selected.equals("All")){cachedShelves("discovery.json","all",false,out);for(String language:Preferences.languages(context))cachedShelves("discovery-"+language+".json",language,true,out);}else cachedShelves("discovery-"+selected+".json",selected,false,out);return out;
  }
  private void cachedShelves(String file,String language,boolean first,Map<String,MusicCatalog.Shelf> out) {
    try {java.io.File f=new java.io.File(context.getFilesDir(),file);if(!f.isFile()||f.length()>4*1024*1024)return;MusicCatalog.Home h=MusicCatalog.deserialize(new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));int n=0;
      for(MusicCatalog.Shelf shelf:h.shelves){if(shelf.entries.isEmpty())continue;out.put("shelf:"+JsonDisk.hash(language+":"+(n++)+":"+shelf.title),shelf);if(first)break;}
    }catch(Exception ignored){}
  }
  boolean refreshNeeded() {String selected=Preferences.get(context).getString("homeLanguage","All");java.io.File file=new java.io.File(context.getFilesDir(),selected.equals("All")?"discovery.json":"discovery-"+selected+".json");return !file.isFile()||System.currentTimeMillis()-file.lastModified()>3600_000;}
  void refreshDiscovery() throws Exception {
    String selected=Preferences.get(context).getString("homeLanguage","All");MusicCatalog.Home h=selected.equals("All")?MusicCatalog.home():MusicCatalog.languageHome(selected);if(h==null||h.shelves.isEmpty())return;
    android.util.AtomicFile f=new android.util.AtomicFile(new java.io.File(context.getFilesDir(),selected.equals("All")?"discovery.json":"discovery-"+selected+".json"));var stream=f.startWrite();try{stream.write(MusicCatalog.serialize(h).getBytes(StandardCharsets.UTF_8));f.finishWrite(stream);}catch(Exception e){f.failWrite(stream);throw e;}
  }
  private MusicCatalog.Entry collection(String id) {
    for(var shelf:shelves().values())for(var e:shelf.entries)if(e.track==null&&!e.browseId.isBlank()&&id.equals("collection:"+JsonDisk.hash(e.browseId)))return e;
    try{var saved=JsonDisk.read(context,"car-collections.json").optJSONObject(id);if(saved!=null&&id.equals("collection:"+JsonDisk.hash(saved.optString("browseId")))){MusicCatalog.Entry e=new MusicCatalog.Entry();e.browseId=saved.getString("browseId");e.title=saved.optString("title");e.artist=saved.optString("artist");e.artwork=saved.optString("artwork");e.kind=saved.optString("kind");return e;}}catch(Exception ignored){}return null;
  }
  private List<MediaItem> collectionItems(String id) throws Exception {
    MusicCatalog.Entry entry=collection(id);if(entry==null)throw new IllegalArgumentException("Collection unavailable");org.json.JSONObject cache=JsonDisk.read(context,"car-collections.json"),saved=cache.optJSONObject(id);
    if(saved!=null&&System.currentTimeMillis()-saved.optLong("savedAt")<3600_000){List<MediaItem> out=new ArrayList<>();var tracks=saved.getJSONArray("tracks");for(int i=0;i<tracks.length();i++)out.add(item(Library.decode(tracks.getJSONObject(i)),"collection-track:"+id.substring(11)+":"+i));return out;}
    List<MusicCatalog.Entry> entries=entry.kind.equals("artist")?MusicCatalog.browse(entry.browseId):MusicCatalog.browseAll(entry.browseId);List<MediaItem> out=new ArrayList<>();org.json.JSONArray tracks=new org.json.JSONArray();
    for(var value:entries)if(value.track!=null&&!value.track.sourceUrl.isBlank()&&out.size()<200){Track t=value.track;if(t.artwork.isBlank())t.artwork=entry.artwork;if(t.artist.isBlank())t.artist=entry.artist;out.add(item(t,"collection-track:"+id.substring(11)+":"+out.size()));tracks.put(Library.encode(t));}
    if(cache.length()>32)cache=new org.json.JSONObject();cache.put(id,new org.json.JSONObject().put("savedAt",System.currentTimeMillis()).put("tracks",tracks).put("browseId",entry.browseId).put("title",entry.title).put("artist",entry.artist).put("artwork",entry.artwork).put("kind",entry.kind));JsonDisk.write(context,"car-collections.json",cache);return out;
  }
  List<MediaItem> children(String parent) throws Exception {
    if(parent.equals("root"))return List.of(category("home","Home","Recommendations for your listening","home"),category("library","Library","Liked songs, playlists and downloads","library"),category("podcasts","Podcasts","Your followed shows","podcasts"),category("continue","Continue listening","Resume your episodes","resume"));
    List<MediaItem> out=new ArrayList<>();
    if(parent.equals("home")){for(var e:shelves().entrySet()){var shelf=e.getValue();out.add(folder(e.getKey(),shelf.title,"Recommended music",shelf.entries.get(0).artwork));}out.add(category("library","Your library","Your saved music is always here","library"));out.add(category("podcasts","Your podcasts","Followed shows and episodes","podcasts"));return out;}
    if(parent.startsWith("shelf:")){var shelf=shelves().get(parent);if(shelf==null)return out;for(var e:shelf.entries){if(e.track!=null&&!e.track.sourceUrl.isBlank())out.add(item(e.track,"recommend:"+JsonDisk.hash(e.track.sourceUrl)));else if(!e.browseId.isBlank())out.add(folder("collection:"+JsonDisk.hash(e.browseId),e.title,e.artist,e.artwork));}return out;}
    if(parent.startsWith("collection:"))return collectionItems(parent);
    if(parent.equals("library")){out.add(category("playlist:favourites","Liked Songs","Your favourite songs","library"));out.add(category("playlists","Playlists","Collections you saved on your phone","library"));out.add(category("playlist:"+SongDownloads.PLAYLIST_ID,"Downloaded","Ready on this phone","download"));if(!DownloadList.playlist(context).tracks.isEmpty())out.add(category("playlist:"+DownloadList.PLAYLIST_ID,"Saved downloads","Songs saved for downloading","download"));return out;}
    if(parent.equals("playlists")||parent.startsWith("playlist:")){for(var p:library().playlists){if(parent.equals("playlists"))out.add(folder("playlist:"+p.id,p.name,p.tracks.size()+" songs",p.tracks.isEmpty()?icon("library"):p.tracks.get(0).artwork));else if(parent.equals("playlist:"+p.id)){for(int i=0;i<p.tracks.size();i++)if(!p.tracks.get(i).sourceUrl.isEmpty())out.add(item(p.tracks.get(i),"track:"+p.id+":"+i));break;}}return out;}
    PodcastStore podcasts=PodcastStore.get(context);
    if(parent.equals("podcasts")){for(var show:podcasts.subscriptions())out.add(folder("show:"+key(show.feedUrl),show.title,show.author,show.artwork));}
    else if(parent.startsWith("show:")||parent.equals("continue"))for(var show:podcasts.knownShows()){if(!parent.equals("continue")&&!parent.equals("show:"+key(show.feedUrl)))continue;for(var ep:show.episodes)if(!parent.equals("continue")||podcasts.progress(ep.id)>0&&!podcasts.completed(ep.id))out.add(episode(ep));}
    return out;
  }
  MediaItem resolve(String id) throws Exception {
    if(id.equals("root"))return folder("root","Cadence");for(MediaItem root:children("root"))if(root.mediaId.equals(id))return root;if(id.equals("playlists"))return category(id,"Playlists","Your saved collections","library");
    if(id.startsWith("shelf:")){var shelf=shelves().get(id);if(shelf!=null)return folder(id,shelf.title);}
    if(id.startsWith("collection:")){var e=collection(id);if(e!=null)return folder(id,e.title,e.artist,e.artwork);}
    if(id.startsWith("collection-track:")){int split=id.lastIndexOf(':');for(var item:collectionItems("collection:"+id.substring(17,split)))if(item.mediaId.equals(id))return item;}
    if(id.startsWith("recommend:"))for(var shelf:shelves().values())for(var e:shelf.entries)if(e.track!=null&&id.equals("recommend:"+JsonDisk.hash(e.track.sourceUrl)))return item(e.track,id);
    if(id.startsWith("playlist:")||id.startsWith("track:")){String key=id.substring(id.indexOf(':')+1);int index=-1;if(id.startsWith("track:")){int sep=key.lastIndexOf(':');if(sep<1)throw new IllegalArgumentException("Invalid track ID");index=Integer.parseInt(key.substring(sep+1));key=key.substring(0,sep);}for(var playlist:library().playlists)if(playlist.id.equals(key)){if(id.startsWith("playlist:"))return folder(id,playlist.name);if(index>=0&&index<playlist.tracks.size()&&!playlist.tracks.get(index).sourceUrl.isBlank())return item(playlist.tracks.get(index),id);}}
    if(id.startsWith("show:")||id.startsWith("episode:"))for(var show:PodcastStore.get(context).knownShows()){if(id.equals("show:"+key(show.feedUrl)))return folder(id,show.title,show.author,show.artwork);for(var ep:show.episodes)if(id.equals("episode:"+key(ep.id)))return episode(ep);}
    throw new IllegalArgumentException("That library item is unavailable.");
  }
  static final class Selection{final List<MediaItem> items;final int index;final long position;Selection(List<MediaItem> items,int index,long position){this.items=items;this.index=index;this.position=position;}}
  Selection selection(String id) throws Exception {
    MediaItem chosen=resolve(id);if(!Boolean.TRUE.equals(chosen.mediaMetadata.isPlayable))throw new IllegalArgumentException("Choose a playable item");List<MediaItem> items=List.of(chosen);long position=0;
    if(id.startsWith("track:"))items=children("playlist:"+id.substring(6,id.lastIndexOf(':')));
    else if(id.startsWith("collection-track:"))items=children("collection:"+id.substring(17,id.lastIndexOf(':')));
    else if(id.startsWith("recommend:")){for(var shelf:shelves().entrySet())if(shelf.getValue().entries.stream().anyMatch(e->e.track!=null&&id.equals("recommend:"+JsonDisk.hash(e.track.sourceUrl)))){List<MediaItem> songs=new ArrayList<>();for(var item:children(shelf.getKey()))if(Boolean.TRUE.equals(item.mediaMetadata.isPlayable))songs.add(item);items=songs;break;}}
    else if(id.startsWith("episode:")){PodcastStore p=PodcastStore.get(context);for(var show:p.knownShows())for(var ep:show.episodes)if(id.equals("episode:"+key(ep.id))){position=p.progress(ep.id);items=children("show:"+key(show.feedUrl));break;}}
    for(int i=0;i<items.size();i++)if(items.get(i).mediaId.equals(id))return new Selection(items,i,position);throw new IllegalArgumentException("That item is no longer in the collection");
  }
  List<MediaItem> search(String term) throws Exception {
    String q=term.trim().toLowerCase(Locale.ROOT);List<MediaItem> out=new ArrayList<>();Set<String> seen=new HashSet<>();if(q.isEmpty())return out;
    for(var playlist:library().playlists)for(int i=0;i<playlist.tracks.size();i++){Track t=playlist.tracks.get(i);if(!t.sourceUrl.isBlank()&&(t.title+" "+t.artist).toLowerCase(Locale.ROOT).contains(q)&&seen.add(t.sourceUrl))out.add(item(t,"track:"+playlist.id+":"+i));}
    for(var shelf:shelves().values())for(var e:shelf.entries)if(e.track!=null&&(e.title+" "+e.artist).toLowerCase(Locale.ROOT).contains(q)&&seen.add(e.track.sourceUrl))out.add(item(e.track,"recommend:"+JsonDisk.hash(e.track.sourceUrl)));
    for(var show:PodcastStore.get(context).subscriptions())if(show.title.toLowerCase(Locale.ROOT).contains(q))out.add(folder("show:"+key(show.feedUrl),show.title,show.author,show.artwork));return out;
  }
  private static String key(String value){return android.util.Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);}
}
