package com.example.voiceassistant.domain.parser

import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.SensitiveAction
import com.example.voiceassistant.domain.model.SettingType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

enum class ConfirmationDecision {
    CONFIRMED,
    CANCELLED,
    UNCLEAR
}

/**
 * Parses natural language voice commands against an explicit allowlist.
 * Ensures sensitive actions trigger confirmation requirements.
 */
class CommandParser(
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Determines whether user voice input is a confirmation ("yes") or cancellation ("no").
     */
    fun parseConfirmationResponse(text: String): ConfirmationDecision {
        val clean = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.,?!]"), "")

        val positiveTokens = setOf(
            "yes", "confirm", "proceed", "go ahead", "do it", "sure", "yeah", "yep", "ok", "okay"
        )
        val negativeTokens = setOf(
            "no", "cancel", "stop", "abort", "never mind", "dont", "don't", "reject", "nope"
        )

        return when {
            positiveTokens.any { clean == it || clean.startsWith("$it ") } -> ConfirmationDecision.CONFIRMED
            negativeTokens.any { clean == it || clean.startsWith("$it ") } -> ConfirmationDecision.CANCELLED
            else -> ConfirmationDecision.UNCLEAR
        }
    }

    /**
     * Parses a spoken command into an [AssistantAction].
     */
    fun parseCommand(rawText: String): AssistantAction {
        val normalized = rawText.trim()
            .replace(Regex("\\s+"), " ")
        val normalizedLowercase = normalized.lowercase(Locale.ROOT)

        if (normalized.isBlank()) {
            return AssistantAction.Unknown(rawText, "No speech recognized.")
        }

        // 1. Check sensitive actions first (requiring confirmation)
        checkSensitiveAction(normalized)?.let { return it }

        // 2. Time & Date
        checkTimeOrDate(normalizedLowercase)?.let { return it }

        // 3. Timers
        checkTimer(normalizedLowercase)?.let { return it }

        // 4. Alarms
        checkAlarm(normalizedLowercase)?.let { return it }

        // 5. Settings screens
        checkSettings(normalizedLowercase)?.let { return it }

        // 6. Open specific apps
        checkOpenApp(normalizedLowercase)?.let { return it }

        // Not in allowlist
        return AssistantAction.Unknown(
            rawCommand = rawText,
            reason = "Command not recognized or not permitted in the allowlist."
        )
    }

    private fun checkSensitiveAction(text: String): AssistantAction? {
        // Send message / SMS: "send a message to [recipient] saying [text]" or "text [recipient] [text]"
        val msgPattern1 = Pattern.compile(
            "^(?:send(?:\\s+a)?\\s+(?:message|text|sms)\\s+to)\\s+([a-zA-Z0-9_\\s]+?)\\s+(?:saying|that)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val msgMatcher1 = msgPattern1.matcher(text)
        if (msgMatcher1.matches()) {
            val recipient = msgMatcher1.group(1)?.trim() ?: "Recipient"
            val body = msgMatcher1.group(2)?.trim() ?: ""
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.SendMessage(recipient, body),
                explanation = "Confirm sending message to '$recipient' saying: \"$body\""
            )
        }

        val msgPattern2 = Pattern.compile(
            "^(?:text|message)\\s+([a-zA-Z0-9_\\s]+?)\\s+(?:saying|that)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val msgMatcher2 = msgPattern2.matcher(text)
        if (msgMatcher2.matches()) {
            val recipient = msgMatcher2.group(1)?.trim() ?: "Recipient"
            val body = msgMatcher2.group(2)?.trim() ?: ""
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.SendMessage(recipient, body),
                explanation = "Confirm sending text to '$recipient': \"$body\""
            )
        }

        // Phone call: "call [target]" or "dial [target]"
        val callPattern = Pattern.compile(
            "^(?:call|dial|make\\s+a\\s+phone\\s+call\\s+to)\\s+([a-zA-Z0-9_+\\s]+)$",
            Pattern.CASE_INSENSITIVE
        )
        val callMatcher = callPattern.matcher(text)
        if (callMatcher.matches()) {
            val target = callMatcher.group(1)?.trim() ?: "unknown"
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.MakePhoneCall(target),
                explanation = "Confirm placing a phone call to '$target'"
            )
        }

        // Deletion actions
        if (text.startsWith("delete ", ignoreCase = true) ||
            text.startsWith("remove ", ignoreCase = true) ||
            text.startsWith("clear data", ignoreCase = true)
        ) {
            val target = text.replaceFirst(Regex("^(?:delete|remove)\\s+", RegexOption.IGNORE_CASE), "").trim()
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.DeleteData(target),
                explanation = "High risk: Confirm deleting '$target'?"
            )
        }

        // Purchases
        if (text.startsWith("buy ", ignoreCase = true) ||
            text.startsWith("purchase ", ignoreCase = true) ||
            text.startsWith("order ", ignoreCase = true)
        ) {
            val item = text.replaceFirst(Regex("^(?:buy|purchase|order)\\s+", RegexOption.IGNORE_CASE), "").trim()
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.MakePurchase(item),
                explanation = "Confirm commercial purchase or order for '$item'?"
            )
        }

        // Security / Privacy changes
        val lowerText = text.lowercase(Locale.ROOT)
        if (lowerText.contains("security") || lowerText.contains("password") ||
            lowerText.contains("pin") || lowerText.contains("privacy setting")
        ) {
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.ChangeSecuritySetting(text),
                explanation = "Warning: Security setting changes require manual interaction."
            )
        }

        return null
    }

    private fun checkTimeOrDate(text: String): AssistantAction? {
        val isTimeQuery = text.contains("what time") ||
                text == "time" ||
                text == "current time" ||
                text == "tell me the time" ||
                text == "what's the time"

        if (isTimeQuery) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formatted = timeFormat.format(Date(timeProvider()))
            return AssistantAction.TellTime("The current time is $formatted.")
        }

        val isDateQuery = text.contains("what date") ||
                text.contains("today's date") ||
                text == "what is today's date" ||
                text == "what's the date" ||
                text == "current date" ||
                text == "what day is it" ||
                text == "tell me the date"

        if (isDateQuery) {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            val formatted = dateFormat.format(Date(timeProvider()))
            return AssistantAction.TellDate("Today is $formatted.")
        }

        return null
    }

    private fun checkTimer(text: String): AssistantAction? {
        // e.g. "set a timer for 10 minutes", "timer 5 minutes", "timer for 30 seconds", "set timer 1 hour"
        val timerRegex = Pattern.compile(
            ".*?(?:timer(?:\\s+for)?)\\s+(\\d+)\\s*(second|seconds|sec|minute|minutes|min|hour|hours|hr)?.*",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = timerRegex.matcher(text)
        if (matcher.matches()) {
            val amount = matcher.group(1)?.toIntOrNull() ?: return null
            val unit = matcher.group(2)?.lowercase(Locale.ROOT) ?: "minutes"

            val seconds = when {
                unit.startsWith("sec") -> amount
                unit.startsWith("hour") || unit.startsWith("hr") -> amount * 3600
                else -> amount * 60 // minutes default
            }

            val label = "$amount $unit Assistant Timer"
            return AssistantAction.SetTimer(durationSeconds = seconds, label = label)
        }
        return null
    }

    private fun checkAlarm(text: String): AssistantAction? {
        // e.g. "set an alarm for 7:30 am", "alarm for 8:00", "wake me up at 6 am", "alarm 7 pm"
        val alarmRegex = Pattern.compile(
            ".*?(?:alarm(?:\\s+for)?|wake\\s+me\\s+up\\s+at)\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?.*",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = alarmRegex.matcher(text)
        if (matcher.matches()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            val amPm = matcher.group(3)?.lowercase(Locale.ROOT)

            if (amPm == "pm" && hour < 12) {
                hour += 12
            } else if (amPm == "am" && hour == 12) {
                hour = 0
            }

            if (hour in 0..23 && minute in 0..59) {
                return AssistantAction.SetAlarm(
                    hour = hour,
                    minute = minute,
                    label = "Assistant Alarm"
                )
            }
        }
        return null
    }

    private fun checkSettings(text: String): AssistantAction? {
        return when {
            text.contains("wi-fi") || text.contains("wifi") ->
                AssistantAction.OpenSetting(SettingType.WIFI, "Wi-Fi Settings")

            text.contains("bluetooth") ->
                AssistantAction.OpenSetting(SettingType.BLUETOOTH, "Bluetooth Settings")

            text.contains("display") || text.contains("screen brightness") ->
                AssistantAction.OpenSetting(SettingType.DISPLAY, "Display Settings")

            text.contains("battery") ->
                AssistantAction.OpenSetting(SettingType.BATTERY, "Battery Settings")

            text.contains("sound") || text.contains("volume") || text.contains("audio settings") ->
                AssistantAction.OpenSetting(SettingType.SOUND, "Sound & Vibration Settings")

            text.contains("app settings") || text.contains("applications") || text.contains("installed apps") ->
                AssistantAction.OpenSetting(SettingType.APPS, "App Management Settings")

            text == "open settings" || text == "device settings" || text == "system settings" ->
                AssistantAction.OpenSetting(SettingType.GENERAL, "System Settings")

            else -> null
        }
    }

    private fun checkOpenApp(text: String): AssistantAction? {
        val appPrefixes = listOf("open ", "launch ", "start ")
        val matchedPrefix = appPrefixes.find { text.startsWith(it) } ?: return null
        val requestedApp = text.removePrefix(matchedPrefix).trim()

        if (requestedApp.isBlank()) return null

        return when (requestedApp) {
            "camera" -> AssistantAction.OpenApp("Camera", null)
            "youtube" -> AssistantAction.OpenApp("YouTube", "com.google.android.youtube")
            "chrome", "google chrome", "browser" -> AssistantAction.OpenApp("Chrome", "com.android.chrome")
            "maps", "google maps" -> AssistantAction.OpenApp("Google Maps", "com.google.android.apps.maps")
            "calculator" -> AssistantAction.OpenApp("Calculator", "com.google.android.calculator")
            "calendar" -> AssistantAction.OpenApp("Calendar", "com.google.android.calendar")
            "clock" -> AssistantAction.OpenApp("Clock", "com.google.android.deskclock")
            "settings" -> AssistantAction.OpenSetting(SettingType.GENERAL, "System Settings")
            else -> AssistantAction.OpenApp(requestedApp.replaceFirstChar { it.uppercase() })
        }
    }
}
