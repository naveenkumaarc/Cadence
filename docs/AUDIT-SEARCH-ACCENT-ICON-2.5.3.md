# Cadence 2.5.3 focused audit

## Search

`MainActivity.searchBox` owns query input and 350 ms source suggestions. Submitted searches previously called `MusicCatalog.searchAll`, which concatenated YouTube Music Songs and Albums, plus `Podcasts.search`, which queried the iTunes podcast directory. The All screen then rendered Podcasts, Songs and Albums in a fixed order. There was no query classification, relaxed retry, alias/fuzzy match, minimum score, merged ranking or short-lived cache. Consequently filler and language words were sent literally, and any podcast result was placed above music regardless of relevance.

The source models expose title, artist, artwork, stable source/browse ID and result type. They do not expose consistent composer, film, popularity or play-count data, so ranking can use those fields only when a source supplies them in the future. Local liked, downloaded, recent-search and subscribed signals are available without changing stored formats.

## Appearance

System/Light/Dark is stored in `AppearanceStore` DataStore. Motion, haptics and the legacy custom integer `colour` are stored in the existing phone-local `appearance` preferences. `UiStyle.palette` is the single semantic colour source used by the activity, dialogs, artwork placeholders and runtime widget tinting. The existing custom-colour dialog supported hue, saturation and hex, but had no named presets and recreated the activity to apply a change.

Appearance is not included in `CloudData.PREFS`, the portable backup playback preferences, or Google Drive sync. The accent update therefore remains local and does not expand those compatibility formats.

## Icon inventory

- `mipmap-anydpi-v26/ic_launcher.xml`: adaptive launcher icon; black background, shared full-colour foreground and the same full-colour asset incorrectly reused as monochrome.
- `drawable/cadence_foreground.xml`: indigo circle with four black round-capped waveform bars.
- `drawable/ic_cadence.xml`: black-square widget placeholder with the same indigo/black mark.
- `design/cadence-loop.svg`: editable but stale earlier loop/play artwork.
- `drawable/car_*.xml`: Android Auto browse-category symbols, not the Cadence brand mark.
- `drawable/widget_*.xml`: transport symbols, not brand marks; their tint is applied at runtime.

There was no manifest round icon, density-specific legacy launcher set, Android 12 splash style, dedicated notification small icon, README logo or store graphic.
