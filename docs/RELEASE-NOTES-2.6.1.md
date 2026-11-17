# Cadence 2.6.1

Cadence 2.6.1 is a focused Search reliability and layout update.

Search now has one lifecycle-aware state model for Idle, Typing, Loading, Results, Empty and Error. Live results appear without leaving and reopening Search, stale work is cancelled, explicit submission runs immediately, and the query and selected type survive tab changes and recreation.

Suggestions now use a compact Apple-style list beneath the always-stable type chips. Matching recents come first; duplicates and the exact typed query are removed; the list is limited to six rows; and the fill action edits the field without accidentally submitting. Loading uses result-shaped skeleton rows, while empty and error states provide useful next actions.

The search field remains one stable focus owner while results change, preserving cursor position and IME composition. Search content receives the keyboard inset, dragging dismisses the keyboard, and bottom navigation and the mini player stay out of the way until the keyboard closes. The title collapses while searching to make room on small screens.

There are no database, backup, Google Drive sync or user-data format migrations, and no new permissions. NewPipeExtractor integration, package name and signing identity are unchanged.

Before/after references and the Light/Dark captures for all six Search states are included under `docs/screenshots/search-2.6.1` in the source tree.

## Download

Download `Cadence-2.6.1.apk` from this release and verify it with `SHA256SUMS-2.6.1.txt`. The APK uses the retained Cadence release certificate and version code 17, so it can update earlier release-signed Cadence installations in place.
