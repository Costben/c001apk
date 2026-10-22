package app.awavenue.c001apk.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * 回复球最后一次量到的位置（根坐标）。
 *
 * 输入面板的右下角直接对到这颗球上，而不是各自去推算 inset：Scaffold 把球放在
 * `FabSpacing`(12dp) + 消费后的 inset 处，弹窗窗口拿到的 inset 又是它自己的，
 * 两边算出来的留白差几 dp，收起时就有一小片球从面板的圆角下面露出来。
 *
 * 存的是根坐标，也就是键盘还没起来那一刻的摆放位置 —— 面板的"归位点"正是这个位置。
 */
object ComposerBallAnchor {
    var rect: Rect? by mutableStateOf(null)
        internal set
}
