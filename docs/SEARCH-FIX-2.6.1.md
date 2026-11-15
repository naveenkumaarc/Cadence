# Search reliability audit

## Root cause

Search did not use a ViewModel before this change. The first character entered the short-query branch in `MainActivity` and set `searchResultsBody` to `GONE`. Later query, loading and result callbacks repopulated that same view but never restored its visibility. Navigating away and back constructed a new visible container, exposing results that had already arrived. The network request, debounce and ranking were not the cause.

Suggestions were a separate child panel owned by the search field. It used the standard secondary button component for every completion, performed a second song lookup, did not remove exact or duplicate completions, and sat before the filter chips. The pill styling and missing row spacing produced the recorded overlap and pushed the intended content below the keyboard.

The IME inset was applied to the outer `ScrollView`, not the changing results/suggestions content. With resize and non-clipping scroll padding combined, the child scroll range could still end underneath the IME.

## Resolution

- `SearchViewModel` owns one immutable `StateFlow<UiState>` with Idle, Typing, Loading, Results, Empty and Error phases.
- Query and chip are retained through `SavedStateHandle`; payload and scroll survive tab changes in the active session.
- Suggestions debounce at 150 ms, live results at 400 ms, and explicit submission runs immediately. Scheduled and in-flight work is cancelled and generation checked.
- Suggestions and results share one content region beneath a stable chip row. Suggestions are recent-first, deduplicated, limited to six and never repeat the typed query.
- IME padding belongs to that content region. Search focus and its editable composing text are never replaced by result rendering.

## Physical-device checks

- Repeat composition with Tamil, Telugu and Malayalam IMEs and their suggestion strips.
- At 130% and 200% text size, check Typing and Results on a small portrait phone and landscape.
- Verify the final row scrolls above Gboard, Samsung Keyboard and one regional keyboard.
- Confirm a slow or offline catalogue produces skeleton, Empty and Error states without losing focus.
