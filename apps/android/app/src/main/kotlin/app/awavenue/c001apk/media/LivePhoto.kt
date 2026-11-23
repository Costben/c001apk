package app.awavenue.c001apk.media

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Coolapk marks the still image of a live photo with `-livepic` in its URL. */
fun String.isLivePhotoUrl(): Boolean = contains("-livepic", ignoreCase = true)

/** Where a live photo's video is played from. */
sealed interface LivePhotoSource {
    val uri: String

    data class Remote(val url: String) : LivePhotoSource {
        override val uri: String get() = url
    }

    data class Local(val file: File) : LivePhotoSource {
        override val uri: String get() = file.toURI().toString()
    }
}

/** Copies the MP4 embedded at the tail of a motion photo out of [source]. */
internal fun extractEmbeddedVideo(source: File, destination: File): File? {
    val info = MotionPhotoParser.parse(source, expectMotionPhoto = true)
    val tailLength = info.embeddedVideoLength ?: return null
    val mp4Offset = source.length() - tailLength
    val copyLength = measureValidMp4Length(source, mp4Offset, tailLength).takeIf { it >= 16L } ?: tailLength
    destination.parentFile?.mkdirs()
    return runCatching {
        copyRange(source, mp4Offset, copyLength, destination)
        destination
    }.getOrElse {
        destination.delete()
        null
    }
}

/**
 * Splits a motion photo into a standalone still image in [stillDestination] and a standalone MP4
 * video in [videoDestination], trimming any non-MP4 trailing vendor bytes from the video.
 */
internal fun splitMotionPhoto(
    source: File,
    stillDestination: File,
    videoDestination: File,
): Boolean {
    val info = MotionPhotoParser.parse(source)
    val tailLength = info.embeddedVideoLength ?: return false
    val splitOffset = source.length() - tailLength
    if (splitOffset <= 0L) return false
    val mp4Length = measureValidMp4Length(source, splitOffset, tailLength)
    if (mp4Length < 16L) return false
    return runCatching {
        stillDestination.parentFile?.mkdirs()
        videoDestination.parentFile?.mkdirs()
        copyRange(source, 0L, splitOffset, stillDestination)
        copyRange(source, splitOffset, mp4Length, videoDestination)
        stillDestination.isFile && stillDestination.length() > 0L &&
            videoDestination.isFile && videoDestination.length() == mp4Length
    }.getOrElse {
        stillDestination.delete()
        videoDestination.delete()
        false
    }
}

/** Walks ISO BMFF top-level boxes starting at [mp4Offset] so trailing non-MP4 data is excluded. */
internal fun measureValidMp4Length(file: File, mp4Offset: Long, maxLength: Long): Long {
    if (mp4Offset < 0L || maxLength < 8L) return 0L
    val limit = minOf(file.length(), mp4Offset + maxLength)
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            var pos = mp4Offset
            var sawFtyp = false
            val header = ByteArray(8)
            while (pos + 8L <= limit) {
                input.seek(pos)
                input.readFully(header)
                val size32 = ((header[0].toLong() and 0xffL) shl 24) or
                    ((header[1].toLong() and 0xffL) shl 16) or
                    ((header[2].toLong() and 0xffL) shl 8) or
                    (header[3].toLong() and 0xffL)
                val typeValid = (4..7).all { idx ->
                    val b = header[idx].toInt() and 0xff
                    b in 0x20..0x7E
                }
                if (!typeValid) break
                val isFtyp = header[4] == 'f'.code.toByte() &&
                    header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() &&
                    header[7] == 'p'.code.toByte()
                if (!sawFtyp) {
                    if (!isFtyp) break
                    sawFtyp = true
                }
                val boxSize = when (size32) {
                    1L -> {
                        if (pos + 16L > limit) break
                        val ext = ByteArray(8)
                        input.readFully(ext)
                        var s = 0L
                        for (b in ext) s = (s shl 8) or (b.toLong() and 0xffL)
                        if (s < 16L) break
                        s
                    }
                    0L -> limit - pos
                    else -> {
                        if (size32 < 8L) break
                        size32
                    }
                }
                if (pos + boxSize > limit) break
                pos += boxSize
            }
            if (sawFtyp) pos - mp4Offset else 0L
        }
    }.getOrDefault(0L)
}

private fun copyRange(source: File, offset: Long, length: Long, destination: File) {
    RandomAccessFile(source, "r").use { input ->
        input.seek(offset)
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0L) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
}

/** JPEG is the only container the exporter can rewrite. */
internal fun File.hasJpegHeader(): Boolean = runCatching {
    inputStream().use { input ->
        val header = ByteArray(2)
        input.read(header) == header.size &&
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte()
    }
}.getOrDefault(false)
