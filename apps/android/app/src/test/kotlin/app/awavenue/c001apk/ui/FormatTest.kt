package app.awavenue.c001apk.ui

import app.awavenue.c001apk.ui.common.fromToday
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.thumbnail
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val now = 1_760_000_000L

    @Test
    fun relativeTimeMatchesFlutterWording() {
        assertEquals("刚刚", fromToday(now, now))
        assertEquals("30秒前", fromToday(now - 30, now))
        assertEquals("5分钟前", fromToday(now - 300, now))
        assertEquals("3小时前", fromToday(now - 3 * 3600, now))
        assertEquals("1天前", fromToday(now - 86400 - 10, now))
        assertEquals("10天前", fromToday(now - 10 * 86400, now))
        assertEquals("", fromToday(null, now))
    }

    @Test
    fun readsEncodedImageSize() {
        assertEquals(1080f to 2400f, imageSize("https://image.coolapk.com/feed/a@1080x2400.jpg"))
        assertEquals(1f to 1f, imageSize("https://image.coolapk.com/feed/a.jpg"))
        assertEquals("https://x/a.jpg.s.jpg", thumbnail("https://x/a.jpg"))
        assertEquals("", thumbnail(""))
    }
}
