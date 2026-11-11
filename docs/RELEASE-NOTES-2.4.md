# Cadence 2.4

Package `app.cadence.player`, version code `10`. Retain the existing release signing key for an in-place update.

- Podcasts lists followed shows, ordered by their latest listening activity. The episode-based Continue listening shelf has been removed from the phone's Podcasts page.
- Albums and other detail screens remember where they were opened. Back returns to Search with its query, filter and results, including when an album was opened through an artist.
- The Home refresh icon is a clean circular arrow. The profile photo now sits to the left of Cadence.
- Home's Music and Podcasts chips switch the saved-content area between playlists and followed podcasts while keeping the Home screen open.
- Search includes podcasts under All and offers a Podcasts filter. Music and podcast search failures are handled independently so results from the other source remain available.
- Home, Search and podcast search fields show a clear button while text is present.
- Save to playlist opens a rounded card list with artwork, playlist names and song counts, plus New playlist.
- Settings → Downloads → Download connection chooses Wi-Fi only or Wi-Fi and mobile data. The choice applies to song and episode transfers on this phone, including existing queued requests. Roaming remains disabled.
- Library and Settings expose Download queue. Each song has live progress and one Pause/Resume button; the queue has one Pause all/Resume all button. Paused requests persist across app restarts. Playlist downloads enter this queue before audio resolution.
- New song transfers retain partial files and use HTTP Range with validators when supported. Expired source URLs can be resolved again. A server that cannot safely resume starts a fresh transfer. Completed downloads from earlier versions remain playable. Pausing an unfinished transfer created by an older DownloadManager-based version moves it to the new queue; that first transfer starts again.
- Settings → Personalise → Colour scheme provides hue and saturation controls, a hex colour field, a preview and Reset to mint. Shared screen surfaces, cards, controls, artwork placeholders, dialogs and widgets follow the chosen palette in Dark, Light and Follow device modes. Accent text contrast is adjusted for readability. Appearance stays on this phone.
- Podcast skip-back and skip-forward icons now point in their corresponding directions.
- New **Cadence Loop** icon: a mint C-shaped loop around a play symbol, on deep teal. Native adaptive and monochrome vector assets are included; the editable source is `design/cadence-loop.svg`.

The phone's requested changes do not remove Android Auto's Continue listening section or the optional cloud queue restore action. Existing libraries, subscriptions, listening positions, Google sign-in configuration and backup format are retained.

See `VERIFICATION-2.4.md` for checks and limits.
