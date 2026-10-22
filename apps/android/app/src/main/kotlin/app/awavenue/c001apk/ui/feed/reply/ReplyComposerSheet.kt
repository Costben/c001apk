package app.awavenue.c001apk.ui.feed.reply

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.lerp
import io.github.suqi8.coui.kmp.layout.DialogDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.ComposerBallAnchor
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.detail.OssUpload
import app.awavenue.c001apk.ui.feed.detail.SelectedImage
import app.awavenue.c001apk.ui.feed.detail.rememberImagePicker
import app.awavenue.c001apk.ui.text.EmojiEditText
import app.awavenue.c001apk.ui.text.EmojiFiles
import app.awavenue.c001apk.ui.text.rememberEmojiInputState
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarDefaults
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.LoadingButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.COUIPopupUtils
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What the composer is answering. [id] empty means a new feed rather than a reply; [title] carries
 * the user being answered, or the title of the target a new feed is published to.
 */
data class ReplyTarget(
    val id: String = "",
    val type: String = "feed",
    val targetType: String = "",
    val targetId: String = "",
    val title: String = "",
    val feedType: String = "feed",
)

/**
 * The reply and publish sheet, ported from the Flutter `ReplyPage`: pictures first go to OSS,
 * then the write endpoint is called, and the legacy picture captcha is answered inline when the
 * server asks for it.
 */
