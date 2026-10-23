# Cadence privacy policy

Effective for Cadence 2.1. Maintainer: Naveen Kumaar. Contact: cnaveenkumaar@gmail.com. Source and support: https://github.com/naveenkumaarc/Cadence.

Cadence is an Android music and podcast player. It can be used without signing in. Playlists, preferences and listening progress are stored on your phone.

## Optional Google profile and Drive sync

If you choose Google sign-in, Google provides your account identifier, email, name and profile photo. Cadence uses this information to display your profile and keep different Google accounts separate. You can set a different Cadence display name and photo without changing your Google account.

If you connect Google Drive, Cadence requests `drive.appdata`, limited to its hidden application storage, together with basic identity permissions. Cadence stores playlist metadata and order, favourites, selected song versions, durations, podcast subscriptions and episode metadata, listening progress, playback preferences, the saved queue, your Cadence profile customisations and the list of requested downloads. Profile photos you choose are resized and stored as part of this profile data.

Actual music/podcast files, local file access grants, transfer IDs, device file paths and download-complete flags are not uploaded. A saved download list does not mean that its audio is available on another phone. Account-specific sync documents and recent recovery copies are stored in your Google Drive. Cadence uses a random installation identifier to keep each phone's updates separate. This metadata is private application data in your Google account, not a publicly shared playlist.

Google credentials and consent are handled by Google's official Android components. Cadence does not collect your Google password, store OAuth access tokens on disk, or send your library to a separate Cadence server. Google supplies tokens when needed; they remain in memory for requests to Google's HTTPS APIs. This is not a claim of end-to-end encryption against Google.

## Other network access

Searching, artwork, music discovery and playback contact the services supplying that content, including YouTube and podcast feed/audio hosts. Those services receive ordinary network requests and connection information. Remote source availability and those services' own policies apply. Cadence does not sell personal data or use Google user data for advertising, tracking or AI model training.

## Your controls and retention

Sign-in and Drive sync are optional. You can pause automatic sync, request Sync now, sign out, and export a local library backup. Signing out stops sync and keeps the current library on the phone. Switching accounts offers a choice about merging local data and keeps an on-device recovery copy. Recent cloud recovery snapshots are retained to help recover earlier library versions.

To revoke Google's authorization, use https://myaccount.google.com/connections. To remove the app's hidden Drive data, use Google Drive Settings → Manage apps → Cadence → Delete hidden app data. Revocation alone does not necessarily delete previously stored data. Uninstalling Cadence removes its private phone data and app-specific audio downloads, but does not itself promise deletion of cloud copies. Files you exported to another location remain until you remove them.

Cadence's use and transfer of information received from Google APIs adheres to the Google API Services User Data Policy, including its Limited Use requirements: https://developers.google.com/terms/api-services-user-data-policy.

If practices change, the policy and release notes will be updated. Contact the maintainer above with privacy questions.
