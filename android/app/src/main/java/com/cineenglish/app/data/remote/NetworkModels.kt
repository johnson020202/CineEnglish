package com.cineenglish.app.data.remote

import com.google.gson.annotations.SerializedName

data class MaterialNetDto(
    val id: Long,
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("release_version") val releaseVersion: String?,
    @SerializedName("subtitle_source") val subtitleSource: String,
    @SerializedName("video_file") val videoFile: String?,
    @SerializedName("audio_file") val audioFile: String?,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("sentence_count") val sentenceCount: Int
)

data class SentenceNetDto(
    val id: Long,
    @SerializedName("material_id") val materialId: Long,
    val index: Int,
    @SerializedName("start_ms") val startMs: Long,
    @SerializedName("end_ms") val endMs: Long,
    val text: String,
    @SerializedName("raw_text") val rawText: String?,
    val speaker: String?,
    @SerializedName("audio_clip_path") val audioClipPath: String?,
    @SerializedName("tts_audio_path") val ttsAudioPath: String?
)

data class WordAssessmentDto(
    val word: String,
    val score: Float,
    @SerializedName("is_problematic") val isProblematic: Boolean,
    @SerializedName("problem_type") val problemType: String?,
    @SerializedName("start_ms") val startMs: Long?,
    @SerializedName("end_ms") val endMs: Long?
)

data class PronunciationResultDto(
    @SerializedName("engine_name") val engineName: String,
    @SerializedName("engine_version") val engineVersion: String,
    @SerializedName("assessment_tier") val assessmentTier: String,
    @SerializedName("overall_score") val overallScore: Float,
    @SerializedName("accuracy_score") val accuracyScore: Float,
    @SerializedName("completeness_score") val completenessScore: Float,
    @SerializedName("fluency_score") val fluencyScore: Float,
    @SerializedName("prosody_score") val prosodyScore: Float,
    @SerializedName("is_passed") val isPassed: Boolean,
    @SerializedName("word_details") val wordDetails: List<WordAssessmentDto>,
    @SerializedName("feedback_en") val feedbackEn: String,
    @SerializedName("confidence_evidence") val confidenceEvidence: String,
    @SerializedName("can_auto_advance") val canAutoAdvance: Boolean
)

data class FreeRecallResultDto(
    @SerializedName("meaning_preserved_score") val meaningScore: Float,
    @SerializedName("grammar_score") val grammarScore: Float,
    @SerializedName("naturalness_score") val naturalnessScore: Float,
    @SerializedName("overall_score") val overallScore: Float,
    @SerializedName("is_acceptable_paraphrase") val isAcceptable: Boolean,
    @SerializedName("suggestions_en") val suggestionsEn: String,
    @SerializedName("key_words_used") val keyWordsUsed: List<String>,
    @SerializedName("missing_points") val missingPoints: List<String>
)

data class DictDefinitionDto(
    val word: String,
    val phonetic: String?,
    @SerializedName("definition_en") val definitionEn: String,
    @SerializedName("simple_explanation") val simpleExplanation: String?,
    @SerializedName("context_sentence") val contextSentence: String?,
    @SerializedName("example_sentences_en") val exampleSentencesEn: List<String>?,
    @SerializedName("source_attribution") val sourceAttribution: String
)

data class SubtitleSearchResponseDto(
    val success: Boolean,
    val data: List<SubtitleItemDto>?,
    val error: String?,
    val message: String?
)

data class SubtitleItemDto(
    @SerializedName("subtitle_id") val subtitleId: String,
    @SerializedName("movie_name") val movieName: String,
    val year: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    val release: String?,
    val language: String,
    val format: String,
    @SerializedName("hearing_impaired") val hearingImpaired: Boolean,
    @SerializedName("download_count") val downloadCount: Int?,
    val ratings: Float?
)

data class AiCapabilityTestDto(
    val capability: String,
    @SerializedName("is_supported") val isSupported: Boolean,
    @SerializedName("is_success") val isSuccess: Boolean,
    @SerializedName("latency_ms") val latencyMs: Int,
    @SerializedName("model_used") val modelUsed: String,
    @SerializedName("sample_output") val sampleOutput: String?,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("available_models") val availableModels: List<String>? = null
)

data class AiModelsResponseDto(
    val success: Boolean,
    val models: List<String> = emptyList(),
    val count: Int = 0,
    val error: String? = null
)

data class RolePlayExtendDto(
    val speaker: String,
    val text: String,
    @SerializedName("is_ai_generated") val isAiGenerated: Boolean,
    val difficulty: String
)
