package app.awavenue.c001apk.network

import app.awavenue.c001apk.data.AppSettings
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.data.Session
import app.awavenue.c001apk.data.TokenUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A file part of a multipart request. */
class FilePart(val name: String, val fileName: String, val bytes: ByteArray, val mimeType: String)

/** The request bodies this client sends. Kept as data so a captcha replay can re-send them. */
sealed interface Body {
    data object None : Body
    data class Form(val fields: List<Pair<String, String>>) : Body
    data class Multipart(val fields: List<Pair<String, String>>, val files: List<FilePart>) : Body
    data class Text(val content: String, val contentType: String) : Body
}

class HttpResponse(
    val code: Int,
    val body: String,
    val captchaVerification: String? = null,
    val setCookies: List<String> = emptyList(),
    val location: String? = null,
) {
    /** The redirect target of an unfollowed 302, or null when the header is absent. */
    val redirectTarget: String? get() = location
}

/**
 * Talks to Coolapk. Request signing, the session cookie and the host-specific header profiles
 * follow the Flutter client's interceptor; the captcha replay follows its request wrapper.
 */
class ApiClient(
    private val settings: AppSettings,
    private val session: () -> Session = { Session() },
    baseClient: OkHttpClient = OkHttpClient(),
) {
    val http: OkHttpClient = baseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(CoolapkHeaders())
        .build()

    private val noRedirectHttp: OkHttpClient = http.newBuilder().followRedirects(false).build()

    /** Set by the UI layer; returns a proof, or null when the user dismissed the challenge. */
    var captchaVerifier: (suspend (CaptchaChallenge) -> String?)? = null

    private val verifying = AtomicBoolean(false)

    /**
     * Read verification earned through the cookie protocol, shared by both API hosts. It lives only
     * in memory and only for the session and device that earned it.
     */
    private class CaptchaCookie(val value: String, val session: Session, val device: String)

    @Volatile
    private var captchaCookie: CaptchaCookie? = null

    private fun captchaCookieFor(session: Session, device: String): String? {
        val cookie = captchaCookie ?: return null
        if (cookie.session == session && cookie.device == device) return cookie.value
        captchaCookie = null
        return null
    }

    suspend fun get(
        url: String,
        query: Map<String, Any?> = emptyMap(),
        skipCaptcha: Boolean = false,
        followRedirects: Boolean = true,
        accept: String? = null,
    ): HttpResponse = request("GET", url, query, Body.None, skipCaptcha, followRedirects, accept)

    suspend fun post(
        url: String,
        query: Map<String, Any?> = emptyMap(),
        body: Body = Body.None,
        skipCaptcha: Boolean = false,
        followRedirects: Boolean = true,
        accept: String? = null,
    ): HttpResponse = request("POST", url, query, body, skipCaptcha, followRedirects, accept)

    /** Decodes the response envelope, so a business failure surfaces as [ApiFailure]. */
    suspend fun getJson(url: String, query: Map<String, Any?> = emptyMap(), skipCaptcha: Boolean = false): JsonObject =
        get(url, query, skipCaptcha).let { readApiObject(it.code, it.body) }

    suspend fun postJson(
        url: String,
        query: Map<String, Any?> = emptyMap(),
        body: Body = Body.None,
        skipCaptcha: Boolean = false,
    ): JsonObject = post(url, query, body, skipCaptcha).let { readApiObject(it.code, it.body) }

    /** Binary responses: captcha images and saved pictures. */
    suspend fun getBytes(url: String, query: Map<String, Any?> = emptyMap()): ByteArray {
        val httpUrl = url.toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> if (v != null) addQueryParameter(k, v.toString()) } }.build()
        val request = Request.Builder().url(httpUrl).header("Accept", "image/*,*/*;q=0.8").get().build()
        val response = noRedirectHttp.newCall(request).await()
        if (response.code !in 200..299) throw ApiFailure.http(response.code)
        return response.bytes
    }

    /**
     * Pictures to save or share. Image hosts answer generic agents with HTTP 567, so this goes out
     * with the CoolMarket agent the image loader uses, and follows CDN redirects.
     */
    suspend fun getImageBytes(url: String): ByteArray {
        val request = Request.Builder().url(url)
            .header("User-Agent", settings.deviceParams.userAgent)
            .header("Accept", "image/*,*/*;q=0.8")
            .get()
            .build()
        val response = http.newCall(request).await()
        if (response.code !in 200..299) throw ApiFailure.http(response.code)
        return response.bytes
    }

    private suspend fun request(
        method: String,
        url: String,
        query: Map<String, Any?>,
        body: Body,
        skipCaptcha: Boolean,
        followRedirects: Boolean,
        accept: String?,
    ): HttpResponse {
        val httpUrl = url.toHttpUrl().newBuilder().apply {
            query.forEach { (key, value) -> if (value != null) addQueryParameter(key, value.toString()) }
        }.build()
        val before = session()
        val device = settings.deviceParams.xAppDevice
        val response = execute(if (followRedirects) http else noRedirectHttp, httpUrl, method, body, accept)
        return verifyAndRetry(response, httpUrl, method, body, accept, before, device, skipCaptcha, followRedirects)
    }

    private suspend fun execute(
        client: OkHttpClient,
        url: HttpUrl,
        method: String,
        body: Body,
        accept: String?,
    ): HttpResponse {
        val builder = Request.Builder().url(url)
        accept?.let { builder.header("Accept", it) }
        builder.method(method, body.toRequestBody(method))
        val raw = client.newCall(builder.build()).await()
        return HttpResponse(
            code = raw.code,
            body = raw.body,
            setCookies = raw.headers.values("Set-Cookie"),
            location = raw.headers["Location"],
        )
    }

    private suspend fun verifyAndRetry(
        response: HttpResponse,
        url: HttpUrl,
        method: String,
        body: Body,
        accept: String?,
        before: Session,
        device: String,
        skip: Boolean,
        followRedirects: Boolean,
    ): HttpResponse {
        if (skip || verifying.get() || captchaVerifier == null) return response
        if (url.scheme != "https" || url.host !in API_HOSTS) return response
        if (session() != before) return response
        val challenge = CaptchaChallenge.fromBody(response.body) ?: return response
        val verifier = captchaVerifier ?: return response
        if (challenge.usesCookie) captchaCookie = null

        val proof = if (verifying.compareAndSet(false, true)) {
            try {
                verifier(challenge)
            } finally {
                verifying.set(false)
            }
        } else {
            null
        }

        if (
            proof == null ||
            !challenge.acceptsProof(proof) ||
            session() != before ||
            settings.deviceParams.xAppDevice != device
        ) {
            return HttpResponse(
                response.code,
                response.body,
                captchaVerification = if (proof == null) "cancelled" else "incomplete",
                setCookies = response.setCookies,
                location = response.location,
            )
        }

        // Replayed exactly once, straight through execute: no verification loop. `url` already
        // carries the original query, so only the proof is added on top of it, unless it rides in
        // the cookie instead.
        if (challenge.usesCookie) captchaCookie = CaptchaCookie(encodeQueryComponent(proof), before, device)
        val verified = when {
            challenge.usesCookie -> request(method, url.toString(), emptyMap(), body, true, followRedirects, accept)
            method == "GET" ->
                request("GET", url.toString(), mapOf(challenge.field to proof), Body.None, true, followRedirects, accept)
            else ->
                request("POST", url.toString(), emptyMap(), body.withField(challenge.field, proof), true, followRedirects, accept)
        }
        // A cookie the server still challenges is spent; keeping it would only fail every request.
        if (challenge.usesCookie && CaptchaChallenge.fromBody(verified.body) != null) captchaCookie = null
        return HttpResponse(verified.code, verified.body, "submitted", verified.setCookies, verified.location)
    }

    // OkHttp rejects any body on GET, so the empty one has to be null rather than zero-length.
    private fun Body.toRequestBody(method: String): RequestBody? = when (this) {
        Body.None -> if (method == "POST") FormBody.Builder().build() else null
        is Body.Form -> FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        is Body.Multipart -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            fields.forEach { (k, v) -> addFormDataPart(k, v) }
            files.forEach { addFormDataPart(it.name, it.fileName, it.bytes.toRequestBody(it.mimeType.toMediaType())) }
        }.build()
        is Body.Text -> content.toRequestBody(contentType.toMediaType())
    }

    private fun Body.withField(key: String, value: String): Body = when (this) {
        Body.None -> Body.Form(listOf(key to value))
        is Body.Form -> Body.Form(fields.filterNot { it.first == key } + (key to value))
        is Body.Multipart -> Body.Multipart(fields.filterNot { it.first == key } + (key to value), files)
        is Body.Text -> this
    }

    private inner class CoolapkHeaders : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url
            if (url.scheme != "https") return chain.proceed(request)
            val params = settings.deviceParams
            val current = session()
            val builder = request.newBuilder()

            when (url.host) {
                "account.coolapk.com" -> {
                    val wantsImage = request.method == "GET" && request.header("Accept")?.startsWith("image/") == true
                    builder.header("sec-ch-ua", "\"Android WebView\";v=\"117\", \"Not;A=Brand\";v=\"8\", \"Chromium\";v=\"117\"")
                        .header("sec-ch-ua-mobile", "?1")
                        .header("sec-ch-ua-platform", "Android")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("User-Agent", params.userAgent)
                        .header("Accept", BROWSER_ACCEPT)
                        .header("X-Requested-With", Protocol.APP_ID)
                    if (current.sessid.isNotEmpty()) {
                        builder.header("Cookie", "${current.sessid}; forward=https://www.coolapk.com")
                    }
                    if (request.method == "POST") {
                        builder.header("X-Requested-With", Protocol.REQUEST_WITH)
                    } else if (wantsImage) {
                        builder.header("Accept", "image/*,*/*;q=0.8")
                            .header("Referer", "https://account.coolapk.com/auth/loginByCoolApk")
                    }
                }

                "api.coolapk.com", "api2.coolapk.com", "www.coolapk.com" -> {
                    val publicFeed = url.host == "www.coolapk.com" &&
                        request.method == "GET" &&
                        PUBLIC_FEED_PATH.matches(url.encodedPath)
                    if (url.host == "www.coolapk.com" && !publicFeed) return chain.proceed(request)
                    val token = try {
                        TokenUtils.getTokenV3(params.xAppDevice, appCode = params.versionCode.toInt())
                    } catch (_: Exception) {
                        throw IOException(ApiFailure(ApiFailureKind.Business, "请求参数异常，请检查客户端协议设置和系统时间"))
                    }
                    builder.header("User-Agent", params.userAgent)
                        .header("X-Requested-With", Protocol.REQUEST_WITH)
                        .header("X-Sdk-Int", params.sdkInt)
                        .header("X-Sdk-Locale", Protocol.LOCALE)
                        .header("X-App-Id", Protocol.APP_ID)
                        .header("X-App-Token", token)
                        .header("X-App-Version", params.versionName)
                        .header("X-App-Code", params.versionCode)
                        .header("X-Api-Version", params.apiVersion)
                        .header("X-App-Device", params.xAppDevice)
                        .header("X-Dark-Mode", "0")
                        .header("X-App-Channel", Protocol.CHANNEL)
                        .header("X-App-Mode", Protocol.MODE)
                        .header("X-App-Supported", params.versionCode)
                    if (publicFeed) {
                        // The public feed JSON wants signed headers but never a session cookie.
                        builder.removeHeader("Cookie")
                    } else {
                        val cookie = if (current.isLogin) current.apiCookie else current.sessid
                        val validation = captchaCookieFor(current, params.xAppDevice)
                        builder.header("Cookie", withValidation(cookie, validation))
                    }
                }

                // The micro-pages answer HTML unless the request looks like an in-app XHR.
                "m.coolapk.com" -> {
                    builder.header("User-Agent", settings.deviceParams.userAgent)
                        .header("X-Requested-With", Protocol.REQUEST_WITH)
                }

                else -> return chain.proceed(request)
            }
            return chain.proceed(builder.build())
        }
    }

    private companion object {
        val API_HOSTS = setOf("api.coolapk.com", "api2.coolapk.com")
        val PUBLIC_FEED_PATH = Regex("^/feed/[0-9]+$")
        const val BROWSER_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8," +
                "application/signed-exchange;v=b3;q=0.7"
    }
}

private val Session.apiCookie: String get() = "uid=$uid; username=$username; token=$token"

internal fun withValidation(cookie: String, validation: String?): String = when {
    validation == null -> cookie
    cookie.isEmpty() -> "validate=$validation"
    else -> "$cookie; validate=$validation"
}

private class RawResponse(code: Int, val bytes: ByteArray, val headers: okhttp3.Headers) {
    val code = code
    val body: String by lazy { bytes.decodeToString() }
}

/** Runs the call on OkHttp's dispatcher and reads the body there, never on the caller's thread. */
private suspend fun Call.await(): RawResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            val result = try {
                response.use { RawResponse(it.code, it.body.bytes(), it.headers) }
            } catch (e: IOException) {
                continuation.resumeWithException(e)
                return
            }
            continuation.resume(result)
        }

        override fun onFailure(call: Call, e: IOException) {
            val failure = (e.cause as? ApiFailure) ?: e
            continuation.resumeWithException(failure)
        }
    })
}
