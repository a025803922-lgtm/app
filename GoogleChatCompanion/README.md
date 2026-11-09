# Google Chat Companion

A separate Android companion project for a personal Gmail Google Chat account. It lives in its own directory so it does not change the existing Assistant Bridge application.

## Hybrid prototype

- **Messages and conversations:** opens the real Google Chat website at https://chat.google.com/ in an in-app Android WebView. Google handles account sign-in and message synchronization; the companion does not scrape or store the full message history.
- **Browser fallback:** use **Open in browser** if Google does not permit sign-in in the embedded WebView on the device. The normal browser may provide a more compatible Google sign-in session.
- **Notifications:** listens to Android notifications posted by the official Google Chat app.
- Stores recent notification previews locally on the device.
- Can mirror a Google Chat notification into a Chat Companion notification.
- Reuses the source notification's content intent and, when available, its RemoteInput reply action.
- Selecting a captured notification opens the original Google Chat conversation when the source notification is still active.

## Setup

1. Install the official Google Chat Android app and sign in with the personal Gmail account.
2. Install Chat Companion.
3. Open Chat Companion and grant Notification access in Android Settings.
4. Allow Chat Companion to post notifications if Android asks.
5. Enable Show companion notifications if you want mirrored notifications.
6. Tap **Open Google Chat** to use the web interface. Sign in to the same personal Google account.

The listener filters for the Google Chat package: `com.google.android.apps.dynamite`.

## Important limitations

- This is a hybrid browser-and-notification companion, not a direct Google Chat API client.
- The WebView shows Google's own website. Google may restrict sign-in in embedded browsers; use **Open in browser** if that happens.
- WebView cookies are kept by Android WebView and are not automatically shared with Chrome.
- The notification list only receives content that Google Chat exposes in Android notifications.
- It cannot download complete conversation history into a native list, reliably discover all conversations, or detect live typing/presence.
- Reply works only when the source notification exposes a compatible RemoteInput reply action.
- Notification access must remain enabled. Android/OEM battery management can affect delivery.
- Mirroring can produce a second notification while the original Google Chat notification is still enabled. Turn off mirroring if you prefer Google's original notification only.

No Google password, OAuth token, or message content is sent to a third-party server. Captured notification previews are stored locally on the device. The WebView communicates directly with Google.

## Build

From this directory, with JDK 17 and Android SDK Platform 35 installed, run:

    gradle testDebugUnitTest assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk
