package app.awavenue.c001apk.data

import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.util.Base64

object TokenUtils {
    private val authBlob: ByteArray by lazy { Base64.getDecoder().decode(TOKEN_V3_DATA.trim()) }
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private val saltPattern = Regex("^[./A-Za-z0-9]{22}$")

    /**
     * Compatible with coolapk-desktop auth.rs; see docs/THIRD_PARTY_NOTICES.md.
     * A fresh signature is derived per request, including the active app code.
     */
    fun getTokenV3(
        deviceCode: String,
        timestamp: Long = System.currentTimeMillis() / 1000,
        appCode: Int = Protocol.VERSION_CODE.toInt(),
    ): String {
        for (offset in 0..15) {
            val time = timestamp + offset
            val index = ((time + appCode) % 100).toInt() * 4 + 0x80
            val chunk = String(authBlob.copyOfRange(index, index + 0x80), Charsets.US_ASCII)
            val segment = String(Base64.getDecoder().decode(chunk), Charsets.UTF_8)
            val plain = "${Protocol.APP_ID}&$segment&${md5(deviceCode)}&$time&$appCode"
            val password = md5(Base64.getEncoder().encodeToString(plain.toByteArray()))
            val source = base64NoPad("${time.toString(16)}/${md5(plain)}").substring(0, 22)
            // Dart's % is Euclidean; mod() keeps the shifted index non-negative the same way.
            val salt = source.substring(0, 21) + ALPHABET[(ALPHABET.indexOf(source[21]) - 5).mod(64)]
            if (!saltPattern.matches(salt)) continue
            val hash = "\$2y" + BCrypt.hashpw(password, "\$2a\$04\$$salt").substring(3)
            return "v3${base64NoPad(hash)}"
        }
        throw IllegalStateException("无法生成请求签名，请检查系统时间")
    }

    fun encodeDevice(deviceInfo: String): String =
        Base64.getEncoder().encodeToString(deviceInfo.toByteArray())
            .reversed()
            .replace(Regex("(\r\n|\r|\n|=)"), "")

    fun base64NoPad(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray()).replace("=", "")

    /** The nonce the login form sends back verbatim. */
    fun createRandomNumber(): String = kotlin.random.Random.nextDouble().toString().replace(".", "undefined")

    fun md5(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
