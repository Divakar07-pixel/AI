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
enum class MessageChannel { SMS, WHATSAPP }

enum class VolumeDirection { UP, DOWN, MUTE, UNMUTE }

enum class MediaCommand { PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS }

sealed class SensitiveAction {
    data class SendMessage(
        val recipient: String,
        val messageText: String,
        val channel: MessageChannel = MessageChannel.SMS
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
        val label: String = "Assistant Alarm",
        /** 0 = today/next occurrence; 1 = tomorrow. */
        val dayOffset: Int = 0
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

    // Search YouTube using the user's query, without requiring confirmation.
    data class SearchYouTube(val query: String) : AssistantAction()

    // Volume control (local AudioManager; no confirmation required).
    data class AdjustVolume(
        val direction: VolumeDirection,
        val steps: Int = 1
    ) : AssistantAction()

    // Flashlight / torch toggle via CameraManager.
    data class SetFlashlight(val enabled: Boolean) : AssistantAction()

    // Media transport controls (play / pause / next / previous).
    data class MediaControl(val command: MediaCommand) : AssistantAction()

    // Read-only battery status.
    object TellBatteryStatus : AssistantAction()

    // Locks the device only after the user has explicitly enabled the device-admin capability.
    object LockDevice : AssistantAction()

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
