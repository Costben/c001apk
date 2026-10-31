package app.awavenue.c001apk.ui.drafts

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Draft
import app.awavenue.c001apk.data.DraftImage
import app.awavenue.c001apk.data.LongPostPackage
import app.awavenue.c001apk.data.LongPostPackageSpec
import app.awavenue.c001apk.data.LongPostTemplate
import app.awavenue.c001apk.data.LongPostTemplateSpec
import app.awavenue.c001apk.data.TemplateImage
import app.awavenue.c001apk.data.TemplateParse
import app.awavenue.c001apk.data.parseLongPostTemplate
import app.awavenue.c001apk.data.readLongPostPackage
import app.awavenue.c001apk.data.resolvePackageImages
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.reply.ReplyComposerSheet
import app.awavenue.c001apk.ui.feed.reply.ReplyTarget
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Download
import io.github.suqi8.coui.kmp.icon.extended.Notes
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The long-post template importer: a document written to [LongPostTemplateSpec] is read here, shown
 * as the post it will become, and then either filed in the draft box or handed to the composer.
 *
 * Pictures are the only part that needs work before that: a template points at a path or a URL, and
 * the composer wants the file, its size and its hash — the same three things the photo picker hands
 * over — so the importer fetches and describes them up front instead of at publish time.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
