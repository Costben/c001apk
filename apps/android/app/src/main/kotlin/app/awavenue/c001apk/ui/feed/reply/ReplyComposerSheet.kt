package app.awavenue.c001apk.ui.feed.reply

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Draft
import app.awavenue.c001apk.data.DraftImage
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.appDialogButtonColors
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.detail.OssUpload
import app.awavenue.c001apk.ui.feed.detail.OssUploadModel
import app.awavenue.c001apk.ui.feed.detail.SelectedImage
import app.awavenue.c001apk.ui.feed.detail.rememberImagePicker
import app.awavenue.c001apk.ui.text.EmojiEditText
import app.awavenue.c001apk.ui.text.EmojiSections
import app.awavenue.c001apk.ui.text.rememberEmojiInputState
import coil3.compose.AsyncImage
import io.github.suqi8.coui.kmp.basic.Checkbox
import io.github.suqi8.coui.kmp.basic.ChipTabData
import io.github.suqi8.coui.kmp.basic.ChipTabGroup
import io.github.suqi8.coui.kmp.basic.GradientStrokeStyle
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InnerShadowStyle
import io.github.suqi8.coui.kmp.basic.MaterialStrokeParams
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.materialShadowEdge
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Lock
import io.github.suqi8.coui.kmp.icon.extended.Ok
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import io.github.suqi8.coui.kmp.squircle.squircleSurface
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

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
 *
 * [draft] reopens a composition that was put aside: its text, pictures and target take the place
 * of the ones [target] would start with, and leaving the sheet again updates that same draft
 * instead of filing a second one. Closing a sheet with unsaved work asks whether to keep it.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
