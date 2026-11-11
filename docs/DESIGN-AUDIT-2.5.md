# Cadence 2.5 design audit

## Architecture

Cadence uses native Android Views created in Java. There is no Jetpack Compose layer. `MainActivity` owns the phone UI and navigation, `UiStyle` provides semantic colours and coarse spacing/type helpers, `AppIcon` draws the outline icon set, and `CadenceDialogBuilder` applies the active palette to native alerts. Playback and Android Auto live in `PlaybackService`.

The application shell contains a header, refreshable scroll body, status region, mini player and four-tab bottom navigation. Screen history, system back, configuration changes and rotation state are handled in `MainActivity`.

## Screens and shared components

Top-level screens are Home, Search, Library, Podcasts and Profile. Detail and utility screens are playlist, album/artist collection, podcast show, podcast downloads, player, queue, import, download queue, Settings and cloud backup recovery.

Reusable UI includes the search field, filter chips, horizontal discovery shelves, artwork cards, track and episode rows, podcast cards, grouped settings, buttons, icon buttons, sliders, switches, empty states, skeleton placeholders, mini player, progress indicators, playlist picker, colour picker and alerts.

## Feature inventory

| Feature | Audit result |
| --- | --- |
| Sleep timer | Missing |
| Equalizer | Missing |
| Crossfade | Present, with 5–10 second options |
| Gapless queue playback | Present through Media3 continuous queue playback |
| Queue editor | Present: drag reorder, remove and play next |
| Home-screen widget | Present |
| Lyrics | Missing |
| Podcast playback speed | Present |
| Skip silence | Missing |

The 2.5 scope adds sleep timer and the platform equalizer. It does not add lyrics or skip-silence because they are outside the requested conditional feature list.

## Android mapping of DESIGN.md

- Action Blue maps to the default accent (`#0066CC` light, `#2997FF` dark), with custom accents contrast-corrected per theme.
- White, parchment, true black and `#1C1C1E` map to background, grouped surface and raised-surface tokens.
- The Android system sans font replaces proprietary SF fonts. Large titles use 34sp/600 with tight letter spacing; body uses 17sp/400.
- The 4/8dp spacing rhythm maps to named spacing tokens and a 16dp screen inset.
- Cards use 18dp corners, controls use pills, artwork uses 14–18dp corners, and transport controls use circles.
- Hairlines and tonal changes replace card shadows. Artwork may retain subtle visual separation.
- Press feedback uses a short scale/opacity animation and respects both Cadence's Motion preference and Android's animator setting.
- The web navigation pattern maps to inset-aware Android top and bottom bars. Android system back remains authoritative.
- Web responsive grids map to centered phone/tablet content and a two-column library where width and font scale permit.
- All interactive controls keep Android's 48dp minimum touch target and receive TalkBack descriptions.

## Risks and validation

The programmatic UI is concentrated in one large activity, so shared helpers must remain the source of styling to avoid divergent screens. This environment has no accelerated Android emulator, and its network policy currently blocks `jitpack.io`, which can prevent a clean Gradle dependency resolution. JVM tests, build, lint and connected checks remain the required validation targets when those facilities are available.
