package app.cadence.core;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public final class SearchRankerTest {
  private static SearchRanker.Document item(String id,SearchRanker.Type type,String title,String artist,String language) {
    return new SearchRanker.Document(id,type,title,artist,type==SearchRanker.Type.ALBUM?title:"","","",language,0,false,false,false,false);
  }
  private static final List<SearchRanker.Document> DATA=List.of(
      item("rajini-song",SearchRanker.Type.SONG,"Rajini introduction theme","Anirudh Ravichander","tamil"),
      item("harini-song",SearchRanker.Type.SONG,"Aalanguyil","Harini","tamil"),
      item("queen-podcast",SearchRanker.Type.PODCAST,"Queen: The official podcast","BBC","english"),
      item("loose-podcast",SearchRanker.Type.PODCAST,"Intro to royalty","Someone","english"),
      item("petta",SearchRanker.Type.ALBUM,"Petta","Anirudh Ravichander","tamil"),
      item("rahman",SearchRanker.Type.ARTIST,"A R Rahman","","tamil"),
      item("rahman-song",SearchRanker.Type.SONG,"Jai Ho","A R Rahman","hindi"),
      item("rahman-album",SearchRanker.Type.ALBUM,"Roja","A R Rahman","tamil"));

  private String first(String query){return SearchRanker.rank(SearchRanker.parse(query),DATA,true).get(0).document().id();}

  @Test public void intentAndFillerWordsDoNotHideTheWantedType() {
    List<SearchRanker.Ranked> rajini=SearchRanker.rank(SearchRanker.parse("rajini intro songs"),DATA,true);
    assertEquals("rajini-song",rajini.get(0).document().id());
    assertFalse(rajini.stream().anyMatch(v->v.document().id().equals("loose-podcast")));
    assertEquals("harini-song",first("harini tamil songs"));
    assertEquals("queen-podcast",first("queen podcast"));
    assertEquals("petta",first("petta album"));
  }

  @Test public void artistExactMatchBecomesTopResultThenMusicFollows() {
    List<SearchRanker.Ranked> ranked=SearchRanker.rank(SearchRanker.parse("ar rahman"),DATA,true);
    assertEquals(List.of("rahman","rahman-song","rahman-album"),ranked.stream().map(v->v.document().id()).toList());
  }

  @Test public void aliasesMisspellingsAndTamilScriptResolveToTheSameResult() {
    assertEquals("rajini-song",first("rajinikant"));
    assertEquals("harini-song",first("harinie"));
    assertEquals(first("rajini"),first("ரஜினிகாந்த்"));
  }

  @Test public void emptyShortAndRepeatedRunsAreSafeAndStable() {
    assertTrue(SearchRanker.rank(SearchRanker.parse(""),List.of(),true).isEmpty());
    assertTrue(SearchRanker.rank(SearchRanker.parse("a"),DATA,true).isEmpty());
    List<String> first=SearchRanker.rank(SearchRanker.parse("ar rahman"),DATA,true).stream().map(v->v.document().id()).toList();
    for(int i=0;i<20;i++)assertEquals(first,SearchRanker.rank(SearchRanker.parse("ar rahman"),DATA,true).stream().map(v->v.document().id()).toList());
  }
}