@Composable
fun ReplyComposerSheet(
    target: ReplyTarget,
    onDismiss: () -> Unit,
    onPosted: () -> Unit,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val initialText = if (target.targetType == "tag") "#${target.title}# " else ""
    val input = rememberEmojiInputState(initialText)
    var onlySelf by remember { mutableStateOf(false) }
    var emojiPanel by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<SelectedImage>>(emptyList()) }
    var progress by remember { mutableStateOf<String?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var captchaImage by remember { mutableStateOf<ByteArray?>(null) }
    var captchaCode by remember { mutableStateOf("") }

    val hint = when {
        target.id.isNotEmpty() -> "回复: ${target.title}"
        target.title.isNotEmpty() -> "发布于: ${target.title}"
        else -> "发布动态"
    }
    val canPublish = input.text.replace("\n", "").isNotEmpty() && !publishing

    // The sheet animates in before the field exists; the delay and the guard mirror the
    // Flutter client's deferred requestFocus.
    LaunchedEffect(Unit) {
        delay(300)
        input.focus()
    }

    val pickImages = rememberImagePicker(
        onLoading = { progress = it },
        onPicked = { picked ->
            val room = 9 - images.size
            if (picked.size > room) context.toast("最多选择9张图片")
            images = images + picked.take(room)
        },
    )

    suspend fun publish() {
        if (publishing) return
        publishing = true
        try {
            var pic = ""
            if (images.isNotEmpty()) {
                progress = "准备上传图片"
                pic = OssUpload.upload(
                    repository = app.repository,
                    images = images,
                    onProgress = { index, total -> progress = "正在上传图片 $index/$total" },
                ).joinToString(",")
            }
            progress = "正在发布"
            val fields = mutableListOf("message" to input.text)
            if (target.id.isEmpty()) {
                val type = if (target.targetType == "apk") "comment" else target.feedType.ifEmpty { "feed" }
                val feedFields = mutableListOf("type" to type)
                if (target.targetType.isNotEmpty()) feedFields += "targetType" to target.targetType
                if (target.targetId.isNotEmpty()) feedFields += "targetId" to target.targetId
                feedFields += "message" to input.text
                feedFields += "status" to if (onlySelf) "-1" else "1"
                feedFields += "pic" to pic
                val result = app.repository.createFeed(feedFields, emptyList())
                progress = null
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    context.toast("发布成功")
                    onPosted()
                }
            } else {
                fields += "replyAndForward" to if (onlySelf) "1" else "0"
                fields += "pic" to pic
                val result = app.repository.postReply(target.id, target.type, fields, emptyList())
                progress = null
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    context.toast("回复成功")
                    onPosted()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failure = ApiFailure.from(e)
            progress = null
            if (failure.kind == ApiFailureKind.Captcha) {
                loadCaptchaImage(app, context) { captchaImage = it }
            } else {
                context.toast(failure.message)
            }
        } finally {
            publishing = false
            progress = null
        }
    }

    // The composer rides just above the keyboard (8dp clear of it) and has no drag handle. The
    // lift is animated here instead of being left to imePadding: a dialog window receives the
    // keyboard inset as one jump, so the panel used to teleport between the two resting places
    // while the keyboard itself slid up. Animating the margin keeps the panel glued to the
    // keyboard on the way up and lets it settle back down when the keyboard goes away.
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val bottomBarBottom = WindowInsets.navigationBars.getBottom(density) +
        WindowInsets.captionBar.getBottom(density)
    val liftTarget = with(density) { maxOf(imeBottom, bottomBarBottom).toDp() }
    val lift by animateDpAsState(
        targetValue = liftTarget,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "composerLift",
    )

    // 回复某人时，这块面板从底部升起，和别的底部弹层一样；发评论时，它由右下角那颗球展开 ——
    // 展开用的是遮罩：面板的版式从头到尾都是最终尺寸，只有可见区域从 56dp 的圆角方形长满整块，
    // 于是内容不会被反复重排，第一帧的轮廓和球严丝合缝，中间不存在两个物体的交接。
    val fromBall = target.type != "reply"
    // 面板的右下角对到球真正量到的位置上。Scaffold 把球放在 inset 之上 FabSpacing(12dp)、
    // 右边缘内 12dp，弹窗窗口自己拿到的 inset 又是另一份，两边各算一遍就会差出几 dp，
    // 露出球的一小片下摆。量一次就没有这个问题，顺带面板也跟着宽了。
    // 读一次就作废：只有从球点开的这一次才该对齐到球，从别处（底栏的"发布动态"）打开时
    // 要退回默认留白，否则会去对齐上一次留下的旧位置。
    val ballRect = remember {
        ComposerBallAnchor.rect.also { ComposerBallAnchor.rect = null }
    }
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val restLift = with(density) { bottomBarBottom.toDp() }
    val cornerPadEnd = if (ballRect != null && windowWidthPx > 0) {
        with(density) { (windowWidthPx - ballRect.right).toDp() }
    } else {
        16.dp
    }
    val cornerPadBottom = if (ballRect != null && windowHeightPx > 0) {
        with(density) { (windowHeightPx - ballRect.bottom).toDp() }
    } else {
        16.dp + restLift
    }
    // 面板底边 = 球的底边 + 键盘比归位 inset 多出来的那一截。
    val panelBottomPad = cornerPadBottom - restLift + lift
    val panelWidth = if (windowWidthPx > 0) {
        with(density) { (windowWidthPx - (cornerPadEnd + 16.dp).toPx()).toDp() }
            .coerceAtMost(DialogDefaults.MaxWidth)
    } else {
        320.dp
    }
    val ballSide = if (ballRect != null && ballRect.width > 0f) {
        with(density) { ballRect.width.toDp() }
    } else {
        56.dp
    }
    // 圆角必须 ≥ 球的半径，否则圆角那段弧会切进球里 —— 面板再大也压不住，球会从弧的内侧
    // 露出一条边（COUI 的 19dp 就是这样，28dp 才刚好相切）。这里多留 2dp 余量。
    val panelCorner = ballSide / 2 + 2.dp
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val panelHeight = if (panelSize.height > 0) with(density) { panelSize.height.toDp() } else 200.dp

    // fold: 0 = 收起（球，或面板还在屏幕下方），1 = 到位。
    val fold = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // 弹簧起手是慢的：球先鼓起来，再被撑成面板，落位时有一点点回弹 —— 比匀速 tween 更像"被拉伸"。
        fold.animateTo(
            1f,
            spring(dampingRatio = 0.78f, stiffness = 400f, visibilityThreshold = 0.001f),
        )
    }
    var closing by remember { mutableStateOf(false) }
    val shown = remember { mutableStateOf(true) }
    fun close() {
        if (closing || publishing) return
        closing = true
        focusManager.clearFocus()
        scope.launch {
            // 收起之前先等面板落回球的位置：键盘收起来时 lift 还要走完一程，如果这时候就开始折叠，
            // 折叠的终点会落在球的下方（面板当时所在的位置），于是收尾那一下对不上球。
            withTimeoutOrNull(900) {
                snapshotFlow { lift.value - liftTarget.value }
                    .first { abs(it) < 1.5f }
            }
            fold.animateTo(0f, tween(durationMillis = 220, easing = FastOutSlowInEasing))
            shown.value = false
            onDismiss()
        }
    }

    val grow = fold.value.coerceIn(0f, 1f)
    // 可见区域右下角锚定，从球的边长长到整块面板；圆角从球的半径收到面板自己的圆角。
    // 横向比纵向先走一点：展开前半程是一块横向拉长的圆角块，后半程才补足高度，
    // 观感上是球被拽开，而不是一个正方形整体放大。
    val revealWidth = lerp(ballSide, panelWidth, grow.coerceAtLeast(0f).pow(0.85f))
    val revealHeight = lerp(ballSide, panelHeight, grow.coerceAtLeast(0f).pow(1.2f))
    val revealRadius = lerp(
        ballSide / 2,
        panelCorner,
        ((grow - 0.45f) / 0.55f).coerceIn(0f, 1f),
    )
    val panelShape = remember(fromBall, revealWidth, revealHeight, revealRadius) {
        if (fromBall) {
            CornerRevealShape(revealWidth, revealHeight, revealRadius)
        } else {
            RoundedCornerShape(panelCorner)
        }
    }
    val panelColor = DialogDefaults.backgroundColor()
    // 表面在球的位置上淡入：那几帧轮廓还和球一模一样，看上去是球自己换了材质。
    val surfaceAlpha = if (fromBall) (grow / 0.22f).coerceIn(0f, 1f) else grow
    val contentAlpha = ((grow - 0.4f) / 0.6f).coerceIn(0f, 1f)
    val slide = if (fromBall) 0.dp else (panelHeight + 40.dp) * (1f - grow)
    val scrimColor = COUITheme.colorScheme.windowDimming

    BackHandler(enabled = true) { close() }

    COUIPopupUtils.DialogLayout(
        visible = shown,
        enterTransition = EnterTransition.None,
        exitTransition = ExitTransition.None,
        enableWindowDim = false,
        renderInRootScaffold = true,
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor.copy(alpha = scrimColor.alpha * grow))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() },
            )
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    // 右下留白就是球的留白，收起时面板正好落在球上。
                    .padding(end = cornerPadEnd, bottom = panelBottomPad)
                    .offset(y = slide)
                    .width(panelWidth)
                    .onSizeChanged { panelSize = it }
                    .dropShadow(
                        shape = panelShape,
                        shadow = BottomFloatingNavigationBarDefaults.BarShadow,
                    )
                    .clip(panelShape)
                    .background(panelColor.copy(alpha = panelColor.alpha * surfaceAlpha))
                    .graphicsLayer {
                        alpha = contentAlpha
                        val scale = 0.96f + 0.04f * grow
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
                    .padding(16.dp),
            ) {
                Text(
                    hint,
                    modifier = Modifier.fillMaxWidth(),
                    style = COUITheme.textStyles.title4,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                // Only the middle scrolls when the keyboard and the emoji panel leave little room; the
                // title and the tool row stay put.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(COUITheme.colorScheme.surfaceContainerHigh),
                    ) {
                        EmojiEditText(
                            state = input,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            hint = "说点什么…",
                            maxCount = 500,
                            minLines = 4,
                            maxLines = 6,
                            textStyle = COUITheme.textStyles.body1,
                            onFocused = { emojiPanel = false },
                        )
                        Text(
                            "${input.text.length}/500",
                            style = COUITheme.textStyles.footnote2,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(end = 12.dp)
                                .padding(bottom = 8.dp),
                        )
                    }
                    if (images.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            images.forEach { image ->
                                Box(Modifier.size(64.dp)) {
                                    AsyncImage(
                                        model = java.io.File(image.path),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                                    )
                                    IconButton(
                                        onClick = { images = images - image },
                                        modifier = Modifier.align(Alignment.TopEnd).size(20.dp),
                                    ) {
                                        Icon(COUIIcons.Close, contentDescription = "移除", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (emojiPanel) {
                        EmojiPanel(
                            modifier = Modifier.height(180.dp).padding(top = 8.dp),
                            onPick = { emoji -> input.insert(emoji) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        // The reply-and-forward toggle sits above the tools, at the window's bottom left.
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onlySelf = !onlySelf }
                                .padding(start = 4.dp, end = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                state = if (onlySelf) ToggleableState.On else ToggleableState.Off,
                                onClick = null,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                if (target.id.isEmpty()) "仅自己可见" else "回复并转发",
                                style = COUITheme.textStyles.body2,
                                color = if (onlySelf) {
                                    COUITheme.colorScheme.onSurface
                                } else {
                                    COUITheme.colorScheme.onSurfaceVariantSummary
                                },
                                maxLines = 1,
                            )
                        }
                        Row(
                            Modifier.height(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolbarIcon(COUIIcons.Photos, selected = false) { pickImages() }
                            Spacer(Modifier.width(8.dp))
                            ToolbarIcon(AppIcons.EmojiEmotions, selected = emojiPanel) {
                                emojiPanel = !emojiPanel
                                if (emojiPanel) {
                                    input.clearFocus()
                                    focusManager.clearFocus()
                                } else {
                                    input.focus()
                                }
                            }
                        }
                    }
                    LoadingButton(
                        text = "发布",
                        onClick = { scope.launch { publish() } },
                        enabled = canPublish,
                        isLoading = publishing,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.padding(bottom = 4.dp),
                        minWidth = 64.dp,
                        minHeight = 32.dp,
                        cornerRadius = 10.dp,
                    )
                }
            }
        }
    }

    OverlayLoadingDialog(show = progress != null, text = progress)

    captchaImage?.let { bytes ->
        CaptchaDialog(
            bytes = bytes,
            code = captchaCode,
            onCodeChange = { captchaCode = it.filter { ch -> ch.isLetterOrDigit() }.take(4) },
            onCancel = { captchaImage = null },
            onConfirm = {
                val code = captchaCode
                captchaImage = null
                scope.launch {
                    try {
                        val result = app.repository.requestValidate(code)
                        if (result == "验证通过") {
                            publish()
                        } else {
                            context.toast(result)
                            loadCaptchaImage(app, context) { captchaImage = it }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        context.toast(ApiFailure.from(e).message)
                        loadCaptchaImage(app, context) { captchaImage = it }
                    }
                }
            },
        )
    }
}

private suspend fun loadCaptchaImage(
    app: app.awavenue.c001apk.C001apkApp,
    context: android.content.Context,
    onLoaded: (ByteArray) -> Unit,
) {
    try {
        val bytes = app.repository.captchaImage()
        if (bytes.isNotEmpty()) onLoaded(bytes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        context.toast("无法获取验证码：${ApiFailure.from(e).message}")
    }
}

@Composable
private fun ToolbarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Quick press-in, then a springy release that overshoots past 1 before settling.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 70)
        } else {
            spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)
        },
        label = "toolbarIconPressScale",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .squircleSurface(
                color = if (selected) COUITheme.colorScheme.secondaryContainer else Color.Unspecified,
                cornerRadius = 8.dp,
            )
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected) {
                COUITheme.colorScheme.onSecondaryContainer
            } else {
                COUITheme.colorScheme.onSurfaceVariantSummary
            },
        )
    }
}

