package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialDeviceIdentityTest {
    @Test
    fun readsShuzlmIdFromTheOfficialBlock() {
        val copied = """
            设备ID: 9f8e7d6c5b4a3210
            ShuzlmID: Zx9_AbC-1234567890
            AndroidID: 0123456789abcdef
            UUID: 00000000-1111-2222-3333-444444444444
        """.trimIndent()
        assertEquals("Zx9_AbC-1234567890", OfficialDeviceIdentity.parse(copied))
    }

    @Test
    fun acceptsFullWidthColonCaseAndBom() {
        val copied = "\uFEFFshuzlmid：ABCdef0123456789\nOAID: 0000"
        assertEquals("ABCdef0123456789", OfficialDeviceIdentity.parse(copied))
    }

    @Test
    fun rejectsTextWithoutShuzlmId() {
        val failure = runCatching { OfficialDeviceIdentity.parse("OAID: 0000\nAndroidID: abcdefgh") }
        assertTrue(failure.exceptionOrNull() is IllegalArgumentException)
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("ShuzlmID"))
    }

    @Test
    fun rejectsDuplicateFields() {
        val copied = "ShuzlmID: ABCdef0123456789\nShuzlmID: ABCdef0123456789"
        val failure = runCatching { OfficialDeviceIdentity.parse(copied) }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("唯一的 ShuzlmID"))
    }

    @Test
    fun rejectsEmptyOrPlaceholderValues() {
        for (value in listOf("", "null", "undefined", "short")) {
            val failure = runCatching { OfficialDeviceIdentity.parse("ShuzlmID: $value") }
            assertTrue(
                "value=$value",
                failure.exceptionOrNull()?.message.orEmpty().contains("数盟 ID 为空或格式不正确"),
            )
        }
    }

    @Test
    fun rejectsOversizedPaste() {
        val failure = runCatching { OfficialDeviceIdentity.parse("x".repeat(32769)) }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("复制内容过长"))
    }
}
