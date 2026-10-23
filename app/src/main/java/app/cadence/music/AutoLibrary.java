package app.cadence.music;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.media3.common.*;
import app.cadence.core.*;
import java.util.*;

/** Catalogue IDs are resolved inside Cadence; remote clients cannot supply media URIs. */
final class AutoLibrary {
  private final Context context;
  AutoLibrary(Context c) { context = c; }
  static MediaItem folder(String id, String title) {
    return new MediaItem.Builder().setMediaId(id).setMediaMetadata(new MediaMetadata.Builder()
        .setTitle(title).setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build();
  }
  static MediaItem item(Track t, String id) throws Exception {
    Bundle extra = new Bundle(); extra.putString("track",Library.encode(t).toString());
    var meta = new MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setExtras(extra)
        .setIsBrowsable(false).setIsPlayable(true).setMediaType(t.podcast ? MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE : MediaMetadata.MEDIA_TYPE_MUSIC);
    if (!t.artwork.isEmpty()) meta.setArtworkUri(Uri.parse(t.artwork));
    return new MediaItem.Builder().setMediaId(id).setUri(t.sourceUrl).setMediaMetadata(meta.build()).build();
  }
  List<MediaItem> children(String parent) throws Exception {
    if (parent.equals("root")) return List.of(folder("playlist:favourites","Liked Songs"),folder("playlists","Playlists"),folder("podcasts","Podcasts"),folder("continue","Continue listening"));
    List<MediaItem> out = new ArrayList<>();
    if (parent.equals("playlists") || parent.startsWith("playlist:")) {
      Library lib = new Library(context);
      for (var p : lib.playlists) {
        if (parent.equals("playlists")) out.add(folder("playlist:"+p.id,p.name));
        else if (parent.equals("playlist:"+p.id)) {
          for (int i=0;i<p.tracks.size();i++) if (!p.tracks.get(i).sourceUrl.isEmpty())
            out.add(item(p.tracks.get(i),"track:"+p.id+":"+i));
          break;
        }
      }
    } else {
      PodcastStore podcasts=PodcastStore.get(context);
      if (parent.equals("podcasts")) for(var show:podcasts.subscriptions()) out.add(folder("show:"+key(show.feedUrl),show.title));
      else if (parent.startsWith("show:") || parent.equals("continue")) for(var show:podcasts.knownShows()) {
        if (!parent.equals("continue") && !parent.equals("show:"+key(show.feedUrl))) continue;
        for(var ep:show.episodes) if (!parent.equals("continue") || podcasts.progress(ep.id)>0 && !podcasts.completed(ep.id))
          out.add(item(podcasts.playable(ep),"episode:"+key(ep.id)));
      }
    }
    return out;
  }
  MediaItem resolve(String id) throws Exception {
    if (id.equals("root")) return folder("root","Cadence");
    if (id.equals("playlists") || id.equals("podcasts") || id.equals("continue"))
      return folder(id,id.equals("playlists")?"Playlists":id.equals("podcasts")?"Podcasts":"Continue listening");
    if (id.startsWith("playlist:") || id.startsWith("track:")) {
      Library library=new Library(context);
      String key=id.substring(id.indexOf(':')+1); int index=-1;
      if(id.startsWith("track:")) {
        int separator=key.lastIndexOf(':');
        if(separator<1) throw new IllegalArgumentException("Invalid track ID");
        index=Integer.parseInt(key.substring(separator+1)); key=key.substring(0,separator);
      }
      for(var playlist:library.playlists) if(playlist.id.equals(key)) {
        if(id.startsWith("playlist:")) return folder(id,playlist.name);
        if(index<0 || index>=playlist.tracks.size() || playlist.tracks.get(index).sourceUrl.isEmpty()) break;
        return item(playlist.tracks.get(index),id);
      }
    } else if(id.startsWith("show:") || id.startsWith("episode:")) {
      PodcastStore podcasts=PodcastStore.get(context);
      for(var show:podcasts.knownShows()) {
        if(id.equals("show:"+key(show.feedUrl))) return folder(id,show.title);
        for(var ep:show.episodes) if(id.equals("episode:"+key(ep.id))) return item(podcasts.playable(ep),id);
      }
    }
    throw new IllegalArgumentException("That library item is unavailable.");
  }
  static final class Selection {
    final List<MediaItem> items; final int index; final long position;
    Selection(List<MediaItem> items,int index,long position) {this.items=items;this.index=index;this.position=position;}
  }
  Selection selection(String id) throws Exception {
    MediaItem chosen=resolve(id);
    if(!Boolean.TRUE.equals(chosen.mediaMetadata.isPlayable)) throw new IllegalArgumentException("Choose a playable item");
    List<MediaItem> items=List.of(chosen); long position=0;
    if(id.startsWith("track:")) items=children("playlist:"+id.substring(6,id.lastIndexOf(':')));
    else if(id.startsWith("episode:")) {
      PodcastStore podcasts=PodcastStore.get(context);
      for(var show:podcasts.knownShows()) for(var ep:show.episodes) if(id.equals("episode:"+key(ep.id))) {
        position=podcasts.progress(ep.id); items=children("show:"+key(show.feedUrl)); break;
      }
    }
    for(int i=0;i<items.size();i++) if(items.get(i).mediaId.equals(id)) return new Selection(items,i,position);
    throw new IllegalArgumentException("That item is no longer in the collection");
  }
  List<MediaItem> search(String term) throws Exception {
    String query=term.trim().toLowerCase(Locale.ROOT); List<MediaItem> out=new ArrayList<>();
    if(query.isEmpty()) return out;
    Library library=new Library(context);
    for(var playlist:library.playlists) for(int i=0;i<playlist.tracks.size();i++) {
      Track track=playlist.tracks.get(i);
      if(!track.sourceUrl.isEmpty() && (track.title+" "+track.artist).toLowerCase(Locale.ROOT).contains(query))
        out.add(item(track,"track:"+playlist.id+":"+i));
    }
    for(var show:PodcastStore.get(context).subscriptions())
      if(show.title.toLowerCase(Locale.ROOT).contains(query)) out.add(folder("show:"+key(show.feedUrl),show.title));
    return out;
  }
  private static String key(String value) { return android.util.Base64.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING); }
}
