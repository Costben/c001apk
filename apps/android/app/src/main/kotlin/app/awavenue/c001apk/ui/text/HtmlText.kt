package app.awavenue.c001apk.ui.text

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
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
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = HtmlTextDefaults.style(),
    color: Color = COUITheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    onTextClick: (() -> Unit)? = null,
    onShowTotalReply: (() -> Unit)? = null,
    onViewFan: (() -> Unit)? = null,
    picArr: List<String>? = null,
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
    val text = remember(html, linkColor, openInBrowser, showEmoji, picArr) {
        val linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor))
        val parsed = AnnotatedString.fromHtml(
            htmlString = html.replace("\n", "<br/>"),
            linkStyles = linkStyles,
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url.orEmpty()
                when {
                    url.contains("/feed/replyList") -> currentOnShowTotalReply?.invoke()
                    url == "/contacts/fans" -> currentOnViewFan?.invoke()
                    url.contains("image.coolapk.com") -> navigator.push(
                        Route.ImageView(picArr?.takeIf { it.isNotEmpty() } ?: listOf(url)),
                    )

                    !openCoolapkLink(context, navigator, url, openInBrowser = openInBrowser) ->
                        currentOnTextClick?.invoke()
                }
            },
        )
        if (showEmoji) withEmoji(parsed, linkColor) else parsed to emptyMap<String, InlineTextContent>()
    }
    Text(
        text = text.first,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = text.second,
    )
}

private fun withEmoji(
    source: AnnotatedString,
    linkColor: Color,
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inline = mutableMapOf<String, InlineTextContent>()
    val result = buildAnnotatedString {
        var last = 0
        for (match in emojiPattern.findAll(source.text)) {
            val code = match.value
            val file = EmojiFiles[code]
            if (file == null && code != EmojiPhotoCode) continue
            append(source.subSequence(last, match.range.first))
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
            last = match.range.last + 1
        }
        append(source.subSequence(last, source.length))
    }
    return result to inline
}
