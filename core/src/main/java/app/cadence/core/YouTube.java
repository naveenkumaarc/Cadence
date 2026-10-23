package app.cadence.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

public final class YouTube {
  static {
    NewPipe.init(new HttpDownloader());
  }

  public static synchronized List<Track> search(String query) throws Exception {
    var handler =
        ServiceList.YouTube.getSearchQHFactory()
            .fromQuery(query, Collections.singletonList("videos"), "");
    SearchInfo info = SearchInfo.getInfo(ServiceList.YouTube, handler);
    List<Track> result = new ArrayList<>();
    for (InfoItem item : info.getRelatedItems())
      if (item instanceof StreamInfoItem stream && stream.getDuration() > 0)
        result.add(
            new Track(
                MusicText.title(stream.getName()),
                MusicText.artist(stream.getUploaderName()),
                stream.getUrl(),
                artwork(stream.getThumbnails()),
                stream.getDuration()));
    return result;
  }

  public static synchronized Resolved resolve(String url) throws Exception {
    StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, Links.youtubeVideo(url));
    AudioStream chosen =
        info.getAudioStreams().stream()
            .filter(s -> s.isUrl() && s.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP)
            .max(Comparator.comparingInt(AudioStream::getAverageBitrate))
            .orElseThrow(
                () ->
                    new IOException(
                        "No supported audio stream. Try another version of this song."));
    Track track =
        new Track(
            MusicText.title(info.getName()),
            MusicText.artist(info.getUploaderName()),
            info.getUrl(),
            artwork(info.getThumbnails()),
            info.getDuration());
    return new Resolved(
        track,
        chosen.getContent(),
        chosen.getFormat() == null ? "audio/webm" : chosen.getFormat().getMimeType(),
        chosen.getAverageBitrate());
  }

  public static synchronized Track metadata(String url) throws Exception {
    StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, Links.youtubeVideo(url));
    return new Track(MusicText.title(info.getName()), MusicText.artist(info.getUploaderName()),
        info.getUrl(), artwork(info.getThumbnails()), info.getDuration());
  }

  private static String artwork(List<Image> images) {
    return images.isEmpty() ? "" : images.get(images.size() - 1).getUrl();
  }

  public static final class Resolved {
    public final Track track;
    public final String streamUrl, mimeType;
    public final int bitrate;

    Resolved(Track t, String url, String mime, int rate) {
      track = t;
      streamUrl = url;
      mimeType = mime;
      bitrate = rate;
    }
  }
}
