package app.awavenue.c001apk.ui.text

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the emoji table against the bundled files: every code the app can render must have its PNG
 * under `assets/emojis`, and the panel tabs must cover the table exactly once.
 */
class EmojiFilesTest {
    private val emojiDir = File("src/main/assets/emojis")

    @Test
    fun everyCodeHasABundledPng() {
        val missing = EmojiFiles.filterValues { !File(emojiDir, it).isFile }.keys
        assertEquals(emptySet<String>(), missing)
        val notPng = EmojiFiles.filterValues { file ->
            val bytes = File(emojiDir, file).inputStream().use { it.readNBytes(4) }
            bytes.size != 4 ||
                bytes[0] != 0x89.toByte() ||
                bytes[1] != 'P'.code.toByte() ||
                bytes[2] != 'N'.code.toByte() ||
                bytes[3] != 'G'.code.toByte()
        }.keys
        assertEquals(emptySet<String>(), notPng)
    }

    @Test
    fun niuniuFamilyIsComplete() {
        val niuniu = EmojiSections.first { it.label == "牛牛" }
        assertEquals(32, niuniu.emojis.size)
        assertTrue(niuniu.emojis.all { it.first.startsWith("[牛牛") })
        assertTrue(niuniu.emojis.all { it.second.startsWith("niuniu_emotion_") })
        assertTrue(EmojiFiles.containsKey("[牛牛戴耳机]"))
    }

    @Test
    fun sectionsCoverTheTableOnce() {
        val sections = EmojiSections.flatMap { section -> section.emojis.map { it.first } }
        assertEquals(EmojiFiles.keys.toList().sorted(), sections.sorted())
        assertEquals(sections.size, sections.distinct().size)
        assertEquals(listOf("常用", "牛牛", "酷币"), EmojiSections.map { it.label })
    }
}
