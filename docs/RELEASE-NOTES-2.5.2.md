# Cadence 2.5.2

Cadence 2.5.2 makes autoplay follow the character of the listening session, fixes full-collection shuffle, and makes downloaded podcast episodes and songs reliably play from local storage. Autoplay now keeps a separate runway of similar songs after the manual queue, balancing close matches with a smaller number of adjacent picks while avoiding recent repeats.

Playlist, album, Liked Songs and Downloads shuffle now loads the complete collection, uses a full Fisher–Yates order and adds large queues in safe chunks. The current song is retained when shuffle changes, the original order can be restored, and unavailable items are reported instead of silently disappearing.

Podcast episode rows now have one download control with queued, progress, complete, retry and removal states. Valid downloaded audio is preferred from episode lists, the queue, Continue listening, notifications and Android Auto; missing or invalid files fall back to streaming without an automatic re-download. The same validation is applied to downloaded songs.

This release also introduces the contrast-safe indigo accent, a full light appearance, a System / Light / Dark control backed by DataStore, live appearance switching without restarting playback, and final Home, bottom-padding and hairline-divider refinements.

There are no database, backup, Google Drive sync or other user-data migrations, and no new permissions. NewPipeExtractor integration and streaming-source handling are unchanged.

## Download

Download `Cadence-2.5.2.apk` from this release and verify it with `SHA256SUMS-2.5.2.txt`. The APK uses the retained Cadence release certificate and version code 14, so it can update earlier release-signed Cadence installations in place.
