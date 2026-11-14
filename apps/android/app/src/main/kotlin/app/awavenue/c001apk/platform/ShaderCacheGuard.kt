package app.awavenue.c001apk.platform

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.RandomAccessFile

/**
 * Drops the HWUI shader disk cache once its Vulkan pipeline blob has grown to the point where HWUI
 * refuses to write it back.
 *
 * HWUI keeps the whole Adreno pipeline cache as one value, and rejects any value at or above 6 MiB;
 * the rejected attempt also skips the bookkeeping that would stop it from retrying, so the cache
 * freezes on whatever snapshot it had and every later cold start has to recompile the pipelines that
 * arrived after it. The file is validated against a device fingerprint rather than the app version,
 * so pipelines from earlier UI revisions pile up in it until the blob crosses that limit.
 */
internal object ShaderCacheGuard {
    private const val FILE_NAME = "com.android.skia.shaders_cache"
    private const val MAGIC = 0x5F426224L
    private const val VERSION = 3L
    private const val BLOB_KEY = 1L

    /** 1 MiB below the hard 6 MiB limit: the blob keeps growing while the process runs. */
    private const val LIMIT = 5L * 1024 * 1024

    fun resetIfSaturated(context: Context) {
        runCatching {
            val file = cacheFile(context)
            if (!file.isFile || file.length() < LIMIT) return
            if (pipelineBlobSize(file) >= LIMIT) file.delete()
        }
    }

    private fun cacheFile(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createDeviceProtectedStorageContext().cacheDir
        } else {
            context.cacheDir
        }
        return File(dir, FILE_NAME)
    }

    /** Byte count of the Vulkan pipeline entry, or -1 when the file does not parse. */
    private fun pipelineBlobSize(file: File): Long = RandomAccessFile(file, "r").use { raf ->
        val word = if (Process.is64Bit()) 8 else 4
        // struct Header { u32 magic; u32 version; u32 deviceVersion; size_t entries; int buildIdLen; char[] buildId; }
        val headerSize = if (word == 8) 32 else 20
        val length = raf.length()
        if (length < headerSize) return@use -1L
        if (raf.uint(8, 4) != MAGIC || raf.uint(12, 4) != VERSION) return@use -1L

        val entries = raf.uint(8L + word * 2, word)
        val buildIdLength = raf.uint(8L + word * 2 + word, 4)
        if (entries <= 0 || entries > 100_000 || buildIdLength > 4096) return@use -1L

        var offset = 8 + align4(headerSize + buildIdLength)
        repeat(entries.toInt()) {
            if (offset + word * 2 > length) return@use -1L
            val keySize = raf.uint(offset, word)
            val valueSize = raf.uint(offset + word, word)
            if (keySize <= 0 || valueSize <= 0) return@use -1L
            val data = offset + word * 2
            if (data + keySize + valueSize > length) return@use -1L
            if (keySize == 4L && raf.uint(data, 4) == BLOB_KEY) return@use valueSize
            offset += align4(word * 2 + keySize + valueSize)
        }
        -1L
    }

    private fun align4(value: Long): Long = (value + 3) and -4L

    private fun RandomAccessFile.uint(offset: Long, words: Int): Long {
        seek(offset)
        return if (words == 8) java.lang.Long.reverseBytes(readLong()) else Integer.toUnsignedLong(Integer.reverseBytes(readInt()))
    }
}
