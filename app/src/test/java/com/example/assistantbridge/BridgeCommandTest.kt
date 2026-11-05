package com.example.assistantbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeCommandTest {
    @Test fun parsesSupportedCommands() {
        assertEquals(BridgeCommand.MUSIC_PLAY_PAUSE, BridgeCommand.parse("MUSIC_PLAY_PAUSE"))
        assertEquals(BridgeCommand.MUSIC_NEXT, BridgeCommand.parse("MUSIC_NEXT"))
        assertEquals(BridgeCommand.MUSIC_PREVIOUS, BridgeCommand.parse("MUSIC_PREVIOUS"))
        assertEquals(BridgeCommand.OPEN_APP, BridgeCommand.parse("OPEN_APP"))
        assertEquals(BridgeCommand.DIAL, BridgeCommand.parse("DIAL"))
    }

    @Test fun rejectsUnknownOrMissingCommands() {
        assertNull(BridgeCommand.parse("RUN_SHELL"))
        assertNull(BridgeCommand.parse(""))
        assertNull(BridgeCommand.parse(null))
    }
}
