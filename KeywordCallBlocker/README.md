# Keyword Call Blocker

Native Kotlin Android app from the supplied project specification.

## Build APK

Requirements: JDK 17, Android SDK Platform 35, and Gradle 8.9.

From this directory run:

```bash
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, push this project to a GitHub repository and run the **Build Android APK** workflow under Actions. Download the `KeywordCallBlocker-APK` artifact from the completed workflow run.

## First-time setup on the phone

1. Install the APK (Android 10/API 29 or newer).
2. Open the app and grant Contacts permission.
3. Tap **Enable Call Blocker** and select it as the call-screening app when Android asks.
4. Add keywords, select days and times, then enable scheduled keyword blocking.

The app matches keywords against saved contact names, not arbitrary unknown caller IDs. Test carefully with a non-essential contact before relying on call rejection. Device manufacturers and phone apps may affect call-screening behavior.
