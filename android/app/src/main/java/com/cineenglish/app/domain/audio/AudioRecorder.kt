package com.cineenglish.app.domain.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.log10

class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var isRecording = false
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var amplitudeJob: Job? = null
    private var silenceCounter = 0
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var onAmplitudeListener: ((Float) -> Unit)? = null
    var onSilenceDetectedListener: (() -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    fun startRecording(sentenceId: Long, enableSilenceStop: Boolean = true): File? {
        if (isRecording) {
            stopRecording()
        }

        // 1. Request Audio Focus
        if (!requestAudioFocus()) {
            onErrorListener?.invoke("Could not obtain audio focus for recording")
            return null
        }

        val recDir = File(context.filesDir, "recordings")
        if (!recDir.exists()) recDir.mkdirs()

        val fileName = "user_rec_s${sentenceId}_${System.currentTimeMillis()}.m4a"
        val outFile = File(recDir, fileName)
        currentOutputFile = outFile

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile.absolutePath)
                setMaxDuration(60000) // 60s max
                prepare()
                start()
            }
            isRecording = true
            silenceCounter = 0

            // Start monitoring amplitude & silence
            startAmplitudeMonitoring(enableSilenceStop)
            return outFile
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            abandonAudioFocus()
            onErrorListener?.invoke("Failed to initialize microphone: ${e.message}")
            return null
        }
    }

    private fun startAmplitudeMonitoring(enableSilenceStop: Boolean) {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isRecording) {
                try {
                    val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                    // Convert to normalized 0.0 - 1.0 range
                    val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                    withContext(Dispatchers.Main) {
                        onAmplitudeListener?.invoke(normalized)
                    }

                    // Check silence
                    if (enableSilenceStop) {
                        if (normalized < 0.05f) {
                            silenceCounter++
                            // ~1.5 seconds of silence after user spoke
                            if (silenceCounter > 15) {
                                withContext(Dispatchers.Main) {
                                    onSilenceDetectedListener?.invoke()
                                }
                            }
                        } else {
                            silenceCounter = 0
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient amplitude read failures
                }
                delay(100)
            }
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return currentOutputFile
        amplitudeJob?.cancel()
        isRecording = false
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            // Stop can fail if recording was too short
        } finally {
            mediaRecorder = null
            abandonAudioFocus()
        }
        return currentOutputFile
    }

    fun cancelRecording() {
        stopRecording()
        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(playbackAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopRecording()
                    }
                }
                .build()
            return audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            return audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        stopRecording()
                    }
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
