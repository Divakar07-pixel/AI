package com.example.voiceassistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered by the persistent notification "Stop" action button.
 */
class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val stopIntent = Intent(context, VoiceAssistantForegroundService::class.java).apply {
            action = VoiceAssistantForegroundService.ACTION_STOP_SERVICE
        }
        context.stopService(stopIntent)
    }
}