fun TemplateImportScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("template-import")
    val backdrop = rememberGlassBackdrop()

    var source by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<TemplateParse?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Draft?>(null) }

    /** Where a zip was unpacked; relative picture paths in [source] resolve against it. */
    var baseDir by remember { mutableStateOf<File?>(null) }

    // Any MIME type on purpose: the system picker's MIME matching hides a .zip from `text/*` and, on
    // the ColorOS build this was tried on, from `application/zip` too — filtering would make the
    // package unreachable. The reader decides by signature instead: zip magic, else text.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "模板"
        scope.launch {
            progress = "正在读取 $name"
            val read = withContext(Dispatchers.IO) {
                val bytes = readPackageBytes(context, uri)
                if (bytes == null) null else readLongPostPackage(bytes, name, File(context.cacheDir, "longpost"))
            }
            progress = null
            when (read) {
                null -> context.toast("这个文件读不出内容")
                is LongPostPackage.Failed -> result = TemplateParse.Failed(read.reason)
                is LongPostPackage.Ok -> {
                    source = read.document
                    baseDir = read.baseDir
                    result = TemplateParse.Ok(read.template, read.warnings)
                    context.toast(
                        if (read.baseDir == null) "已读取模板" else "已从压缩包读取 ${read.template.images.size} 张图",
                    )
                }
            }
        }
    }

    /** Parses what is in the field, resolving relative pictures against the package they came from. */
    fun reparse() {
        val parsed = parseLongPostTemplate(source)
        val directory = baseDir
        result = if (parsed is TemplateParse.Ok && directory != null) {
            val (template, warnings) = resolvePackageImages(parsed.template, directory)
            TemplateParse.Ok(template, parsed.warnings + warnings)
        } else {
            parsed
        }
    }

    /** Fetches, describes and stages the template's pictures; unreadable ones are reported, not fatal. */
    suspend fun stage(template: LongPostTemplate): List<DraftImage> {
        val staged = mutableListOf<DraftImage>()
        template.images.forEachIndexed { index, image ->
            progress = "正在准备图片 ${index + 1}/${template.images.size}"
            val bytes = withContext(Dispatchers.IO) { readSource(context, image) }
            if (bytes == null) {
                context.toast("第 ${index + 1} 张图片读不到，已跳过")
                return@forEachIndexed
            }
            val described = withContext(Dispatchers.IO) { describeBytes(context, bytes, image.source) }
            if (described == null) {
                context.toast("第 ${index + 1} 张图片不是能上传的图片，已跳过")
                return@forEachIndexed
            }
            staged += described
        }
        return staged
    }

    fun draftOf(template: LongPostTemplate, images: List<DraftImage>) = Draft(
        id = UUID.randomUUID().toString(),
        text = template.message,
        images = images,
        replyId = "",
        replyType = "feed",
        targetType = "",
        targetId = "",
        title = "",
        feedType = "feed",
        onlySelf = template.onlySelf,
        updatedAt = System.currentTimeMillis(),
    )

    fun saveToDrafts(template: LongPostTemplate) {
        scope.launch {
            val images = stage(template)
            progress = "正在写入草稿箱"
            val saved = runCatching {
                app.drafts.save(
                    id = UUID.randomUUID().toString(),
                    text = template.message,
                    images = images,
                    onlySelf = template.onlySelf,
                )
            }.getOrNull()
            progress = null
            if (saved == null) {
                context.toast("草稿保存失败")
            } else {
                context.toast("已保存到草稿箱")
                navigator.pop()
            }
        }
    }

    fun openComposer(template: LongPostTemplate) {
        scope.launch {
            val images = stage(template)
            progress = null
            editing = draftOf(template, images)
        }
    }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "长文模板导入",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(
                Modifier
                    .fillMaxSize()
                    .glassSource(backdrop)
                    .overScrollVertical()
                    .verticalScroll(rememberPageScrollState("template-import"))
                    .padding(
                        start = 10.dp,
                        top = innerPadding.calculateTopPadding() + 10.dp,
                        end = 10.dp,
                        bottom = innerPadding.calculateBottomPadding() + 10.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SpecCard()

                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    TextField(
                        value = source,
                        onValueChange = {
                            source = it
                            result = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = "模板内容",
                        minLines = 6,
                        maxLines = 12,
                        showClearButton = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pickFile.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f).height(44.dp),
                        ) {
                            Icon(COUIIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入文件")
                        }
                        Button(
                            onClick = { reparse() },
                            enabled = source.isNotBlank(),
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text("解析预览")
                        }
                    }
                }

                when (val parsed = result) {
                    null -> Text(
                        "贴一份按规范写好的模板，或者导入一个压缩包（正文 + 图片 + type）。",
                        modifier = Modifier.padding(horizontal = 6.dp),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )

                    is TemplateParse.Failed -> Card(
                        Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            parsed.reason,
                            style = COUITheme.textStyles.body2,
                            color = COUITheme.colorScheme.error,
                        )
                    }

                    is TemplateParse.Ok -> TemplatePreview(
                        template = parsed.template,
                        warnings = parsed.warnings,
                        onSave = { saveToDrafts(parsed.template) },
                        onPublish = { openComposer(parsed.template) },
                    )
                }
            }
        }
    }

    editing?.let { draft ->
        ReplyComposerSheet(
            target = ReplyTarget(),
            draft = draft,
            onDismiss = { editing = null },
            onPosted = {
                editing = null
                navigator.pop()
            },
        )
    }

    OverlayLoadingDialog(show = progress != null, text = progress)
}

/** The spec, as the importer applies it: the header keys, the picture rule and the body. */
@Composable
private fun SpecCard() {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                COUIIcons.Notes,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(8.dp))
            Text("模板规范", style = COUITheme.textStyles.body2)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "头部可选，认 title / visibility(public|self) / topics / type(feed|article)；" +
                "正文空行分段，图片写成 ![说明](路径或链接)，按出现顺序进图集（最多 " +
                "${LongPostTemplateSpec.MAX_IMAGES} 张）；标题作正文首行，话题追加在末尾。",
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "压缩包：放一份 ${LongPostPackageSpec.DOCUMENT_NAME}，图片放进去，正文里写相对路径即可，" +
                "比如 ![外观](images/01.png)；整个包最多 " +
                "${LongPostPackageSpec.MAX_PACKAGE_BYTES / 1024 / 1024}MB。" +
                "目前只能发动态，type 写 article 会被拒绝。",
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            LongPostTemplateSpec.DOCUMENT,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .padding(8.dp),
            style = COUITheme.textStyles.footnote2.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

