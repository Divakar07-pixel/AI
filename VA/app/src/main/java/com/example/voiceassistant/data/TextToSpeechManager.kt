package com.example.voiceassistant.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class TextToSpeechManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit = {}
) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English US
                    tts?.setLanguage(Locale.US)
                }
                isInitialized = true
                onInitComplete(true)
            } else {
                isInitialized = false
                onInitComplete(false)
            }
        }
    }

    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: () -> Unit = {}
    ) {
        if (!isInitialized || tts == null) {
            onError()
            return
        }

        val utteranceId = "utterance_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceIdStr: String?) {
                onStart()
            }

            override fun onDone(utteranceIdStr: String?) {
                onDone()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceIdStr: String?) {
                onError()
            }

            override fun onError(utteranceIdStr: String?, errorCode: Int) {
                onError()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        if (isInitialized) {
            tts?.stop()
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
