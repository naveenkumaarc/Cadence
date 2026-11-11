# Cadence 2.3

Native UI polish and a complete Android Auto browse tree.

Install Cadence-2.3.apk over 2.2 without uninstalling. VersionCode is 9; package app.cadence.player and the retained Cadence release certificate are unchanged.

## On your phone

The mini player now shows subtle playback progress. Track options use a vertical overflow icon, controls have consistent ripple feedback and touch targets, titles can use two lines, and duration remains beside the artist. Shared design tokens define the palette, spacing and type hierarchy. Playlist/album cards have consistent padding and long-name handling.

Empty playlists, downloads, search history and podcast subscriptions explain how to get started, with direct actions. Loading recommendations and search results show placeholders. Home has a refresh button near discovery, and Home/Search support pull-to-refresh; active searches also have an accessible refresh button. Read failures offer a retry. Recents use a clearer Clear all confirmation.

Settings → Personalise provides Dark, Light and Follow device themes, plus Motion and Touch feedback. Dark is the default. Animation and haptic preferences respect the phone's system settings. Wider layouts centre the content and use two library columns when text size permits. Appearance choices are local to each phone.

## In Android Auto

Open Cadence in the car to browse Home, Library, Podcasts and Continue listening. Home reads the same discovery cache and language preference as the phone, with background refresh when stale. Library shows liked songs, saved playlists, downloaded songs and saved download requests. Recommendation shelves, albums and playlists can be opened; selecting a song keeps its collection queue and the selected index. Podcast episodes retain their saved progress and expose played/remaining indicators.

The service accepts unpaged legacy browse requests and supports subscriptions, search/style metadata and artwork. Requests are resolved from known catalogue IDs, preserving the restriction against arbitrary external media addresses. Android Auto controls the driving layout and may display content differently from the phone. A physical car/head unit check is still required; automated clients do not substitute for that check. Sideloaded apps may require Android Auto's Unknown sources option if the app itself is absent from the car launcher.

## Data and compatibility

Google account continuity, named backup selection, per-device podcast positions, local downloads and the earlier playback fixes are retained. No new permissions or Google scopes are requested. Audio files remain on each phone. The original 0.3.1 signing-key migration requirement is unchanged. See VERIFICATION.md for recorded checks and limits.
