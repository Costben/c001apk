package app.awavenue.c001apk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LongPostPackageTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun workDir() = temp.newFolder("unpacked")

    /** Builds a zip in memory; [entries] maps entry names to their bytes. */
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ok(bytes: ByteArray, fileName: String = "模板.zip"): LongPostPackage.Ok {
        val read = readLongPostPackage(bytes, fileName, workDir())
        assertTrue("expected a read, got $read", read is LongPostPackage.Ok)
        return read as LongPostPackage.Ok
    }

    private fun failure(bytes: ByteArray): String =
        (readLongPostPackage(bytes, "模板.zip", workDir()) as LongPostPackage.Failed).reason

    @Test
    fun aBareDocumentNeedsNoUnpacking() {
        val read = ok("---\ntitle: 上手\n---\n正文。".toByteArray(), "模板.md")
        assertEquals("上手", read.template.title)
        assertEquals("正文。", read.template.body)
        assertNull(read.baseDir)
        assertTrue(!isZipArchive(read.document.toByteArray()))
    }

    @Test
    fun zipSignaturesAreRecognised() {
        assertTrue(isZipArchive(zip("post.md" to "正文。".toByteArray())))
        assertTrue(!isZipArchive("---\ntitle: 上手\n---\n正文。".toByteArray()))
        assertTrue(!isZipArchive(ByteArray(0)))
    }

    @Test
    fun picturesInsideThePackageBecomeAbsolutePaths() {
        val read = ok(
            zip(
                "post.md" to "---\ntitle: 开箱\n---\n先看正面。\n\n![正面](images/01.png)\n\n后面。".toByteArray(),
                "images/01.png" to byteArrayOf(1, 2, 3),
            ),
        )
        assertEquals(1, read.template.images.size)
        val picture = File(read.template.images.first().source)
        assertTrue("picture should be absolute: ${picture.path}", picture.isAbsolute)
        assertTrue(picture.isFile)
        assertTrue(read.warnings.isEmpty())
        assertEquals("开箱", read.template.title)
    }

    @Test
    fun postMdWinsWhenThePackageHoldsMoreThanOneDocument() {
        val read = ok(
            zip(
                "readme.txt" to "别的正文。".toByteArray(),
                "post.md" to "---\ntitle: 正式\n---\n正文。".toByteArray(),
            ),
        )
        assertEquals("正式", read.template.title)
    }

    @Test
    fun twoDocumentsWithoutPostMdIsAFailure() {
        val reason = failure(
            zip(
                "a.md" to "正文一。".toByteArray(),
                "b.md" to "正文二。".toByteArray(),
            ),
        )
        assertTrue(reason.contains("2 份正文"))
    }

    @Test
    fun aPackageWithoutADocumentIsAFailure() {
        val reason = failure(zip("images/01.png" to byteArrayOf(1, 2, 3)))
        assertTrue(reason.contains("没有找到正文"))
    }

    @Test
    fun entriesThatClimbOutOfThePackageAreRefused() {
        val reason = failure(zip("../evil.md" to "正文。".toByteArray()))
        assertTrue(reason.contains("不安全的路径"))
    }

    @Test
    fun aMissingPictureIsDroppedWithAWarning() {
        val read = ok(
            zip(
                "post.md" to "正文。\n\n![丢了](images/02.png)".toByteArray(),
            ),
        )
        assertTrue(read.template.images.isEmpty())
        assertEquals(1, read.warnings.size)
        assertTrue(read.warnings.first().contains("images/02.png"))
    }

    @Test
    fun picturesNobodyReferencesAreReported() {
        val read = ok(
            zip(
                "post.md" to "正文。\n\n![正面](images/01.png)".toByteArray(),
                "images/01.png" to byteArrayOf(1),
                "images/02.png" to byteArrayOf(2),
            ),
        )
        assertEquals(1, read.template.images.size)
        assertEquals(1, read.warnings.size)
        assertTrue(read.warnings.first().contains("images/02.png"))
    }

    @Test
    fun anArticlePackageIsRefusedAtParseTime() {
        val reason = failure(zip("post.md" to "---\ntype: article\n---\n正文。".toByteArray()))
        assertTrue(reason.contains("article"))
    }

    @Test
    fun anEmptyFileIsAFailure() {
        assertEquals("这个文件是空的", failure(ByteArray(0)))
    }

    @Test
    fun aBinaryThatIsNotAPackageIsRefused() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00)
        assertTrue(failure(png).contains("不是模板"))
    }
}
