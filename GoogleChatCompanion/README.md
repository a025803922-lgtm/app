# Google Chat Companion

A separate Android companion project for a personal Gmail Google Chat account. It lives in its own directory so it does not change the existing Assistant Bridge application.

## First prototype

- Listens for Android notifications posted by the official Google Chat app.
- Stores recent notification previews locally on the device.
- Can mirror a Google Chat notification into a Chat Companion notification.
- Reuses the source notification's content intent and, when available, its RemoteInput reply action.
- Opens the original Google Chat conversation when a captured notification is selected.

## Setup

1. Install the official Google Chat Android app and sign in with the personal Gmail account.
2. Install Chat Companion.
3. Open Chat Companion and grant Notification access in Android Settings.
4. Allow Chat Companion to post notifications if Android asks.
5. Enable Show companion notifications if you want mirrored notifications.

The listener filters for the Google Chat package: com.google.android.apps.dynamite.

## Important limitations

This is a notification-based companion, not a direct Google Chat API client.

- It only receives content that Google Chat exposes in Android notifications.
- It cannot download complete conversation history or reliably discover all conversations.
- Reply works only when the source notification exposes a compatible RemoteInput reply action.
- It cannot detect live typing indicators or online/presence state.
- Notification access must remain enabled. Android/OEM battery management can affect delivery.
- Mirroring can produce a second notification while the original Google Chat notification is still enabled. Turn off mirroring if you prefer Google's original notification only.

No Google password, OAuth token, or message content is sent to a third-party server. Captured notification previews are stored locally on the device.

## Build

From this directory, with JDK 17 and Android SDK Platform 35 installed, run:

    gradle testDebugUnitTest assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk
