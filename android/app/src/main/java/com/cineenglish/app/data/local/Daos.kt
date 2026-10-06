package com.cineenglish.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY createdAt DESC")
    fun getAllMaterialsFlow(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE id = :id")
    suspend fun getMaterialById(id: Long): MaterialEntity?

    @Query("SELECT COUNT(*) FROM materials")
    suspend fun getMaterialCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Query("SELECT * FROM materials WHERE title LIKE :query LIMIT 1")
    suspend fun findMaterialByTitleLike(query: String): MaterialEntity?

    @Query("DELETE FROM materials WHERE title LIKE :query")
    suspend fun deleteMaterialsByTitleLike(query: String)

    @Delete
    suspend fun deleteMaterial(material: MaterialEntity)
}

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE materialId = :materialId ORDER BY `index` ASC")
    fun getSentencesByMaterialFlow(materialId: Long): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE materialId = :materialId ORDER BY `index` ASC")
    suspend fun getSentencesByMaterial(materialId: Long): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE id = :id")
    suspend fun getSentenceById(id: Long): SentenceEntity?

    @Query("SELECT COUNT(*) FROM sentences WHERE materialId = :materialId")
    suspend fun getSentenceCountByMaterial(materialId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)

    @Update
    suspend fun updateSentence(sentence: SentenceEntity)

    @Delete
    suspend fun deleteSentence(sentence: SentenceEntity)
}

@Dao
interface PracticeRecordDao {
    @Query("SELECT * FROM practice_records WHERE sentenceId = :sentenceId ORDER BY createdAt DESC")
    fun getRecordsBySentenceFlow(sentenceId: Long): Flow<List<PracticeRecordEntity>>

    @Query("SELECT * FROM practice_records WHERE materialId = :materialId ORDER BY createdAt DESC")
    fun getRecordsByMaterialFlow(materialId: Long): Flow<List<PracticeRecordEntity>>

    @Query("SELECT * FROM practice_records ORDER BY createdAt DESC")
    fun getAllRecordsFlow(): Flow<List<PracticeRecordEntity>>

    @Query("SELECT * FROM practice_records WHERE syncStatus = 0")
    suspend fun getPendingSyncRecords(): List<PracticeRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PracticeRecordEntity): Long

    @Update
    suspend fun updateRecord(record: PracticeRecordEntity)

    @Delete
    suspend fun deleteRecord(record: PracticeRecordEntity)

    @Query("SELECT COUNT(*) FROM practice_records WHERE isPassed = 1")
    suspend fun getPassedCount(): Int

    @Query("SELECT COUNT(*) FROM practice_records")
    suspend fun getTotalAttempts(): Int

    @Query("SELECT AVG(overallScore) FROM practice_records")
    suspend fun getAverageScore(): Float?
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabularies ORDER BY createdAt DESC")
    fun getAllVocabulariesFlow(): Flow<List<VocabularyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: VocabularyEntity): Long

    @Delete
    suspend fun deleteVocabulary(vocab: VocabularyEntity)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_items WHERE isMastered = 0 AND isIgnored = 0 AND nextReviewAt <= :currentTime ORDER BY nextReviewAt ASC LIMIT :limit")
    fun getDueReviewItemsFlow(currentTime: Long, limit: Int = 20): Flow<List<ReviewItemEntity>>

    @Query("SELECT * FROM review_items WHERE isMastered = 0 AND isIgnored = 0")
    fun getAllActiveWeaknessesFlow(): Flow<List<ReviewItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewItem(item: ReviewItemEntity): Long

    @Update
    suspend fun updateReviewItem(item: ReviewItemEntity)
}
