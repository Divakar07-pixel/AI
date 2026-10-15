package com.example.voiceassistant.service

import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.voiceassistant.BuildConfig
import com.example.voiceassistant.MainActivity
import com.example.voiceassistant.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-started foreground microphone service. Porcupine performs wake-word inference locally.
 * A custom Android .ppn model and a Picovoice AccessKey are required; neither is committed.
 */
class VoiceAssistantForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_assistant_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.example.voiceassistant.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.voiceassistant.action.STOP_SERVICE"
        const val ACTION_PAUSE_DETECTION = "com.example.voiceassistant.action.PAUSE_DETECTION"
        const val EXTRA_WAKE_WORD_DETECTED = "com.example.voiceassistant.extra.WAKE_WORD_DETECTED"
        private const val TAG = "HeyMaxWakeWord"
        private const val KEYWORD_ASSET = "Hey-Max_android.ppn"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    private var porcupineManager: PorcupineManager? = null
    private var notificationText = "Listening locally for “Hey Max”."
    private var wakeWordDetected = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_DETECTION -> {
                stopWakeWordDetection()
                notificationText = "Paused while Max handles your command."
                updateNotification()
            }
            else -> {
                notificationText = "Listening locally for “Hey Max”."
                startForegroundCompat()
                _isServiceRunning.value = true
                if (porcupineManager == null) startWakeWordDetection()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startWakeWordDetection() {
        if (BuildConfig.PICOVOICE_ACCESS_KEY.isBlank()) {
            showSetupError("Setup needed: add PICOVOICE_ACCESS_KEY to VA/local.properties.")
            return
        }
        val modelExists = assets.list("")?.contains(KEYWORD_ASSET) == true
        if (!modelExists) {
            showSetupError("Setup needed: add $KEYWORD_ASSET to app/src/main/assets.")
            return
        }

        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY)
                .setKeywordPath(KEYWORD_ASSET)
                .setSensitivity(0.65f)
                .build(this) {
                    onWakeWordDetected()
                }
            porcupineManager?.start()
            notificationText = "Listening locally for “Hey Max”. Tap to open Max."
            updateNotification()
            Log.i(TAG, "Offline Hey Max wake-word detection started.")
        } catch (error: PorcupineException) {
            Log.e(TAG, "Could not start Porcupine wake-word detection.", error)
            stopWakeWordDetection()
            showSetupError("Wake-word engine failed. Check AccessKey and Android .ppn model.")
        } catch (error: Exception) {
            Log.e(TAG, "Unexpected wake-word initialization error.", error)
            stopWakeWordDetection()
            showSetupError("Wake-word setup failed. See app logs for details.")
        }
    }

    private fun onWakeWordDetected() {
        if (wakeWordDetected) return
        wakeWordDetected = true
        stopWakeWordDetection()
        notificationText = "Hey Max heard you. Tap this notification to speak a command."
        updateNotification()
        Log.i(TAG, "Wake word detected; waiting for user to tap the notification before command capture.")
    }

    private fun stopWakeWordDetection() {
        val manager = porcupineManager
        porcupineManager = null
        if (manager != null) {
            try {
                manager.stop()
            } catch (error: Exception) {
                Log.w(TAG, "Porcupine stop reported an error.", error)
            }
            try {
                manager.delete()
            } catch (error: Exception) {
                Log.w(TAG, "Porcupine cleanup reported an error.", error)
            }
        }
    }

    private fun showSetupError(message: String) {
        notificationText = message
        updateNotification()
        Log.e(TAG, message)
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (wakeWordDetected) putExtra(EXTRA_WAKE_WORD_DETECTED, true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, StopServiceReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_listening_title))
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.service_stop_action),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.listening_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.listening_service_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun stopForegroundService() {
        stopWakeWordDetection()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopWakeWordDetection()
        _isServiceRunning.value = false
        super.onDestroy()
    }
}
