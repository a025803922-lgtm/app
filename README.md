# Assistant Bridge

Initial Android bridge prototype for the Qin/Topway TS10S head unit.

Target device: Android 12 (SDK 31), ARM64 (arm64-v8a), 160 dpi.

## Current prototype

- Simple test activity.
- Test media actions: play/pause, next, previous.
- Launch Waze and ChatGPT.
- Open the phone dialer with a number (the user confirms the call).
- Exported broadcast receiver for future integrations.

## Build on Windows

1. Install Android Studio and use its bundled JDK 17.
2. Clone or download this repository.
3. Open the repository folder in Android Studio.
4. Allow Gradle sync. Install Android SDK Platform 35 if prompted.
5. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
6. Install the APK on the head unit.

The APK is architecture-independent because it contains no native libraries.

## Broadcast command protocol

Receiver action: com.example.assistantbridge.COMMAND

Set the explicit package to com.example.assistantbridge and include:
- token: value of SHARED_TOKEN in BridgeActions.kt
- command: MUSIC_PLAY_PAUSE, MUSIC_NEXT, MUSIC_PREVIOUS, OPEN_APP, or DIAL
- package: required for OPEN_APP
- number: required for DIAL

Example sender code:

    val intent = Intent("com.example.assistantbridge.COMMAND")
        .setPackage("com.example.assistantbridge")
        .putExtra("token", "CHANGE_THIS_TOKEN")
        .putExtra("command", "MUSIC_NEXT")
    sendBroadcast(intent)

## Limitations and security

- This prototype is not connected to ChatGPT.
- Media-button delivery depends on the head unit's Android media-session implementation and active player.
- DIAL opens the dialer; it does not place a call automatically.
- The exported receiver uses a placeholder shared token. This is not strong authentication. Before connecting a real sender, replace it and use stronger IPC authentication.
- No root commands or unrestricted shell access are included.
