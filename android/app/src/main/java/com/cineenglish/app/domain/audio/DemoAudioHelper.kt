package com.cineenglish.app.domain.audio

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DemoAudioHelper {

    private const val TAG = "DemoAudioHelper"

    // Normalized text (lower case, only letters and spaces) to asset filename
    private val DEMO_AUDIO_MAP = mapOf(
        // Forrest Gump
        "hello my name's forrest forrest gump do you want a chocolate" to "gump_0.mp3",
        "hello my names forrest forrest gump do you want a chocolate" to "gump_0.mp3",
        "mama always said life was like a box of chocolates you never know what you're gonna get" to "gump_1.mp3",
        "mama always said life was like a box of chocolates you never know what youre gonna get" to "gump_1.mp3",
        "my mama always said you've got to put the past behind you before you can move on" to "gump_2.mp3",
        "my mama always said youve got to put the past behind you before you can move on" to "gump_2.mp3",
        "i'm not a smart man but i know what love is" to "gump_3.mp3",
        "im not a smart man but i know what love is" to "gump_3.mp3",
        "you have to do the best with what god gave you" to "gump_4.mp3",

        // Friends S01E01
        "there's nothing to tell he's just some guy i work with" to "friends_0.mp3",
        "theres nothing to tell hes just some guy i work with" to "friends_0.mp3",
        "c'mon you're going out with the guy there's gotta be something wrong with him" to "friends_1.mp3",
        "cmon youre going out with the guy theres gotta be something wrong with him" to "friends_1.mp3",
        "all right joey be nice so does he have a hump a hump and a hairpiece" to "friends_2.mp3",
        "all right, joey, be nice. so does he have a hump? a hump and a hairpiece?" to "friends_2.mp3",
        "wait does he eat chalk" to "friends_3.mp3",
        "just 'cause i don't want her to go through what i went through with carl- oh" to "friends_4.mp3",
        "just cause i dont want her to go through what i went through with carl oh" to "friends_4.mp3",
        "okay everybody relax this is not even a date it's just two people going out to dinner and not having sex" to "friends_5.mp3",
        "okay everybody relax this is not even a date its just two people going out to dinner and not having sex" to "friends_5.mp3",
        "sounds like a date to me" to "friends_6.mp3",
        "alright so i'm back in high school i'm standing in the middle of the cafeteria and i realize i am totally naked" to "friends_7.mp3",
        "alright so im back in high school im standing in the middle of the cafeteria and i realize i am totally naked" to "friends_7.mp3"
    )

    private fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun hasDemoAudio(text: String): Boolean {
        val norm = normalize(text)
        return DEMO_AUDIO_MAP.containsKey(norm) || DEMO_AUDIO_MAP.keys.any { norm.contains(it) || it.contains(norm) }
    }

    fun getOrExtractDemoAudio(context: Context, text: String): File? {
        val norm = normalize(text)
        var assetName = DEMO_AUDIO_MAP[norm]

        if (assetName == null) {
            // Partial match fallback
            val match = DEMO_AUDIO_MAP.entries.firstOrNull { (k, _) ->
                norm.length > 10 && (norm.startsWith(k.take(20)) || k.startsWith(norm.take(20)))
            }
            assetName = match?.value
        }

        if (assetName == null) return null

        val cacheDir = File(context.cacheDir, "demo_audio")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val destFile = File(cacheDir, assetName)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile
        }

        return try {
            context.assets.open("demo_audio/$assetName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Successfully extracted demo audio to ${destFile.absolutePath} (${destFile.length()} bytes)")
            destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract asset demo_audio/$assetName: ${e.message}", e)
            null
        }
    }

    fun preloadAllDemoAudio(context: Context) {
        Thread {
            try {
                val list = context.assets.list("demo_audio") ?: return@Thread
                val cacheDir = File(context.cacheDir, "demo_audio")
                if (!cacheDir.exists()) cacheDir.mkdirs()

                for (name in list) {
                    val dest = File(cacheDir, name)
                    if (!dest.exists() || dest.length() == 0L) {
                        context.assets.open("demo_audio/$name").use { input ->
                            FileOutputStream(dest).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Preloaded demo audio: $name (${dest.length()} bytes)")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading demo audio: ${e.message}", e)
            }
        }.start()
    }
}
