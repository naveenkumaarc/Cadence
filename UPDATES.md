# Cadence 0.3.1 — requested update checklist

All features below are implemented in the native Android app. The verification report distinguishes automated checks from tests that need a real phone or car.

| Requested update | Implementation |
| --- | --- |
| Remove the C avatar | Header contains Cadence, queue and Settings. |
| Home search and suggestions | Top search bar; debounced suggestions and matching songs; submit shows song results and an album-cover shelf. |
| Album/artist/song search with covers | Music catalogue metadata and cover art; Songs, Albums, Artists and Playlists filters. |
| Fix Home discovery languages | Selected language replaces discovery shelves; preferences editable in Settings. Tamil, Telugu, Malayalam and English are defaults. |
| All playlists horizontally | Every saved playlist is in Home's horizontal row. |
| Proper queue | Play next, Add to queue, drag, move, remove, clear, shuffle and repeat; manual additions retain priority. |
| Podcast transport | Rewind and forward around Play; hold to choose 15, 30 or 60 seconds independently. |
| Music transport | Previous/next plus separate 15-second rewind/forward; Save, Shuffle and Repeat icons. |
| Playlist Play all / Shuffle play | Both controls available; large lists render in pages while retaining the complete queue. |
| Back up playlists | Settings → Save backup and Restore backup merge playlists, duplicates, subscriptions and progress. |
| Import from file last | Final Settings action; CSV, OPML, MP3 and FLAC imports. No Spotify Connect UI. |
| Crossfade | Off or 5–15 seconds; two renderers overlap eligible music and respect pause/seek/queue edits and audio focus. |
| Fluid interaction | Network work and Android Auto library reads run off the playback thread; list paging, artwork caching, one seek on release and a single reused player dialog. |
| Android Auto | Browsable library, selected-song playlist queue, podcast timestamp resume, voice-search handling and standard transport. A separate app UID tested the library contract. Physical head-unit testing remains. |
| Icon | Original green-disc Cadence waveform, adaptive icon and monochrome support. |
| Android widget | Cover, title, artist, progress and transport; podcast skip intervals; restart a completed song with one press. |
| Podcast spacing | Separate metadata, progress and action margins; square artwork, capped title and scroll-safe player. |
| Remember last playback | Persisted queue/index/timestamp and podcast progress; restored paused until Play. |
| Explicit podcast subscription | Directory/RSS previews do not subscribe. Subscribe or OPML import adds shows to Your shows. |
| Related-song autoplay | YouTube Music radio from the selected search song; deduplication; extends through manual songs and shuffle. Podcasts and explicit playlists stay finite. |
| Whole YouTube Music playlists | Public playlist search/link preview, all continuation pages, Add playlist to library. Failed paging does not silently save a partial playlist. |
| Spotify playlist metadata | Import the playlist CSV; version selection for unmatched songs. A Spotify account is not connected. |
| Play Protect | Release signing, non-debuggable build, HTTPS-only policy and playback-only permissions. No Google scan verdict is claimed; an unknown-app scan prompt cannot be guaranteed away. |

## Install

If you have Cadence 0.3.0, install this APK over it. Do not uninstall first: the package and retained release signing key are identical, and the version code is higher.

If you only have the old 0.2.0 app, keep it installed and follow tools/TRANSFER.md to move its library into the newer app. The old debug signing key was not available, which is why the 0.3 series uses a separate package.

## Real-device checks

YouTube audio media hosts are restricted from this environment, so catalogue/radio/playlist tests do not prove streaming on the phone. Also check screen-off playback, Bluetooth, Android Auto on a real head unit, remote podcast downloads and the exact Play Protect message on the phone. Those checks cannot be replaced by a successful build or local audio test.
