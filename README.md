# BanglaLens (Android)

Select text in any app → tap **বাংলা ⇄ English** in the selection menu → translation popup.
Direction is automatic: English → বাংলা, বাংলা → English.

- Selection menu (Chrome, PDF readers, WhatsApp, Messenger…): may sit under the ⋮ overflow.
- Replace: appears in editable fields; swaps the selection with the translation.
- Share sheet: share text → BanglaLens.
- App icon: opens and auto-translates whatever is on the clipboard.

## Install
Grab the latest `.apk` from [Releases](https://github.com/bipro-b/bangla-lens-android/releases),
open it on your phone, and allow installs from that source when Android asks.

## Publish a release
```
git tag v1.1 && git push origin v1.1
```
Actions builds the APK and attaches it to a GitHub Release named after the tag.
Bump `versionCode`/`versionName` in `app/build.gradle.kts` to match before tagging.

Pushes to `main` still build an APK; it lands as the `BanglaLens-apk` Actions artifact.

### Signing
Without secrets, releases are signed with a throwaway debug key, so a new build
can't install over an older one — users must uninstall first. To fix that once:
```
keytool -genkey -v -keystore release.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks          # paste as the KEYSTORE_BASE64 secret
```
Then add repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Keep `release.jks` backed up and out of git — losing it means no more upgrades.

## Build locally
Open in Android Studio, or with Gradle 8.7 + JDK 17 + Android SDK: `gradle :app:assembleRelease`
