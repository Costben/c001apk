package app.awavenue.c001apk.ui.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.R
import app.awavenue.c001apk.media.isLivePhotoUrl
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.isGifUrl
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.image.LiveBadgeSize
import app.awavenue.c001apk.ui.image.LocalImageOrigins
import app.awavenue.c001apk.ui.image.imageOrigin
import app.awavenue.c001apk.ui.image.isLongImageAspect
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val GridSpacing = 5.dp
private val ImageCornerRadius = 12.dp
private const val LONG_IMAGE_RATIO = 22f / 9f

/** 多图格子里、单张大图上，实况标记各自离左下角的距离（与 Cobo COUI 一致）。 */
internal val GridLiveBadgeInset = 2.dp
internal val SingleLiveBadgeInset = 4.dp

/** 看图页撤走之后标记淡回来的时长。 */
private const val LiveBadgeFadeMillis = 220

/**
 * Feed pictures laid out like the Flutter client: one picture sized by its encoded aspect
 * ratio, four in a 2×2 block, everything else three per row. With [maxCount] set, only that many
 * are shown and the last one carries the count of the rest.
 */
@Composable
fun ImageGrid(
    pictures: List<String>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    maxCount: Int = Int.MAX_VALUE,
) {
    if (pictures.isEmpty()) return
    val shown = pictures.take(maxCount)
    val hidden = pictures.size - shown.size
    BoxWithConstraints(modifier) {
        val cell = (maxWidth - GridSpacing * 2) / 3
        if (pictures.size == 1) {
            val (width, height) = imageSize(pictures[0])
            val ratioWH = width / height
            val ratioHW = height / width
            val itemWidth = when {
                ratioWH > 1.5f -> maxWidth
                ratioWH >= 1f || (height > width && ratioHW < 1.5f) -> cell * 2
                else -> cell
            }
            GridImage(
                url = pictures[0],
                width = itemWidth,
                height = itemWidth * minOf(ratioHW, LONG_IMAGE_RATIO),
                single = true,
            ) { onClick(0) }
        } else {
            val columns = if (shown.size == 4) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
                shown.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                        row.forEachIndexed { columnIndex, url ->
                            val index = rowIndex * columns + columnIndex
                            val more = if (index == shown.lastIndex) hidden else 0
                            GridImage(
                                url = url,
                                width = cell,
                                height = cell,
                                more = more,
                                single = false,
                            ) { onClick(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridImage(
    url: String,
    width: Dp,
    height: Dp,
    more: Int = 0,
    single: Boolean = false,
    onClick: () -> Unit,
) {
    val origins = LocalImageOrigins.current
    val (w, h) = imageSize(url)
    val declaredAspect = if (w == 1f && h == 1f) null else w / h
    var measuredAspect by remember(url) { mutableStateOf<Float?>(null) }
    val aspect = declaredAspect ?: measuredAspect
    val isLive = url.isLivePhotoUrl()
    val isGif = isGifUrl(url)
    val isLong = isLongImageAspect(aspect)
    // A live photo's thumbnail suffix is not served; the still image doubles as its preview.
    val model = if (isLive || isGif) url else thumbnail(url)
    val cornerBadge = when {
        isGif -> "GIF"
        isLong -> "长图"
        else -> null
    }
    Box(
        Modifier
            .size(width, min(height, width * LONG_IMAGE_RATIO))
            .imageOrigin(url, ImageCornerRadius, aspect = aspect, model = model)
            .clip(rememberSquircleShape(ImageCornerRadius))
            .clickable(onClick = onClick),
    ) {
        NetworkImage(
            url = model,
            modifier = Modifier.size(width, height),
            alignment = if (isLong) Alignment.TopCenter else Alignment.Center,
            onSuccess = { state ->
                origins.putCacheKey(url, state.result.memoryCacheKey)
                if (declaredAspect == null) {
                    val s = state.painter.intrinsicSize
                    if (s.width > 0f && s.height > 0f) measuredAspect = s.width / s.height
                }
            },
        )
        if (more > 0) {
            Box(
                Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$more", style = COUITheme.textStyles.title3, color = Color.White)
            }
        } else {
            if (isLive) {
                LiveBadge(
                    url = url,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(if (single) SingleLiveBadgeInset else GridLiveBadgeInset),
                )
            }
            if (cornerBadge != null) {
                MediaCornerBadge(
                    url = url,
                    text = cornerBadge,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(if (single) SingleLiveBadgeInset else 4.dp),
                )
            }
        }
    }
}

/**
 * 缩略图左下角的实况标记（对齐 Cobo COUI / ColorOS 相册实况图标）。
 *
 * 被打开的那一张在看图页覆盖时先淡出让位，退场落位后再淡回。
 */
@Composable
fun LiveBadge(url: String, modifier: Modifier = Modifier) {
    val covered = LocalImageOrigins.current.viewing == url
    val fade = animateFloatAsState(
        targetValue = if (covered) 0f else 1f,
        animationSpec = tween(LiveBadgeFadeMillis),
        label = "liveBadge",
    )
    Box(
        modifier
            .graphicsLayer { alpha = fade.value }
            .size(LiveBadgeSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_live_photo),
            contentDescription = "实况",
            modifier = Modifier.size(13.dp),
            tint = Color.White,
        )
    }
}

/** 缩略图右下角的「长图 / GIF」角标，看图页覆盖当前图时先隐去，退场落位后再淡回。 */
@Composable
fun MediaCornerBadge(url: String, text: String, modifier: Modifier = Modifier) {
    val covered = LocalImageOrigins.current.viewing == url
    val fade = animateFloatAsState(
        targetValue = if (covered) 0f else 1f,
        animationSpec = tween(LiveBadgeFadeMillis),
        label = "mediaCornerBadge",
    )
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .graphicsLayer { alpha = fade.value }
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
