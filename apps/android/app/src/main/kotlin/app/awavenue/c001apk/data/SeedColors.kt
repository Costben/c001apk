package app.awavenue.c001apk.data

import androidx.compose.ui.graphics.Color

/** The seed colours the Flutter client offers, in the same order and with the same labels. */
object SeedColors {
    val labels = listOf(
        "默认", "红色", "粉色", "紫色", "靛蓝", "蓝色", "浅蓝", "青色", "蓝绿", "绿色",
        "浅绿", "青柠", "黄色", "琥珀", "橙色", "深橙", "棕色", "蓝灰", "樱粉",
    )

    val colors = listOf(
        Color(0xFF6650A4), Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
        Color(0xFF009688), Color(0xFF4FAF50), Color(0xFF8BC3A4), Color(0xFFCDDC39),
        Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
        Color(0xFF795548), Color(0xFF607D8F), Color(0xFFFF9CA8),
    )

    fun colorAt(index: Int): Color = colors.getOrElse(index) { colors.first() }

    fun labelAt(index: Int): String = labels.getOrElse(index) { labels.first() }
}
