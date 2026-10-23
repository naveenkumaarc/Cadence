# Cadence 2.0 updates and migration

Version 2.0 uses version code 5 and package `app.cadence.player`.

The supplied 0.3.1 APK uses a different signing certificate. Its private key was not included in the source, so the delivered new-key 2.0 APK cannot replace that installation directly. A version name or version code cannot override Android's certificate check.

Before removing the old app, use its Settings → Save backup control. Save and verify the JSON outside app-private storage and keep a second copy. After installing 2.0, use Settings → Restore backup. Downloaded audio and Android local-file access grants must be restored separately.

Keep the separately supplied PRIVATE signing kit safe. Future builds must use that same key, the same application ID and an increasing version code. Those builds can update the 2.0 installation while retaining app-private data.

The private signing kit is not in the public repository or source ZIP. Use the environment variables documented in README.md when producing a signed release. CI produces an unsigned release that must be signed before installation.

An unknown-app Play Protect scan prompt is controlled by Google. Allow its scan and follow the result; a private signing key does not constitute Google approval.

The existing tools/TRANSFER.md applies only to the historical 0.2.0 package transition. It is not required for a 0.3.1 JSON backup migration.
