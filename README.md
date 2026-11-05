# Assistant Bridge

Android companion bridge for the Topway TS10S head unit.

Target device: Android 12 (SDK 31), ARM64 (arm64-v8a), 160 dpi.

## Version 0.2 features

- Music play/pause, next, and previous.
- Music-stream volume up/down.
- Uses the installed Hebrew Voice recognition service (`com.hebvoice.stt/.HebrewRecognitionService`) for Hebrew speech input.
- Voice commands for music controls, volume, and opening ChatGPT.
- A "Speak text to ChatGPT" mode: recognized Hebrew text is passed to ChatGPT using Android's text-share intent. ChatGPT may show the text for review; the bridge does not automatically send a message.
- Launch ChatGPT and Waze.
- Open the phone dialer with a number (the user confirms the call).
- Broadcast receiver for external integrations.

## Hebrew voice commands

Examples:
- "הגבר קול" / "ווליום למעלה"
- "הנמך קול" / "ווליום למטה"
- "נגן מוזיקה"
- "השיר הבא"
- "השיר הקודם"
- "פתח ChatGPT"

The speech activity asks for microphone permission the first time it is used. It explicitly binds to the Hebrew Voice recognition service identified on the TS10S. If that service is unavailable or its component name differs, recognition will report an error.

## Build on Windows

1. Install Android Studio and use its bundled JDK 17.
2. Clone or download this repository.
3. Open the repository folder in Android Studio.
4. Allow Gradle sync. Install Android SDK Platform 35 if prompted.
5. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
6. Install the APK on the head unit.

GitHub Actions also runs unit tests and builds a debug APK. Download the `AssistantBridge-debug-apk` artifact from a successful workflow run.

The APK is architecture-independent because it contains no native libraries.

## Broadcast command protocol

Receiver action: `com.example.assistantbridge.COMMAND`

Set the explicit package to `com.example.assistantbridge` and include:
- `token`: value of `SHARED_TOKEN` in `BridgeActions.kt`
- `command`: one of `MUSIC_PLAY_PAUSE`, `MUSIC_NEXT`, `MUSIC_PREVIOUS`, `VOLUME_UP`, `VOLUME_DOWN`, `OPEN_APP`, `OPEN_CHATGPT`, `VOICE_COMMAND`, `VOICE_TO_CHATGPT`, or `DIAL`
- `package`: required for `OPEN_APP`
- `number`: required for `DIAL`

Example sender code:

    val intent = Intent("com.example.assistantbridge.COMMAND")
        .setPackage("com.example.assistantbridge")
        .putExtra("token", "CHANGE_THIS_TOKEN")
        .putExtra("command", "VOLUME_UP")
    sendBroadcast(intent)

## ChatGPT integration boundary

The bridge can launch ChatGPT and can hand recognized speech to it through Android's text-sharing mechanism. The user reviews and sends the text in ChatGPT. The ChatGPT app does not expose a general supported interface for this bridge to read private conversation replies or automatically execute arbitrary commands from them.

## Security and limitations

- The exported receiver uses a placeholder shared token. This is not strong authentication. Replace it and use stronger IPC authentication before connecting a real sender.
- Media-button delivery depends on the head unit's Android media-session implementation and active player.
- Volume commands adjust Android's music stream volume.
- `DIAL` opens the dialer; it does not place a call automatically.
- No root commands or unrestricted shell access are included.