@Composable
internal fun EmojiPanel(modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    val emojis = remember { EmojiFiles.entries.toList() }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
        emojis.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (code, file) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 5.dp)
                            .combinedClickable(onClick = { onPick(code) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/emojis/$file",
                            contentDescription = code,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/** The legacy picture captcha: a 4-character code next to the image the server just drew. */
@Composable
private fun CaptchaDialog(
    bytes: ByteArray,
    code: String,
    onCodeChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = "验证码",
        onDismissRequest = onCancel,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = bytes,
                    contentDescription = "验证码",
                    modifier = Modifier.weight(1f).height(64.dp),
                )
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = code,
                    onValueChange = onCodeChange,
                    modifier = Modifier.weight(1f),
                    label = "验证码",
                    maxCount = 4,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(text = "取消", onClick = onCancel)
                Spacer(Modifier.width(8.dp))
                TextButton(text = "验证并继续", onClick = onConfirm)
            }
        }
    }
}

/**
 * 面板展开时的可见区域：一块锚在右下角、从球的大小长到整块面板的圆角矩形。这里用遮罩而不是缩放，
 * 因为缩放会把圆角压成椭圆，也会让面板里的内容每帧重新排版。
 */
private class CornerRevealShape(
    private val width: Dp,
    private val height: Dp,
    private val radius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val visibleWidth = min(with(density) { width.toPx() }, size.width)
        val visibleHeight = min(with(density) { height.toPx() }, size.height)
        val corner = with(density) { radius.toPx() }
            .coerceIn(0f, min(visibleWidth, visibleHeight) / 2f)
        return Outline.Rounded(
            RoundRect(
                rect = Rect(
                    left = size.width - visibleWidth,
                    top = size.height - visibleHeight,
                    right = size.width,
                    bottom = size.height,
                ),
                cornerRadius = CornerRadius(corner, corner),
            ),
        )
    }
}
