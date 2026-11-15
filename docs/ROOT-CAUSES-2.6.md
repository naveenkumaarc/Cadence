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
