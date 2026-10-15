package com.example.voiceassistant.domain.parser

import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.MessageChannel
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
            .replace(Regex("\\s+"), " ")
            .trim()
            .let { phrase ->
                when (phrase) {
                    "aama", "ama", "aamam", "seri", "sari", "ok pannu", "pannunga" -> "yes"
                    "illa", "vendam", "vendaam", "cancel pannu", "niruthu" -> "no"
                    else -> phrase
                }
            }

        // Exact phrases only: "yes, but no" must never confirm an action.
        val positiveTokens = setOf(
            "yes", "yes please", "confirm", "proceed", "go ahead", "do it",
            "sure", "yeah", "yep", "ok", "okay"
        )
        val negativeTokens = setOf(
            "no", "no cancel", "cancel", "stop", "abort", "never mind",
            "dont", "don't", "reject", "nope"
        )

        return when {
            clean in positiveTokens -> ConfirmationDecision.CONFIRMED
            clean in negativeTokens -> ConfirmationDecision.CANCELLED
            else -> ConfirmationDecision.UNCLEAR
        }
    }

    /**
     * Parses a spoken command into an [AssistantAction].
     */
    fun parseCommand(rawText: String): AssistantAction {
        val normalized = normalizeTanglish(rawText).trim()
            .replace(Regex("\\s+"), " ")
        val normalizedLowercase = normalized.lowercase(Locale.ROOT)

        if (normalized.isBlank()) {
            return AssistantAction.Unknown(rawText, "No speech recognized.")
        }

        // Search requests are navigational and do not need a confirmation prompt.
        val youtubeSearch = Regex(
            "^(?:(?:open|launch|start)\\s+youtube\\s+and\\s+)?(?:search(?:\\s+youtube)?(?:\\s+for)?|youtube\\s+search(?:\\s+for)?)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(normalized)
        if (youtubeSearch != null) {
            val query = youtubeSearch.groupValues[1].trim()
            if (query.isNotBlank()) return AssistantAction.SearchYouTube(query)
        }

        // Convert combined phone-launch + call requests to the allowlisted call action.
        Regex(
            "^(?:(?:open|launch|start)\\s+)?(?:phone|dialer|telephone)\\s+(?:and\\s+)?(?:call|dial)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(normalized)?.let {
            return checkSensitiveAction("call ${it.groupValues[1].trim()}")!!
        }
        Regex(
            "^(?:phone\\s+call|make\\s+a\\s+call\\s+to)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(normalized)?.let {
            return checkSensitiveAction("call ${it.groupValues[1].trim()}")!!
        }

        // 1. Check sensitive actions first (requiring confirmation)
        checkSensitiveAction(normalized)?.let { return it }

        // Locking is available only through Android's user-approved device-admin API.
        if (normalizedLowercase in setOf("lock phone", "lock my phone", "lock mobile", "lock my mobile")) {
            return AssistantAction.LockDevice
        }

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

    /**
     * Normalizes common Tamil-English (Tanglish) commands locally. Device actions
     * remain allowlisted and are never delegated to the AI model.
     */
    private fun normalizeTanglish(input: String): String {
        val original = input.trim().replace(Regex("\\s+"), " ")
        val text = original.lowercase(Locale.ROOT)
            .replace(Regex("[.,?!]"), "")
            .replace(Regex("\\s+"), " ")

        val exactAliases = mapOf(
            "time enna" to "what time is it",
            "ippo time enna" to "what time is it",
            "mani enna" to "what time is it",
            "innaiku date enna" to "what is today's date",
            "indru date enna" to "what is today's date",
            "wifi settings open pannu" to "open wifi settings",
            "wifi settings thira" to "open wifi settings",
            "wifi on pannu" to "open wifi settings",
            "wifi off pannu" to "open wifi settings",
            "bluetooth on pannu" to "open bluetooth settings",
            "bluetooth off pannu" to "open bluetooth settings",
            "bluetooth settings open pannu" to "open bluetooth settings",
            "settings open pannu" to "open settings",
            "whatsapp" to "open whatsapp",
            "whats app" to "open whatsapp",
            "phone" to "open phone",
            "phone app" to "open phone",
            "dialer" to "open phone",
            "camera open pannu" to "open camera",
            "mobile lock pannu" to "lock my phone",
            "phone lock pannu" to "lock my phone",
            "en phone lock pannu" to "lock my phone"
        )
        exactAliases[text]?.let { return it }

        // Handle combined WhatsApp + message requests before the generic app-launch parser.
        // Speech recognition may render WhatsApp as "what's app" or "whats app".
        Regex(
            "^(?:(?:open|launch|start)\\s+)?(?:app\\s+)?(?:whats\\s*app|what'?s\\s*app|whatsapp)\\s+(?:and\\s+)?(?:send|sent)\\s+(?:a\\s+)?(?:message|text)\\s+to\\s+(.+?)(?:\\s*,\\s*|\\s+)(?:saying|that|content\\s+is|message\\s+is)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).matchEntire(original)?.let {
            return "send whatsapp message to ${it.groupValues[1].trim().trimEnd(',', ';', ':').trim()} saying ${it.groupValues[2].trim()}"
        }

        Regex("^(.+?)\\s+ku\\s+(?:message|sms)\\s+anuppu\\s+(.+)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.let {
                return "text ${it.groupValues[1].trim()} saying ${it.groupValues[2].trim()}"
            }

        Regex("^(.+?)\\s+(?:ku|kitta)\\s+call\\s+(?:pannu|panunga)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.let { return "call ${it.groupValues[1].trim()}" }

        Regex("^(.+?)\\s+(?:open\\s+pannu|open\\s+panunga|thira|thirakku)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.let { return "open ${it.groupValues[1].trim()}" }

        Regex("^(.+?)\\s+(?:launch|start)\\s+pannu$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.let { return "open ${it.groupValues[1].trim()}" }

        Regex("^(?:timer|oru timer)\\s+(?:for\\s+)?(.+?)\\s+(?:set\\s+pannu|set\\s+panunga)$", RegexOption.IGNORE_CASE)
            .matchEntire(original)?.let { return "set a timer for ${it.groupValues[1].trim()}" }

        return original
    }

    private fun checkSensitiveAction(text: String): AssistantAction? {
        val normalized = text.trim()
        val whatsappPattern = Pattern.compile(
            "^(?:(?:send|sent)\\s+)?(?:a\\s+)?whatsapp\\s+(?:message|text)\\s+to\\s+(.+?)\\s+(?:saying|that|content\\s+is|message\\s+is)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val whatsappMatcher = whatsappPattern.matcher(normalized)
        if (whatsappMatcher.matches()) {
            val recipient = whatsappMatcher.group(1)?.trim()?.trimEnd(',', ';', ':')?.trim().orEmpty()
            val body = whatsappMatcher.group(2)?.trim().orEmpty()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return AssistantAction.RequireConfirmation(
                    action = SensitiveAction.SendMessage(recipient, body, MessageChannel.WHATSAPP),
                    explanation = "Confirm opening WhatsApp for '$recipient' with this text: \"$body\""
                )
            }
        }

        val msgPattern1 = Pattern.compile(
            "^(?:send(?:\\s+a)?\\s+(?:message|text|sms)\\s+to)\\s+(.+?)\\s+(?:saying|that|content\\s+is|message\\s+is)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val msgMatcher1 = msgPattern1.matcher(normalized)
        if (msgMatcher1.matches()) {
            val recipient = msgMatcher1.group(1)?.trim().orEmpty()
            val body = msgMatcher1.group(2)?.trim().orEmpty()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return AssistantAction.RequireConfirmation(
                    action = SensitiveAction.SendMessage(recipient, body),
                    explanation = "Confirm opening a message to '$recipient' with this text: \"$body\""
                )
            }
        }

        val msgPattern2 = Pattern.compile(
            "^(?:text|message)\\s+(.+?)\\s+(?:saying|that)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )
        val msgMatcher2 = msgPattern2.matcher(normalized)
        if (msgMatcher2.matches()) {
            val recipient = msgMatcher2.group(1)?.trim().orEmpty()
            val body = msgMatcher2.group(2)?.trim().orEmpty()
            if (recipient.isNotBlank() && body.isNotBlank()) {
                return AssistantAction.RequireConfirmation(
                    action = SensitiveAction.SendMessage(recipient, body),
                    explanation = "Confirm opening a message to '$recipient' with this text: \"$body\""
                )
            }
        }

        val callPattern = Pattern.compile(
            "^(?:call|dial|make\\s+a\\s+phone\\s+call\\s+to)\\s+(.+?)$",
            Pattern.CASE_INSENSITIVE
        )
        val callMatcher = callPattern.matcher(normalized)
        if (callMatcher.matches()) {
            val target = callMatcher.group(1)?.trim().orEmpty()
            if (target.isNotBlank()) {
                return AssistantAction.RequireConfirmation(
                    action = SensitiveAction.MakePhoneCall(target),
                    explanation = "Confirm opening the dialer for '$target'"
                )
            }
        }

        val deleteMatcher = Pattern.compile(
            "^(?:delete|remove)\\s+(.+)$", Pattern.CASE_INSENSITIVE
        ).matcher(normalized)
        if (deleteMatcher.matches() || normalized.matches(Regex("^clear\\s+data(?:\\s+.*)?$", RegexOption.IGNORE_CASE))) {
            val target = if (deleteMatcher.matches()) deleteMatcher.group(1).trim()
                else normalized.replaceFirst(Regex("^clear\\s+data\\s*", RegexOption.IGNORE_CASE), "").ifBlank { "requested data" }
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.DeleteData(target),
                explanation = "High risk: confirm the request to delete '$target'? Automated deletion is blocked."
            )
        }

        val purchaseMatcher = Pattern.compile(
            "^(?:buy|purchase|order)\\s+(.+)$", Pattern.CASE_INSENSITIVE
        ).matcher(normalized)
        if (purchaseMatcher.matches()) {
            val item = purchaseMatcher.group(1)?.trim().orEmpty()
            if (item.isNotBlank()) {
                return AssistantAction.RequireConfirmation(
                    action = SensitiveAction.MakePurchase(item),
                    explanation = "Confirm the request concerning '$item'? Automated purchases are blocked."
                )
            }
        }

        val lowerText = normalized.lowercase(Locale.ROOT)
        val securityRequest = lowerText.matches(
            Regex("^(change|update|reset|disable|enable|remove|set)\\s+.*(password|pin|screen lock|privacy setting|security setting).*$")
        )
        if (securityRequest) {
            return AssistantAction.RequireConfirmation(
                action = SensitiveAction.ChangeSecuritySetting(normalized),
                explanation = "Security changes require manual interaction. Open Security Settings?"
            )
        }

        return null
    }

    private fun checkTimeOrDate(text: String): AssistantAction? {
        val isTimeQuery = text.matches(Regex(
            "^(what time is it|what('s| is) the time|current time|tell me the time|time)$"
        ))
        if (isTimeQuery) {
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            return AssistantAction.TellTime("The current time is ${timeFormat.format(Date(timeProvider()))}.")
        }

        val isDateQuery = text.matches(Regex(
            "^(what date is it|what is today's date|what's today's date|today's date|what's the date|what is the date|current date|what day is it|tell me the date)$"
        ))
        if (isDateQuery) {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
            return AssistantAction.TellDate("Today is ${dateFormat.format(Date(timeProvider()))}.")
        }
        return null
    }

    private fun checkTimer(text: String): AssistantAction? {
        val pattern = Regex(
            "^(?:set\\s+(?:a\\s+)?timer(?:\\s+for)?|timer(?:\\s+for)?)\\s+(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)?$",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.matchEntire(text.trim()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2].lowercase(Locale.ROOT).ifBlank { "minutes" }
        if (amount <= 0L) return AssistantAction.Unknown(text, "Timer duration must be greater than zero.")
        val multiplier = when {
            unit.startsWith("sec") -> 1L
            unit.startsWith("hour") || unit.startsWith("hr") -> 3600L
            else -> 60L
        }
        if (amount > 604800L / multiplier) {
            return AssistantAction.Unknown(text, "Timer duration must be between 1 second and 7 days.")
        }
        val seconds = amount * multiplier
        if (seconds !in 1L..604800L) {
            return AssistantAction.Unknown(text, "Timer duration must be between 1 second and 7 days.")
        }
        val duration = seconds.toInt()
        return AssistantAction.SetTimer(durationSeconds = duration, label = "$amount $unit Assistant Timer")
    }

    private fun checkAlarm(text: String): AssistantAction? {
        // Flexible local parsing for common spoken forms:
        // "set alarm on 5 40 tomorrow morning", "alarm tomorrow at 5:40",
        // "wake me up at 5:40 am", "set an alarm for 17:40".
        var phrase = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("[,?!]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (!Regex(".*\\b(alarm|wake me up)\\b.*").containsMatchIn(phrase)) return null

        var dayOffset = when {
            Regex("\\bday after tomorrow\\b").containsMatchIn(phrase) -> 2
            Regex("\\btomorrow\\b").containsMatchIn(phrase) -> 1
            else -> 0
        }
        phrase = phrase
            .replace(Regex("\\bday after tomorrow\\b"), " ")
            .replace(Regex("\\btomorrow\\b"), " ")
            .replace(Regex("\\btoday\\b"), " ")
            .replace(Regex("\\b(in the )?morning\\b"), " am ")
            .replace(Regex("\\b(at )?night\\b"), " pm ")
            .replace(Regex("\\b(in the )?evening\\b"), " pm ")
            .replace(Regex("\\b(in the )?afternoon\\b"), " pm ")
            .replace(Regex("\\s+"), " ")
            .trim()

        phrase = phrase
            .replaceFirst(Regex("^(?:please )?(?:set )?(?:an? )?alarm(?: clock)?\\s*"), "")
            .replaceFirst(Regex("^wake me up\\s*"), "")
            .replaceFirst(Regex("^(?:for|on|at)\\s+"), "")
            .replaceFirst(Regex("\\bat\\s+"), "")
            .replaceFirst(Regex("\\bfor\\s+"), "")
            .trim()

        val timeMatch = Regex("^(.*?)(\\d{1,2})(?::|\\s+)(\\d{2})(?:\\s*(am|pm))?$", RegexOption.IGNORE_CASE)
            .matchEntire(phrase)
        val hourOnlyMatch = if (timeMatch == null) {
            Regex("^(.*?)(\\d{1,2})\\s*(am|pm)?$", RegexOption.IGNORE_CASE).matchEntire(phrase)
        } else null
        var hour: Int
        val minute: Int
        var amPm: String
        when {
            timeMatch != null -> {
                hour = timeMatch.groupValues[2].toIntOrNull() ?: return null
                minute = timeMatch.groupValues[3].toIntOrNull() ?: return null
                amPm = timeMatch.groupValues[4].lowercase(Locale.ROOT)
                val prefix = timeMatch.groupValues[1]
                if (prefix.isNotBlank() && !prefix.matches(Regex("^[a-z ]*$"))) return null
            }
            hourOnlyMatch != null -> {
                hour = hourOnlyMatch.groupValues[2].toIntOrNull() ?: return null
                minute = 0
                amPm = hourOnlyMatch.groupValues[3].lowercase(Locale.ROOT)
                val prefix = hourOnlyMatch.groupValues[1].trim()
                if (prefix.isNotBlank()) return null
            }
            else -> return null
        }

        if (minute !in 0..59) return AssistantAction.Unknown(text, "Alarm minutes must be between 00 and 59.")
        if (amPm.isNotEmpty()) {
            if (hour !in 1..12) return AssistantAction.Unknown(text, "Use an hour from 1 to 12 with AM or PM.")
            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0
        } else {
            if (hour !in 0..23) return AssistantAction.Unknown(text, "Use a 24-hour time or include morning/afternoon.")
            // "morning" is normalized to AM; spoken "5 40 tomorrow morning" gets 05:40.
            if (Regex("\\b(am)\\b").containsMatchIn(phrase) && hour == 12) hour = 0
        }
        return AssistantAction.SetAlarm(
            hour = hour,
            minute = minute,
            label = "Assistant Alarm",
            dayOffset = dayOffset
        )
    }

    private fun checkSettings(text: String): AssistantAction? {
        val target = text.trim()
            .removePrefix("open ").removePrefix("show ").removePrefix("go to ").removePrefix("launch ")
            .removeSuffix(" settings").trim()
        return when {
            target in setOf("wi-fi", "wifi", "wireless", "network") || text in setOf("open wifi settings", "open wi-fi settings") ->
                AssistantAction.OpenSetting(SettingType.WIFI, "Wi-Fi Settings")
            target == "bluetooth" -> AssistantAction.OpenSetting(SettingType.BLUETOOTH, "Bluetooth Settings")
            target in setOf("display", "screen", "screen brightness") -> AssistantAction.OpenSetting(SettingType.DISPLAY, "Display Settings")
            target == "battery" -> AssistantAction.OpenSetting(SettingType.BATTERY, "Battery Settings")
            target in setOf("sound", "volume", "audio") -> AssistantAction.OpenSetting(SettingType.SOUND, "Sound & Vibration Settings")
            target in setOf("app", "apps", "applications", "installed apps") || text in setOf("open app settings", "open applications") ->
                AssistantAction.OpenSetting(SettingType.APPS, "App Management Settings")
            text in setOf("open settings", "device settings", "system settings") ->
                AssistantAction.OpenSetting(SettingType.GENERAL, "System Settings")
            else -> null
        }
    }

    private fun checkOpenApp(text: String): AssistantAction? {
        val appPrefixes = listOf("open ", "launch ", "start ")
        val matchedPrefix = appPrefixes.find { text.startsWith(it) } ?: return null
        val requestedApp = text.removePrefix(matchedPrefix).trim()

        if (requestedApp.isBlank()) return null
        if (requestedApp.length > 80 || requestedApp.any { it.isISOControl() }) return null

        return when (requestedApp) {
            "whatsapp", "whats app", "what's app" -> AssistantAction.OpenApp("WhatsApp", "com.whatsapp")
            "phone", "phone app", "dialer", "telephone" -> AssistantAction.OpenApp("Phone", "com.google.android.dialer")
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