fun ReplyComposerSheet(
    target: ReplyTarget,
    onDismiss: () -> Unit,
    onPosted: () -> Unit,
    draft: Draft? = null,
) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // A resumed draft carries its own target and content; a fresh sheet starts from the target it
    // was opened for. Everything below reads this one, so publishing stays single-minded.
    val activeTarget = draft?.let {
        ReplyTarget(
            id = it.replyId,
            type = it.replyType,
            targetType = it.targetType,
            targetId = it.targetId,
            title = it.title,
            feedType = it.feedType,
        )
    } ?: target

    val initialText = draft?.text
        ?: if (activeTarget.targetType == "tag") "#${activeTarget.title}# " else ""
    val input = rememberEmojiInputState(initialText)
    var onlySelf by remember { mutableStateOf(draft?.onlySelf == true) }
    var emojiPanel by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 谁可以看 is asked in a dialog over the panel, the way the reference client does it.
    var visibilityMenu by remember { mutableStateOf(false) }
    var images by remember {
        mutableStateOf(
            draft?.images.orEmpty().map { image ->
                SelectedImage(image.path, OssUploadModel(image.name, image.resolution, image.md5))
            },
        )
    }
    // The id the draft box files this composition under: a resumed draft keeps its own, so
    // saving twice replaces rather than duplicates.
    val draftId = remember { draft?.id ?: UUID.randomUUID().toString() }
    val initialImages = remember { draft?.images.orEmpty().map { it.path } }
    var askSave by remember { mutableStateOf(false) }
    var savingDraft by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<String?>(null) }
    var publishing by remember { mutableStateOf(false) }
    var captchaImage by remember { mutableStateOf<ByteArray?>(null) }
    var captchaCode by remember { mutableStateOf("") }

    val hint = when {
        activeTarget.id.isNotEmpty() -> "回复: ${activeTarget.title}"
        activeTarget.title.isNotEmpty() -> "发布于: ${activeTarget.title}"
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

    // The draft question and the exit path sit above publish(), so a successful post can leave
    // through the same animation as a plain close.
    var shown by remember { mutableStateOf(true) }
    var closing by remember { mutableStateOf(false) }
    var afterExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** Unsaved work: text that moved away from what the sheet opened with, or a changed picture set. */
    fun hasUnsavedWork(): Boolean =
        input.text.trim() != initialText.trim() || images.map { it.path } != initialImages

    /** Slides the panel out; [then] runs once it has left the screen. */
    fun leave(then: () -> Unit) {
        if (closing) return
        closing = true
        focusManager.clearFocus()
        afterExit = then
        shown = false
    }

    /** Leaving: work worth keeping is offered to the draft box first, an untouched sheet just folds. */
    fun requestClose() {
        if (closing || publishing) return
        if (askSave) {
            // The question is the library's dialog now, so its own answers settle it; this only
            // catches a backdrop tap that lands while it is up.
            askSave = false
            return
        }
        if (hasUnsavedWork()) {
            input.clearFocus()
            focusManager.clearFocus()
            askSave = true
        } else {
            leave(onDismiss)
        }
    }

    /** 继续编辑: the panel never moved, so all it takes is the keyboard back. */
    fun keepEditing() {
        askSave = false
        input.focus()
    }

    /** A second exit while the question is up: the composition is dropped, not filed. */
    fun discardDraft() {
        askSave = false
        leave(onDismiss)
    }

    /** 谁可以看 opens with the keyboard out of the way; picking a scope puts the caret back. */
    fun openVisibility() {
        input.clearFocus()
        focusManager.clearFocus()
        visibilityMenu = true
    }

    /** Keeps the scope, closes the dialog and returns to the field. */
    fun pickVisibility(selfOnly: Boolean) {
        onlySelf = selfOnly
        visibilityMenu = false
        input.focus()
    }

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
            if (activeTarget.id.isEmpty()) {
                val type = if (activeTarget.targetType == "apk") "comment" else activeTarget.feedType.ifEmpty { "feed" }
                val feedFields = mutableListOf("type" to type)
                if (activeTarget.targetType.isNotEmpty()) feedFields += "targetType" to activeTarget.targetType
                if (activeTarget.targetId.isNotEmpty()) feedFields += "targetId" to activeTarget.targetId
                feedFields += "message" to input.text
                feedFields += "status" to if (onlySelf) "-1" else "1"
                feedFields += "pic" to pic
                val result = app.repository.createFeed(feedFields, emptyList())
                progress = null
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    context.toast("发布成功")
                    app.drafts.remove(draftId)
                    leave(onPosted)
                }
            } else {
                fields += "replyAndForward" to if (onlySelf) "1" else "0"
                fields += "pic" to pic
                val result = app.repository.postReply(activeTarget.id, activeTarget.type, fields, emptyList())
                progress = null
                if (result.message.isNotEmpty()) {
                    context.toast(result.message)
                } else {
                    context.toast("回复成功")
                    app.drafts.remove(draftId)
                    leave(onPosted)
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

    fun saveToDrafts() {
        if (savingDraft) return
        savingDraft = true
        scope.launch {
            val saved = runCatching {
                app.drafts.save(
                    id = draftId,
                    text = input.text,
                    images = images.map {
                        DraftImage(it.path, it.model.name, it.model.resolution, it.model.md5)
                    },
                    replyId = activeTarget.id,
                    replyType = activeTarget.type,
                    targetType = activeTarget.targetType,
                    targetId = activeTarget.targetId,
                    title = activeTarget.title,
                    feedType = activeTarget.feedType,
                    onlySelf = onlySelf,
                )
            }.getOrNull()
            savingDraft = false
            askSave = false
            if (saved == null) {
                context.toast("草稿保存失败")
            } else {
                context.toast("已保存到草稿箱")
                leave(onDismiss)
            }
        }
    }

    // The panel is the ColorOS 17 bottom-panel template: the sheet supplies the frosted surface and
    // its material rim, while the toolbar, the title, the status line and the capsule action are
    // laid out here at the template's measured rhythm. The drag bar stays hidden — the toolbar is
    // the panel's top row, and the sheet keeps its slim strip so the panel remains draggable.
    val density = LocalDensity.current
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    // The panel runs to the window edge and pads itself clear of the gesture bar; sitting on the
    // IME, the same gap would read as dead space, so it shrinks to the native keyboard spacing.
    // The two gaps are blended over the last stretch of the keyboard's travel instead of switched
    // the moment the IME reaches zero. A switch lands on a frame of its own — the keyboard's inset
    // has already finished animating by then, so the panel used to fall to the bottom and only
    // then jump 28dp taller, reading as a flash instead of a grow.
    val keyboardGapProgress = (keyboard / ComposerPanel.KeyboardGapRange).coerceIn(0f, 1f)
    val bottomGap = ComposerPanel.BottomInset +
        (ComposerPanel.KeyboardBottomInset - ComposerPanel.BottomInset) * keyboardGapProgress

    OverlayBottomSheet(
        show = shown,
        showDragHandle = false,
        insideMargin = DpSize(ComposerPanel.SideInset, bottomGap),
        onDismissRequest = { requestClose() },
        onDismissFinished = { afterExit?.invoke() },
        startAction = {
            CloseCapsule(onClick = { requestClose() })
        },
        endAction = {
            Box {
                OverflowButton(onClick = { menuOpen = true })
                MoreMenu(
                    show = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    entries = listOf(
                        // An untouched sheet has nothing worth filing; the box would just collect
                        // blanks, so the entry says so instead.
                        "保存到草稿箱" to {
                            if (input.text.isNotBlank() || images.isNotEmpty()) {
                                saveToDrafts()
                            } else {
                                context.toast("还没有写内容")
                            }
                        },
                    ),
                )
            }
        },
    ) {
        // Back is answered inside the panel rather than left to the sheet's own handler: that one
        // slides the panel away before it asks, and an unsaved composition has to be offered to the
        // draft box while the work is still in place. With the question up, an exit drops it.
        BackHandler(enabled = true) {
            // Swallow the exit that lands while the panel is already folding, or while a post is
            // in flight: neither may fall through to the screen underneath.
            if (closing) return@BackHandler
            if (askSave) discardDraft() else requestClose()
        }

        Spacer(Modifier.height(ComposerPanel.ToolbarGap))
        // The trimmed line box is taller than the measured ink box, so the title is centred inside
        // a pinned-height box and measured unbounded, which keeps ascenders and descenders whole.
        Box(
            Modifier.fillMaxWidth().height(ComposerPanel.TitleHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = hint,
                modifier = Modifier.wrapContentHeight(unbounded = true),
                style = trimmedLineHeight,
                fontSize = 28.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = COUITheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(ComposerPanel.StatusGap))
        StatusLine(text = "${input.text.length}/${ComposerPanel.MaxMessage}")
        Spacer(Modifier.height(ComposerPanel.StatusToBodyGap))
        // The sheet hands the content whatever is left once its toolbar is in, so the field is
        // capped by that leftover instead of by a guess about the window: with the keyboard up the
        // panel rides on it, and a field sized from the full window would push the action off the
        // bottom. Everything below the field is subtracted here, since it shares the same space.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val belowField = (if (images.isNotEmpty()) ComposerPanel.ImagesHeight else 0.dp) +
                (if (emojiPanel) ComposerPanel.EmojiHeight else 0.dp) +
                ComposerPanel.ToolsGap + ComposerPanel.ToolsHeight +
                ComposerPanel.ActionGap + ComposerPanel.ActionHeight
            val editorMax = (maxHeight - belowField).coerceAtLeast(ComposerPanel.MinEditorHeight)
            // The card itself is capped rather than scrolled: a scroll viewport shorter than the
            // field would cut the card off square at the bottom, and the field already scrolls its
            // own text when it runs out of lines.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = editorMax)
                    .squircleSurface(
                        color = COUITheme.colorScheme.surfaceContainer,
                        cornerRadius = ComposerPanel.EditorRadius,
                    ),
            ) {
                EmojiEditText(
                    state = input,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    hint = "说点什么…",
                    maxCount = ComposerPanel.MaxMessage,
                    minLines = 4,
                    maxLines = 6,
                    textStyle = COUITheme.textStyles.body1,
                    onFocused = { emojiPanel = false },
                )
            }
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
            // 212dp lands exactly on a row boundary: a 32dp chip row, a 6dp gap, four 42dp grid
            // rows and the closing 6dp, so switching tabs does not resize the sheet and the last
            // row is never sliced.
            EmojiPanel(
                modifier = Modifier.height(212.dp).padding(top = 8.dp),
                onPick = { emoji -> input.insert(emoji) },
            )
        }

        Spacer(Modifier.height(ComposerPanel.ToolsGap))
        // One row for the field's tools: pictures and emoji on the start side, the visibility
        // toggle on the end side, where the template puts its non-primary controls.
        Row(
            Modifier.fillMaxWidth().height(ComposerPanel.ToolsHeight),
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
            Spacer(Modifier.weight(1f))
            if (activeTarget.id.isEmpty()) {
                // 谁可以看: the official publish form's visibility, shown as the current scope so the
                // choice is visible without opening the sheet.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { openVisibility() }
                        .padding(start = 8.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (onlySelf) COUIIcons.Lock else COUIIcons.Community,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (onlySelf) {
                            COUITheme.colorScheme.onSurface
                        } else {
                            COUITheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (onlySelf) "仅自己可见" else "所有人可见",
                        style = COUITheme.textStyles.body2,
                        color = if (onlySelf) {
                            COUITheme.colorScheme.onSurface
                        } else {
                            COUITheme.colorScheme.onSurfaceVariantSummary
                        },
                        maxLines = 1,
                    )
                }
            } else {
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
                        "回复并转发",
                        style = COUITheme.textStyles.body2,
                        color = if (onlySelf) {
                            COUITheme.colorScheme.onSurface
                        } else {
                            COUITheme.colorScheme.onSurfaceVariantSummary
                        },
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(ComposerPanel.ActionGap))
        ActionCapsule(
            text = "发布",
            enabled = canPublish,
            onClick = { scope.launch { publish() } },
        )
    }

    // The question the close path raises, as the library's centered alert dialog: 继续编辑 is the
    // negative answer, 保存 the positive one, and an exit while it is up drops the composition
    // instead of filing it.
    OverlayAlertDialog(
        buttonColors = appDialogButtonColors(),
        show = askSave,
        title = "保存到草稿箱？",
        summary = "这次编辑还没发布，存进草稿箱下次可以接着写。",
        cancelText = "继续编辑",
        confirmText = "保存",
        onCancel = { keepEditing() },
        onConfirm = { saveToDrafts() },
    ) {
        BackHandler(enabled = askSave) { discardDraft() }
    }

    // 谁可以看: the publish form's visibility, asked as a dialog over the panel. Only a fresh feed
    // has it; a reply carries 回复并转发 in the same corner instead.
    if (visibilityMenu) {
        VisibilityDialog(
            onlySelf = onlySelf,
            onPick = { pickVisibility(it) },
            onDismiss = {
                visibilityMenu = false
                input.focus()
            },
        )
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

/**
 * Geometry of the ColorOS 17 panel template, taken from the library's demo at the 3.875 px/dp it was
 * measured on: a 10dp handle strip, a 50dp toolbar carrying a 60x40 close capsule and a 40dp
 * overflow circle, a 28sp title, a 12sp status line, the body, one tool row and a 44dp capsule
 * action, closing on a 40dp bottom inset.
 */
private object ComposerPanel {
    /**
     * The field's ceiling. The old form stopped at 500, but the server takes much longer bodies —
     * an 1832-character feed posts fine — so the sheet allows a long post and the template importer
     * writes into it.
     */
    const val MaxMessage = 2000

    val SideInset = 16.dp

    /** Inset between the capsule action and the window bottom: 155px. */
    val BottomInset = 40.dp

    /** The same gap while the sheet sits on the keyboard, where 40dp would read as dead space. */
    val KeyboardBottomInset = 12.dp

    /**
     * How much of the keyboard's travel the foot gap spends easing from [BottomInset] to
     * [KeyboardBottomInset]. The gap is a continuous function of the IME inset, so the panel's
     * foot keeps moving while the keyboard leaves instead of settling first and growing after.
     */
    val KeyboardGapRange = 80.dp

    /** Height of the close capsule and the overflow circle: 155px. */
    val ToolbarButtonHeight = 40.dp

    /** Distance from the toolbar's foot to the title ink: 80px. */
    val ToolbarGap = 3.dp

    /** Ink height of the 28sp title: 105px. */
    val TitleHeight = 27.dp

    /** Gap between the title and the status line: 29px. */
    val StatusGap = 12.dp

    /** Ink height of the 12sp status line: 44px. */
    val StatusHeight = 12.dp

    val StatusToBodyGap = 16.dp

    /** The list-row radius the template uses for its squircle cards. */
    val EditorRadius = 23.dp

    /** The field keeps at least three lines plus its own padding before it starts scrolling. */
    val MinEditorHeight = 88.dp

    /** A 64dp thumbnail row plus its 10dp gap. */
    val ImagesHeight = 74.dp

    /** The emoji grid's 212dp plus its 8dp gap. */
    val EmojiHeight = 220.dp

    val ToolsHeight = 40.dp
    val ToolsGap = 16.dp
    val ActionGap = 20.dp

    /** Height of the capsule action: 171px. */
    val ActionHeight = 44.dp

    /** The toolbar buttons' rim: 0.5 alpha over 2dp, as the template measured it. */
    val ToolbarButtonStroke = MaterialStrokeParams(
        edgeAlpha = 0.5f,
        edgeWidth = 2.dp,
        shadowFadeIn = 0.03f,
    )

    /** `couiColorPrimary` measured on the live capsule: #1A8CFE at the top, #3198FF at the bottom. */
    val ActionGradient = listOf(Color(0xFF1A8CFE), Color(0xFF3198FF))

    /** The dark-mode toolbar fill: 12% white over the dark panel. */
    val ToolbarFillDark = Color(0x20FFFFFF)

    /** The light-mode halo round a toolbar button: dark, deepest below the button. */
    val ToolbarHaloLight = Shadow(
        radius = 16.dp,
        color = Color.Black,
        offset = DpOffset(0.dp, 6.dp),
        alpha = 0.12f,
    )

    /** The dark-mode halo: the same geometry in faint white. */
    val ToolbarHaloDark = Shadow(
        radius = 16.dp,
        color = Color.White,
        offset = DpOffset(0.dp, 6.dp),
        alpha = 0.05f,
    )

    /** The capsule's 1dp edge light: white at both ends, clear across the middle. */
    val ActionEdgeGradient = listOf(
        Color.White.copy(alpha = 0.18f),
        Color.Transparent,
        Color.White.copy(alpha = 0.15f),
    )
}

/**
 * The two toolbar buttons' shared dress. Light mode has no fill of its own — the capsule reads the
 * panel underneath — and carries a dark halo that is deepest under its bottom edge; dark mode
 * swaps in a 12% white fill and a faint light halo. The halo never paints inside the capsule, so it
 * is drawn with the capsule cut out: a plain gradient started at the bottom edge left a hard line
 * across the button's foot.
 */
@Composable
private fun PanelToolbarButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = COUITheme.colorScheme.background.luminance() < 0.5f
    val fill = if (isDark) ComposerPanel.ToolbarFillDark else Color.Transparent
    val halo = if (isDark) ComposerPanel.ToolbarHaloDark else ComposerPanel.ToolbarHaloLight
    Box {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val capsule = Path().apply {
                        addOutline(CircleShape.createOutline(size, layoutDirection, this@drawWithContent))
                    }
                    clipPath(capsule, ClipOp.Difference) { this@drawWithContent.drawContent() }
                }
                .dropShadow(CircleShape, halo),
        )
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(fill)
                .materialShadowEdge(
                    stroke = ComposerPanel.ToolbarButtonStroke,
                    edgeStyle = GradientStrokeStyle.EDGE_STYLE_1,
                    shadowStyle = InnerShadowStyle.SHADOW_STYLE_1,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/** The capsule close button in the panel toolbar: 60x40dp, full radius. */
@Composable
private fun CloseCapsule(onClick: () -> Unit) {
    PanelToolbarButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .height(ComposerPanel.ToolbarButtonHeight)
                // 16dp a side keeps the capsule at the native 60dp for a two-character label.
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "关闭",
                style = trimmedLineHeight,
                fontSize = 14.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                color = COUITheme.colorScheme.onBackground,
            )
        }
    }
}