/** The parsed template as the post it will become, with the count and the pictures it carries. */
@Composable
private fun TemplatePreview(
    template: LongPostTemplate,
    warnings: List<String>,
    onSave: () -> Unit,
    onPublish: () -> Unit,
) {
    val overLimit = template.charCount > LongPostTemplateSpec.MAX_MESSAGE
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (template.title.isEmpty()) "预览" else template.title,
                modifier = Modifier.weight(1f),
                style = COUITheme.textStyles.body1,
                maxLines = 2,
            )
            Text(
                "${template.charCount}/${LongPostTemplateSpec.MAX_MESSAGE}",
                style = COUITheme.textStyles.footnote1,
                color = if (overLimit) {
                    COUITheme.colorScheme.error
                } else {
                    COUITheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(if (template.onlySelf) "仅自己可见" else "所有人可见")
                append(" · 图片 ${template.images.size}/${LongPostTemplateSpec.MAX_IMAGES}")
                if (template.topics.isNotEmpty()) append(" · #${template.topics.joinToString(" #")}#")
            },
            style = COUITheme.textStyles.footnote1,
            color = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        warnings.forEach { warning ->
            Spacer(Modifier.height(4.dp))
            Text(warning, style = COUITheme.textStyles.footnote1, color = COUITheme.colorScheme.error)
        }
        Spacer(Modifier.height(10.dp))
        Text(template.message, style = COUITheme.textStyles.body2)
        if (template.images.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                template.images.forEach { image ->
                    AsyncImage(
                        model = if (image.isRemote) image.source else File(image.source),
                        contentDescription = image.caption.ifEmpty { null },
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSave,
                enabled = !overLimit,
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Text("存到草稿箱")
            }
            Button(
                onClick = onPublish,
                enabled = !overLimit,
                modifier = Modifier.weight(1f).height(44.dp),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("去发布")
            }
        }
    }
}

// ---- pictures -------------------------------------------------------------------------------

private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024

/**
 * Reads the picked file whole, refusing anything larger than a package may be.
 *
 * The document picker hands over a stream, not a file: a template may come from a chat app's cache
 * or a cloud provider, so the size is counted as it is read rather than asked for up front.
 */
private fun readPackageBytes(context: Context, uri: Uri): ByteArray? = runCatching {
    val limit = LongPostPackageSpec.MAX_PACKAGE_BYTES
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            if (output.size() + read > limit) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}.getOrNull()

private val imageClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

/** The bytes behind a template picture: read from disk, or fetched from the link it names. */
private fun readSource(context: Context, image: TemplateImage): ByteArray? = runCatching {
    if (image.isRemote) {
        val request = Request.Builder().url(image.source).header("Accept", "image/*").build()
        imageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val declared = response.body.contentLength()
            if (declared > MAX_IMAGE_BYTES) return null
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            response.body.byteStream().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (output.size() + read > MAX_IMAGE_BYTES) return null
                    output.write(buffer, 0, read)
                }
            }
            output.toByteArray()
        }
    } else {
        val file = File(image.source)
        if (!file.isFile || file.length() > MAX_IMAGE_BYTES) return null
        file.readBytes()
    }
}.getOrNull()

/** Copies the bytes into the cache and describes them the way the upload ticket expects. */
private fun describeBytes(context: Context, bytes: ByteArray, source: String): DraftImage? = runCatching {
    if (bytes.isEmpty()) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val suffix = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(source.substringAfterLast('.', "").lowercase())
        ?.substringAfter('/', "jpg")
        ?: "jpg"
    val file = File(context.cacheDir, "${UUID.randomUUID()}.$suffix")
    file.outputStream().use { it.write(bytes) }
    DraftImage(
        path = file.absolutePath,
        name = "${UUID.randomUUID().toString().replace("-", "")}.$suffix",
        resolution = "${options.outWidth}x${options.outHeight}",
        md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) },
    )
}.getOrNull()
