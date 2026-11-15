# Cadence 2.6.0

Cadence 2.6.0 is a focused playback and interface reliability release.

Album, playlist and podcast detail pages now use a contextual back bar and a shared padded header. Album actions reflow safely on narrow screens and at large font sizes, the first track has clear separation, and album library/download actions expose their actual state.

Search keeps one stable text field while results update, preserving focus, cursor position and IME composition. Queries are debounced and stale work is cancelled; loading uses skeleton rows, dragging results dismisses the keyboard, and playback/navigation chrome moves out of the way while the IME is visible. Recents also gain compact clearing and swipe-to-delete.

Library filters now share one saved source of truth with their content. Taps, horizontal swipes and state restoration keep the selected chip aligned with the visible page, and the chip row scrolls selected filters fully into view. Equivalent content-control chips were audited across Search, Home and podcast languages.

The Now Playing equalizer is now a real app-owned audio effect attached to Media3's active audio session. Its sheet exposes device presets, per-band frequency and dB controls, enable/reset actions, persistent app-wide settings, explicit unsupported-device feedback, and the optional system equalizer as a secondary action. The effect is reattached when the player or audio session changes and remains service-owned for background, notification and Android Auto playback.

Additional polish covers gesture-safe Now Playing controls, fading marquee edges, music/podcast-specific skip actions, compact Library actions, swipe gestures and haptics on the mini player, thin progress/divider treatments, and consistent 48 dp interaction targets.

There are no database, backup, Google Drive sync or user-data format migrations, and no new permissions. NewPipeExtractor integration, package name and signing identity are unchanged.

## Download

Download `Cadence-2.6.0.apk` from this release and verify it with `SHA256SUMS-2.6.0.txt`. The APK uses the retained Cadence release certificate and version code 16, so it can update earlier release-signed Cadence installations in place.
