package app.awavenue.c001apk.ui.app

import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.platform.InstalledApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkScreenTest {
    private fun entity(raw: String) = Entity(Json.parseToJsonElement(raw).jsonObject)

    @Test
    fun readsSnakeCaseAndCamelCaseCommentStatus() {
        assertEquals(1, entity("""{"comment_status":1,"commentStatusText":"允许评论"}""").apkCommentStatus())
        assertEquals(-1, entity("""{"comment_status":-1,"commentStatusText":"禁止评论"}""").apkCommentStatus())
        assertEquals(1, entity("""{"commentStatus":1}""").apkCommentStatus())
        assertEquals(0, entity("""{"title":"App"}""").apkCommentStatus())
    }

    @Test
    fun detectsNewerRemoteVersionAgainstInstalledApp() {
        val local = InstalledApp(
            packageName = "com.example.app",
            appName = "Example",
            versionName = "1.0.0",
            versionCode = 100L,
            apkSize = 1024L,
            lastUpdateTime = 0L,
            icon = null,
        )
        assertTrue(entity("""{"apkversioncode":"101"}""").hasNewerVersionThan(local))
        assertFalse(entity("""{"apkversioncode":"100"}""").hasNewerVersionThan(local))
        assertFalse(entity("""{"apkversioncode":"99"}""").hasNewerVersionThan(local))
        assertFalse(entity("""{"apkversioncode":"101"}""").hasNewerVersionThan(null))
    }

    @Test
    fun parsesUpdateBitTypeAndPackageNameFallbacks() {
        assertEquals("64位", entity("""{"pkg_bit_type":2}""").updateBitTypeLabel())
        assertEquals("64位", entity("""{"pkg_bit_type":3}""").updateBitTypeLabel())
        assertEquals("32位", entity("""{"pkg_bit_type":1}""").updateBitTypeLabel())
        assertEquals("64位", entity("""{"pkgBitType":2}""").updateBitTypeLabel())
        assertEquals("", entity("""{"pkg_bit_type":0}""").updateBitTypeLabel())

        assertEquals("com.tencent.mm", entity("""{"packageName":"com.tencent.mm","apkname":"fallback"}""").updatePackageName())
        assertEquals("com.tencent.mm", entity("""{"apkname":"com.tencent.mm"}""").updatePackageName())
    }

    @Test
    fun prunesAlreadyUpdatedAndUninstalledApps() {
        val updates = listOf(
            entity("""{"packageName":"com.a","apkversioncode":"200"}"""),
            entity("""{"packageName":"com.b","apkversioncode":"150"}"""),
            entity("""{"packageName":"com.c","apkversioncode":"300"}"""),
        )
        val installed = mapOf(
            "com.a" to InstalledApp("A", "com.a", "2.0", 200L, 0L, 0L, null),
            "com.b" to InstalledApp("B", "com.b", "1.4", 140L, 0L, 0L, null),
        )
        val remaining = pruneUpdatedApps(updates, installed)
        assertEquals(listOf("com.b"), remaining.map { it.updatePackageName() })
    }
}
