package app.awavenue.c001apk.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

class MotionPhotoTest {

    @Test
    fun externalMotionPhotoKeepsDeclaredVideoMetadata() {
        val file = temporaryFile(
            """
            <rdf:Description GCamera:MotionPhoto="1"
                GCamera:MotionPhotoVersion="1"
                GCamera:MotionPhotoPresentationTimestampUs="1433276" />
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto"
                Item:Length="6606384" />
            """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1),
        )

        val result = MotionPhotoParser.parse(file)

        assertTrue(result.declaresMotionPhoto)
        assertTrue(result.hasMotionPhoto)
        assertEquals(6_606_384L, result.declaredVideoLength)
        assertNull(result.embeddedVideoLength)
        assertEquals(1_433_276L, result.presentationTimestampUs)
        file.delete()
    }

    @Test
    fun embeddedMotionPhotoRequiresAnMp4AtTheDeclaredTail() {
        val video = mp4Bytes()
        val xmp = """
            <rdf:Description GCamera:MotionPhoto="1" />
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto"
                Item:Length="${video.size}" />
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)
        val file = temporaryFile(xmp + video)

        val result = MotionPhotoParser.parse(file)

        assertEquals(video.size.toLong(), result.declaredVideoLength)
        assertEquals(video.size.toLong(), result.embeddedVideoLength)
        file.delete()
    }

    @Test
    fun ultraHdrDoesNotImplyMotionPhoto() {
        val file = temporaryFile(
            "<rdf:Description hdrgm:Version=\"1.0\" />padding"
                .toByteArray(StandardCharsets.ISO_8859_1),
        )

        val result = MotionPhotoParser.parse(file)

        assertTrue(result.hasUltraHdr)
        assertFalse(result.hasMotionPhoto)
        file.delete()
    }

    @Test
    fun livePhotoUrlHintSupportsExternalVideoWithoutXmp() {
        val file = temporaryFile(ByteArray(32) { it.toByte() })

        val result = MotionPhotoParser.parse(file, expectMotionPhoto = true)

        assertTrue(result.declaresMotionPhoto)
        assertTrue(result.hasMotionPhoto)
        assertNull(result.declaredVideoLength)
        assertNull(result.embeddedVideoLength)
        file.delete()
    }

    @Test
    fun imageWithoutGainMapMetadataIsNotUltraHdr() {
        val file = temporaryFile(ByteArray(32) { it.toByte() })

        val result = MotionPhotoParser.parse(file)

        assertFalse(result.hasUltraHdr)
        assertFalse(result.hasMotionPhoto)
        file.delete()
    }

    @Test
    fun onlyLivePicUrlsCountAsLivePhotos() {
        assertTrue("https://image.coolapk.com/feed/x-livepic.jpg".isLivePhotoUrl())
        assertTrue("https://image.coolapk.com/feed/x-LIVEPIC.jpg".isLivePhotoUrl())
        assertFalse("https://image.coolapk.com/feed/x-uhdr.jpg".isLivePhotoUrl())
        assertFalse("https://image.coolapk.com/feed/x.jpg".isLivePhotoUrl())
    }

    @Test
    fun externalVideoIsExportedAsColorOsCompatibleMotionPhoto() {
        val image = temporaryFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        val video = temporaryFile(mp4Bytes("mp42"), ".mp4")
        val output = File.createTempFile("motion-photo-export-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(image, video, output, 1_433_276L)

            val parsed = MotionPhotoParser.parse(output)
            val raw = output.readBytes()
            val text = raw.toString(Charsets.ISO_8859_1)
            assertTrue(parsed.hasMotionPhoto)
            assertEquals(video.length(), parsed.declaredVideoLength)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertEquals(1_433_276L, parsed.presentationTimestampUs)
            assertTrue(text.contains("GCamera:MotionPhoto=\"1\""))
            assertTrue(text.contains("rdf:about=\"\""))
            assertTrue(text.contains("Item:Length=\"${video.length()}\""))
            assertFalse(text.contains("GCamera:MicroVideo"))
            val xmpPos = text.indexOf("http://ns.adobe.com/xap/1.0/")
            val dqtPos = (0 until raw.size - 1).first { raw[it] == 0xff.toByte() && raw[it + 1] == 0xdb.toByte() }
            assertTrue("XMP should precede DQT: xmp=$xmpPos dqt=$dqtPos", xmpPos in 1 until dqtPos)
            assertEquals(0, text.split("xmlns:GCamera=").size - 2)
        } finally {
            image.delete()
            video.delete()
            output.delete()
        }
    }

    @Test
    fun preservesUltraHdrGainMapWhenAddingMotionPhotoMetadata() {
        val gainMapTail = Base64.getDecoder().decode(ONE_PIXEL_JPEG)
        val xmp = """
            <x:xmpmeta xmlns:x="adobe:ns:meta/">
              <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description
                    xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
                    xmlns:Container="http://ns.google.com/photos/1.0/container/"
                    xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
                    hdrgm:Version="1.0">
                  <Container:Directory>
                    <rdf:Seq>
                      <rdf:li rdf:parseType="Resource">
                        <Container:Item Item:Mime="image/jpeg" Item:Semantic="Primary" Item:Length="0" Item:Padding="0"/>
                      </rdf:li>
                      <rdf:li rdf:parseType="Resource">
                        <Container:Item Item:Mime="image/jpeg" Item:Semantic="GainMap" Item:Length="${gainMapTail.size}" Item:Padding="0"/>
                      </rdf:li>
                    </rdf:Seq>
                  </Container:Directory>
                </rdf:Description>
              </rdf:RDF>
            </x:xmpmeta>
        """.trimIndent()
        val image = temporaryFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG).withHeaderXmp(xmp) + gainMapTail, ".jpg")
        val video = temporaryFile(mp4Bytes("isom"), ".mp4")
        val output = File.createTempFile("motion-photo-hdr-export-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(image, video, output, 0L)

            val exportedBytes = output.readBytes().toString(Charsets.ISO_8859_1)
            val parsed = MotionPhotoParser.parse(output)
            assertTrue(exportedBytes.contains("hdrgm:Version=\"1.0\""))
            assertTrue(parsed.hasUltraHdr)
            assertTrue(parsed.hasMotionPhoto)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertTrue(exportedBytes.contains("Item:Semantic=\"GainMap\""))
            assertTrue(exportedBytes.contains("Item:Length=\"${gainMapTail.size}\""))
            assertTrue(exportedBytes.contains("Item:Semantic=\"MotionPhoto\""))
            assertTrue(exportedBytes.contains("Item:Length=\"${video.length()}\""))
        } finally {
            image.delete()
            video.delete()
            output.delete()
        }
    }

    @Test
    fun alreadyEmbeddedMotionPhotoIsKeptWithoutDuplicatingTail() {
        val baseImage = temporaryFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        val video = temporaryFile(mp4Bytes("mp42"), ".mp4")
        val embedded = File.createTempFile("motion-photo-embedded-", ".jpg").apply { delete() }
        val reexported = File.createTempFile("motion-photo-reexport-", ".jpg").apply { delete() }

        try {
            MotionPhotoExporter.export(baseImage, video, embedded, 500_000L)
            MotionPhotoExporter.export(embedded, null, reexported, null)

            assertEquals(embedded.length(), reexported.length())
            val parsed = MotionPhotoParser.parse(reexported)
            assertEquals(video.length(), parsed.embeddedVideoLength)
            assertEquals(500_000L, parsed.presentationTimestampUs)
        } finally {
            baseImage.delete()
            video.delete()
            embedded.delete()
            reexported.delete()
        }
    }

    @Test
    fun detectsFtypMajorBrandsCorrectly() {
        val mov = temporaryFile(mp4Bytes("qt  "), ".mov")
        val mp4 = temporaryFile(mp4Bytes("mp42"), ".mp4")
        val jpg = temporaryFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG), ".jpg")
        try {
            assertTrue(MotionPhotoParser.isIsoBmff(mov))
            assertEquals("qt  ", MotionPhotoParser.ftypMajorBrand(mov))
            assertTrue(MotionPhotoParser.isIsoBmff(mp4))
            assertEquals("mp42", MotionPhotoParser.ftypMajorBrand(mp4))
            assertFalse(MotionPhotoParser.isIsoBmff(jpg))
            assertNull(MotionPhotoParser.ftypMajorBrand(jpg))
            assertTrue(MotionPhotoParser.hasJpegHeader(jpg))
            assertFalse(MotionPhotoParser.hasJpegHeader(mov))
        } finally {
            mov.delete()
            mp4.delete()
            jpg.delete()
        }
    }

    @Test
    fun splitMotionPhotoSeparatesStillAndTrimsNonMp4VendorTrailer() {
        val jpegWithXmp = Base64.getDecoder().decode(ONE_PIXEL_JPEG)
        val cleanMp4 = mp4Bytes() + mp4Box("moov", ByteArray(12) { 0x11 })
        val vendorTrailer = byteArrayOf(0x9a.toByte(), 0x99.toByte(), 0x99.toByte(), 0x3f, 0x01, 0x02)
        val totalTail = cleanMp4 + vendorTrailer
        val xmp = """
            <rdf:Description OpCamera:OLivePhotoVersion="2" OpCamera:VideoLength="${cleanMp4.size}" />
            <Container:Item Item:Mime="video/mp4" Item:Semantic="MotionPhoto" Item:Length="${totalTail.size}" />
        """.trimIndent()
        val stillExpected = jpegWithXmp.withHeaderXmp(xmp)
        val source = temporaryFile(stillExpected + totalTail, ".jpg")
        val stillOut = File.createTempFile("motion-photo-still-", ".jpg")
        val videoOut = File.createTempFile("motion-photo-vid-", ".mp4")

        val splitOk = splitMotionPhoto(source, stillOut, videoOut)

        assertTrue(splitOk)
        assertTrue(stillExpected.contentEquals(stillOut.readBytes()))
        assertTrue(cleanMp4.contentEquals(videoOut.readBytes()))
        assertEquals(cleanMp4.size.toLong(), measureValidMp4Length(source, stillExpected.size.toLong(), totalTail.size.toLong()))

        source.delete()
        stillOut.delete()
        videoOut.delete()
    }

    @Test
    fun childElementXmpAndXmpLessTailMp4AreDetected() {
        val cleanMp4 = mp4Bytes() + mp4Box("moov", ByteArray(8) { 0x22 })
        val xmpChild = """
            <rdf:Description GCamera:MotionPhoto="1" />
            <Container:Item>
                <Item:Mime>video/mp4</Item:Mime>
                <Item:Length>${cleanMp4.size}</Item:Length>
            </Container:Item>
        """.trimIndent().toByteArray(StandardCharsets.ISO_8859_1)
        val childFile = temporaryFile(xmpChild + cleanMp4, ".jpg")
        val childParsed = MotionPhotoParser.parse(childFile)
        assertEquals(cleanMp4.size.toLong(), childParsed.declaredVideoLength)
        assertEquals(cleanMp4.size.toLong(), childParsed.embeddedVideoLength)

        val xmpLessFile = temporaryFile(Base64.getDecoder().decode(ONE_PIXEL_JPEG) + cleanMp4, ".jpg")
        val xmpLessParsed = MotionPhotoParser.parse(xmpLessFile)
        assertTrue(xmpLessParsed.hasMotionPhoto)
        assertEquals(cleanMp4.size.toLong(), xmpLessParsed.embeddedVideoLength)

        childFile.delete()
        xmpLessFile.delete()
    }

    private fun temporaryFile(bytes: ByteArray, suffix: String = ".jpg"): File =
        File.createTempFile("motion-photo-test-", suffix).apply { writeBytes(bytes) }

    private fun mp4Bytes(brand: String = "mp42"): ByteArray {
        val brandBytes = brand.toByteArray(StandardCharsets.US_ASCII)
        require(brandBytes.size == 4)
        return byteArrayOf(
            0, 0, 0, 16,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            brandBytes[0], brandBytes[1], brandBytes[2], brandBytes[3],
            0, 0, 0, 0,
        )
    }

    private fun mp4Box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
            type[0].code.toByte(),
            type[1].code.toByte(),
            type[2].code.toByte(),
            type[3].code.toByte(),
        ) + payload
    }

    private fun ByteArray.withHeaderXmp(xmp: String): ByteArray {
        val payload = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII) +
            xmp.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { stream ->
            stream.write(this, 0, 2)
            stream.writeByte(0xff)
            stream.writeByte(0xe1)
            stream.writeShort(payload.size + 2)
            stream.write(payload)
            stream.write(this, 2, size - 2)
        }
        return out.toByteArray()
    }

    private companion object {
        const val ONE_PIXEL_JPEG =
            "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8U" +
                "HRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwL" +
                "DBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy" +
                "MjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAA" +
                "AAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEG" +
                "E1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RF" +
                "RkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKj" +
                "pKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP0" +
                "9fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgEC" +
                "BAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLR" +
                "ChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0" +
                "dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbH" +
                "yMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwD5/ooo" +
                "oA//2Q=="
    }
}
