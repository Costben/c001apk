package app.awavenue.c001apk.data

import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * What the importer reads: either a bare document, or a zip that carries the document and the
 * pictures it shows side by side.
 *
 * A zip is the shape a writer actually hands over — one file with the text, the screenshots and the
 * `type` line inside it — so the importer unpacks it, finds the document, and rewrites the picture
 * paths to the copies it just wrote. Everything it had to change is reported in [Ok.warnings].
 */
sealed interface LongPostPackage {
    /**
     * [document] is the template text as it was read (for the editor field), [template] is what it
     * parses to, and [baseDir] is where the zip was unpacked — kept so a re-parse after editing
     * still resolves the relative picture paths.
     */
    data class Ok(
        val document: String,
        val template: LongPostTemplate,
        val warnings: List<String>,
        val baseDir: File?,
    ) : LongPostPackage

    data class Failed(val reason: String) : LongPostPackage
}

/** The package's limits. A long post is nine pictures and a few thousand words; this is generous. */
object LongPostPackageSpec {
    const val MAX_PACKAGE_BYTES = 32 * 1024 * 1024
    const val MAX_ENTRY_BYTES = 8 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64 * 1024 * 1024
    const val MAX_ENTRIES = 64

    /** The document inside a zip may carry any of these names. */
    val DOCUMENT_SUFFIXES = listOf(".md", ".markdown", ".txt")

    /** A package that follows the spec names its document this, so it wins when there are several. */
    const val DOCUMENT_NAME = "post.md"
}

/** Zip local-file-header and empty-archive signatures. A plain document never starts like this. */
fun isZipArchive(bytes: ByteArray): Boolean {
    if (bytes.size < 4) return false
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    val b2 = bytes[2].toInt() and 0xFF
    val b3 = bytes[3].toInt() and 0xFF
    if (b0 != 0x50 || b1 != 0x4B) return false
    return (b2 == 0x03 && b3 == 0x04) || (b2 == 0x05 && b3 == 0x06)
}

/**
 * Reads a template package.
 *
 * A document on its own is parsed as is. A zip is unpacked into [workDir] (which is cleared first),
 * its document is parsed, and the pictures it names are pointed at the unpacked copies. Pictures that
 * cannot be found are dropped with a warning rather than failing the whole import — the words are
 * the part that took the writer time.
 */
fun readLongPostPackage(bytes: ByteArray, fileName: String, workDir: File): LongPostPackage {
    if (bytes.isEmpty()) return LongPostPackage.Failed("这个文件是空的")
    if (bytes.size > LongPostPackageSpec.MAX_PACKAGE_BYTES) {
        return LongPostPackage.Failed(
            "文件 ${bytes.size / 1024 / 1024}MB，超过 ${LongPostPackageSpec.MAX_PACKAGE_BYTES / 1024 / 1024}MB 上限",
        )
    }

    if (!isZipArchive(bytes)) {
        // Anything can be picked now that the filter is `*/*`: a binary that is not a package reads
        // as garbage text, so it is refused here instead of turning into a nonsense draft.
        if (bytes.any { it == 0.toByte() }) {
            return LongPostPackage.Failed("这个文件不是模板，需要 .md / .txt 文本或者一个压缩包")
        }
        val document = bytes.toString(Charsets.UTF_8)
        return when (val parsed = parseLongPostTemplate(document)) {
            is TemplateParse.Failed -> LongPostPackage.Failed(parsed.reason)
            is TemplateParse.Ok -> LongPostPackage.Ok(document, parsed.template, parsed.warnings, null)
        }
    }

    val unpacked = unpack(bytes, fileName, workDir)
    if (unpacked is Unpack.Failed) return LongPostPackage.Failed(unpacked.reason)
    val directory = (unpacked as Unpack.Ok).directory

    val documents = directory.walkTopDown()
        .filter { it.isFile && LongPostPackageSpec.DOCUMENT_SUFFIXES.any { suffix ->
            it.name.lowercase().endsWith(suffix)
        } }
        .toList()
    val document = when {
        documents.isEmpty() -> return LongPostPackage.Failed(
            "压缩包里没有找到正文，需要放一个 ${LongPostPackageSpec.DOCUMENT_NAME}",
        )
        documents.size == 1 -> documents.first()
        else -> documents.firstOrNull {
            it.name.equals(LongPostPackageSpec.DOCUMENT_NAME, ignoreCase = true)
        } ?: return LongPostPackage.Failed(
            "压缩包里有 ${documents.size} 份正文（${documents.joinToString("、") { it.name }}），" +
                "只留一份，或者把要用的那份改名成 ${LongPostPackageSpec.DOCUMENT_NAME}",
        )
    }

    val text = runCatching { document.readText() }.getOrNull()
    if (text.isNullOrBlank()) return LongPostPackage.Failed("压缩包里的 ${document.name} 读不出内容")

    return when (val parsed = parseLongPostTemplate(text)) {
        is TemplateParse.Failed -> LongPostPackage.Failed(parsed.reason)
        is TemplateParse.Ok -> {
            val (template, warnings) = resolvePackageImages(parsed.template, directory)
            LongPostPackage.Ok(text, template, parsed.warnings + warnings, directory)
        }
    }
}

