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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.voiceassistant.MainActivity
import com.example.voiceassistant.R
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User-started foreground microphone service. openWakeWord runs Hey Jarvis inference locally.
 * Public pretrained model assets are fetched at build time; no account or API key is required.
 */
class VoiceAssistantForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "voice_assistant_foreground_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_SERVICE = "com.example.voiceassistant.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.voiceassistant.action.STOP_SERVICE"
        const val ACTION_PAUSE_DETECTION = "com.example.voiceassistant.action.PAUSE_DETECTION"
        const val EXTRA_WAKE_WORD_DETECTED = "com.example.voiceassistant.extra.WAKE_WORD_DETECTED"
        private const val TAG = "HeyJarvisWakeWord"

        private val _isServiceRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isServiceRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isServiceRunning
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var detectionJob: Job? = null
    private var wakeWordEngine: WakeWordEngine? = null
    private var notificationText = "Listening locally for “Hey Jarvis”."
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
                notificationText = "Paused while the assistant handles your command."
                updateNotification()
            }
            else -> {
                notificationText = "Listening locally for “Hey Jarvis”."
                startForegroundCompat()
                _isServiceRunning.value = true
                if (wakeWordEngine == null) startWakeWordDetection()
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
        wakeWordDetected = false
        val requiredAssets = listOf(
            "hey_jarvis_v0.1.onnx",
            "melspectrogram.onnx",
            "embedding_model.onnx"
        )
        val availableAssets = assets.list("")?.toSet().orEmpty()
        val missingAsset = requiredAssets.firstOrNull { it !in availableAssets }
        if (missingAsset != null) {
            showSetupError("Wake-word model missing: $missingAsset. Rebuild with internet access.")
            return
        }

        try {
            val model = WakeWordModel(
                name = "Hey Jarvis",
                modelPath = "hey_jarvis_v0.1.onnx",
                threshold = 0.5f
            )
            val engine = WakeWordEngine(
                context = applicationContext,
                models = listOf(model)
            )
            wakeWordEngine = engine
            detectionJob = serviceScope.launch {
                engine.detections.collect { detection ->
                    if (detection.model.name == "Hey Jarvis" && detection.score >= 0.5f) {
                        withContext(Dispatchers.Main) { onWakeWordDetected() }
                    }
                }
            }
            engine.start()
            notificationText = "Listening locally for “Hey Jarvis”. Tap notification after detection."
            updateNotification()
            Log.i(TAG, "Offline Hey Jarvis wake-word detection started.")
        } catch (error: Exception) {
            Log.e(TAG, "Could not start openWakeWord detection.", error)
            stopWakeWordDetection()
            showSetupError("Wake-word engine failed. Check model assets and app logs.")
        }
    }

    private fun onWakeWordDetected() {
        if (wakeWordDetected) return
        wakeWordDetected = true
        stopWakeWordDetection()
        notificationText = "Hey Jarvis heard you. Tap this notification to speak a command."
        updateNotification()
        Log.i(TAG, "Wake word detected; waiting for the user to tap the notification before command capture.")
    }

    private fun stopWakeWordDetection() {
        detectionJob?.cancel()
        detectionJob = null
        val engine = wakeWordEngine
        wakeWordEngine = null
        if (engine != null) {
            try {
                engine.stop()
            } catch (error: Exception) {
                Log.w(TAG, "openWakeWord stop reported an error.", error)
            }
            try {
                engine.release()
            } catch (error: Exception) {
                Log.w(TAG, "openWakeWord cleanup reported an error.", error)
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
        stopWakeWordDetection()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopWakeWordDetection()
        _isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }
}
