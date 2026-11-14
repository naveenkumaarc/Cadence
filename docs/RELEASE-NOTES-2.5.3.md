# Cadence 2.5.3

Cadence 2.5.3 improves search relevance, adds a contrast-safe accent colour picker with Rose as the new-install default, and introduces the new Rose waveform app icon.

Search now understands music, album, artist, playlist and podcast intent; treats language and filler words separately from names; supports spelling aliases and fuzzy matching; and retries relaxed tokens before showing an empty state. Results from all five types are scored together off the main thread, stale requests are cancelled, recent results are cached, and the All tab shows a Top result followed by score-ordered sections instead of always placing podcasts first.

Personalise now includes Rose, Violet, Indigo blue, Coral, Teal, Amber, Monochrome and Classic blue presets, plus a custom hue/lightness and hex editor. Every preset and adjusted custom colour meets WCAG AA for accent text and filled controls in both appearances. Existing implicit-blue users remain on Classic blue, existing custom colours remain custom, and new installs start with Rose. The choice applies immediately to the app, widget and media notification without interrupting playback. Destructive actions use a separate danger token and confirmation.

The launcher, round launcher, Android 13 monochrome, Android 12 splash, notification small icon, widget placeholder and README/store artwork now use an original Rose circle with four white round-capped bars. Legacy density PNGs and a 512×512 export are generated from the same vector master.

There are no database, backup, Google Drive sync or other user-data migrations, and no new permissions. Appearance remains phone-local because it was not previously part of backup or Drive sync. NewPipeExtractor integration and streaming-source handling are unchanged.

## Download

Download `Cadence-2.5.3.apk` from this release and verify it with `SHA256SUMS-2.5.3.txt`. The APK uses the retained Cadence release certificate and version code 15, so it can update earlier release-signed Cadence installations in place.
