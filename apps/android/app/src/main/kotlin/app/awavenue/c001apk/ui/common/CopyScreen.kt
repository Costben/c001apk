package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.theme.COUITheme

private val feedLinkUrl = Regex("""<a class="feed-link-url"\s+href="([^<>"]*)"[^<]*[^>]*>""")

/** The whole message as selectable text, with the feed links kept readable rather than hidden. */
@Composable
fun CopyScreen(text: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val linkColor = COUITheme.colorScheme.primary

    val body = remember(text, linkColor) {
        val withLinks = feedLinkUrl.replace(text) { " ${it.groupValues[1]} " }
        AnnotatedString.fromHtml(
            htmlString = "\n" + withLinks.replace("\n", "<br/>") + "\n",
            linkStyles = TextLinkStyles(style = SpanStyle(color = linkColor)),
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url.orEmpty()
                when {
                    url.startsWith("http://") || url.startsWith("https://") -> uriHandler.openUri(url)
                    url.startsWith("/") -> uriHandler.openUri(Protocol.URL_COOLAPK + url)
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "",
                gradientGlass = true,
                navigationIcon = {
                    TopBarIconButton(onClick = { navigator.pop() }) {
                        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    TopBarIconButton(onClick = { context.copyText(body.text) }) {
                        Icon(COUIIcons.Copy, contentDescription = "复制")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberPageScrollState("copy")),
        ) {
            SelectionContainer {
                Text(
                    text = body,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    style = TextStyle(fontSize = 22.sp, lineHeight = 1.5.em),
                )
            }
        }
    }
}
