# Cadence 2.2

Account-wide and device-specific podcast continuity, plus clearly labelled backup selection.

Install over Cadence 2.0, 2.0.1 or 2.1 without uninstalling. Package and signing key remain the same; versionCode is 8. The original 0.3.1 signing-key migration requirement is unchanged.

## Listening positions

The default is Continue from latest across the signed-in Google account. Profile → Podcast resume can select Continue on this device instead. The restore-shaped icon in each episode row opens a list containing latest activity, this device and other saved device positions, with progress and full timestamps. Selecting one starts the episode there. An intentional rewind is newer activity; paused checkpoints that have not moved are not new listening activity.

Different episodes and different devices have independent records. Reinstalling creates a new installation record; choose the previous installation's named record to continue from it. Older progress still works and is labelled as earlier Cadence activity when a precise human-readable device name was never recorded.

## Choosing a backup

Profile → Choose backup to restore lists current device cloud copies and earlier recovery copies, including device names, dates, times and time zones. The displayed timestamp is the original saved copy's time, rather than the later time at which it was archived. Confirm the selected copy before restoring. A Before last restore copy is kept on this phone, scoped to the Google account.

Choose cloud backup is available during account setup before automatic sync starts. Merge this library is the alternative for keeping and combining the current phone library. A cancelled selection does not upload an empty replacement library. Local JSON backup filenames and confirmations include their device name and creation time.

## Sync feedback

Sync now runs a foreground attempt independently of old background retry chains. Duplicate taps are coalesced; the UI shows the active phase, last successful sync time and safe error codes. Optional recovery-archive failure does not block the main library save. Drive rate/storage errors are distinguished from permission failures. Tokens and private Google response messages are not displayed or logged.

Hidden Drive access and optional sign-in are unchanged. Library metadata, device labels, positions and desired downloads sync; audio files stay on each phone. Older recovery copies that lack a device name use a clear fallback label. No Play Protect certification is claimed.

See VERIFICATION.md for the final recorded checks and remaining real-device limits.
