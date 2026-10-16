package com.example.voiceassistant.service

/**
 * Integration boundary for a future, commercially cleared offline wake-word detector.
 *
 * No implementation is registered yet. Do not mark wake-word support as available
 * until a model with compatible rights is supplied and the detector is validated on-device.
 */
interface WakeWordDetector {
    val isAvailable: Boolean

    fun start(onWakeWordDetected: () -> Unit)

    fun stop()
}
