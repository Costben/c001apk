package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Expected values come from the Flutter client's TokenUtils run on the same inputs. */
class TokenUtilsTest {
    @Test
    fun tokenV3MatchesFlutterImplementation() {
        val cases = listOf(
            Triple(1700000000L, "abc", "v3JDJ5JDA0JE5qVTFNMll4TURBdk1HRmhZamc1T082b3pnQi5jU0hZdlFWNlpVU01DcnRQUFQ3MTVUTWJT"),
            Triple(1700000000L, "deviceCodeXYZ==", "v3JDJ5JDA0JE5qVTFNMll4TURBdlkyVTBZVFUzTk9XdWRSQllqWFh4OEpyNXM5bjRURHhTVzBEUmxraXND"),
            Triple(1760000000L, "abc", "v3JDJ5JDA0JE5qaGxOemM0TURBdk5qa3hNR1V5T09oZ2k3b1pjeno0Nk1RVXVrQURoOHFyNDNOenFwU1lD"),
            Triple(1760000000L, "deviceCodeXYZ==", "v3JDJ5JDA0JE5qaGxOemM0TURBdk4yTXlOVFJpTWVwTkR3L1NYVEczMmVIOGNYTmYwdXRPUUUuaWkyWTB1"),
            Triple(1790000123L, "abc", "v3JDJ5JDA0JE5tRmlNVE5pWm1JdllUVmxaVFExTWVWRklmVFpndElmZmlXaEpCMHlBUzQ3VDlrUDg3YnMu"),
            Triple(1790000123L, "deviceCodeXYZ==", "v3JDJ5JDA0JE5tRmlNVE5pWm1Jdk9HUTJaVEkxWk9hNWdGdHFFTEcvbTl5LmxucFk3T0RzUlhTU3hrMDdx"),
        )
        for ((time, device, expected) in cases) {
            assertEquals("time=$time device=$device", expected, TokenUtils.getTokenV3(device, timestamp = time, appCode = 2604201))
        }
    }

    @Test
    fun encodeDeviceMatchesFlutterImplementation() {
        assertEquals(
            "AbsVnbgsDRMlUVCByOzITMDJUQgszTQB1TgszTQB1TgsDMwoDMwoDMwoDMwoDMwojMwAyOgsDI7QWa",
            TokenUtils.encodeDevice("id; ; ; 02:00:00:00:00:00; OPPO; OPPO; ABC123; BUILD; null"),
        )
    }
}
