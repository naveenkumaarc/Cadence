package app.cadence.core;
import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;

public class RadioPlanTest {
  private RadioPlan.Item music(String id,boolean radio) {
    return new RadioPlan.Item("https://www.youtube.com/watch?v="+id,radio,false);
  }
  @Test public void manualTailRetainsTheRadioContext() {
    var radio=music("abcdefghijk",true);
    assertEquals(radio.source,RadioPlan.seed(List.of(radio,music("12345678901",false)),0));
    assertEquals(radio.source,RadioPlan.seed(List.of(radio,music("12345678901",false)),1));
  }
  @Test public void traversalOrderRatherThanPhysicalIndexChoosesTheSeed() {
    var first=music("abcdefghijk",true);var last=music("12345678901",true);
    assertEquals(last.source,RadioPlan.seed(List.of(first,music("98765432109",false),last),1));
    assertEquals(first.source,RadioPlan.seed(List.of(last,music("98765432109",false),first),1));
  }
  @Test public void explicitPlaylistsAndPodcastsStayFinite() {
    var song=music("abcdefghijk",true);
    var episode=new RadioPlan.Item("https://example.com/episode.mp3",false,true);
    assertEquals("",RadioPlan.seed(List.of(music("abcdefghijk",false)),0));
    assertEquals("",RadioPlan.seed(List.of(song,episode),0));
    assertEquals("",RadioPlan.seed(List.of(song,episode),1));
  }
  @Test public void onlyTheLastThreeUpcomingItemsTriggerExtension() {
    var song=music("abcdefghijk",true);
    assertEquals("",RadioPlan.seed(List.of(song,song,song,song),0));
    assertEquals(song.source,RadioPlan.seed(List.of(song,song,song,song),1));
    assertEquals("",RadioPlan.seed(List.of(song),-1));
  }
}
