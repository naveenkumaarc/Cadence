# Cadence 2.6 root-cause notes

## Task 1 — album detail actions

The catalogue-detail renderer appended three full-width buttons directly to the vertical body.
Only one explicit 8 dp spacer separated the first two actions, the save action had no spacer,
and the track list began after an 18 dp gap. The layout therefore had no horizontal action-row
contract and no way to reflow at large font scales or narrow widths. Detail pages also reused the
global app bar, while the artwork started at the generic body inset and depended on the prior
scroll position being reset.

The fix introduces a measured action layout that keeps Play and Shuffle flexible, gives all four
actions 48 dp minimum targets, and moves actions to additional rows before they can intersect. A
shared padded detail header now supplies centred artwork, title, byline and metadata for catalogue,
playlist and podcast pages. Detail app bars own their back navigation and title, and the first track
is separated from the actions by a 24 dp token spacer.

## Task 2 — search keyboard focus

`searchMusic()` and `applySearch()` both called `showSearch()`. That method removed every child from
the screen body and created a new `EditText`, so each loading/result transition destroyed the IME's
focus owner and its composing region. The root inset listener also combined system-bar and IME
insets, deliberately lifting the mini player and navigation above the keyboard.

Search now keeps one attached input while only its result container is replaced. Debounced work is
still generation-checked and stale work is cancelled; loading uses skeleton rows without replacing
the field. IME insets hide playback/navigation chrome, and a user drag dismisses the keyboard while
result updates never do.

## Task 3 — library chip selection

The chip background was fixed when `showLibrary()` created the row, but a tap changed
`libraryFilter` and called only `renderLibraryContent()`. The content therefore followed the new
value while the existing chip drawables still represented the old value. The selected filter also
was not included in saved instance state.

Library filter changes now flow through the single `libraryFilter` state and rebuild the chip row
and content together. That value is saved/restored, horizontal page gestures update the same state,
and the edge-to-edge scroll row automatically brings its selected chip fully into view.
