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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.awavenue.c001apk.media.isLivePhotoUrl
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.isGifUrl
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.feed.LiveBadge
import app.awavenue.c001apk.ui.feed.MediaCornerBadge
import app.awavenue.c001apk.ui.feed.SingleLiveBadgeInset
import app.awavenue.c001apk.ui.image.LocalImageOrigins
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.image.isLongImageAspect
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.text.FeedText
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

@Composable
fun ArticleBody(
    article: FeedArticle,
    images: List<String>,
    modifier: Modifier = Modifier,
    forcePlain: Boolean = false,
) {
    when (article.type) {
        "title" -> HtmlText(
            article.title,
            modifier.padding(horizontal = 16.dp),
            style = HtmlTextDefaults.style().copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
        )

        "text" -> {
            val navigator = LocalNavigator.current
            val haptics = LocalHapticFeedback.current
            FeedText(
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
                fontSize = 16.sp,
                forcePlain = forcePlain,
                picArr = images,
            )
        }

        "image" -> ArticleImage(article, images, modifier.padding(horizontal = 16.dp))

        "shareUrl" -> {
            val openLink = rememberLinkOpener()
            Box(
                modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(rememberSquircleShape(ImageCornerRadius))
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
    val origins = LocalImageOrigins.current
    val url = article.url
    BoxWithConstraints(modifier) {
        val (width, height) = imageSize(url)
        val declaredAspect = if (width == 1f && height == 1f) null else (width / height).takeIf { it.isFinite() && it > 0f }
        var measuredAspect by remember(url) { mutableStateOf<Float?>(null) }
        val aspect = declaredAspect ?: measuredAspect
        val ratioHW = (if (width <= 0f) 1f else height / width).takeIf { it.isFinite() && it > 0f } ?: 1f
        val imageWidth = maxWidth
        val imageHeight = imageWidth * minOf(ratioHW, LONG_IMAGE_RATIO)
        val isLive = url.isLivePhotoUrl()
        val isGif = isGifUrl(url)
        val isLong = isLongImageAspect(aspect)
        val model = if (isLive || isGif) url else thumbnail(url)
        val badge = when {
            isGif -> "GIF"
            isLong -> "长图"
            else -> null
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(imageWidth, imageHeight)
                    .imageOrigin(url, ImageCornerRadius, aspect = aspect, model = model)
                    .clip(rememberSquircleShape(ImageCornerRadius))
                    .clickable(
                        onClick = rememberHapticClick {
                            navigator.push(Route.ImageView(images, images.indexOf(url).coerceAtLeast(0)))
                        },
                    ),
            ) {
                NetworkImage(
                    url = model,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = if (isLong) ContentScale.Crop else ContentScale.FillBounds,
                    alignment = if (isLong) Alignment.TopCenter else Alignment.Center,
                    onSuccess = { state ->
                        origins.putCacheKey(url, state.result.memoryCacheKey)
                        if (declaredAspect == null) {
                            val s = state.painter.intrinsicSize
                            if (s.width.isFinite() && s.height.isFinite() && s.width > 0f && s.height > 0f) {
                                measuredAspect = s.width / s.height
                            }
                        }
                    },
                )
                if (isLive) {
                    LiveBadge(
                        url = url,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(SingleLiveBadgeInset),
                    )
                }
                if (badge != null) {
                    MediaCornerBadge(
                        url = url,
                        text = badge,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(SingleLiveBadgeInset),
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
