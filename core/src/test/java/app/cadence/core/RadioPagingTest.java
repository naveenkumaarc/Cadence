package app.cadence.core;
import static org.junit.Assert.*;
import com.google.gson.*;
import org.junit.Test;

public class RadioPagingTest {
 @Test public void radioUsesMusicMetadataAndDeduplicatesVideos() {
   String item="{\"playlistPanelVideoRenderer\":{\"videoId\":\"abcdefghijk\",\"title\":{\"runs\":[{\"text\":\"Song [Official Audio]\"}]},\"longBylineText\":{\"runs\":[{\"text\":\"Artist - Topic\"}]},\"lengthText\":{\"simpleText\":\"3:41\"},\"thumbnail\":{\"thumbnails\":[{\"url\":\"https://example.com/cover.jpg\"}]}}}";
   var tracks=MusicCatalog.parseRadio(JsonParser.parseString("{\"contents\":["+item+","+item+"]}").getAsJsonObject());
   assertEquals(1,tracks.size()); assertEquals("Song",tracks.get(0).title);
   assertEquals("Artist",tracks.get(0).artist); assertEquals(221,tracks.get(0).durationSeconds);
   assertFalse(tracks.get(0).podcast);
 }
 @Test public void playlistPagingUsesSongShelfRatherThanRecommendationCarousel() {
   var root=JsonParser.parseString("{\"musicCarouselShelfRenderer\":{\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"wrong\"}}]},\"musicPlaylistShelfRenderer\":{\"contents\":[{\"continuationItemRenderer\":{\"continuationEndpoint\":{\"commandExecutorCommand\":{\"commands\":[{\"continuationCommand\":{\"request\":\"CONTINUATION_REQUEST_TYPE_BROWSE\",\"token\":\"next-song-page\"}}]}}}}]}}").getAsJsonObject();
   assertEquals("next-song-page",MusicCatalog.collectionContinuation(root));
   var continuation=JsonParser.parseString("{\"onResponseReceivedActions\":[{\"appendContinuationItemsAction\":{\"continuationItems\":[{\"continuationItemRenderer\":{\"continuationEndpoint\":{\"continuationCommand\":{\"token\":\"page3\"}}}}]}}]}").getAsJsonObject();
   assertEquals("page3",MusicCatalog.collectionContinuation(continuation));
   assertEquals("",MusicCatalog.collectionContinuation(new JsonObject()));
 }
}
