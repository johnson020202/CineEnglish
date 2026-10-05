package com.cineenglish.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.worker.RecordingUploadWorker
import java.util.concurrent.TimeUnit

class CineApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize local Room DB
        AppDatabase.getInstance(this)

        // Pre-warm Native TTS Engine for zero-latency American English speech
        com.cineenglish.app.domain.audio.NativeTtsEngine.initialize(this)

        // Preload and cache all pristine studio demo audio files from assets
        com.cineenglish.app.domain.audio.DemoAudioHelper.preloadAllDemoAudio(this)

        // Schedule periodic background recording sync every 15 minutes when connected
        val syncRequest = PeriodicWorkRequestBuilder<RecordingUploadWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "periodic_recording_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
