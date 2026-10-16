package com.example.voiceassistant.service

import android.app.admin.DeviceAdminReceiver

/**
 * Enables the optional lock-screen command only after the device owner explicitly
 * approves this app as a device administrator in Android Settings.
 */
class AssistantDeviceAdminReceiver : DeviceAdminReceiver()
