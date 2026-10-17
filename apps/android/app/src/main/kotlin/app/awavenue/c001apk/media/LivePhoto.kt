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
    val length = info.embeddedVideoLength ?: return null
    destination.parentFile?.mkdirs()
    return runCatching {
        RandomAccessFile(source, "r").use { input ->
            input.seek(source.length() - length)
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        destination
    }.getOrElse {
        destination.delete()
        null
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
