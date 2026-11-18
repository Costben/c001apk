package app.awavenue.c001apk.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class CoolapkLinkTest {
    @Test
    fun stripsHostFromCoolmarketDeepLinks() {
        assertEquals("/feed/75144331", coolapkPath("coolmarket://feed/75144331"))
        assertEquals("/feed/75144331", coolapkPath("coolmarket://www.coolapk.com/feed/75144331"))
        assertEquals("/feed/75144331", coolapkPath("coolmarket://www.coolapk1s.com/feed/75144331"))
        assertEquals("/feed/75144331", coolapkPath("coolmarket://com.coolapk.market/feed/75144331"))
        assertEquals(
            "/feed/75144331?shareKey=abc&rid=12",
            coolapkPath("coolmarket://www.coolapk.com/feed/75144331?shareKey=abc&rid=12"),
        )
        assertEquals(
            "/search?searchValue=coui",
            coolapkPath("coolmarket://com.coolapk.market/search?searchValue=coui"),
        )
        assertEquals("/", coolapkPath("coolmarket://www.coolapk.com"))
    }

    @Test
    fun extractsPathFromWebsiteAndMirrorLinks() {
        assertEquals("/feed/75144331", coolapkPath("https://www.coolapk.com/feed/75144331"))
        assertEquals("/feed/75144331", coolapkPath("https://www.coolapk1s.com/feed/75144331"))
        assertEquals(
            "/u/12345?tab=feed",
            coolapkPath("https://coolapk1s.com/u/12345?tab=feed"),
        )
    }
}
