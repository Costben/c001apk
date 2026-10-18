package app.awavenue.c001apk.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class ApiFailureKind {
    Transport, Http, Business, Authentication, Captcha, RateLimit, RiskControl, ClientUpgrade, InvalidResponse, Parsing,
}

/**
 * Only safe, local messages enter the UI. Never include a response body,
 * request URL, headers or the underlying exception's message.
 */
class ApiFailure(val kind: ApiFailureKind, override val message: String, val httpStatus: Int? = null) : Exception(message) {
    companion object {
        fun http(status: Int?) =
            ApiFailure(ApiFailureKind.Http, "请求失败（HTTP ${status ?: "未知"}），请稍后重试", status)

        fun from(error: Throwable): ApiFailure = when (error) {
            is ApiFailure -> error
            is InterruptedIOException -> ApiFailure(ApiFailureKind.Transport, "请求超时，请稍后重试")
            is SSLException -> ApiFailure(ApiFailureKind.Transport, "服务器证书验证失败")
            is UnknownHostException -> ApiFailure(ApiFailureKind.Transport, "连接失败，请检查网络设置")
            is IOException -> ApiFailure(ApiFailureKind.Transport, "请求失败，请检查网络后重试")
            else -> ApiFailure(ApiFailureKind.Parsing, "数据解析失败，请更新客户端或稍后重试")
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Checks the envelope before any model mapping. An HTTP 200 alone says nothing
 * about login, risk controls or the shape of the payload.
 */
fun readApiObject(status: Int, body: String, requireData: Boolean = true): JsonObject {
    if (status !in 200..299) throw ApiFailure.http(status)
    val element = try {
        json.parseToJsonElement(body)
    } catch (_: Exception) {
        throw ApiFailure(ApiFailureKind.InvalidResponse, "服务器响应格式异常，请稍后重试")
    }
    val obj = element as? JsonObject
        ?: throw ApiFailure(ApiFailureKind.InvalidResponse, "服务器响应格式异常，请稍后重试")

    val marker = obj.text("messageStatus").lowercase()
    val message = obj.text("message")
    val error = obj["error"]
    val clues = "$marker $message ${error.text()}".lowercase()
    val code = obj.text("code").toIntOrNull()
    val businessStatus = obj.text("status").toIntOrNull()
    val data = obj["data"]
    val failed = (code != null && code !in listOf(0, 1, 200)) ||
        (businessStatus != null && businessStatus !in listOf(0, 1)) ||
        marker.startsWith("err_") ||
        (error != null && error !is JsonNull && error.text() !in listOf("", "0", "false")) ||
        (requireData && (data == null || data is JsonNull) && message.isNotEmpty() &&
            code == null && businessStatus == null)
    if (failed) {
        throw when {
            businessStatus == -10 || "网络环境" in clues ->
                ApiFailure(ApiFailureKind.RiskControl, "服务端检测到网络环境异常，请检查代理、DNS 或网络过滤设置后重试")
            marker == "err_request_need_upgrade_new_version" ->
                ApiFailure(ApiFailureKind.ClientUpgrade, "当前客户端协议未被服务端接受，请更新客户端后重试")
            "captcha" in clues || "验证码" in clues ->
                ApiFailure(ApiFailureKind.Captcha, "请求需要验证码验证，请完成验证后重试")
            "login" in clues || "auth" in clues || "登录" in clues || code == 401 ->
                ApiFailure(ApiFailureKind.Authentication, "登录状态已失效或需要登录，请重新登录")
            "rate_limit" in clues || "频繁" in clues || code == 429 ->
                ApiFailure(ApiFailureKind.RateLimit, "请求过于频繁，请稍后重试")
            else -> ApiFailure(ApiFailureKind.Business, "服务器未能完成请求，请稍后重试")
        }
    }
    if (requireData && !obj.containsKey("data")) {
        throw ApiFailure(ApiFailureKind.InvalidResponse, "服务器响应缺少数据字段，请稍后重试")
    }
    return obj
}

/** Envelope check straight off a response, for callers that need the raw headers too. */
fun readApiObject(response: HttpResponse): JsonObject = readApiObject(response.code, response.body)

/**
 * Write endpoints answer with `status` and `message` but no `data`; a success must not be
 * reported as a missing-field failure just because the payload is absent.
 */
fun readActionObject(response: HttpResponse): JsonObject =
    readApiObject(response.code, response.body, requireData = false)

private fun JsonObject.text(key: String): String = this[key].text()

private fun JsonElement?.text(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    else -> toString()
}
