package app.awavenue.c001apk.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The NEC v2 challenge the API raises instead of answering. Only the captcha id and the field
 * name are protocol configuration; a proof is short lived and never stored or printed.
 */
class CaptchaChallenge(val captchaId: String, val field: String) {
    /** A blank field means the proof travels as the `validate` cookie, not as a parameter. */
    val usesCookie: Boolean get() = this.field.isEmpty()

    fun proof(validate: String?): String? {
        if (validate.isNullOrEmpty() || validate.length > 8192) return null
        if (validate.any { it.isWhitespace() || it.code < 0x20 }) return null
        return "NEC:${captchaId.take(8)}:$validate"
    }

    fun acceptsProof(proof: String): Boolean =
        proof.startsWith("NEC:${captchaId.take(8)}:") &&
            proof.length > 13 &&
            proof.length <= 8205 &&
            proof.none { it.isWhitespace() || it.code < 0x20 }

    companion object {
        const val DEFAULT_ID = "414e5c9b866a03db03f860a1a9101672"
        private const val DEFAULT_FIELD = "_v2_post_token"
        private val ID_PATTERN = Regex("^[a-zA-Z0-9]{16,64}$")
        private val FIELD_PATTERN = Regex("^_[a-zA-Z0-9_]{1,63}$")
        private val json = Json { ignoreUnknownKeys = true }

        fun fromBody(body: String): CaptchaChallenge? {
            val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
            if (root.text("messageStatus") != "err_request_captcha_v2") return null
            val rawExtra = root["messageExtra"] ?: root["extra"]
            val extra = when {
                rawExtra is JsonPrimitive && rawExtra.isString ->
                    runCatching { json.parseToJsonElement(rawExtra.content) as? JsonObject }.getOrNull()
                rawExtra is JsonObject -> rawExtra
                else -> null
            } ?: root
            val id = extra.text("captchaId").takeIf { ID_PATTERN.matches(it) } ?: DEFAULT_ID
            // Only an explicitly blank field selects the cookie protocol; an absent or invalid one
            // keeps the legacy form field.
            val field = (extra["captchaField"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.let { if (it.isBlank()) "" else it.takeIf(FIELD_PATTERN::matches) }
                ?: DEFAULT_FIELD
            return CaptchaChallenge(id, field)
        }

        private fun JsonObject.text(key: String): String =
            (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull.orEmpty()
    }
}

/**
 * Dart's `Uri.encodeQueryComponent`, so the `validate` cookie is byte-identical to the Flutter
 * client's: RFC 3986 unreserved characters stay, a space becomes `+`, the rest is percent-encoded.
 */
internal fun encodeQueryComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xff
        when {
            c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~" ->
                append(c.toChar())
            c == ' '.code -> append('+')
            else -> append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xf])
        }
    }
}
