package com.cineenglish.app.worker

import android.content.Context
import androidx.work.*
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.data.local.AppSettings
import com.cineenglish.app.data.remote.ApiClient
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class RecordingUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.getInstance(applicationContext)
        val settings = AppSettings(applicationContext)
        val pendingRecords = db.practiceRecordDao().getPendingSyncRecords()

        if (pendingRecords.isEmpty()) {
            return Result.success()
        }

        val baseUrl = settings.backendUrlFlow.first()
        val apiService = ApiClient.getService(baseUrl)

        var hasFailure = false

        for (record in pendingRecords) {
            val audioFile = File(record.localAudioPath)
            if (!audioFile.exists() || audioFile.length() == 0L) {
                // If local file is missing, mark as failed
                db.practiceRecordDao().updateRecord(record.copy(syncStatus = 2))
                continue
            }

            try {
                val filePart = MultipartBody.Part.createFormData(
                    "audio_file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
                )

                val response = apiService.uploadRecording(
                    materialId = record.materialId.toString().toRequestBody(),
                    sentenceId = record.sentenceId.toString().toRequestBody(),
                    practiceMode = record.practiceMode.toRequestBody(),
                    engineName = record.engineName.toRequestBody(),
                    overallScore = record.overallScore.toString().toRequestBody(),
                    accuracyScore = record.accuracyScore.toString().toRequestBody(),
                    completenessScore = record.completenessScore.toString().toRequestBody(),
                    fluencyScore = record.fluencyScore.toString().toRequestBody(),
                    prosodyScore = record.prosodyScore.toString().toRequestBody(),
                    isPassed = record.isPassed.toString().toRequestBody(),
                    attemptCount = record.attemptCount.toString().toRequestBody(),
                    wordDetailsJson = record.wordDetailsJson?.toRequestBody(),
                    feedbackEn = record.feedbackEn?.toRequestBody(),
                    audioFile = filePart
                )

                if (response.isSuccessful) {
                    db.practiceRecordDao().updateRecord(record.copy(syncStatus = 1))
                } else {
                    hasFailure = true
                }
            } catch (e: Exception) {
                hasFailure = true
            }
        }

        return if (hasFailure) Result.retry() else Result.success()
    }

    companion object {
        fun enqueueSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val uploadWork = OneTimeWorkRequestBuilder<RecordingUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_practice_recordings",
                ExistingWorkPolicy.REPLACE,
                uploadWork
            )
        }
    }
}
