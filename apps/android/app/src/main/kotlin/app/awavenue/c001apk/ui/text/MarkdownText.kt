package app.awavenue.c001apk.ui.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.openCoolapkLink
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.theme.COUITheme

private val emojiPattern = Regex("\\[[^\\]]+\\]")
private val ImageShape = RoundedCornerShape(12.dp)
private const val LONG_IMAGE_RATIO = 22f / 9f

/**
 * One post body: Markdown when the conservative detector says so, the original HTML otherwise.
 *
 * @param preview the feed-card variant: a flat, line-limited summary instead of the full layout.
 * @param forcePlain 查看原文: the source text exactly as the author wrote it.
 */
@Composable
fun FeedText(
    source: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    preview: Boolean = false,
    forcePlain: Boolean = false,
    previewMaxLines: Int = 6,
    picArr: List<String> = emptyList(),
    onTextClick: (() -> Unit)? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    if (source.isEmpty()) return
    val style = HtmlTextDefaults.style().copy(fontSize = fontSize)
    if (forcePlain) {
        Text(
            source,
            modifier = modifier.then(if (onTextClick != null) Modifier.clickable(onClick = onTextClick) else Modifier),
            style = style,
            maxLines = if (preview) previewMaxLines else Int.MAX_VALUE,
            overflow = if (preview) TextOverflow.Ellipsis else TextOverflow.Clip,
            onTextLayout = onTextLayout,
        )
        return
    }
    val content = remember(source) { MarkdownContent.parse(source) }
    if (!content.isMarkdown) {
        HtmlText(
            source,
            modifier,
            style = style,
            maxLines = if (preview) previewMaxLines else Int.MAX_VALUE,
            onTextClick = onTextClick,
            onTextLayout = onTextLayout,
        )
        return
    }
    if (preview) {
        Text(
            content.preview,
            modifier = modifier.then(if (onTextClick != null) Modifier.clickable(onClick = onTextClick) else Modifier),
            style = style,
            maxLines = previewMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = onTextLayout,
        )
        return
    }
    MarkdownBody(content, modifier, fontSize = fontSize, picArr = picArr)
}

/** The full Markdown layout, also used for release notes. */
@Composable
fun MarkdownBody(
    source: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    explicit: Boolean = false,
    coolapk: Boolean = true,
    picArr: List<String> = emptyList(),
    showEmoji: Boolean? = null,
) {
    val content = remember(source, explicit, coolapk) {
        MarkdownContent.parse(source, explicit = explicit, coolapk = coolapk)
    }
    MarkdownBody(content, modifier, fontSize = fontSize, picArr = picArr, showEmoji = showEmoji)
}

@Composable
fun MarkdownBody(
    content: MarkdownContent,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 16.sp,
    picArr: List<String> = emptyList(),
    showEmoji: Boolean? = null,
) {
    if (!content.isMarkdown) {
        Text(content.source, modifier = modifier, style = HtmlTextDefaults.style().copy(fontSize = fontSize))
        return
    }
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val showEmojiEnabled = showEmoji ?: app.settings.showEmoji
    val images = remember(content, picArr) { (picArr + content.imageUrls).distinct() }
    val openUrl: (String) -> Unit = { url ->
        when {
            // An anchor points inside this body; there is no in-page outline to jump to.
            url.startsWith("#") -> Unit
            url.contains("image.coolapk.com") -> navigator.push(
                Route.ImageView(images, images.indexOf(url).coerceAtLeast(0)),
            )
            else -> openCoolapkLink(context, navigator, url, openInBrowser = app.settings.openInBrowser)
        }
    }
    val openImage: (String) -> Unit = { url ->
        val index = images.indexOf(url).coerceAtLeast(0)
        navigator.push(Route.ImageView(images, index))
    }
    Column(modifier.fillMaxWidth()) {
        MarkdownBlocks(
            blocks = content.blocks,
            fontSize = fontSize,
            images = images,
            showEmoji = showEmojiEnabled,
            onOpenUrl = openUrl,
            onOpenImage = openImage,
            depth = 0,
        )
    }
}

