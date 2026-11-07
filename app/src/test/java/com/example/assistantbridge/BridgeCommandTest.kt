package com.example.assistantbridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeCommandTest {
    @Test fun parsesSupportedCommands() {
        assertEquals(BridgeCommand.MUSIC_PLAY_PAUSE, BridgeCommand.parse("MUSIC_PLAY_PAUSE"))
        assertEquals(BridgeCommand.MUSIC_NEXT, BridgeCommand.parse("MUSIC_NEXT"))
        assertEquals(BridgeCommand.MUSIC_PREVIOUS, BridgeCommand.parse("MUSIC_PREVIOUS"))
        assertEquals(BridgeCommand.VOLUME_UP, BridgeCommand.parse("VOLUME_UP"))
        assertEquals(BridgeCommand.VOLUME_DOWN, BridgeCommand.parse("VOLUME_DOWN"))
        assertEquals(BridgeCommand.OPEN_APP, BridgeCommand.parse("OPEN_APP"))
        assertEquals(BridgeCommand.OPEN_CHATGPT, BridgeCommand.parse("OPEN_CHATGPT"))
        assertEquals(BridgeCommand.VOICE_COMMAND, BridgeCommand.parse("VOICE_COMMAND"))
        assertEquals(BridgeCommand.VOICE_TO_CHATGPT, BridgeCommand.parse("VOICE_TO_CHATGPT"))
        assertEquals(BridgeCommand.DIAL, BridgeCommand.parse("DIAL"))
    }

    @Test fun rejectsUnknownOrMissingCommands() {
        assertNull(BridgeCommand.parse("RUN_SHELL"))
        assertNull(BridgeCommand.parse(""))
        assertNull(BridgeCommand.parse(null))
    }
}

class VoiceCommandParserTest {
    @Test fun parsesHebrewAndEnglishVolumeCommands() {
        assertEquals(VoiceAction.VOLUME_UP, VoiceCommandParser.parse("הגבר קול"))
        assertEquals(VoiceAction.VOLUME_DOWN, VoiceCommandParser.parse("ווליום למטה"))
        assertEquals(VoiceAction.VOLUME_UP, VoiceCommandParser.parse("volume up"))
        assertEquals(VoiceAction.VOLUME_UP, VoiceCommandParser.parse("hagdil et ha-volyum"))
        assertEquals(VoiceAction.VOLUME_DOWN, VoiceCommandParser.parse("hanemich et ha-volyum"))
    }

    @Test fun parsesMediaAndChatGptCommands() {
        assertEquals(VoiceAction.MUSIC_NEXT, VoiceCommandParser.parse("השיר הבא"))
        assertEquals(VoiceAction.MUSIC_PREVIOUS, VoiceCommandParser.parse("השיר הקודם"))
        assertEquals(VoiceAction.MUSIC_PLAY_PAUSE, VoiceCommandParser.parse("נגן מוזיקה"))
        assertEquals(VoiceAction.OPEN_CHATGPT, VoiceCommandParser.parse("פתח chatgpt"))
        assertEquals(VoiceAction.MUSIC_PLAY_PAUSE, VoiceCommandParser.parse("nagen music"))
        assertEquals(VoiceAction.MUSIC_PLAY_PAUSE, VoiceCommandParser.parse("nagen muzika"))
        assertEquals(VoiceAction.MUSIC_NEXT, VoiceCommandParser.parse("maavar haba"))
        assertEquals(VoiceAction.MUSIC_PREVIOUS, VoiceCommandParser.parse("maavar kodem"))
    }

    @Test fun parsesBluetoothPhoneCommandsInHebrewAndEnglish() {
        assertEquals(VoiceAction.OPEN_BLUETOOTH_PHONE, VoiceCommandParser.parse("פתח טלפון בלוטות"))
        assertEquals(VoiceAction.OPEN_BLUETOOTH_PHONE, VoiceCommandParser.parse("open bluetooth phone"))
        assertEquals(VoiceAction.OPEN_BLUETOOTH_PHONE, VoiceCommandParser.parse("open bt phone"))
    }

    @Test fun parsesCallContactTargetsInHebrewEnglishAndTransliteration() {
        assertEquals("home", VoiceCommandParser.parseCallTarget("call home"))
        assertEquals("david", VoiceCommandParser.parseCallTarget("Call David"))
        assertEquals("אמא", VoiceCommandParser.parseCallTarget("תתקשר לאמא"))
        assertEquals("דוד", VoiceCommandParser.parseCallTarget("התקשר אל דוד"))
        assertEquals("בית", VoiceCommandParser.parseCallTarget("titkasher la-bayit"))
        assertEquals("בית", VoiceCommandParser.parseCallTarget("titkasher l-bayit"))
        assertNull(VoiceCommandParser.parseCallTarget("nagen muzika"))
    }

    @Test fun convertsCommonPhoneticHebrewForChatGpt() {
        assertEquals("אני רוצה לדעת מה מזג האוויר היום", HebrewTransliterator.toHebrew("ani rotze ladaat ma mezeg ha-avir hayom"))
        assertEquals("שלום", HebrewTransliterator.toHebrew("shalom"))
        assertEquals("What is the weather today?", HebrewTransliterator.toHebrew("What is the weather today?"))
    }

    @Test fun rejectsUnrecognizedSpeech() {
        assertNull(VoiceCommandParser.parse("שלום מה נשמע"))
    }
}
