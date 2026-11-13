# Cadence 2.5.0 verification

Verified on 2026-11-11 with JDK 17, Android SDK 35, build-tools 35.0.0 and the Gradle 8.11.1 wrapper.

## Build checks

The following targets pass:

- `:core:test`
- `:app:assembleDebug`
- `:app:lintDebug` (zero errors)
- `:app:assembleDebugAndroidTest`
- `:app:testDebugUnitTest` (no JVM test sources in the app module)
- `:carprobe:assembleDebug`
- `-PcadenceUnsigned :app:assembleRelease :app:lintRelease` (zero errors)

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. An unsigned release is generated with `-PcadenceUnsigned`; the published signed release is generated at `app/build/outputs/apk/release/app-release.apk` when the external signing environment is configured.

The cloud worker blocks `jitpack.io` with HTTP 403. For this verification only, the pinned `NewPipeExtractor` `v0.26.5` tag and its pinned TeamNewPipe NanoJSON commit were compiled unchanged in `/tmp` and supplied through a temporary Gradle init script. The repository dependency declaration and all extractor, streaming and data-format code remain unchanged. A normal developer machine with JitPack access can run the README commands directly.

## UI and accessibility checks

`ThemeScreenshotIntegrationTest` covers Home, Library, Settings and Player in Light and Dark themes. It exports these files when the connected suite runs:

- `cadence25-light-home.png`
- `cadence25-light-library.png`
- `cadence25-light-settings.png`
- `cadence25-light-player.png`
- `cadence25-dark-home.png`
- `cadence25-dark-library.png`
- `cadence25-dark-settings.png`
- `cadence25-dark-player.png`

The test asserts WCAG AA contrast for primary, secondary, accent and on-accent text; checks TalkBack labels on the new icon controls; exercises the responsive rules used at 130% and 200% font scale; verifies the tablet library breakpoint; and checks reduced-motion preference handling. Existing integration coverage verifies mini-player seek progress, queue behavior, playback, downloads, cloud continuity and the Android Auto browse tree.

The token contrast ratios are 16.83:1 primary, 5.07:1 secondary and 5.57:1 accent in Light; and 19.29:1 primary, 9.50:1 secondary and 6.96:1 accent in Dark. On-accent content is 5.57:1 in Light and 6.96:1 in Dark.

The artwork-tinted player uses a bounded sample of at most roughly 24 by 24 pixels and a solid fallback token. It does not use runtime blur or a continuously rendered image effect.

## Device status

The instrumentation APK compiles, and `tools/run-native-checks.sh` installs the car probe, runs the connected suite and pulls the eight theme captures. This cloud worker has no attached Android device and no `/dev/kvm`, so connected runtime tests, screenshots, TalkBack traversal and physical rotation were not executed here. Run the native-check script on an Android 8 or newer device before publishing a signed release.

No permissions, database schema, backup format, Google Drive payload, package name or signing configuration changed in 2.5.0.

## Release signature

`downloads/Cadence-2.5.0.apk` verifies with APK Signature Scheme v2 and certificate SHA-256 `26d127669bb122cc6768726c3b8a4a2573e3edf62c10cc20d8d8e90a84c1024f`. This is the same certificate as `downloads/Cadence-2.4.1.apk`. The package remains `app.cadence.player`, and version code 12 is greater than the 2.4.1 version code 11, allowing an in-place update.
