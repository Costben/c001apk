package app.awavenue.c001apk.network

import app.awavenue.c001apk.model.TwilightRuleSet
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads the public 秋风 rule snapshot. It uses its own transport on purpose: the rules live on
 * GitHub, which must never see the Coolapk session cookie or the account headers.
 */
class TwilightRulesClient internal constructor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
) {
    suspend fun fetch(): TwilightRuleSet = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(
            Request.Builder().url(URL)
                .header("Accept", "application/json")
                .header("User-Agent", "c001apk-content-filter/1")
                .build(),
        )
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use(::readRules) }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }

    companion object {
        const val URL = "https://raw.githubusercontent.com/TG-Twilight/c001apk/filters/rules.json"

        internal fun readRules(response: Response): TwilightRuleSet {
            require(response.code == 200) { "规则下载失败（HTTP ${response.code}）" }
            val body = requireNotNull(response.body) { "规则文件为空" }
            val type = body.contentType()?.let { "${it.type}/${it.subtype}" }
            require(type in setOf("application/json", "text/plain")) { "规则文件格式无效" }
            require(body.contentLength() in -1L..TwilightRuleSet.MAX_BYTES.toLong()) { "规则文件过大" }
            val bytes = body.byteStream().readAtMost(TwilightRuleSet.MAX_BYTES + 1)
            require(bytes.size <= TwilightRuleSet.MAX_BYTES) { "规则文件过大" }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
            return TwilightRuleSet.parse(text)
        }

        /** Reads until EOF or [limit] bytes; the caller checks which happened. */
        private fun InputStream.readAtMost(limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "规则文件过大" }
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }
}
