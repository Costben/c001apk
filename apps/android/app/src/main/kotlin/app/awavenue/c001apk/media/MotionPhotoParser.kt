package app.awavenue.c001apk.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

internal data class EmbeddedMediaInfo(
    val hasUltraHdr: Boolean,
    val declaresMotionPhoto: Boolean,
    val embeddedVideoLength: Long?,
    val declaredVideoLength: Long?,
    val presentationTimestampUs: Long?,
) {
    val hasMotionPhoto: Boolean
        get() = declaresMotionPhoto || embeddedVideoLength != null
}

/**
 * Reads the XMP of a motion photo and locates the MP4 some cameras append to the JPEG.
 *
 * The declared length is only trusted when the bytes at that offset really start an MP4 box,
 * because Coolapk also serves live photos whose video lives behind a separate endpoint; those
 * images declare a length but carry no video.
 */
internal object MotionPhotoParser {

    private const val MAX_XMP_SCAN_BYTES = 512 * 1024

    private val itemTagRegex = Regex("""<[^>]+Item:Mime=\"video/mp4\"[^>]*>""")
    private val itemElementRegex =
        Regex("""<[^/>\s]*:Item\b[^>]*>.*?</[^/>\s]*:Item\s*>""", RegexOption.DOT_MATCHES_ALL)
    private val lengthRegex = Regex("""Item:Length=\"(\d+)\"""")
    private val elementLengthRegex =
        Regex("""<[^/>\s]*:Length\s*>\s*(\d+)\s*</[^/>\s]*:Length\s*>""")
    private val microVideoOffsetRegex = Regex("""GCamera:MicroVideoOffset=\"(\d+)\"""")
    private val onePlusVideoLengthRegex = Regex("""OpCamera:VideoLength=\"(\d+)\"""")
    private val presentationTimestampRegex =
        Regex("""(?:GCamera:)?MotionPhotoPresentationTimestampUs=\"(\d+)\"""")

    fun parse(
        file: File,
        expectMotionPhoto: Boolean = false,
    ): EmbeddedMediaInfo {
        if (!file.isFile || file.length() < 16L) {
            return EmbeddedMediaInfo(false, false, null, null, null)
        }

        val xmp = readPrefix(file)
        val hasUltraHdr = xmp.contains("hdrgm:Version", ignoreCase = true)
        val declaresMotionPhoto = expectMotionPhoto ||
            xmp.contains("GCamera:MotionPhoto=\"1\"") ||
            xmp.contains("GCamera:MicroVideo=\"1\"") ||
            xmp.contains("OpCamera:OLivePhotoVersion=\"")
        val declaredVideoLength = findContainerVideoLength(xmp)
            ?: microVideoOffsetRegex.find(xmp)?.groupValues?.get(1)?.toLongOrNull()
            ?: onePlusVideoLengthRegex.find(xmp)?.groupValues?.get(1)?.toLongOrNull()
        val embeddedVideoLength = declaredVideoLength
            ?.takeIf {
                (declaresMotionPhoto || expectMotionPhoto) &&
                    it in 12 until file.length() && hasFtypAtTail(file, it)
            }
            ?: if (declaresMotionPhoto || expectMotionPhoto) {
                findMp4LengthFromTail(file)
            } else if (file.hasJpegHeader()) {
                findMp4LengthFromTail(file, requireFollowingBox = true)
            } else {
                null
            }
        val presentationTimestampUs = presentationTimestampRegex.find(xmp)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

        return EmbeddedMediaInfo(
            hasUltraHdr = hasUltraHdr,
            declaresMotionPhoto = declaresMotionPhoto || embeddedVideoLength != null,
            embeddedVideoLength = embeddedVideoLength,
            declaredVideoLength = declaredVideoLength,
            presentationTimestampUs = presentationTimestampUs,
        )
    }

    private fun readPrefix(file: File): String {
        val length = minOf(file.length(), MAX_XMP_SCAN_BYTES.toLong()).toInt()
        val bytes = ByteArray(length)
        RandomAccessFile(file, "r").use { input ->
            input.readFully(bytes)
        }
        return String(bytes, StandardCharsets.ISO_8859_1)
    }

    private fun findContainerVideoLength(xmp: String): Long? {
        itemTagRegex.find(xmp)?.value?.let { tag ->
            lengthRegex.find(tag)?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        }
        return itemElementRegex.findAll(xmp)
            .firstOrNull { it.value.contains("video/mp4") }
            ?.let { match ->
                lengthRegex.find(match.value)?.groupValues?.get(1)?.toLongOrNull()
                    ?: elementLengthRegex.find(match.value)?.groupValues?.get(1)?.toLongOrNull()
            }
    }

    private fun hasFtypAtTail(file: File, videoLength: Long): Boolean {
        val offset = file.length() - videoLength
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset + 4L)
                val type = ByteArray(4)
                input.readFully(type)
                String(type, StandardCharsets.US_ASCII) == "ftyp"
            }
        }.getOrDefault(false)
    }

    private fun findMp4LengthFromTail(
        file: File,
        requireFollowingBox: Boolean = false,
    ): Long? = runCatching {
        val scanLength = minOf(file.length(), 64L * 1024 * 1024).toInt()
        val scanOffset = file.length() - scanLength
        val bytes = ByteArray(scanLength)
        RandomAccessFile(file, "r").use { input ->
            input.seek(scanOffset)
            input.readFully(bytes)
        }
        for (index in 4 until bytes.size - 4) {
            if (bytes[index] == 'f'.code.toByte() &&
                bytes[index + 1] == 't'.code.toByte() &&
                bytes[index + 2] == 'y'.code.toByte() &&
                bytes[index + 3] == 'p'.code.toByte()
            ) {
                val boxSize = ((bytes[index - 4].toInt() and 0xff) shl 24) or
                    ((bytes[index - 3].toInt() and 0xff) shl 16) or
                    ((bytes[index - 2].toInt() and 0xff) shl 8) or
                    (bytes[index - 1].toInt() and 0xff)
                if (boxSize in 12..4096) {
                    val ftypStart = index - 4
                    if (requireFollowingBox) {
                        val nextBoxOffset = ftypStart + boxSize
                        if (nextBoxOffset + 8 > bytes.size) continue
                        val nextType = String(bytes, nextBoxOffset + 4, 4, StandardCharsets.US_ASCII)
                        if (nextType !in MP4_TOP_LEVEL_BOXES) continue
                    }
                    val mp4Offset = scanOffset + ftypStart.toLong()
                    return@runCatching file.length() - mp4Offset
                }
            }
        }
        null
    }.getOrNull()

    private val MP4_TOP_LEVEL_BOXES = setOf("moov", "mdat", "free", "wide", "skip", "uuid", "meta")
}
