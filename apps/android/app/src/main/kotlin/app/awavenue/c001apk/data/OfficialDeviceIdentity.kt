package app.awavenue.c001apk.data

/**
 * Reads the 数盟 ID out of the text the official Coolapk client copies from its
 * 检测日志与测试 page. The button is labelled 复制 OAID, but only after the fifth tap does the
 * clipboard carry the block that includes `ShuzlmID`; the OAID, AndroidID and UUID in the same
 * block are different identifiers and do not work as a device token.
 */
object OfficialDeviceIdentity {
    /** The copied block is a few hundred bytes; anything larger is not the official output. */
    private const val MAX_LENGTH = 32768

    private val field = Regex("""(?im)^\s*ShuzlmID\s*[:：]\s*([^\r\n]*)""")
    private val idShape = Regex("""^[A-Za-z0-9_+/.=-]{8,4096}$""")

    /**
     * Returns the `ShuzlmID` value, or throws [IllegalArgumentException] with a message meant for
     * the user when the text is not the official block or the field is empty.
     */
    fun parse(text: String): String {
        require(text.length <= MAX_LENGTH) { "复制内容过长，请重新从官方酷安复制" }
        val matches = field.findAll(text.removePrefix("\uFEFF")).toList()
        require(matches.size == 1) {
            "未识别到唯一的 ShuzlmID，请在官方同一页面累计点击「复制 OAID」5 次，再粘贴完整内容"
        }
        val id = matches.single().groupValues[1].trim()
        require(id.isNotEmpty() && id.lowercase() !in setOf("null", "undefined") && idShape.matches(id)) {
            "数盟 ID 为空或格式不正确，请打开官方酷安稍等后重新复制"
        }
        return id
    }
}
