# BanglaLens (Android)

Select text in any app → tap **বাংলা ⇄ English** in the selection menu → translation popup.
Direction is automatic: English → বাংলা, বাংলা → English.

- Selection menu (Chrome, PDF readers, WhatsApp, Messenger…): may sit under the ⋮ overflow.
- Replace: appears in editable fields; swaps the selection with the translation.
- Share sheet: share text → BanglaLens.
- App icon: opens and auto-translates whatever is on the clipboard.

## Build without Android Studio
Push to GitHub → Actions → build-apk → download the BanglaLens-apk artifact → install app-release.apk.

## Build locally
Open in Android Studio, or with Gradle 8.7 + JDK 17 + Android SDK: `gradle :app:assembleRelease`
