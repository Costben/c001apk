package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val tag = "2.0.0-release"
    private val base = "https://github.com/Costben/c001apk/releases"

    private fun asset(name: String, url: String = "$base/download/$tag/$name", size: Int = 10, state: String = "uploaded") =
        """{"name":"$name","browser_download_url":"$url","state":"$state","size":$size}"""

    private fun release(
        assets: List<String> = emptyList(),
        draft: Boolean = false,
        prerelease: Boolean = false,
        page: String = "$base/tag/$tag",
        tagName: String = tag,
    ) = """{"draft":$draft,"prerelease":$prerelease,"tag_name":"$tagName","html_url":"$page",
        "body":"notes","published_at":"2026-10-16T08:00:00Z","assets":[${assets.joinToString(",")}]}"""

    @Test
    fun prereleaseSortsBelowItsStableVersion() {
        val dev = AppVersion.parseBuild("2.0.0-dev")!!
        assertEquals("dev", dev.preRelease)
        assertTrue(AppVersion.parseRelease("2.0.0-release")!! > dev)
        assertTrue(AppVersion.parseRelease("1.2.6")!! < dev)
        assertTrue(AppVersion.parseRelease("v2.0.1+20261016")!! > AppVersion.parseBuild("2.0.0")!!)
        assertEquals(0, AppVersion.parseBuild("2.0.0-release")!!.compareTo(AppVersion.parseRelease("2.0.0")!!))
    }

    @Test
    fun prereleaseNamesAndNumbersSortInOrder() {
        val ordered = listOf(
            "2.0.0-dev",
            "2.0.0-pre",
            "2.0.0-alpha",
            "2.0.0-beta.1",
            "2.0.0-beta.2",
            "2.0.0-rc.1",
            "2.0.0",
            "2.0.1-pre",
        ).map { AppVersion.parseRelease(it)!! }
        assertEquals(ordered, ordered.sorted())
        assertTrue(AppVersion.parseRelease("2.0.14-pre")!! > AppVersion.parseRelease("2.0.13")!!)
        assertTrue(AppVersion.parseRelease("2.0.14")!! > AppVersion.parseRelease("2.0.14-pre")!!)
    }

    @Test
    fun malformedVersionsAreRejected() {
        assertNull(AppVersion.parseRelease("2.0"))
        assertNull(AppVersion.parseRelease("02.0.0"))
    }

    @Test
    fun picksThisDevicesComposeApk() {
        val parsed = AppRelease.fromJson(
            release(
                listOf(
                    asset("c001apk-2.0.0-android-arm64-v8a.apk"),
                    asset("c001apk-coui-2.0.0+20261016-android-arm64-v8a.apk"),
                    asset("c001apk-coui-2.0.0-android-x86_64.apk"),
                    asset("c001apk-coui-2.0.0.apk"),
                ),
            ),
        )
        assertEquals("2.0.0", parsed.version.toString())
        assertEquals("$base/download/$tag/c001apk-coui-2.0.0+20261016-android-arm64-v8a.apk", parsed.downloadFor(listOf("arm64-v8a")))
        assertEquals("$base/download/$tag/c001apk-coui-2.0.0.apk", parsed.downloadFor(listOf("armeabi-v7a")))
        assertEquals(1792137600L, parsed.publishedAt)
    }

    @Test
    fun acceptsAPrereleaseRelease() {
        val preTag = "2.0.14-pre"
        val parsed = AppRelease.fromJson(
            release(
                assets = listOf(
                    asset(
                        "c001apk-coui-2.0.14-pre.apk",
                        url = "$base/download/$preTag/c001apk-coui-2.0.14-pre.apk",
                    ),
                ),
                prerelease = true,
                page = "$base/tag/$preTag",
                tagName = preTag,
            ),
        )
        assertEquals("2.0.14-pre", parsed.version.toString())
        assertEquals(
            "$base/download/$preTag/c001apk-coui-2.0.14-pre.apk",
            parsed.downloadFor(listOf("arm64-v8a")),
        )
    }

    @Test
    fun listPicksTheHighestVersion() {
        val stable = release(
            assets = listOf(
                asset(
                    "c001apk-coui-2.0.13.apk",
                    url = "$base/download/2.0.13-release/c001apk-coui-2.0.13.apk",
                ),
            ),
            page = "$base/tag/2.0.13-release",
            tagName = "2.0.13-release",
        )
        val preTag = "2.0.14-pre"
        val pre = release(
            assets = listOf(
                asset(
                    "c001apk-coui-2.0.14-pre.apk",
                    url = "$base/download/$preTag/c001apk-coui-2.0.14-pre.apk",
                ),
            ),
            prerelease = true,
            page = "$base/tag/$preTag",
            tagName = preTag,
        )
        val parsed = AppRelease.fromJsonList("[$stable,$pre]")
        assertEquals(2, parsed.size)
        assertEquals("2.0.14-pre", parsed.maxByOrNull { it.version }!!.version.toString())
    }

    @Test
    fun fallsBackToTheReleasePage() {
        val parsed = AppRelease.fromJson(
            release(
                listOf(
                    // The Flutter client's APK is a different app.
                    asset("c001apk-2.0.0-android-arm64-v8a.apk"),
                    asset("c001apk-coui-2.0.0.apk", size = 0),
                    asset("c001apk-coui-2.0.0-android-arm64-v8a.apk", state = "new"),
                    asset("c001apk-coui-2.0.0-android-x86_64.apk", url = "https://example.com/c001apk-coui-2.0.0-android-x86_64.apk"),
                    asset("c001apk-coui-2.0.0-android-armeabi-v7a.apk", url = "https://github.com/TG-Twilight/c001apk/releases/download/$tag/c001apk-coui-2.0.0-android-armeabi-v7a.apk"),
                ),
            ),
        )
        assertEquals("$base/tag/$tag", parsed.downloadFor(listOf("arm64-v8a", "x86_64", "armeabi-v7a")))
    }

    @Test
    fun rejectsUnpublishedOrForeignReleases() {
        assertThrows(IllegalArgumentException::class.java) { AppRelease.fromJson(release(draft = true)) }
        // The tag suffix and the GitHub flag must agree.
        assertThrows(IllegalArgumentException::class.java) { AppRelease.fromJson(release(prerelease = true)) }
        assertThrows(IllegalArgumentException::class.java) { AppRelease.fromJson(release(tagName = "2.0.0-pre")) }
        assertThrows(IllegalArgumentException::class.java) {
            AppRelease.fromJson(release(page = "https://github.com/TG-Twilight/c001apk/releases/tag/$tag"))
        }
        assertThrows(IllegalArgumentException::class.java) { AppRelease.fromJson(release(page = "$base/tag/1.0.0")) }
        assertThrows(IllegalArgumentException::class.java) { AppRelease.fromJson("<html>") }
    }
}
