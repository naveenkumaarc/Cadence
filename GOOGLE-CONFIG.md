# Google identity configuration

Public OAuth identifiers (not secrets):

- Google Cloud project: `cadence-512513`
- Package: `app.cadence.player`
- Release certificate SHA-1: `5D:73:42:A5:88:5D:5F:C0:4B:E4:83:05:FD:01:0F:26:02:A6:3E:63`
- Android release OAuth client: `823603474331-bp6m1tpbi3lfad536ccfhtbdt1odrshr.apps.googleusercontent.com`
- Google sign-in Web client: `823603474331-q4ng1jpvtftt9bjpadfei0go1d6ipeej.apps.googleusercontent.com`

The Android app uses Credential Manager and Google's AuthorizationClient. It does not use a Web client secret, a service account or a Cadence backend. Never put client secrets or private signing material in the APK or repository.

For a fork, register that app's package and signing certificate in the fork owner's Google project and supply `-PcadenceGoogleClientId=YOUR_WEB_CLIENT_ID` while building. Drive API and OAuth consent configuration must belong to the same project. Debug builds require a separate registered Android client for their debug signing certificate if Google sign-in itself is being tested.