/**
 * Points the template's relative pictures at the copies inside [baseDir].
 *
 * A picture written as `images/01.png` is a name, not a path on this device: it only means something
 * relative to the package. Absolute paths and links are left alone, because the writer meant those
 * literally. Pictures that are missing, or that try to reach outside the package, are dropped.
 */
fun resolvePackageImages(template: LongPostTemplate, baseDir: File): Pair<LongPostTemplate, List<String>> {
    val warnings = mutableListOf<String>()
    val root = runCatching { baseDir.canonicalFile }.getOrDefault(baseDir)
    val resolved = mutableListOf<TemplateImage>()

    template.images.forEachIndexed { index, image ->
        if (image.isRemote || File(image.source).isAbsolute) {
            resolved += image
            return@forEachIndexed
        }
        val relative = image.source.removePrefix("./")
        val file = File(root, relative)
        val canonical = runCatching { file.canonicalFile }.getOrNull()
        val inside = canonical != null && canonical.path.startsWith(root.path + File.separator)
        if (canonical == null || !inside || !canonical.isFile) {
            warnings += "第 ${index + 1} 张图 ${image.source} 在压缩包里找不到，已跳过"
            return@forEachIndexed
        }
        resolved += image.copy(source = canonical.absolutePath)
    }

    val referenced = resolved.map { it.source }.toSet()
    val unused = root.walkTopDown()
        .filter { it.isFile }
        .filter { file -> LongPostPackageSpec.DOCUMENT_SUFFIXES.none { file.name.lowercase().endsWith(it) } }
        .filter { it.absolutePath !in referenced }
        .map { it.relativeTo(root).path }
        .toList()
    if (unused.isNotEmpty()) {
        warnings += "压缩包里的 ${unused.joinToString("、")} 没有被正文引用，已忽略"
    }

    return template.copy(images = resolved) to warnings
}

// ---- unpacking ------------------------------------------------------------------------------

private sealed interface Unpack {
    data class Ok(val directory: File) : Unpack
    data class Failed(val reason: String) : Unpack
}

/**
 * Writes the archive's entries into [workDir].
 *
 * Entry names are untrusted: an archive can carry `../../evil` or an absolute path and land the file
 * outside the cache, so every name is resolved and checked against the directory before it is used.
 */
private fun unpack(bytes: ByteArray, fileName: String, workDir: File): Unpack {
    runCatching { workDir.deleteRecursively() }
    if (!workDir.mkdirs() && !workDir.isDirectory) {
        return Unpack.Failed("解压目录建不出来，${fileName} 读不了")
    }
    val root = workDir.canonicalFile

    var entries = 0
    var total = 0L
    val buffer = ByteArray(16 * 1024)

    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = try {
                zip.nextEntry
            } catch (error: Exception) {
                return Unpack.Failed("压缩包解不开：${error.message ?: "格式不对"}")
            } ?: break
            if (entry.isDirectory) continue
            if (++entries > LongPostPackageSpec.MAX_ENTRIES) {
                return Unpack.Failed("压缩包里文件超过 ${LongPostPackageSpec.MAX_ENTRIES} 个")
            }

            val name = entry.name.replace('\\', '/').trimStart('/')
            if (name.isEmpty() || name.split('/').any { it == ".." }) {
                return Unpack.Failed("压缩包里有不安全的路径 ${entry.name}")
            }
            val target = File(root, name).canonicalFile
            if (!target.path.startsWith(root.path + File.separator)) {
                return Unpack.Failed("压缩包里有不安全的路径 ${entry.name}")
            }
            target.parentFile?.mkdirs()

            var written = 0L
            target.outputStream().use { output ->
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    written += read
                    total += read
                    if (written > LongPostPackageSpec.MAX_ENTRY_BYTES) {
                        return Unpack.Failed("压缩包里的 $name 超过 8MB")
                    }
                    if (total > LongPostPackageSpec.MAX_TOTAL_BYTES) {
                        return Unpack.Failed("压缩包解压后超过 64MB")
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
    }
    return Unpack.Ok(root)
}