@Composable
private fun MarkdownBlocks(
    blocks: List<MdBlock>,
    fontSize: TextUnit,
    images: List<String>,
    showEmoji: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    depth: Int,
) {
    blocks.forEach { block ->
        when (block) {
            is MdBlock.Heading -> {
                val size = when (block.level) {
                    1 -> 22.sp
                    2 -> 20.sp
                    3 -> 18.sp
                    4 -> 17.sp
                    5 -> 16.sp
                    else -> 15.sp
                }
                InlineFlow(
                    block.inlines,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                    style = HtmlTextDefaults.style().copy(fontSize = size, fontWeight = FontWeight.Bold, lineHeight = 1.35.em),
                    showEmoji = showEmoji,
                    onOpenUrl = onOpenUrl,
                    onOpenImage = onOpenImage,
                )
            }

            is MdBlock.Paragraph -> InlineFlow(
                block.inlines,
                modifier = Modifier.padding(bottom = 10.dp),
                style = HtmlTextDefaults.style().copy(fontSize = fontSize),
                showEmoji = showEmoji,
                onOpenUrl = onOpenUrl,
                onOpenImage = onOpenImage,
            )

            is MdBlock.Code -> CodeBlock(block, fontSize)

            is MdBlock.Quote -> {
                val barColor = COUITheme.colorScheme.dividerLine
                Column(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(barColor, size = Size(3.dp.toPx(), size.height))
                        }
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    MarkdownBlocks(block.blocks, fontSize, images, showEmoji, onOpenUrl, onOpenImage, depth)
                }
            }

            is MdBlock.ListBlock -> Column(Modifier.padding(start = (depth * 14).dp, bottom = 10.dp)) {
                block.items.forEachIndexed { index, item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = when {
                                item.checked == true -> "☑"
                                item.checked == false -> "☐"
                                block.ordered -> "${block.start + index}."
                                else -> "•"
                            },
                            modifier = Modifier.width(30.dp),
                            style = HtmlTextDefaults.style().copy(fontSize = fontSize),
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Column(Modifier.weight(1f)) {
                            MarkdownBlocks(
                                item.blocks,
                                fontSize,
                                images,
                                showEmoji,
                                onOpenUrl,
                                onOpenImage,
                                depth + 1,
                            )
                        }
                    }
                }
            }

            is MdBlock.Table -> MarkdownTable(block, fontSize, showEmoji, onOpenUrl, onOpenImage)

            MdBlock.Rule -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(1.dp)
                    .background(COUITheme.colorScheme.dividerLine),
            )

            is MdBlock.Image -> MarkdownImage(block.url, block.alt, images, onOpenImage)
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, fontSize: TextUnit) {
    val scheme = COUITheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceContainerHighest)
            .horizontalScroll(rememberScrollState())
            .padding(12.dp),
    ) {
        SelectionContainer {
            Text(
                block.code.trimEnd('\n'),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize * 0.9f,
                    lineHeight = 1.5.em,
                    color = scheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MdBlock.Table,
    fontSize: TextUnit,
    showEmoji: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val columns = maxOf(table.header.size, table.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val scheme = COUITheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            repeat(columns) { column ->
                Column(Modifier.width(IntrinsicSize.Max)) {
                    TableCell(
                        table.header.getOrNull(column).orEmpty(),
                        table.aligns.getOrNull(column) ?: MdAlign.Start,
                        header = true,
                        fontSize = fontSize,
                        showEmoji = showEmoji,
                        onOpenUrl = onOpenUrl,
                        onOpenImage = onOpenImage,
                    )
                    table.rows.forEach { row ->
                        TableCell(
                            row.getOrNull(column).orEmpty(),
                            table.aligns.getOrNull(column) ?: MdAlign.Start,
                            header = false,
                            fontSize = fontSize,
                            showEmoji = showEmoji,
                            onOpenUrl = onOpenUrl,
                            onOpenImage = onOpenImage,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    inlines: List<MdInline>,
    align: MdAlign,
    header: Boolean,
    fontSize: TextUnit,
    showEmoji: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    InlineFlow(
        inlines,
        modifier = Modifier
            .border(0.5.dp, COUITheme.colorScheme.dividerLine)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        style = HtmlTextDefaults.style().copy(
            fontSize = fontSize,
            fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = when (align) {
                MdAlign.Center -> TextAlign.Center
                MdAlign.End -> TextAlign.End
                else -> TextAlign.Start
            },
        ),
        showEmoji = showEmoji,
        onOpenUrl = onOpenUrl,
        onOpenImage = onOpenImage,
    )
}

@Composable
private fun MarkdownImage(
    url: String,
    alt: String,
    images: List<String>,
    onOpenImage: (String) -> Unit,
) {
    if (!MarkdownContent.allowsUrl(url, image = true)) return
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val (width, height) = imageSize(url)
        val ratio = if (width <= 0f) 1f else height / width
        val imageWidth = maxWidth
        val imageHeight = imageWidth * minOf(ratio, LONG_IMAGE_RATIO)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(imageWidth)
                    .height(imageHeight)
                    .clip(ImageShape)
                    .clickable { onOpenImage(url) },
            ) {
                NetworkImage(
                    thumbnail(url),
                    Modifier.fillMaxSize(),
                    contentDescription = alt,
                    contentScale = ContentScale.FillBounds,
                )
            }
            if (alt.isNotEmpty()) {
                Text(
                    alt,
                    modifier = Modifier.padding(top = 4.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun InlineFlow(
    inlines: List<MdInline>,
    modifier: Modifier,
    style: TextStyle,
    showEmoji: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenImage: (String) -> Unit,
) {
    val linkColor = COUITheme.colorScheme.primary
    val codeBackground = COUITheme.colorScheme.surfaceContainerHighest
    val currentOpenUrl by rememberUpdatedState(onOpenUrl)
    val currentOpenImage by rememberUpdatedState(onOpenImage)
    val rendered = remember(inlines, linkColor, codeBackground, showEmoji) {
        buildInline(inlines, linkColor, codeBackground, showEmoji) { url -> currentOpenUrl(url) } to
            buildInlineContent(inlines, showEmoji) { url -> currentOpenImage(url) }
    }
    Text(
        text = rendered.first,
        modifier = modifier,
        style = style,
        inlineContent = rendered.second,
    )
}

private fun buildInline(
    inlines: List<MdInline>,
    linkColor: Color,
    codeBackground: Color,
    showEmoji: Boolean,
    onOpenUrl: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    fun append(nodes: List<MdInline>) {
        nodes.forEach { node ->
            when (node) {
                is MdInline.Text -> appendText(node.text, showEmoji)
                is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(node.children) }
                is MdInline.Emphasis -> withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(node.children)
                }
                is MdInline.Strikethrough -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(node.children)
                }
                is MdInline.Code -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        fontSize = 0.92.em,
                    ),
                ) { appendText(node.text, showEmoji = false) }
                is MdInline.Link -> withLink(
                    LinkAnnotation.Url(
                        node.url,
                        TextLinkStyles(SpanStyle(color = linkColor)),
                        { _ -> onOpenUrl(node.url) },
                    ),
                ) { append(node.children) }
                is MdInline.Image -> {
                    val id = "mdimg:${node.url}"
                    appendInlineContent(id, node.alt.ifEmpty { "图片" })
                }
                MdInline.Break -> append('\n')
            }
        }
    }
    append(inlines)
}

private fun AnnotatedString.Builder.appendText(text: String, showEmoji: Boolean) {
    var last = 0
    for (match in emojiPattern.findAll(text)) {
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val code = match.value
        val file = if (showEmoji) EmojiFiles[code] else null
        if (file != null || (showEmoji && code == EmojiPhotoCode)) {
            appendInlineContent("emoji:$code", code)
        } else {
            append(code)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}

private fun buildInlineContent(
    inlines: List<MdInline>,
    showEmoji: Boolean,
    onOpenImage: (String) -> Unit,
): Map<String, InlineTextContent> {
    val content = mutableMapOf<String, InlineTextContent>()
    fun visit(nodes: List<MdInline>) {
        nodes.forEach { node ->
            when (node) {
                is MdInline.Image -> {
                    val id = "mdimg:${node.url}"
                    if (id !in content) {
                        content[id] = InlineTextContent(
                            Placeholder(1.4.em, 1.4.em, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            Icon(
                                COUIIcons.Photos,
                                contentDescription = node.alt.ifEmpty { "图片" },
                                modifier = Modifier.fillMaxSize().clickable { onOpenImage(node.url) },
                                tint = COUITheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is MdInline.Text -> if (showEmoji) {
                    for (match in emojiPattern.findAll(node.text)) {
                        val code = match.value
                        val file = EmojiFiles[code]
                        val id = "emoji:$code"
                        if ((file != null || code == EmojiPhotoCode) && id !in content) {
                            content[id] = InlineTextContent(
                                Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter),
                            ) {
                                if (file == null) {
                                    Icon(
                                        COUIIcons.Photos,
                                        contentDescription = code,
                                        modifier = Modifier.fillMaxSize(),
                                        tint = COUITheme.colorScheme.primary,
                                    )
                                } else {
                                    AsyncImage(
                                        model = "file:///android_asset/emojis/$file",
                                        contentDescription = code,
                                    )
                                }
                            }
                        }
                    }
                }
                is MdInline.Strong -> visit(node.children)
                is MdInline.Emphasis -> visit(node.children)
                is MdInline.Strikethrough -> visit(node.children)
                is MdInline.Link -> visit(node.children)
                is MdInline.Code, MdInline.Break -> Unit
            }
        }
    }
    visit(inlines)
    return content
}
