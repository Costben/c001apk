package app.awavenue.c001apk.data

import java.security.MessageDigest

/**
 * Wire transforms for the 数盟 registration report, from People-11/shuzilm_id revision 03118bc4.
 * Kept apart from the network code so the vectors can be checked without a device.
 */
internal object DuCodec {

    private val KEY = "xp7j@&!v5]2k#+3z{n9\$q^r%[8f1*d4e:c>0l?m/6)yb-=w}".toByteArray()

    /** Request and response share one stream cipher: a repeating XOR with key-table entry 16. */
    fun xor(data: ByteArray): ByteArray = ByteArray(data.size) { (data[it].toInt() xor KEY[it % KEY.size].toInt()).toByte() }

    /** The encoding parameter: the time string's last three characters with the dot dropped. */
    fun param(time: String): Int = time.takeLast(3).replace(".", "").toIntOrNull() ?: 0

    /** The four shuffles, picked by `(p / 100) % 4`. */
    fun shuffle(s: String, p: Int): String {
        val n = s.length
        val m = n / 2
        return buildString(n) {
            when (p / 100 % 4) {
                0 -> { if (n % 2 == 1) append(s[m]); for (i in 0 until m) append(s[n - 1 - i]).append(s[m - 1 - i]) }
                1 -> { for (i in 0 until m) append(s[n - 1 - i]).append(s[i]); if (n % 2 == 1) append(s[m]) }
                2 -> { for (i in n - 1 downTo 0 step 2) append(s[i]); for (i in n - 2 downTo 0 step 2) append(s[i]) }
                else -> { for (i in 0 until n step 2) append(s[i]); for (i in 1 until n step 2) append(s[i]) }
            }
        }
    }

    /** Field value obfuscation: rotate printable ASCII by `p % 100 % 57 + 1`, then shuffle. */
    fun enc(s: String, p: Int): String {
        val k = p % 100 % 57 + 1
        return shuffle(s.map { if (it.code in 0x20..0x7e) ((it.code - 0x20 + k) % 95 + 0x20).toChar() else it }.joinToString(""), p)
    }

    /** Drops the character at `n-1-p%10` when the string is longer than nine, else the last one. */
    fun dropChar(s: String, p: Int): String {
        if (s.length <= 1) return s
        val i = if (s.length > 9) s.length - 1 - p % 10 else s.length - 1
        return s.removeRange(i, i + 1)
    }

    /** Companion of [dropChar]: MD5 of the character sum plus `p%100/10`, first seven characters. */
    fun checksum(s: String, p: Int): String =
        if (s.length <= 1) "CFCD208" else md5Upper((s.sumOf { it.code } + p % 100 / 10).toString()).take(7)

    /** A MAC-shaped value from `SHA1(android_id)`, sliced `[1:7]:[13:19]:[7:13]`. */
    fun fakeMac(sha1Upper: String): String =
        "${sha1Upper.substring(1, 7)}:${sha1Upper.substring(13, 19)}:${sha1Upper.substring(7, 13)}"

    fun md5Upper(s: String): String = hashHexUpper("MD5", s)

    fun hashHexUpper(alg: String, s: String): String =
        MessageDigest.getInstance(alg).digest(s.toByteArray()).joinToString("") { "%02X".format(it) }
}