/** The circular overflow button in the panel toolbar: a 40dp circle with three dots. */
@Composable
private fun OverflowButton(onClick: () -> Unit) {
    val ink = COUITheme.colorScheme.onBackground
    PanelToolbarButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(ComposerPanel.ToolbarButtonHeight)
                .semantics { contentDescription = "更多" },
            contentAlignment = Alignment.Center,
        ) {
            OverflowDots(ink)
        }
    }
}

/** The three stacked dots of the overflow button: 3.5dp wide, 17.5dp tall. */
@Composable
private fun OverflowDots(ink: Color) {
    Canvas(modifier = Modifier.size(width = 3.5.dp, height = 17.5.dp)) {
        val radius = size.width / 2f
        val pitch = (size.height - size.width) / 2f
        repeat(3) { index ->
            drawCircle(
                color = ink,
                radius = radius,
                center = Offset(size.width / 2f, radius + pitch * index),
            )
        }
    }
}

/** The `● 12sp` line under the title: a 6dp dot, a 5dp gap and the state text. */
@Composable
private fun StatusLine(text: String) {
    val dotColor = COUITheme.colorScheme.onSurfaceVariantActions
    Row(
        modifier = Modifier.height(ComposerPanel.StatusHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color = dotColor)
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = trimmedLineHeight,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            color = COUITheme.colorScheme.onBackground,
        )
    }
}

