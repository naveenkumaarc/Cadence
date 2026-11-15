package app.cadence.music;

import static org.junit.Assert.*;

import androidx.lifecycle.SavedStateHandle;
import app.cadence.core.MusicCatalog;
import app.cadence.core.SearchRanker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class SearchViewModelTest {
  private static final class TestDispatcher implements SearchViewModel.Dispatcher {
    private record Pending(long at, Runnable action, boolean[] cancelled) {}
    private final List<Pending> tasks = new ArrayList<>();
    private long now;
    public SearchViewModel.Task schedule(long delay, Runnable action) {
      boolean[] cancelled = {false}; tasks.add(new Pending(now + delay, action, cancelled));
      return () -> cancelled[0] = true;
    }
    public SearchViewModel.Task execute(Runnable action) { return schedule(0, action); }
    public void close() { tasks.clear(); }
    void advanceBy(long millis) { now += millis; runDue(); }
    void moveClock(long millis) { now += millis; }
    void runNext() {
      Pending next = tasks.stream().filter(task -> task.at <= now)
          .min(Comparator.comparingLong(Pending::at)).orElseThrow();
      tasks.remove(next); if (!next.cancelled[0]) next.action.run();
    }
    Runnable takeNextAction() {
      Pending next = tasks.stream().filter(task -> task.at <= now)
          .min(Comparator.comparingLong(Pending::at)).orElseThrow();
      tasks.remove(next); return next.action;
    }
    void runDue() {
      while (true) {
        Pending next = tasks.stream().filter(task -> task.at <= now)
            .min(Comparator.comparingLong(Pending::at)).orElse(null);
        if (next == null) return;
        tasks.remove(next); if (!next.cancelled[0]) next.action.run();
      }
    }
  }

  private static final class FakeRepository implements SearchViewModel.Repository {
    final Map<String, SearchViewModel.Payload> values = new HashMap<>();
    final List<String> calls = new ArrayList<>();
    RuntimeException failure;
    public SearchViewModel.Payload search(String query, String filter) {
      calls.add(query + ":" + filter);
      if (failure != null) throw failure;
      return values.getOrDefault(query, SearchViewModel.Payload.empty());
    }
    public List<String> suggestions(String query) { return List.of(query + " songs"); }
  }

  private static SearchViewModel.Payload result(String query) {
    MusicCatalog.Entry entry = new MusicCatalog.Entry();
    entry.title = query; entry.kind = "song";
    SearchRanker.Document document = new SearchRanker.Document(
        query, SearchRanker.Type.SONG, query, "Artist", "", "", "", "", 0,
        false, false, false, false);
    SearchViewModel.Item item = new SearchViewModel.Item(
        new SearchRanker.Ranked(document, 100, true), entry, null);
    return new SearchViewModel.Payload(List.of(entry), List.of(), List.of(item), "");
  }

  @Test public void debounceEmitsLoadingThenResults() {
    TestDispatcher dispatcher = new TestDispatcher(); FakeRepository repository = new FakeRepository();
    repository.values.put("ilayaraja", result("ilayaraja"));
    SearchViewModel model = new SearchViewModel(new SavedStateHandle(), dispatcher);model.attach(repository);
    model.onQueryChanged("ilayaraja");
    assertEquals(SearchViewModel.Phase.TYPING, model.state().getValue().phase());
    dispatcher.advanceBy(399);assertEquals(SearchViewModel.Phase.TYPING, model.state().getValue().phase());
    dispatcher.advanceBy(1);assertEquals(SearchViewModel.Phase.RESULTS, model.state().getValue().phase());
    assertEquals(List.of("ilayaraja:All"), repository.calls);
  }

  @Test public void rapidTypingCancelsEarlierRequest() {
    TestDispatcher dispatcher = new TestDispatcher(); FakeRepository repository = new FakeRepository();
    repository.values.put("ilayaraja", result("ilayaraja"));
    SearchViewModel model = new SearchViewModel(new SavedStateHandle(), dispatcher);model.attach(repository);
    model.onQueryChanged("ilai");dispatcher.advanceBy(200);model.onQueryChanged("ilayaraja");dispatcher.advanceBy(400);
    assertEquals(List.of("ilayaraja:All"), repository.calls);
    assertEquals("ilayaraja", model.state().getValue().query());
  }

  @Test public void outOfOrderResponseIsIgnored() {
    TestDispatcher dispatcher = new TestDispatcher(); FakeRepository repository = new FakeRepository();
    repository.values.put("first", result("first"));repository.values.put("second", result("second"));
    SearchViewModel model = new SearchViewModel(new SavedStateHandle(), dispatcher);model.attach(repository);
    model.submit("first");Runnable stale = dispatcher.takeNextAction();
    model.submit("second");dispatcher.runDue();
    assertEquals("second", model.state().getValue().payload().ranked().get(0).music().title);
    stale.run();
    assertEquals("second", model.state().getValue().payload().ranked().get(0).music().title);
  }

  @Test public void emptyAndErrorAreExplicitStates() {
    TestDispatcher dispatcher = new TestDispatcher(); FakeRepository repository = new FakeRepository();
    SearchViewModel model = new SearchViewModel(new SavedStateHandle(), dispatcher);model.attach(repository);
    model.onQueryChanged("nothing");dispatcher.advanceBy(400);
    assertEquals(SearchViewModel.Phase.EMPTY, model.state().getValue().phase());
    repository.failure = new RuntimeException("offline");model.onQueryChanged("failure");dispatcher.advanceBy(400);
    assertEquals(SearchViewModel.Phase.ERROR, model.state().getValue().phase());
  }

  @Test public void queryAndChipAreSaved() {
    TestDispatcher dispatcher = new TestDispatcher(); SavedStateHandle saved = new SavedStateHandle();
    SearchViewModel model = new SearchViewModel(saved, dispatcher);model.attach(new FakeRepository());
    model.onQueryChanged("ilayaraja");model.selectFilter("Songs");
    assertEquals("ilayaraja", saved.get("search.query"));assertEquals("Songs", saved.get("search.filter"));
  }
}
