package app.cadence.music;

import androidx.lifecycle.FlowLiveDataConversions;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import app.cadence.core.MusicCatalog;
import app.cadence.core.Podcasts;
import app.cadence.core.SearchRanker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;

/** One lifecycle-owned source of truth for the complete Search surface. */
public final class SearchViewModel extends ViewModel {
  public enum Phase { IDLE, TYPING, LOADING, RESULTS, EMPTY, ERROR }

  public record Item(
      SearchRanker.Ranked ranking, MusicCatalog.Entry music, Podcasts.Show podcast) {}

  public record Payload(
      List<MusicCatalog.Entry> music,
      List<Podcasts.Show> podcasts,
      List<Item> ranked,
      String error) {
    public Payload {
      music = List.copyOf(music);
      podcasts = List.copyOf(podcasts);
      ranked = List.copyOf(ranked);
      error = error == null ? "" : error;
    }

    public static Payload empty() { return new Payload(List.of(), List.of(), List.of(), ""); }
  }

  public record UiState(
      Phase phase,
      String query,
      String filter,
      Payload payload,
      List<String> suggestions,
      String message,
      boolean submitted,
      int scrollPosition) {
    public UiState {
      query = query == null ? "" : query;
      filter = filter == null || filter.isBlank() ? "All" : filter;
      payload = payload == null ? Payload.empty() : payload;
      suggestions = List.copyOf(suggestions == null ? List.of() : suggestions);
      message = message == null ? "" : message;
    }
  }

  public interface Repository {
    Payload search(String query, String filter) throws Exception;
    List<String> suggestions(String query) throws Exception;
    default List<String> recentQueries() { return List.of(); }
  }

  interface Task { void cancel(); }

  interface Dispatcher {
    Task schedule(long delayMillis, Runnable task);
    Task execute(Runnable task);
    void close();
  }

  private static final class ExecutorDispatcher implements Dispatcher {
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(3);
    ExecutorDispatcher() { executor.setRemoveOnCancelPolicy(true); }
    public Task schedule(long delay, Runnable task) {
      var future = executor.schedule(task, delay, TimeUnit.MILLISECONDS);
      return () -> future.cancel(true);
    }
    public Task execute(Runnable task) {
      var future = executor.submit(task);
      return () -> future.cancel(true);
    }
    public void close() { executor.shutdownNow(); }
  }

  private static final String QUERY_KEY = "search.query";
  private static final String FILTER_KEY = "search.filter";
  private static final String SCROLL_KEY = "search.scroll";
  private final SavedStateHandle savedState;
  private final Dispatcher dispatcher;
  private final MutableStateFlow<UiState> mutableState;
  private LiveData<UiState> lifecycleState;
  private Repository repository;
  private Task suggestionDelay, suggestionWork, resultDelay, resultWork;
  private long editGeneration, requestGeneration;

  public SearchViewModel(SavedStateHandle savedState) {
    this(savedState, new ExecutorDispatcher());
  }

  SearchViewModel(SavedStateHandle savedState, Dispatcher dispatcher) {
    this.savedState = savedState;
    this.dispatcher = dispatcher;
    String query = value(savedState.get(QUERY_KEY), "");
    String filter = value(savedState.get(FILTER_KEY), "All");
    Integer scroll = savedState.get(SCROLL_KEY);
    UiState initial = new UiState(query.isBlank() ? Phase.IDLE : Phase.TYPING, query, filter,
        Payload.empty(), List.of(), "", false, scroll == null ? 0 : scroll);
    mutableState = StateFlowKt.MutableStateFlow(initial);
  }

  public StateFlow<UiState> state() { return mutableState; }
  LiveData<UiState> lifecycleState() {
    if (lifecycleState == null) lifecycleState = FlowLiveDataConversions.asLiveData(
        mutableState, EmptyCoroutineContext.INSTANCE, 5_000L);
    return lifecycleState;
  }

  void attach(Repository repository) {
    this.repository = repository;
    UiState current = mutableState.getValue();
    if (!current.query().isBlank() && current.payload().ranked().isEmpty()) startSearch(false);
  }

  public void onQueryChanged(String value) {
    String query = value == null ? "" : value;
    cancelPending();
    long generation = ++editGeneration;
    requestGeneration++;
    UiState previous = mutableState.getValue();
    if (query.isBlank()) {
      emit(new UiState(Phase.IDLE, "", "All", Payload.empty(), List.of(), "", false, 0));
      return;
    }
    emit(new UiState(Phase.TYPING, query, previous.filter(), previous.payload(), List.of(), "",
        false, previous.scrollPosition()));
    suggestionDelay = dispatcher.schedule(150, () -> loadSuggestions(generation, query));
    resultDelay = dispatcher.schedule(400, () -> {
      if (generation == editGeneration) startSearch(false);
    });
  }

  public void submit(String value) {
    String query = value == null ? "" : value.trim();
    if (query.isEmpty()) return;
    cancelPending();
    editGeneration++;
    UiState current = mutableState.getValue();
    emit(new UiState(Phase.LOADING, query, current.filter(), current.payload(), List.of(), "", true,
        current.scrollPosition()));
    startSearch(true);
  }

