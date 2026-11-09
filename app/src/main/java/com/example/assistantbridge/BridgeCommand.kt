package com.example.assistantbridge

enum class BridgeCommand {
    MUSIC_PLAY_PAUSE,
    MUSIC_NEXT,
    MUSIC_PREVIOUS,
    VOLUME_UP,
    VOLUME_DOWN,
    HOTSPOT_ON,
    HOTSPOT_OFF,
    OPEN_APP,
    OPEN_CHATGPT,
    VOICE_COMMAND,
    VOICE_TO_CHATGPT,
    DIAL;

    companion object {
        fun parse(value: String?): BridgeCommand? =
            entries.firstOrNull { it.name == value }
    }
}
