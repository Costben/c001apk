package app.awavenue.c001apk.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiFailureTest {
    private fun kindOf(status: Int, body: String) =
        assertThrows(ApiFailure::class.java) { readApiObject(status, body) }.kind

    @Test
    fun acceptsSuccessfulEnvelope() {
        val obj = readApiObject(200, """{"data":[{"entityType":"feed"}]}""")
        assertEquals(true, obj.containsKey("data"))
    }

    @Test
    fun classifiesFailures() {
        assertEquals(ApiFailureKind.Http, kindOf(502, "{}"))
        assertEquals(ApiFailureKind.InvalidResponse, kindOf(200, "<html>"))
        assertEquals(ApiFailureKind.InvalidResponse, kindOf(200, """{"message":""}"""))
        assertEquals(ApiFailureKind.RiskControl, kindOf(200, """{"status":-10,"message":"网络环境异常"}"""))
        assertEquals(ApiFailureKind.ClientUpgrade, kindOf(200, """{"messageStatus":"err_request_need_upgrade_new_version"}"""))
        assertEquals(ApiFailureKind.Captcha, kindOf(200, """{"messageStatus":"err_request_captcha_v2","data":null}"""))
        assertEquals(ApiFailureKind.Authentication, kindOf(200, """{"message":"请先登录"}"""))
        assertEquals(ApiFailureKind.RateLimit, kindOf(200, """{"code":429,"message":"too many"}"""))
        assertEquals(ApiFailureKind.Business, kindOf(200, """{"status":2,"message":"failed","data":null}"""))
    }

    @Test
    fun actionEnvelopeWithoutDataIsAccepted() {
        val obj = readApiObject(
            200,
            """{"status":1,"error":0,"message":"关注成功","messageStatus":1}""",
            requireData = false,
        )
        assertEquals("关注成功", obj["message"]?.toString()?.trim('"'))
    }

    @Test
    fun dataEnvelopeStillRequiresData() {
        val error = assertThrows(ApiFailure::class.java) {
            readApiObject(200, """{"status":1,"error":0,"message":"关注成功","messageStatus":1}""")
        }
        assertEquals(ApiFailureKind.InvalidResponse, error.kind)
    }

    @Test
    fun actionEnvelopeStillRejectsFailures() {
        val error = assertThrows(ApiFailure::class.java) {
            readApiObject(200, """{"status":2,"message":"failed"}""", requireData = false)
        }
        assertEquals(ApiFailureKind.Business, error.kind)
        assertEquals("failed", error.message)
    }

    @Test
    fun businessFailureSurfacesSafeServerMessageAndRedactsUnsafeBodies() {
        val replyRejected = assertThrows(ApiFailure::class.java) {
            readApiObject(
                200,
                """{"status":500,"error":-1,"message":"动态被作者设为仅自己可见，无法回复","messageStatus":"err_on_feed_only_publisher_self"}""",
                requireData = false,
            )
        }
        assertEquals(ApiFailureKind.Business, replyRejected.kind)
        assertEquals("动态被作者设为仅自己可见，无法回复", replyRejected.message)

        val htmlMessage = assertThrows(ApiFailure::class.java) {
            readApiObject(200, """{"status":500,"message":"<html>502 Bad Gateway</html>"}""", requireData = false)
        }
        assertEquals("服务器未能完成请求，请稍后重试", htmlMessage.message)

        val urlMessage = assertThrows(ApiFailure::class.java) {
            readApiObject(200, """{"status":500,"message":"see https://api.coolapk.com/internal"}""", requireData = false)
        }
        assertEquals("服务器未能完成请求，请稍后重试", urlMessage.message)
    }
}
