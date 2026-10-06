package com.cineenglish.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

object BuiltinMaterialsLoader {
    private const val TAG = "BuiltinMaterialsLoader"

    private val TIME_PATTERN = Pattern.compile("(\\d+):(\\d+):(\\d+)[,\\.](\\d+)\\s*-->\\s*(\\d+):(\\d+):(\\d+)[,\\.](\\d+)")
    private val TAG_PATTERN = Pattern.compile("<[^>]*>")
    private val BRACKET_PATTERN = Pattern.compile("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}")
    private val SPEAKER_PATTERN = Pattern.compile("^([A-Z\\s]{2,15}):\\s*(.+)$")

    data class ParsedSubItem(
        val startMs: Long,
        val endMs: Long,
        val text: String,
        val speaker: String? = null
    )

    /**
     * Parse an SRT formatted string into clean subtitle dialogue items.
     */
    fun parseSrt(content: String): List<ParsedSubItem> {
        val blocks = content.replace("\r\n", "\n").replace("\r", "\n").split("\n\n")
        val items = mutableListOf<ParsedSubItem>()

        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var arrowIdx = -1
            for (i in lines.indices) {
                if (lines[i].contains("-->")) {
                    arrowIdx = i
                    break
                }
            }
            if (arrowIdx == -1) continue

            val timeLine = lines[arrowIdx]
            val matcher = TIME_PATTERN.matcher(timeLine)
            if (!matcher.find()) continue

            val startH = matcher.group(1)?.toLongOrNull() ?: 0L
            val startM = matcher.group(2)?.toLongOrNull() ?: 0L
            val startS = matcher.group(3)?.toLongOrNull() ?: 0L
            val startMsPart = matcher.group(4)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
            val startMs = (startH * 3600 + startM * 60 + startS) * 1000 + startMsPart

            val endH = matcher.group(5)?.toLongOrNull() ?: 0L
            val endM = matcher.group(6)?.toLongOrNull() ?: 0L
            val endS = matcher.group(7)?.toLongOrNull() ?: 0L
            val endMsPart = matcher.group(8)?.padEnd(3, '0')?.take(3)?.toLongOrNull() ?: 0L
            val endMs = (endH * 3600 + endM * 60 + endS) * 1000 + endMsPart

            val dialogLines = lines.subList(arrowIdx + 1, lines.size)
            var rawText = dialogLines.joinToString(" ")

            // Clean text
            rawText = TAG_PATTERN.matcher(rawText).replaceAll("")
            rawText = BRACKET_PATTERN.matcher(rawText).replaceAll("")
            rawText = rawText.replace("\uFEFF", "").replace("\u200B", "")
            rawText = rawText.replace(Regex("\\s+"), " ").trim()

            var speaker: String? = null
            val spMatcher = SPEAKER_PATTERN.matcher(rawText)
            if (spMatcher.matches()) {
                speaker = spMatcher.group(1)?.lowercase()?.replaceFirstChar { it.uppercase() }?.trim()
                rawText = spMatcher.group(2)?.trim() ?: ""
            }

            if (rawText.startsWith("-")) {
                rawText = rawText.trimStart('-', ' ').trim()
            }

            if (rawText.length >= 2) {
                items.add(
                    ParsedSubItem(
                        startMs = startMs,
                        endMs = endMs,
                        text = rawText,
                        speaker = speaker
                    )
                )
            }
        }
        return items
    }

    private fun readAssetFile(context: Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).readText()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read asset file: $assetPath", e)
            null
        }
    }

    /**
     * Check if full Shawshank and Forrest Gump are loaded; if not, import them automatically.
     */
    suspend fun ensureFullClassicsImported(context: Context, db: AppDatabase, forceReload: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val existingShawshank = db.materialDao().findMaterialByTitleLike("%Shawshank%")
            val shawshankSentenceCount = existingShawshank?.let { db.sentenceDao().getSentenceCountByMaterial(it.id) } ?: 0
            val needShawshank = forceReload || existingShawshank == null || shawshankSentenceCount < 1600

            val existingGump = db.materialDao().findMaterialByTitleLike("%Forrest Gump%")
            val gumpSentenceCount = existingGump?.let { db.sentenceDao().getSentenceCountByMaterial(it.id) } ?: 0
            val needGump = forceReload || existingGump == null || gumpSentenceCount < 1500

            Log.i(TAG, "Checking classics: Shawshank ($shawshankSentenceCount), Gump ($gumpSentenceCount), needShawshank=$needShawshank, needGump=$needGump")

            if (needShawshank) {
                importShawshank(context, db)
            }

            if (needGump) {
                importForrestGump(context, db)
            }

            // Ensure Friends is also available if DB is empty
            val friends = db.materialDao().findMaterialByTitleLike("%Friends%")
            if (friends == null && !needShawshank && !needGump && db.materialDao().getMaterialCount() == 0) {
                importFriendsDemo(db)
            }
            Unit
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring classics imported", e)
        }
    }

    suspend fun importShawshank(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val content = readAssetFile(context, "subtitles/The_Shawshank_Redemption_1994.srt") ?: return@withContext
        val parsed = parseSrt(content)
        if (parsed.isEmpty()) return@withContext
        Log.i(TAG, "Parsed Shawshank: ${parsed.size} sentences")

        val duration = parsed.lastOrNull()?.endMs ?: 0L

        db.withTransaction {
            db.materialDao().deleteMaterialsByTitleLike("%Shawshank%")

            val materialId = db.materialDao().insertMaterial(
                MaterialEntity(
                    title = "The Shawshank Redemption (1994)",
                    year = 1994,
                    mediaType = "movie",
                    releaseVersion = "Full Official English Script",
                    subtitleSource = "official_classics",
                    sentenceCount = parsed.size,
                    durationMs = duration
                )
            )

            val sentenceEntities = parsed.mapIndexed { idx, item ->
                SentenceEntity(
                    materialId = materialId,
                    index = idx,
                    startMs = item.startMs,
                    endMs = item.endMs,
                    speaker = item.speaker,
                    text = item.text
                )
            }

            sentenceEntities.chunked(300).forEach { batch ->
                db.sentenceDao().insertSentences(batch)
            }
        }
        Log.i(TAG, "Successfully imported The Shawshank Redemption with ${parsed.size} sentences")
    }

    suspend fun importForrestGump(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val content = readAssetFile(context, "subtitles/Forrest_Gump_1994.srt") ?: return@withContext
        val parsed = parseSrt(content)
        if (parsed.isEmpty()) return@withContext
        Log.i(TAG, "Parsed Forrest Gump: ${parsed.size} sentences. Starting DB write...")

        val duration = parsed.lastOrNull()?.endMs ?: 0L

        try {
            db.withTransaction {
                db.materialDao().deleteMaterialsByTitleLike("%Forrest Gump%")

                val materialId = db.materialDao().insertMaterial(
                    MaterialEntity(
                        title = "Forrest Gump (1994)",
                        year = 1994,
                        mediaType = "movie",
                        releaseVersion = "Full Official English Script",
                        subtitleSource = "official_classics",
                        sentenceCount = parsed.size,
                        durationMs = duration
                    )
                )

                val sentenceEntities = parsed.mapIndexed { idx, item ->
                    SentenceEntity(
                        materialId = materialId,
                        index = idx,
                        startMs = item.startMs,
                        endMs = item.endMs,
                        speaker = item.speaker,
                        text = item.text
                    )
                }

                sentenceEntities.chunked(300).forEach { batch ->
                    db.sentenceDao().insertSentences(batch)
                }
            }
            Log.i(TAG, "Successfully imported Forrest Gump with ${parsed.size} sentences")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import Forrest Gump into DB", e)
        }
    }

    private suspend fun importFriendsDemo(db: AppDatabase) {
        val friendsMatId = db.materialDao().insertMaterial(
            MaterialEntity(
                title = "Friends S01E01 - Central Perk Encounter",
                subtitleSource = "builtin_demo",
                mediaType = "tv_show",
                season = 1,
                episode = 1,
                releaseVersion = "Classic Sitcom Audio",
                sentenceCount = 8,
                durationMs = 32000L
            )
        )
        val friendsSentences = listOf(
            SentenceEntity(materialId = friendsMatId, index = 0, startMs = 0, endMs = 3500, speaker = "Monica", text = "There's nothing to tell! He's just some guy I work with!"),
            SentenceEntity(materialId = friendsMatId, index = 1, startMs = 3800, endMs = 7200, speaker = "Joey", text = "C'mon, you're going out with the guy! There's gotta be something wrong with him!"),
            SentenceEntity(materialId = friendsMatId, index = 2, startMs = 7500, endMs = 11500, speaker = "Chandler", text = "All right Joey, be nice. So does he have a hump? A hump and a hairpiece?"),
            SentenceEntity(materialId = friendsMatId, index = 3, startMs = 12000, endMs = 14500, speaker = "Phoebe", text = "Wait, does he eat chalk?"),
            SentenceEntity(materialId = friendsMatId, index = 4, startMs = 15000, endMs = 19000, speaker = "Phoebe", text = "Just, 'cause, I don't want her to go through what I went through with Carl- oh!"),
            SentenceEntity(materialId = friendsMatId, index = 5, startMs = 19500, endMs = 24500, speaker = "Monica", text = "Okay, everybody relax. This is not even a date. It's just two people going out to dinner and not having sex."),
            SentenceEntity(materialId = friendsMatId, index = 6, startMs = 25000, endMs = 27500, speaker = "Joey", text = "Sounds like a date to me."),
            SentenceEntity(materialId = friendsMatId, index = 7, startMs = 28000, endMs = 32000, speaker = "Chandler", text = "Alright, so I'm back in high school, I'm standing in the middle of the cafeteria, and I realize I am totally naked.")
        )
        db.sentenceDao().insertSentences(friendsSentences)
    }
}