/**
 * The capsule call-to-action: 44dp tall, full radius, filled with the ColorOS 17 primary gradient.
 * A disabled capsule keeps its shape and fades, so the action stays where the thumb expects it.
 */
@Composable
private fun ActionCapsule(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComposerPanel.ActionHeight)
            // The fade has to wrap the fill as well as the label, so it is the outermost layer:
            // applied after the background it would only wash out the text.
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(CircleShape)
            .background(Brush.verticalGradient(ComposerPanel.ActionGradient))
            // The native capsule is lit along its top and bottom edge only, fading across the middle.
            .border(
                BorderStroke(1.dp, Brush.verticalGradient(ComposerPanel.ActionEdgeGradient)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = trimmedLineHeight,
            fontSize = 16.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

/**
 * The library's line-height style keeps the full font box, which renders taller than the panel's
 * measured line boxes. Every text slot here is pinned to its measured height, so the trimmed style
 * keeps the glyphs centred inside them.
 */
private val trimmedLineHeight: TextStyle = TextStyle(
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

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
    val tabs = remember { EmojiSections.map { ChipTabData(it.label) } }
    var section by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxWidth()) {
        ChipTabGroup(
            tabs = tabs,
            selectedIndex = section,
            onSelectedChange = { section = it },
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
        ) {
            EmojiSections[section].emojis.chunked(7).forEach { row ->
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
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** The legacy picture captcha: a 4-character code next to the image the server just drew. */
@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun CaptchaDialog(
    bytes: ByteArray,
    code: String,
    onCodeChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayAlertDialog(
        buttonColors = appDialogButtonColors(),
        show = true,
        title = "验证码",
        summary = "输入图片里的四个字符，验证通过后继续发布。",
        cancelText = "取消",
        confirmText = "验证并继续",
        onCancel = onCancel,
        onConfirm = onConfirm,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
    }
}


/**
 * 谁可以看: the two scopes the official publish form accepts. `publish_status` is derived from the
 * picked scope in the repository, so the sheet only carries the choice.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun VisibilityDialog(
    onlySelf: Boolean,
    onPick: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(show = true, title = "谁可以看", onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            VisibilityOption(
                icon = COUIIcons.Community,
                title = "所有人",
                selected = !onlySelf,
                onClick = { onPick(false) },
            )
            VisibilityOption(
                icon = COUIIcons.Lock,
                title = "仅自己",
                selected = onlySelf,
                onClick = { onPick(true) },
            )
        }
    }
}

@Composable
private fun VisibilityOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, Modifier.weight(1f), style = COUITheme.textStyles.body1)
        if (selected) {
            Icon(
                COUIIcons.Ok,
                contentDescription = "已选择",
                modifier = Modifier.size(20.dp),
                tint = COUITheme.colorScheme.primary,
            )
        }
    }
}
