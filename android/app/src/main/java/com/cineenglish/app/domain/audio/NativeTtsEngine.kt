package com.cineenglish.app.domain.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

object NativeTtsEngine {
    private const val TAG = "NativeTtsEngine"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val pendingTasks = mutableListOf<() -> Unit>()

    fun initialize(context: Context) {
        if (tts != null) return
        val appContext = context.applicationContext
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Locale.US is missing or not supported on this TTS engine, fallback to English")
                    tts?.language = Locale.ENGLISH
                }
                isInitialized = true
                Log.i(TAG, "NativeTtsEngine initialized successfully with American English")
                synchronized(pendingTasks) {
                    pendingTasks.forEach { it.invoke() }
                    pendingTasks.clear()
                }
            } else {
                Log.e(TAG, "Failed to initialize TextToSpeech: status=$status")
            }
        }
    }

    fun speak(
        text: String,
        speed: Float = 1.0f,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val action: () -> Unit = {
            try {
                tts?.let { engine ->
                    engine.stop()
                    engine.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
                    engine.setPitch(1.0f)

                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    val utteranceId = UUID.randomUUID().toString()
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) {
                            if (id == utteranceId) mainHandler.post { onStart?.invoke() }
                        }
                        override fun onDone(id: String?) {
                            if (id == utteranceId) mainHandler.post { onDone?.invoke() }
                        }
                        override fun onError(id: String?) {
                            if (id == utteranceId) mainHandler.post { onError?.invoke("Native TTS error") }
                        }
                    })

                    val params = Bundle()
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
                    val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
                    if (result != TextToSpeech.SUCCESS) {
                        mainHandler.post { onError?.invoke("Speak returned status $result") }
                    }
                } ?: run {
                    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    mainHandler.post { onError?.invoke("TTS not available") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "speak exception: ${e.message}", e)
                onError?.invoke(e.message ?: "Unknown error")
            }
            Unit
        }

        if (isInitialized) {
            action()
        } else {
            synchronized(pendingTasks) {
                pendingTasks.add(action)
            }
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stop exception: ${e.message}")
        }
    }

    fun isReady(): Boolean = isInitialized && tts != null
}
