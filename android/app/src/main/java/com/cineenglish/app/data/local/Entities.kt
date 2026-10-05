package com.cineenglish.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val mediaType: String = "movie",
    val releaseVersion: String? = null,
    val subtitleSource: String = "local",
    val localSubtitlePath: String? = null,
    val localVideoUri: String? = null,
    val localAudioUri: String? = null,
    val durationMs: Long = 0,
    val sentenceCount: Int = 0,
    val lastPracticedSentenceIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sentences",
    foreignKeys = [
        ForeignKey(
            entity = MaterialEntity::class,
            parentColumns = ["id"],
            childColumns = ["materialId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("materialId"), Index("index")]
)
data class SentenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val rawText: String? = null,
    val speaker: String? = null,
    val localAudioClipUri: String? = null,
    val cachedTtsAudioPath: String? = null
)

@Entity(
    tableName = "practice_records",
    foreignKeys = [
        ForeignKey(
            entity = SentenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sentenceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sentenceId"), Index("materialId"), Index("syncStatus")]
)
data class PracticeRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val sentenceId: Long,
    val practiceMode: String = "sentence_shadowing", // sentence_shadowing, recall_recite, recall_free, role_play
    val localAudioPath: String,
    val remoteAudioUrl: String? = null,
    val durationMs: Long = 0,
    val engineName: String = "CineAcousticEngine",
    val engineTier: String = "phoneme_acoustic", // phoneme_acoustic, text_matching, ai_reference_only
    val overallScore: Float = 0f,
    val accuracyScore: Float = 0f,
    val completenessScore: Float = 0f,
    val fluencyScore: Float = 0f,
    val prosodyScore: Float = 0f,
    val wordDetailsJson: String? = null,
    val feedbackEn: String? = null,
    val isPassed: Boolean = false,
    val attemptCount: Int = 1,
    val isFlaggedUnreasonable: Boolean = false,
    val syncStatus: Int = 0, // 0: Pending upload, 1: Synced, 2: Failed
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vocabularies")
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val phonetic: String? = null,
    val definitionEn: String,
    val simpleExplanation: String? = null,
    val contextSentence: String? = null,
    val materialId: Long? = null,
    val sentenceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "review_items")
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemType: String = "word", // word, phrase, sentence
    val content: String,
    val contextSentence: String? = null,
    val materialId: Long? = null,
    val sentenceId: Long? = null,
    val errorCount: Int = 1,
    val confidenceLevel: Float = 0.5f,
    val srsStage: Int = 0,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val isMastered: Boolean = false,
    val isIgnored: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
