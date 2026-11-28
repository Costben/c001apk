package app.awavenue.c001apk.ui.text

import android.graphics.Typeface
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.openCoolapkLink
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.theme.COUITheme

private val emojiPattern = Regex("\\[[^\\]]+\\]")

object HtmlTextDefaults {
    /** Message body: 15sp at 1.5 line height, as in the Flutter client. */
    @Composable
    fun style(): TextStyle =
        COUITheme.textStyles.body1.copy(fontSize = 15.sp, lineHeight = 1.5.em, fontWeight = FontWeight.Normal)
}

/**
 * Renders Coolapk's message HTML: links tinted with the primary colour, line breaks kept and
 * `[emoji]` codes replaced by the bundled images.
 *
 * @param badgeTags Draw `[楼主]`, `[层主]` and `[置顶]` as chips instead of plain text, so a tag
 *   cannot be read as part of the name before it.
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HtmlTextDefaults.style(),
    color: Color = COUITheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    badgeTags: Boolean = false,
    onTextClick: (() -> Unit)? = null,
    onShowTotalReply: (() -> Unit)? = null,
    onViewFan: (() -> Unit)? = null,
    picArr: List<String>? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val linkColor = COUITheme.colorScheme.primary
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val app = LocalApp.current
    val openInBrowser = app.settings.openInBrowser
    val showEmoji = app.settings.showEmoji
    val currentOnTextClick by rememberUpdatedState(onTextClick)
    val currentOnShowTotalReply by rememberUpdatedState(onShowTotalReply)
    val currentOnViewFan by rememberUpdatedState(onViewFan)
    val openLink: (String) -> Unit = { url ->
        when {
            url.contains("/feed/replyList") -> currentOnShowTotalReply?.invoke()
            url == "/contacts/fans" -> currentOnViewFan?.invoke()
            url.contains("image.coolapk.com") -> navigator.push(
                Route.ImageView(picArr?.takeIf { it.isNotEmpty() } ?: listOf(url)),
            )

            !openCoolapkLink(context, navigator, url, openInBrowser = openInBrowser) ->
                currentOnTextClick?.invoke()
        }
    }
    val currentOpenLink by rememberUpdatedState(openLink)
    val text = remember(html, linkColor, showEmoji, badgeTags) {
        val parsed = parseHtmlToAnnotatedString(html, linkColor)
        if (showEmoji || badgeTags) {
            runCatching {
                decorate(parsed, linkColor, showEmoji = showEmoji, badgeTags = badgeTags)
            }.getOrElse { parsed to emptyMap() }
        } else {
            parsed to emptyMap()
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text.first,
        modifier = modifier.pointerInput(text.first) {
            // A link takes only the touches that land on its own glyphs; the rest of the text belongs to
            // whatever holds it. Compose's own link handling gives a link that wraps one box spanning
            // every line it touches, which swallowed taps meant for the row around it.
            awaitEachGesture {
                val down = awaitFirstDown()
                val url = layout?.linkAt(text.first, down.position) ?: return@awaitEachGesture
                val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                up.consume()
                currentOpenLink(url)
            }
        },
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = text.second,
        onTextLayout = {
            layout = it
            onTextLayout(it)
        },
    )
}

private const val LinkTag = "html-link"
private val FallbackTagPattern = Regex("<[^>]*>")

/**
 * Parses Coolapk HTML safely into an [AnnotatedString].
 *
 * Compose's `AnnotatedString.fromHtml` throws `IllegalArgumentException` whenever Android's HTML
 * parser emits overlapping `ParagraphStyle` spans (e.g. nested alignment, quote or list tags).
 * Falling back to [HtmlCompat.fromHtml] with span-only conversion preserves links and emphasis
 * without crashing on malformed or nested block markup.
 */
internal fun parseHtmlToAnnotatedString(html: String, linkColor: Color): AnnotatedString {
    val normalized = html.replace("\n", "<br/>")
    runCatching {
        return tintLinks(AnnotatedString.fromHtml(normalized), linkColor)
    }
    runCatching {
        val spanned = HtmlCompat.fromHtml(normalized, HtmlCompat.FROM_HTML_MODE_COMPACT)
        return buildAnnotatedString {
            append(spanned.toString())
            val length = spanned.length
            spanned.getSpans(0, length, Any::class.java).forEach { span ->
                val start = spanned.getSpanStart(span).coerceIn(0, length)
                val end = spanned.getSpanEnd(span).coerceIn(start, length)
                if (start >= end) return@forEach
                when (span) {
                    is URLSpan -> {
                        val url = span.url.orEmpty()
                        addStyle(SpanStyle(color = linkColor), start, end)
                        addStringAnnotation(LinkTag, url, start, end)
                    }
                    is StyleSpan -> when (span.style) {
                        Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        Typeface.BOLD_ITALIC -> addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start,
                            end,
                        )
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                }
            }
        }
    }
    return AnnotatedString(FallbackTagPattern.replace(html, ""))
}

