package com.example.voiceassistant

import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.SensitiveAction
import com.example.voiceassistant.domain.model.SettingType
import com.example.voiceassistant.domain.parser.CommandParser
import com.example.voiceassistant.domain.parser.ConfirmationDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandParserTest {

    private lateinit var parser: CommandParser

    // Fixed timestamp: Monday, October 13, 2026, 12:00:00 UTC
    private val fixedTimestamp = 1791892800000L

    @Before
    fun setUp() {
        parser = CommandParser(timeProvider = { fixedTimestamp })
    }

    // ==========================================
    // 1. Time and Date Tests
    // ==========================================

    @Test
    fun testParseTellTime() {
        val actions = listOf(
            parser.parseCommand("what time is it"),
            parser.parseCommand("current time"),
            parser.parseCommand("tell me the time"),
            parser.parseCommand("what's the time")
        )

        for (action in actions) {
            assertTrue("Expected TellTime action, got: $action", action is AssistantAction.TellTime)
        }
    }

    @Test
    fun testParseTellDate() {
        val actions = listOf(
            parser.parseCommand("what date is it"),
            parser.parseCommand("what is today's date"),
            parser.parseCommand("today's date"),
            parser.parseCommand("what day is it")
        )

        for (action in actions) {
            assertTrue("Expected TellDate action, got: $action", action is AssistantAction.TellDate)
        }
    }

    // ==========================================
    // 2. Timer Tests
    // ==========================================

    @Test
    fun testParseTimerMinutes() {
        val action = parser.parseCommand("set a timer for 10 minutes")
        assertTrue(action is AssistantAction.SetTimer)
        val timer = action as AssistantAction.SetTimer
        assertEquals(600, timer.durationSeconds)
    }

    @Test
    fun testParseTimerSeconds() {
        val action = parser.parseCommand("timer for 45 seconds")
        assertTrue(action is AssistantAction.SetTimer)
        val timer = action as AssistantAction.SetTimer
        assertEquals(45, timer.durationSeconds)
    }

    @Test
    fun testParseTimerHours() {
        val action = parser.parseCommand("set a timer for 2 hours")
        assertTrue(action is AssistantAction.SetTimer)
        val timer = action as AssistantAction.SetTimer
        assertEquals(7200, timer.durationSeconds)
    }

    // ==========================================
    // 3. Alarm Tests
    // ==========================================

    @Test
    fun testParseAlarmMorning() {
        val action = parser.parseCommand("set an alarm for 7:30 am")
        assertTrue(action is AssistantAction.SetAlarm)
        val alarm = action as AssistantAction.SetAlarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
    }

    @Test
    fun testParseAlarmEvening() {
        val action = parser.parseCommand("set alarm for 8:15 pm")
        assertTrue(action is AssistantAction.SetAlarm)
        val alarm = action as AssistantAction.SetAlarm
        assertEquals(20, alarm.hour)
        assertEquals(15, alarm.minute)
    }

    @Test
    fun testParseWakeMeUp() {
        val action = parser.parseCommand("wake me up at 6 am")
        assertTrue(action is AssistantAction.SetAlarm)
        val alarm = action as AssistantAction.SetAlarm
        assertEquals(6, alarm.hour)
        assertEquals(0, alarm.minute)
    }

    // ==========================================
    // 4. Open App Tests
    // ==========================================

    @Test
    fun testParseOpenApps() {
        val camera = parser.parseCommand("open camera") as AssistantAction.OpenApp
        assertEquals("Camera", camera.appName)

        val youtube = parser.parseCommand("launch youtube") as AssistantAction.OpenApp
        assertEquals("YouTube", youtube.appName)
        assertEquals("com.google.android.youtube", youtube.packageName)

        val chrome = parser.parseCommand("open chrome") as AssistantAction.OpenApp
        assertEquals("Chrome", chrome.appName)
        assertEquals("com.android.chrome", chrome.packageName)

        val maps = parser.parseCommand("open maps") as AssistantAction.OpenApp
        assertEquals("Google Maps", maps.appName)
    }

    // ==========================================
    // 5. Settings Tests
    // ==========================================

    @Test
    fun testParseSettings() {
        val wifi = parser.parseCommand("open wifi settings") as AssistantAction.OpenSetting
        assertEquals(SettingType.WIFI, wifi.settingType)

        val bt = parser.parseCommand("open bluetooth settings") as AssistantAction.OpenSetting
        assertEquals(SettingType.BLUETOOTH, bt.settingType)

        val display = parser.parseCommand("open display settings") as AssistantAction.OpenSetting
        assertEquals(SettingType.DISPLAY, display.settingType)

        val battery = parser.parseCommand("open battery settings") as AssistantAction.OpenSetting
        assertEquals(SettingType.BATTERY, battery.settingType)

        val sound = parser.parseCommand("open sound settings") as AssistantAction.OpenSetting
        assertEquals(SettingType.SOUND, sound.settingType)
    }

    // ==========================================
    // 6. Sensitive Action Confirmation Tests
    // ==========================================

    @Test
    fun testSensitiveSendMessageRequiresConfirmation() {
        val action = parser.parseCommand("send a message to Alice saying I will arrive at five")
        assertTrue("Expected RequireConfirmation, got: $action", action is AssistantAction.RequireConfirmation)

        val req = action as AssistantAction.RequireConfirmation
        assertTrue(req.action is SensitiveAction.SendMessage)
        val sendMsg = req.action as SensitiveAction.SendMessage
        assertEquals("Alice", sendMsg.recipient)
        assertEquals("I will arrive at five", sendMsg.messageText)
    }

    @Test
    fun testSensitiveTextRequiresConfirmation() {
        val action = parser.parseCommand("text John saying Running late")
        assertTrue(action is AssistantAction.RequireConfirmation)

        val req = action as AssistantAction.RequireConfirmation
        val sendMsg = req.action as SensitiveAction.SendMessage
        assertEquals("John", sendMsg.recipient)
        assertEquals("Running late", sendMsg.messageText)
    }

    @Test
    fun testSensitiveCallRequiresConfirmation() {
        val action = parser.parseCommand("call Mom")
        assertTrue(action is AssistantAction.RequireConfirmation)

        val req = action as AssistantAction.RequireConfirmation
        assertTrue(req.action is SensitiveAction.MakePhoneCall)
        val call = req.action as SensitiveAction.MakePhoneCall
        assertEquals("Mom", call.contactOrNumber)
    }

    @Test
    fun testSensitiveDeleteRequiresConfirmation() {
        val action = parser.parseCommand("delete all my contacts")
        assertTrue(action is AssistantAction.RequireConfirmation)

        val req = action as AssistantAction.RequireConfirmation
        assertTrue(req.action is SensitiveAction.DeleteData)
    }

    @Test
    fun testSensitivePurchaseRequiresConfirmation() {
        val action = parser.parseCommand("buy tickets")
        assertTrue(action is AssistantAction.RequireConfirmation)

        val req = action as AssistantAction.RequireConfirmation
        assertTrue(req.action is SensitiveAction.MakePurchase)
    }

    // ==========================================
    // 7. Confirmation Response Voice Decision Tests
    // ==========================================

    @Test
    fun testConfirmationPositiveDecisions() {
        val affirmatives = listOf("yes", "Yes, please", "confirm", "proceed", "go ahead", "sure", "ok")
        for (token in affirmatives) {
            assertEquals(
                "Expected CONFIRMED for '$token'",
                ConfirmationDecision.CONFIRMED,
                parser.parseConfirmationResponse(token)
            )
        }
    }

    @Test
    fun testConfirmationNegativeDecisions() {
        val negatives = listOf("no", "No, cancel", "cancel", "stop", "abort", "never mind", "dont")
        for (token in negatives) {
            assertEquals(
                "Expected CANCELLED for '$token'",
                ConfirmationDecision.CANCELLED,
                parser.parseConfirmationResponse(token)
            )
        }
    }

    @Test
    fun testConfirmationUnclearDecisions() {
        val unclear = listOf("what did you say", "banana", "maybe tomorrow")
        for (token in unclear) {
            assertEquals(
                "Expected UNCLEAR for '$token'",
                ConfirmationDecision.UNCLEAR,
                parser.parseConfirmationResponse(token)
            )
        }
    }

    // ==========================================
    // 8. Unsupported / Unknown Command Tests
    // ==========================================

    @Test
    fun testUnsupportedCommandsAreRejectedSafely() {
        val unknown1 = parser.parseCommand("hack the pentagon")
        assertTrue(unknown1 is AssistantAction.Unknown)

        val unknown2 = parser.parseCommand("")
        assertTrue(unknown2 is AssistantAction.Unknown)
    }
}
