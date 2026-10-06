package com.cineenglish.app

import com.cineenglish.app.data.local.BuiltinMaterialsLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SrtParserTest {
    @Test
    fun testParseShawshank() {
        val file = File("src/main/assets/subtitles/The_Shawshank_Redemption_1994.srt")
        assertTrue("Shawshank file exists", file.exists())
        val items = BuiltinMaterialsLoader.parseSrt(file.readText())
        println("Shawshank parsed items: ${items.size}")
        assertTrue(items.size >= 1600)
    }

    @Test
    fun testParseForrestGump() {
        val file = File("src/main/assets/subtitles/Forrest_Gump_1994.srt")
        assertTrue("Forrest Gump file exists", file.exists())
        val items = BuiltinMaterialsLoader.parseSrt(file.readText())
        println("Forrest Gump parsed items: ${items.size}")
        assertTrue(items.size >= 1500)
    }
}
