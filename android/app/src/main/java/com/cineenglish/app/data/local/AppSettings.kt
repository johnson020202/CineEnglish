package com.cineenglish.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "cine_settings")

class AppSettings(private val context: Context) {

    companion object {
        val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_API_KEY_ENC = stringPreferencesKey("ai_api_key_enc")
        val KEY_CHAT_MODEL = stringPreferencesKey("chat_model")
        val KEY_TTS_MODEL = stringPreferencesKey("tts_model")
        val KEY_STT_MODEL = stringPreferencesKey("stt_model")
        val KEY_OPENSUBTITLES_API_KEY = stringPreferencesKey("opensubtitles_api_key")

        val KEY_PASS_THRESHOLD_OVERALL = floatPreferencesKey("pass_threshold_overall")
        val KEY_PASS_THRESHOLD_COMPLETENESS = floatPreferencesKey("pass_threshold_completeness")
        val KEY_AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
        val KEY_MAX_ATTEMPTS = intPreferencesKey("max_attempts")
        val KEY_SILENCE_STOP = booleanPreferencesKey("silence_stop")
        val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        val KEY_PLAYBACK_SPEED = floatPreferencesKey("playback_speed")

        fun isEmulator(): Boolean {
            return (android.os.Build.FINGERPRINT.startsWith("generic")
                    || android.os.Build.FINGERPRINT.startsWith("unknown")
                    || android.os.Build.MODEL.contains("google_sdk")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK built for x86")
                    || android.os.Build.MANUFACTURER.contains("Genymotion")
                    || android.os.Build.HARDWARE.contains("goldfish")
                    || android.os.Build.PRODUCT.contains("sdk")
                    || android.os.Build.PRODUCT.contains("google_sdk"))
        }

        fun getDefaultBackendUrl(): String {
            return if (isEmulator()) "http://10.0.2.2:8000" else "http://127.0.0.1:8000"
        }
    }

    val backendUrlFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_BACKEND_URL] ?: getDefaultBackendUrl()
    }

    val aiBaseUrlFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_AI_BASE_URL] ?: "https://api.openai.com/v1"
    }

    val chatModelFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_CHAT_MODEL] ?: "gpt-4o-mini"
    }

    val ttsVoiceFlow: Flow<String> = context.dataStore.data.map {
        it[KEY_TTS_VOICE] ?: "alloy"
    }

    val autoAdvanceFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_AUTO_ADVANCE] ?: false
    }

    val passThresholdOverallFlow: Flow<Float> = context.dataStore.data.map {
        it[KEY_PASS_THRESHOLD_OVERALL] ?: 80.0f
    }

    val passThresholdCompletenessFlow: Flow<Float> = context.dataStore.data.map {
        it[KEY_PASS_THRESHOLD_COMPLETENESS] ?: 90.0f
    }

    val silenceStopFlow: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SILENCE_STOP] ?: true
    }

    val playbackSpeedFlow: Flow<Float> = context.dataStore.data.map {
        it[KEY_PLAYBACK_SPEED] ?: 1.0f
    }

    suspend fun saveBackendUrl(url: String) {
        context.dataStore.edit { it[KEY_BACKEND_URL] = url.trimEnd('/') }
    }

    suspend fun saveAiBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_AI_BASE_URL] = url.trimEnd('/') }
    }

    suspend fun saveChatModel(model: String) {
        context.dataStore.edit { it[KEY_CHAT_MODEL] = model.trim() }
    }

    suspend fun saveAiApiKey(key: String) {
        val encrypted = KeystoreManager.encrypt(key)
        context.dataStore.edit { it[KEY_AI_API_KEY_ENC] = encrypted }
    }

    fun getAiApiKeyFlow(): Flow<String> = context.dataStore.data.map {
        val enc = it[KEY_AI_API_KEY_ENC] ?: ""
        KeystoreManager.decrypt(enc)
    }

    suspend fun saveThresholds(overall: Float, completeness: Float, autoAdvance: Boolean) {
        context.dataStore.edit {
            it[KEY_PASS_THRESHOLD_OVERALL] = overall
            it[KEY_PASS_THRESHOLD_COMPLETENESS] = completeness
            it[KEY_AUTO_ADVANCE] = autoAdvance
        }
    }

    suspend fun saveTtsVoice(voice: String) {
        context.dataStore.edit { it[KEY_TTS_VOICE] = voice }
    }

    suspend fun savePlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[KEY_PLAYBACK_SPEED] = speed }
    }
}
