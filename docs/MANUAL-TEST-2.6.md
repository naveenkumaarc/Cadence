# Cadence 2.6 manual test checklist

## Detail pages

- Open an album, playlist and podcast at 100%, 130% and 200% font scale in Light and Dark.
- Confirm artwork starts below the contextual app bar and system Back returns to the prior page.
- Rotate to landscape and verify Play, Shuffle, library and download actions wrap without touching.
- Toggle album library state and verify the check state survives leaving and reopening the page.
- Start an album download and verify each track shows only one download-state indicator.

## Search and Library

- Type `rajini intro sing` character by character with English, Tamil, Telugu and Malayalam IMEs.
- Confirm suggestions and results never clear focus, move the cursor or discard composing text.
- Confirm the mini player and bottom navigation hide with the IME and return when it closes.
- Drag the result list to dismiss the IME; tap the IME Search action to submit and dismiss it.
- Tap and swipe through every Library filter, rotate, and confirm the highlighted chip matches content.
- Repeat the chip check on Home filters, Search types and podcast-language chips.

## Equalizer

- Test one local file, one downloaded song, one stream and one podcast.
- Toggle the effect, select every device preset and move every band through its full range.
- Leave playback running in the background and control it from notification and Android Auto.
- Change tracks, enable crossfade, rebuild the queue and restart the service; settings must reapply.
- Reboot the app and confirm enabled state, preset and custom bands persist.
- On a device without the effect, confirm the sheet says `Not supported on this device`.
- When the OEM panel resolves, confirm `Open system equalizer` opens it with the active session.

## Remaining UI

- Confirm the gesture-navigation indicator never crosses the Now Playing bottom actions.
- Verify long marquee titles fade at both edges and podcast skip buttons do not appear for music.
- Swipe Search recents to delete; confirm result rows still retain their save action.
- Swipe the mini player up, left and right; confirm open/next/previous and play-pause haptics.
- Inspect every accent preset in Light and Dark for readable text, subtle pressed states and dividers.
