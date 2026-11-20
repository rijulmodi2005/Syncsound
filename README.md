# SyncSound

Multi-device synchronized music player (Android 7.0+, tested target: Android 14/15/16, Samsung One UI).

## Get the APK (no Android Studio needed)
1. Create a free GitHub repo and upload this whole folder (keep `syncsound.keystore`).
2. Open the **Actions** tab -> **Build SyncSound APK** -> wait ~5-8 min.
3. Download `SyncSound.apk` from **Releases -> latest** (a public link you can share) or from the run's Artifacts.

## Or build locally
Install Android Studio, open this folder, then run `./build-apk.sh`  (Windows: `gradlew.bat assembleRelease`).
APK path: `app/build/outputs/apk/release/app-release.apk`

## Install on a Samsung phone
1. Send `SyncSound.apk` to the phone (WhatsApp, Telegram, Drive, USB, link).
2. Tap it -> if asked, allow **Install unknown apps** for that app (Settings > Apps > the app > Install unknown apps).
3. If Samsung **Auto Blocker** is on (Settings > Security and privacy), turn it off while installing.
4. Open SyncSound and allow Notifications, Camera (QR join), and Nearby/Wi-Fi if asked.
5. For reliable background playback: Settings > Apps > SyncSound > Battery > **Unrestricted**.

## Using it
Host phone creates a room, others scan the QR / open the invite link (all on the same Wi-Fi), or open the web-speaker link in any browser.

## Notes
- `syncsound.keystore` signs every build with the same key, so new APKs update the old install. Keep it safe and private if you publish publicly; for Play Store use your own key.
- Spotify needs your own Spotify Client ID entered in Settings. YouTube uses the official embed player.
