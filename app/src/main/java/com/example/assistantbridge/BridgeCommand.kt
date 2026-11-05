package com.example.assistantbridge

enum class BridgeCommand {
    MUSIC_PLAY_PAUSE,
    MUSIC_NEXT,
    MUSIC_PREVIOUS,
    OPEN_APP,
    DIAL;

    companion object {
        fun parse(value: String?): BridgeCommand? =
            entries.firstOrNull { it.name == value }
    }
}
