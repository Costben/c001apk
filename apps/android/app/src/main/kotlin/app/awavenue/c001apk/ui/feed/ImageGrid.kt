package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.imageSize
import app.awavenue.c001apk.ui.common.thumbnail
import app.awavenue.c001apk.ui.image.imageOrigin
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

private val GridSpacing = 5.dp
private val ImageCornerRadius = 12.dp
private val ImageShape = RoundedCornerShape(ImageCornerRadius)
private const val LONG_IMAGE_RATIO = 22f / 9f

/**
 * Feed pictures laid out like the Flutter client: one picture sized by its encoded aspect
 * ratio, four in a 2×2 block, everything else three per row.
 */
@Composable
fun ImageGrid(pictures: List<String>, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pictures.isEmpty()) return
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
            GridImage(pictures[0], itemWidth, itemWidth * minOf(ratioHW, LONG_IMAGE_RATIO)) { onClick(0) }
        } else {
            val columns = if (pictures.size == 4) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
                pictures.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(GridSpacing)) {
                        row.forEachIndexed { columnIndex, url ->
                            val index = rowIndex * columns + columnIndex
                            GridImage(url, cell, cell) { onClick(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridImage(url: String, width: Dp, height: Dp, onClick: () -> Unit) {
    val (w, h) = imageSize(url)
    val badge = when {
        url.endsWith(".gif") -> "GIF"
        h / w >= LONG_IMAGE_RATIO -> "长图"
        else -> null
    }
    Box(
        Modifier
            .size(width, min(height, width * LONG_IMAGE_RATIO))
            .imageOrigin(url, ImageCornerRadius)
            .clip(ImageShape)
            .clickable(onClick = onClick),
    ) {
        NetworkImage(thumbnail(url), Modifier.size(width, height))
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
}
