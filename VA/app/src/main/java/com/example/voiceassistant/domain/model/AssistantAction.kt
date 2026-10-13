package com.example.voiceassistant.domain.model

/**
 * Supported Android settings screens in the allowlist.
 */
enum class SettingType {
    WIFI,
    BLUETOOTH,
    DISPLAY,
    BATTERY,
    SOUND,
    APPS,
    GENERAL
}

/**
 * Sensitive operations that require explicit user confirmation
 * before execution (voice "Confirm" or tapping the Confirm button).
 */
sealed class SensitiveAction {
    data class SendMessage(
        val recipient: String,
        val messageText: String
    ) : SensitiveAction()

    data class MakePhoneCall(
        val contactOrNumber: String
    ) : SensitiveAction()

    data class DeleteData(
        val description: String
    ) : SensitiveAction()

    data class MakePurchase(
        val itemDescription: String
    ) : SensitiveAction()

    data class ChangeSecuritySetting(
        val settingName: String
    ) : SensitiveAction()
}

/**
 * Explicit allowlist of actions the assistant is permitted to perform.
 */
sealed class AssistantAction {
    // 1. Tell Time
    data class TellTime(val formattedTime: String) : AssistantAction()

    // 2. Tell Date
    data class TellDate(val formattedDate: String) : AssistantAction()

    // 3. Set Timers
    data class SetTimer(
        val durationSeconds: Int,
        val label: String = "Assistant Timer"
    ) : AssistantAction()

    // 4. Set Alarms
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String = "Assistant Alarm"
    ) : AssistantAction()

    // 5. Open Apps
    data class OpenApp(
        val appName: String,
        val packageName: String? = null
    ) : AssistantAction()

    // 6. Open Settings
    data class OpenSetting(
        val settingType: SettingType,
        val displayName: String
    ) : AssistantAction()

    // Sensitive action awaiting explicit user confirmation
    data class RequireConfirmation(
        val action: SensitiveAction,
        val explanation: String
    ) : AssistantAction()

    // Unknown or unsupported command
    data class Unknown(
        val rawCommand: String,
        val reason: String = "This command is not in the allowed action list."
    ) : AssistantAction()
}

/**
 * Result of executing an action.
 */
sealed class ExecutionResult {
    data class Success(val message: String) : ExecutionResult()
    data class Cancelled(val message: String) : ExecutionResult()
    data class Failure(val error: String) : ExecutionResult()
}
