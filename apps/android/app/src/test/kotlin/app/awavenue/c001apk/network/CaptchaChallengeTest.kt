package app.awavenue.c001apk.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptchaChallengeTest {
    private fun challenge(extra: String) =
        CaptchaChallenge.fromBody("""{"messageStatus":"err_request_captcha_v2","messageExtra":$extra,"data":null}""")!!

    @Test
    fun readsServerFieldAndId() {
        val c = challenge("""{"captchaId":"0123456789abcdef0123456789abcdef","captchaField":"_custom_token"}""")
        assertEquals("0123456789abcdef0123456789abcdef", c.captchaId)
        assertEquals("_custom_token", c.field)
        assertFalse(c.usesCookie)
    }

    @Test
    fun blankFieldSelectsCookieProtocol() {
        assertTrue(challenge("""{"captchaType":"NEC","captchaId":"0123456789abcdef0123456789abcdef","captchaField":""}""").usesCookie)
        assertTrue(challenge("""{"captchaField":"  "}""").usesCookie)
        // The extra may also arrive as a JSON string.
        assertTrue(challenge(""""{\"captchaField\":\"\"}"""").usesCookie)
    }

    @Test
    fun absentOrInvalidFieldKeepsLegacyDefault() {
        assertEquals("_v2_post_token", challenge("""{}""").field)
        assertEquals("_v2_post_token", challenge("""{"captchaField":null}""").field)
        assertEquals("_v2_post_token", challenge("""{"captchaField":1}""").field)
        assertEquals("_v2_post_token", challenge("""{"captchaField":"no-underscore"}""").field)
    }

    @Test
    fun ignoresOtherResponses() {
        assertNull(CaptchaChallenge.fromBody("""{"data":{"id":123}}"""))
        assertNull(CaptchaChallenge.fromBody("<html>"))
    }

    @Test
    fun encodesCookieLikeDart() {
        assertEquals("NEC%3A01234567%3Asynthetic%2Bproof%2Fvalue", encodeQueryComponent("NEC:01234567:synthetic+proof/value"))
        assertEquals("a-b_c.d~e", encodeQueryComponent("a-b_c.d~e"))
        assertEquals("%2A+%E9%AA%8C", encodeQueryComponent("* 验"))
    }

    @Test
    fun appendsValidationToSessionCookie() {
        assertEquals("uid=1; token=t", withValidation("uid=1; token=t", null))
        assertEquals("uid=1; token=t; validate=v", withValidation("uid=1; token=t", "v"))
        assertEquals("validate=v", withValidation("", "v"))
    }
}
