package com.example.voiceassistant.data

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.voiceassistant.domain.model.AssistantAction
import com.example.voiceassistant.domain.model.ExecutionResult
import com.example.voiceassistant.domain.model.MessageChannel
import com.example.voiceassistant.domain.model.SensitiveAction
import com.example.voiceassistant.domain.model.SettingType
import com.example.voiceassistant.service.AssistantDeviceAdminReceiver

class ActionExecutor(private val context: Context) {

    /**
     * Executes an approved [AssistantAction] safely.
     */
    fun execute(action: AssistantAction): ExecutionResult {
        return try {
            when (action) {
                is AssistantAction.TellTime -> {
                    ExecutionResult.Success(action.formattedTime)
                }

                is AssistantAction.TellDate -> {
                    ExecutionResult.Success(action.formattedDate)
                }

                is AssistantAction.SetTimer -> {
                    executeSetTimer(action.durationSeconds, action.label)
                }

                is AssistantAction.SetAlarm -> {
                    executeSetAlarm(action.hour, action.minute, action.label)
                }

                is AssistantAction.OpenSetting -> {
                    executeOpenSetting(action.settingType, action.displayName)
                }

                AssistantAction.LockDevice -> executeLockDevice()

                is AssistantAction.OpenApp -> {
                    executeOpenApp(action.appName, action.packageName)
                }

                is AssistantAction.SearchYouTube -> {
                    executeYouTubeSearch(action.query)
                }

                is AssistantAction.RequireConfirmation -> {
                    ExecutionResult.Failure("Action requires explicit user confirmation before execution.")
                }

                is AssistantAction.Unknown -> {
                    ExecutionResult.Failure(action.reason)
                }
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("Failed to execute action: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Executes a confirmed sensitive action.
     */
    fun executeSensitiveAction(sensitiveAction: SensitiveAction): ExecutionResult {
        return try {
            when (sensitiveAction) {
                is SensitiveAction.SendMessage -> {
                    val recipient = sensitiveAction.recipient.trim()
                    if (sensitiveAction.channel == MessageChannel.WHATSAPP) {
                        // Resolve the spoken contact to a phone number, then open that exact
                        // WhatsApp chat with the message pre-filled. WhatsApp still requires
                        // the user to tap Send; the app never sends silently.
                        val phoneNumber = resolveWhatsAppNumber(recipient)
                            ?: return ExecutionResult.Failure(
                                "I couldn't identify one WhatsApp number for '$recipient'. Grant Contacts permission, use a unique contact name, or speak the full international phone number."
                            )
                        val digits = phoneNumber.filter(Char::isDigit)
                        if (digits.length < 7) {
                            return ExecutionResult.Failure("The number for '$recipient' doesn't look valid for WhatsApp.")
                        }
                        val chatUri = Uri.parse(
                            "https://wa.me/$digits?text=${Uri.encode(sensitiveAction.messageText)}"
                        )
                        val intent = Intent(Intent.ACTION_VIEW, chatUri).apply {
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                            ExecutionResult.Success(
                                "Opened the WhatsApp chat for '$recipient' with your message filled in. Review it and tap Send in WhatsApp."
                            )
                        } else {
                            ExecutionResult.Failure("WhatsApp is not installed or could not open this chat.")
                        }
                    } else {
                        // Open SMS pre-populated; the user must review and tap Send.
                        val isPhoneNumber = recipient.matches(Regex("^\\+?[0-9() .-]{5,20}$"))
                        val uri = if (isPhoneNumber) Uri.parse("smsto:${Uri.encode(recipient)}") else Uri.parse("smsto:")
                        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                            putExtra("sms_body", sensitiveAction.messageText)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                            val nextStep = if (isPhoneNumber) "Review the message and tap Send in your messaging app."
                                else "Choose the contact '$recipient', review the message, then tap Send."
                            ExecutionResult.Success("Messaging app opened. $nextStep")
                        } else {
                            ExecutionResult.Failure("No messaging application available on this device.")
                        }
                    }
                }

                is SensitiveAction.MakePhoneCall -> {
                    // Open dialer safely with contact/number pre-populated
                    val target = sensitiveAction.contactOrNumber.trim()
                    val isPhoneNumber = target.matches(Regex("^\\+?[0-9*#() .-]{3,25}$"))
                    val dialUri = if (isPhoneNumber) Uri.parse("tel:${Uri.encode(target)}") else Uri.parse("tel:")
                    val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (dialIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(dialIntent)
                        val nextStep = if (isPhoneNumber) "Review the number and tap Call if correct."
                            else "Find '${target}' in Contacts and tap Call."
                        ExecutionResult.Success("Dialer opened. $nextStep")
                    } else {
                        ExecutionResult.Failure("No dialer application found.")
                    }
                }

                is SensitiveAction.DeleteData -> {
                    ExecutionResult.Success(
                        "Action acknowledged. To safeguard your device, automated deletion of '${sensitiveAction.description}' is blocked. Please delete manually."
                    )
                }

                is SensitiveAction.MakePurchase -> {
                    ExecutionResult.Success(
                        "Purchase requested for '${sensitiveAction.itemDescription}'. Automated purchases are blocked for your security."
                    )
                }

                is SensitiveAction.ChangeSecuritySetting -> {
                    val securityIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(securityIntent)
                    ExecutionResult.Success("Opened Security Settings for manual configuration.")
                }
            }
        } catch (e: Exception) {
            ExecutionResult.Failure("Could not complete action: ${e.localizedMessage}")
        }
    }

    /**
     * Resolves a spoken contact name to one phone number. Ambiguous names are rejected
     * instead of opening or messaging the wrong person.
     */
    private fun resolveWhatsAppNumber(recipient: String): String? {
        if (recipient.matches(Regex("^\\+?[0-9() .-]{7,25}$"))) {
            return recipient
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val matches = mutableListOf<Pair<String, String>>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIndex >= 0 && numberIndex >= 0) {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (name.isNotBlank() && number.isNotBlank()) matches.add(name to number)
                }
            }
        }

        val exact = matches.filter { it.first.equals(recipient, ignoreCase = true) }
            .distinctBy { it.second.filter(Char::isDigit) }
        val candidates = if (exact.isNotEmpty()) exact else {
            matches.filter { it.first.contains(recipient, ignoreCase = true) }
                .distinctBy { it.second.filter(Char::isDigit) }
        }
        return candidates.singleOrNull()?.second
    }

    private fun executeSetTimer(seconds: Int, label: String): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ExecutionResult.Success("Clock app opened with a ${formatDuration(seconds)} timer request. Confirm it in the Clock app.")
        } else {
            ExecutionResult.Failure("No clock or timer app available to set timer.")
        }
    }

    private fun executeSetAlarm(hour: Int, minute: Int, label: String): ExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            val timeString = String.format("%02d:%02d", hour, minute)
            ExecutionResult.Success("Clock app opened with an alarm request for $timeString. Confirm it in the Clock app.")
        } else {
            ExecutionResult.Failure("No clock app available to set alarm.")
        }
    }

    private fun executeOpenSetting(settingType: SettingType, displayName: String): ExecutionResult {
        val action = when (settingType) {
            SettingType.WIFI -> Settings.ACTION_WIFI_SETTINGS
            SettingType.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
            SettingType.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
            SettingType.BATTERY -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            SettingType.SOUND -> Settings.ACTION_SOUND_SETTINGS
            SettingType.APPS -> Settings.ACTION_APPLICATION_SETTINGS
            SettingType.GENERAL -> Settings.ACTION_SETTINGS
        }

        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            ExecutionResult.Success("Opened $displayName.")
        } catch (e: ActivityNotFoundException) {
            // Fallback to general settings if specific sub-setting is unsupported on this OEM device
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            ExecutionResult.Success("Opened General Settings (specific screen not supported on device).")
        }
    }

    private fun executeYouTubeSearch(query: String): ExecutionResult {
        val searchUri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        val intent = Intent(Intent.ACTION_VIEW, searchUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ExecutionResult.Success("Searching YouTube for '$query'.")
        } else {
            ExecutionResult.Failure("No app or browser can open YouTube search results.")
        }
    }

    private fun executeOpenApp(appName: String, packageName: String?): ExecutionResult {
        val pm = context.packageManager

        // Special handling for Camera
        if (appName.equals("Camera", ignoreCase = true)) {
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return if (cameraIntent.resolveActivity(pm) != null) {
                context.startActivity(cameraIntent)
                ExecutionResult.Success("Opening Camera.")
            } else {
                ExecutionResult.Failure("Camera app could not be launched.")
            }
        }

        // Try package name launch intent if specified
        if (!packageName.isNullOrBlank()) {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ExecutionResult.Success("Opening $appName.")
            }
        }

        // Search installed apps by display label
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(mainIntent, 0)
        val exactMatch = resolveInfoList.firstOrNull {
            it.loadLabel(pm).toString().equals(appName, ignoreCase = true)
        }
        val partialMatch = exactMatch ?: resolveInfoList.firstOrNull {
            it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
        }
        if (partialMatch != null) {
            val label = partialMatch.loadLabel(pm).toString()
            val intent = pm.getLaunchIntentForPackage(partialMatch.activityInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult.Success("Opening $label.")
            }
        }

        return ExecutionResult.Failure("App '$appName' is not installed on this device.")
    }

    private fun executeLockDevice(): ExecutionResult {
        val policyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(context, AssistantDeviceAdminReceiver::class.java)

        return if (policyManager.isAdminActive(adminComponent)) {
            policyManager.lockNow()
            ExecutionResult.Success("Locking your phone now.")
        } else {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Enable this optional capability only if you want the assistant to lock your phone on your explicit command."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult.Success("Android opened the device-admin consent screen. Review and enable it to use the voice lock command.")
        }
    }

    private fun formatDuration(seconds: Int): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600} hour(s)"
            seconds >= 60 -> "${seconds / 60} minute(s)"
            else -> "$seconds seconds"
        }
    }
}
