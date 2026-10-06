package com.cineenglish.app.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface CineApiService {

    @GET("/health")
    suspend fun checkHealth(): Map<String, String>

    @GET("/api/v1/materials")
    suspend fun getMaterials(): List<MaterialNetDto>

    @GET("/api/v1/materials/{id}")
    suspend fun getMaterialDetail(@Path("id") id: Long): MaterialNetDto

    @GET("/api/v1/materials/{id}/sentences")
    suspend fun getSentences(@Path("id") id: Long): List<SentenceNetDto>

    @FormUrlEncoded
    @POST("/api/v1/materials/{id}/sentences/shift-time")
    suspend fun shiftTimeline(
        @Path("id") id: Long,
        @Field("offset_ms") offsetMs: Long
    ): Response<ResponseBody>

    @Multipart
    @POST("/api/v1/materials/import-file")
    suspend fun importLocalSubtitle(
        @Part("title") title: RequestBody,
        @Part("year") year: RequestBody?,
        @Part("release_version") release: RequestBody?,
        @Part file: MultipartBody.Part
    ): MaterialNetDto

    @GET("/api/v1/subtitles/search")
    suspend fun searchSubtitles(
        @Query("query") query: String,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
        @Query("year") year: Int? = null,
        @Query("api_key") apiKey: String? = null
    ): SubtitleSearchResponseDto

    @POST("/api/v1/subtitles/download-and-import")
    suspend fun downloadSubtitle(
        @Body body: Map<String, Any>,
        @Query("api_key") apiKey: String? = null
    ): MaterialNetDto

    @Multipart
    @POST("/api/v1/assessment/evaluate-sentence")
    suspend fun evaluateSentence(
        @Part("sentence_id") sentenceId: RequestBody,
        @Part("reference_text") referenceText: RequestBody? = null,
        @Part("attempt_count") attemptCount: RequestBody,
        @Part("pass_threshold_overall") thresholdOverall: RequestBody,
        @Part("pass_threshold_completeness") thresholdCompleteness: RequestBody,
        @Part("engine_mode") engineMode: RequestBody,
        @Part audioFile: MultipartBody.Part
    ): PronunciationResultDto

    @POST("/api/v1/assessment/evaluate-free-recall")
    suspend fun evaluateFreeRecall(
        @Body body: Map<String, Any>
    ): FreeRecallResultDto

    @FormUrlEncoded
    @POST("/api/v1/assessment/report-unreasonable-score")
    suspend fun reportUnreasonableScore(
        @Field("record_id") recordId: Long,
        @Field("reason") reason: String
    ): Response<ResponseBody>

    @Multipart
    @POST("/api/v1/recordings/upload")
    suspend fun uploadRecording(
        @Part("material_id") materialId: RequestBody,
        @Part("sentence_id") sentenceId: RequestBody,
        @Part("practice_mode") practiceMode: RequestBody,
        @Part("engine_name") engineName: RequestBody,
        @Part("overall_score") overallScore: RequestBody,
        @Part("accuracy_score") accuracyScore: RequestBody,
        @Part("completeness_score") completenessScore: RequestBody,
        @Part("fluency_score") fluencyScore: RequestBody,
        @Part("prosody_score") prosodyScore: RequestBody,
        @Part("is_passed") isPassed: RequestBody,
        @Part("attempt_count") attemptCount: RequestBody,
        @Part("word_details_json") wordDetailsJson: RequestBody?,
        @Part("feedback_en") feedbackEn: RequestBody?,
        @Part audioFile: MultipartBody.Part
    ): Response<ResponseBody>

    @GET("/api/v1/dictionary/lookup")
    suspend fun lookupDictionary(
        @Query("word") word: String,
        @Query("context") context: String? = null,
        @Query("simplify_level") level: Int = 1
    ): DictDefinitionDto

    @GET("/api/v1/ai/synthesize-american-voice")
    @Streaming
    suspend fun synthesizeVoice(
        @Query("text") text: String,
        @Query("voice") voice: String = "alloy",
        @Query("speed") speed: Float = 1.0f
    ): Response<ResponseBody>

    @POST("/api/v1/ai/test-capability")
    suspend fun testAiCapability(
        @Body body: Map<String, Any>
    ): AiCapabilityTestDto

    @GET("/api/v1/ai/models")
    suspend fun getAiModels(
        @Query("base_url") baseUrl: String,
        @Query("api_key") apiKey: String? = null
    ): AiModelsResponseDto

    @POST("/api/v1/ai/roleplay-extend")
    suspend fun extendRolePlay(
        @Body body: Map<String, Any>
    ): RolePlayExtendDto
}