/**
 * Turns the parsed links into tinted ranges tagged with their url, which [linkAt] reads back.
 * Paragraph styles are dropped so later emoji/tag decoration can slice the text without splitting
 * paragraph ranges mid-line.
 */
private fun tintLinks(source: AnnotatedString, linkColor: Color): AnnotatedString {
    val links = source.getLinkAnnotations(0, source.length)
    if (links.isEmpty() && source.paragraphStyles.isEmpty()) return source
    return buildAnnotatedString {
        append(AnnotatedString(source.text, source.spanStyles))
        links.forEach { range ->
            val start = range.start.coerceIn(0, source.length)
            val end = range.end.coerceIn(start, source.length)
            if (start >= end) return@forEach
            val url = (range.item as? LinkAnnotation.Url)?.url.orEmpty()
            addStyle(SpanStyle(color = linkColor), start, end)
            addStringAnnotation(LinkTag, url, start, end)
        }
    }
}

/** The url of the link whose glyph sits under [position], or `null` when the touch misses every link. */
private fun TextLayoutResult.linkAt(text: AnnotatedString, position: Offset): String? {
    val offset = runCatching { getOffsetForPosition(position) }.getOrNull() ?: return null
    // The offset is the caret nearest the touch, so the glyph under it is on either side.
    val char = listOf(offset, offset - 1).firstOrNull { index ->
        index in text.indices && runCatching { getBoundingBox(index).contains(position) }.getOrDefault(false)
    } ?: return null
    return text.getStringAnnotations(LinkTag, char, char + 1).firstOrNull()?.item
}

/** The author tags Coolapk writes into the reply HTML as bracketed text. */
private val authorTagPattern = Regex("\\[(楼主|层主|置顶)]")

/**
 * Replaces `[emoji]` codes with the bundled images and, when [badgeTags] is set, the author tags
 * with chips. Chips take precedence, so a tag never renders as an emoji or plain text.
 */
private fun decorate(
    source: AnnotatedString,
    linkColor: Color,
    showEmoji: Boolean,
    badgeTags: Boolean,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = mutableMapOf<String, InlineTextContent>()
    val result = buildAnnotatedString {
        var last = 0
        for (match in emojiPattern.findAll(source.text)) {
            val code = match.value
            val tag = if (badgeTags) authorTagPattern.matchEntire(code)?.groupValues?.get(1) else null
            val file = if (tag == null && showEmoji) EmojiFiles[code] else null
            val isPhoto = showEmoji && code == EmojiPhotoCode
            if (tag == null && file == null && !isPhoto) continue
            append(source.subSequence(last, match.range.first))
            if (tag != null) {
                val id = "tag:$tag"
                appendInlineContent(id, tag)
                inline.getOrPut(id) {
                    InlineTextContent(Placeholder(2.0.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)) {
                        AuthorTag(tag, Modifier.fillMaxSize())
                    }
                }
            } else {
                val id = "emoji:$code"
                appendInlineContent(id, code)
                inline.getOrPut(id) {
                    InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.TextCenter)) {
                        if (file == null) {
                            Icon(
                                COUIIcons.Photos,
                                contentDescription = code,
                                modifier = Modifier.fillMaxSize(),
                                tint = linkColor,
                            )
                        } else {
                            AsyncImage(model = "file:///android_asset/emojis/$file", contentDescription = code)
                        }
                    }
                }
            }
            last = match.range.last + 1
        }
        append(source.subSequence(last, source.length))
    }
    return result to inline
}

/** One author tag, drawn as a chip so it cannot be mistaken for part of the name. */
@Composable
private fun AuthorTag(tag: String, modifier: Modifier = Modifier) {
    val scheme = COUITheme.colorScheme
    val op = tag == "楼主"
    Box(
        modifier = modifier
            .clip(rememberSquircleShape(4.dp))
            .background(if (op) scheme.tertiaryContainer else scheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = tag,
            style = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
            color = if (op) scheme.primary else scheme.onSecondaryVariant,
            maxLines = 1,
        )
    }
}
