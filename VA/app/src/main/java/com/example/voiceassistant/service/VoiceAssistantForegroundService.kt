package com.example.voiceassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.voiceassistant.MainActivity
import com.example.voiceassistant.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-only placeholder for the future wake-word detector.
 *
 * It deliberately does not open the microphone, load a model, or claim that
 * "Hey Jarvis" detection works. Keep this feature disabled until a model with
 * compatible commercial rights is provided, integrated, and tested.
 */
class VoiceAssistantForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_assistant_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.example.voiceassistant.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.voiceassistant.action.STOP_SERVICE"
        const val ACTION_PAUSE_DETECTION = "com.example.voiceassistant.action.PAUSE_DETECTION"
        const val EXTRA_WAKE_WORD_DETECTED = "com.example.voiceassistant.extra.WAKE_WORD_DETECTED"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning
    }

    private var notificationText = "Wake-word detection is not configured. Manual microphone input remains available."

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
                notificationText = "Wake-word detection is not configured. Use the microphone button in the app."
                updateNotification()
            }
            else -> {
                notificationText = "Wake-word detection is pending an approved model. Manual input is available."
                startForegroundCompat()
                _isServiceRunning.value = true
                updateNotification()
            }
        }
        return START_NOT_STICKY
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

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
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
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        super.onDestroy()
    }
}