  public void startNewSearch(String value) {
    String query = value == null ? "" : value.trim();
    if (query.isEmpty()) return;
    cancelPending(); editGeneration++;
    UiState current = mutableState.getValue();
    emit(new UiState(Phase.LOADING, query, "All", current.payload(), List.of(), "", true,
        current.scrollPosition()));
    startSearch(true);
  }

  public void restore(String query, String filter, int scrollPosition) {
    UiState current = mutableState.getValue();
    if (!current.query().isBlank()) return;
    String restored = query == null ? "" : query;
    String selected = filter == null || filter.isBlank() ? "All" : filter;
    emit(new UiState(restored.isBlank() ? Phase.IDLE : Phase.TYPING, restored, selected,
        Payload.empty(), List.of(), "", false, Math.max(0, scrollPosition)));
  }

  public void selectFilter(String filter) {
    UiState current = mutableState.getValue();
    String selected = filter == null || filter.isBlank() ? "All" : filter;
    if (selected.equals(current.filter())) return;
    Phase immediate = current.payload().ranked().isEmpty() ? current.phase() : Phase.RESULTS;
    emit(new UiState(immediate, current.query(), selected, current.payload(),
        immediate == Phase.TYPING ? current.suggestions() : List.of(), "", current.submitted(),
        current.scrollPosition()));
    if (!current.query().isBlank()) startSearch(current.submitted());
  }

  public void retry() { if (!mutableState.getValue().query().isBlank()) startSearch(true); }

  public void setScrollPosition(int position) {
    UiState current = mutableState.getValue();
    emit(new UiState(current.phase(), current.query(), current.filter(), current.payload(),
        current.suggestions(), current.message(), current.submitted(), Math.max(0, position)));
  }

  private void loadSuggestions(long generation, String query) {
    Repository source = repository;
    if (source == null) return;
    suggestionWork = dispatcher.execute(() -> {
      List<String> remote = List.of();
      try { remote = source.suggestions(query); } catch (Exception ignored) { }
      List<String> clean = cleanSuggestions(query, source.recentQueries(), remote);
      if (generation != editGeneration) return;
      UiState current = mutableState.getValue();
      if (current.phase() != Phase.TYPING || !current.query().equals(query)) return;
      emit(new UiState(Phase.TYPING, current.query(), current.filter(), current.payload(), clean, "",
          false, current.scrollPosition()));
    });
  }

  private void startSearch(boolean submitted) {
    Repository source = repository;
    UiState current = mutableState.getValue();
    if (source == null || current.query().trim().length() < 2) return;
    if (resultDelay != null) resultDelay.cancel();
    if (resultWork != null) resultWork.cancel();
    long request = ++requestGeneration;
    String query = current.query().trim(), filter = current.filter();
    emit(new UiState(Phase.LOADING, query, filter, current.payload(), List.of(), "", submitted,
        current.scrollPosition()));
    resultWork = dispatcher.execute(() -> {
      Payload result;
      try { result = source.search(query, filter); }
      catch (Exception failure) {
        result = new Payload(List.of(), List.of(), List.of(),
            "Search is unavailable. Check your connection and try again.");
      }
      if (request != requestGeneration) return;
      UiState latest = mutableState.getValue();
      if (!latest.query().trim().equals(query) || !latest.filter().equals(filter)) return;
      Phase phase = !result.ranked().isEmpty() ? Phase.RESULTS
          : result.error().isBlank() ? Phase.EMPTY : Phase.ERROR;
      emit(new UiState(phase, query, filter, result, List.of(), result.error(), submitted,
          latest.scrollPosition()));
    });
  }

  static List<String> cleanSuggestions(
      String query, List<String> recent, List<String> remote) {
    String wanted = query.trim().toLowerCase(Locale.ROOT);
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    for (List<String> source : List.of(recent, remote))
      for (String candidate : source) {
        String value = candidate == null ? "" : candidate.trim();
        String key = value.toLowerCase(Locale.ROOT);
        if (value.isEmpty() || key.equals(wanted)) continue;
        if (source == recent && !key.startsWith(wanted)) continue;
        unique.putIfAbsent(key, value);
        if (unique.size() == 6) return new ArrayList<>(unique.values());
      }
    return new ArrayList<>(unique.values());
  }

  private void emit(UiState state) {
    mutableState.setValue(state);
    savedState.set(QUERY_KEY, state.query());
    savedState.set(FILTER_KEY, state.filter());
    savedState.set(SCROLL_KEY, state.scrollPosition());
  }

  private void cancelPending() {
    if (suggestionDelay != null) suggestionDelay.cancel();
    if (suggestionWork != null) suggestionWork.cancel();
    if (resultDelay != null) resultDelay.cancel();
    if (resultWork != null) resultWork.cancel();
  }

  private static String value(String value, String fallback) {
    return value == null ? fallback : value;
  }

  @Override protected void onCleared() {
    cancelPending();
    dispatcher.close();
  }
}
