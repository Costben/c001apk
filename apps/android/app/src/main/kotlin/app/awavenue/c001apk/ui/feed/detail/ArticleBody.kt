package app.awavenue.c001apk.ui.feed.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.HtmlText
import app.awavenue.c001apk.ui.text.HtmlTextDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One block of the structured long-form body, decoded from the feed's `message_raw_output`. */
data class FeedArticle(
    val type: String = "",
    val message: String = "",
    val url: String = "",
    val description: String = "",
    val title: String = "",
) {
    companion object {
        private val SUPPORTED = setOf("title", "text", "image", "shareUrl")

        /** Throws when the payload is not the JSON array the endpoint promises. */
        fun parse(raw: String): List<FeedArticle> {
            if (raw.isEmpty() || raw == "null") return emptyList()
            val array = Json.parseToJsonElement(raw) as? JsonArray ?: return emptyList()
            return array.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val type = obj.text("type")
                if (type !in SUPPORTED) return@mapNotNull null
                FeedArticle(
                    type = type,
                    message = obj.text("message"),
                    url = obj.text("url"),
                    description = obj.text("description"),
                    title = obj.text("title"),
                )
            }
        }

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}

private val LONG_IMAGE_RATIO = 22f / 9f
private val ImageCornerRadius = 12.dp
private val ImageShape = RoundedCornerShape(ImageCornerRadius)

@Composable
fun ArticleBody(article: FeedArticle, images: List<String>, modifier: Modifier = Modifier) {
    when (article.type) {
        "title" -> HtmlText(
            article.title,
            modifier.padding(horizontal = 16.dp),
            style = HtmlTextDefaults.style().copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )

        "text" -> {
            val navigator = LocalNavigator.current
            val haptics = LocalHapticFeedback.current
            HtmlText(
                article.message,
                modifier
                    .padding(horizontal = 16.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            navigator.push(Route.Copy(article.message))
                        },
                    ),
                style = HtmlTextDefaults.style().copy(fontSize = 16.sp),
            )
        }

        "image" -> ArticleImage(article, images, modifier.padding(horizontal = 16.dp))

        "shareUrl" -> {
            val openLink = rememberLinkOpener()
            Box(
                modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(ImageShape)
                    .background(COUITheme.colorScheme.surfaceContainer)
                    .clickable(onClick = rememberHapticClick { openLink(article.url, article.title) })
                    .padding(10.dp),
            ) {
                Text(article.title)
            }
        }
    }
}

@Composable
private fun ArticleImage(article: FeedArticle, images: List<String>, modifier: Modifier) {
    val navigator = LocalNavigator.current
    val url = article.url
    BoxWithConstraints(modifier) {
        val (width, height) = imageSize(url)
        val ratioHW = if (width <= 0f) 1f else height / width
        val imageWidth = maxWidth
        val imageHeight = imageWidth * minOf(ratioHW, LONG_IMAGE_RATIO)
        val badge = when {
            url.endsWith(".gif") -> "GIF"
            ratioHW >= LONG_IMAGE_RATIO -> "长图"
            else -> null
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(imageWidth, imageHeight)
                    .imageOrigin(url, ImageCornerRadius)
                    .clip(ImageShape)
                    .clickable(
                        onClick = rememberHapticClick {
                            navigator.push(Route.ImageView(images, images.indexOf(url).coerceAtLeast(0)))
                        },
                    ),
            ) {
                NetworkImage(thumbnail(url), Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                if (badge != null) {
                    Text(
                        badge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(COUITheme.colorScheme.primaryContainer)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = COUITheme.textStyles.footnote2,
                        color = COUITheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            if (article.description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    article.description,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
