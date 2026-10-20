package app.awavenue.c001apk.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.data.Protocol
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.AppIcons
import app.awavenue.c001apk.ui.common.MoreMenu
import app.awavenue.c001apk.ui.common.copyText
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.feed.ActionSheetItem
import app.awavenue.c001apk.ui.feed.FooterStatus
import app.awavenue.c001apk.ui.feed.ListStatus
import app.awavenue.c001apk.ui.feed.detail.OssUpload
import app.awavenue.c001apk.ui.feed.detail.SelectedImage
import app.awavenue.c001apk.ui.feed.detail.rememberImagePicker
import app.awavenue.c001apk.ui.feed.reply.EmojiPanel
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import io.github.suqi8.coui.kmp.basic.TopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Copy
import io.github.suqi8.coui.kmp.icon.extended.Delete
import io.github.suqi8.coui.kmp.icon.extended.Email
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.icon.extended.Photos
import io.github.suqi8.coui.kmp.icon.extended.Send
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayLoadingDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The private conversation with one user, ported from the Flutter `ChatPage`. */
@Composable
fun ChatScreen(ukey: String, uid: String, username: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberPageListState("chat")
    val scrollBehavior = rememberPageScrollBehavior("chat")
    val backdrop = rememberGlassBackdrop()

    val viewModel: ChatViewModel = viewModel(key = "chat-$ukey") { ChatViewModel(app.repository, ukey) }

    var input by remember { mutableStateOf(TextFieldValue("")) }
    var emojiPanel by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var messageMenu by remember { mutableStateOf<Entity?>(null) }
    var progress by remember { mutableStateOf<String?>(null) }

    val canSend = input.text.replace("\n", "").isNotEmpty()

    fun settle(error: String) {
        if (error.isNotEmpty()) {
            context.toast(error)
        } else {
            input = TextFieldValue("")
            scope.launch { listState.animateScrollToItem(0) }
        }
    }

    fun sendText() {
        if (progress != null || !canSend) return
        val text = input.text
        scope.launch {
            progress = "正在发送"
            try {
                settle(viewModel.send(uid, text, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            } finally {
                progress = null
            }
        }
    }

    fun uploadAndSend(image: SelectedImage) {
        if (progress != null) return
        scope.launch {
            progress = "正在上传图片"
            try {
                val uploaded = OssUpload.upload(
                    repository = app.repository,
                    images = listOf(image),
                    toUid = uid,
                    uploadBucket = "message",
                    uploadDir = "message",
                    onProgress = { index, total -> progress = "正在上传图片 $index/$total" },
                ).firstOrNull()
                if (uploaded == null) {
                    context.toast("图片上传失败，请检查网络后重试")
                } else {
                    progress = "正在发送"
                    settle(viewModel.send(uid, input.text, uploaded))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                context.toast(ApiFailure.from(e).message)
            } finally {
                progress = null
            }
        }
    }

    // The picker only hands back a batch, so a single picture is taken from the front of it.
    val pickImage = rememberImagePicker(
        maxCount = 1,
        onLoading = { progress = it },
        onPicked = { picked -> picked.firstOrNull()?.let { uploadAndSend(it) } },
    )

    LaunchedEffect(listState, viewModel) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 4
        }.distinctUntilChanged().filter { it }.collect { viewModel.loadMore() }
    }

    LaunchedEffect(viewModel.refreshError) {
        viewModel.refreshError?.let {
            context.toast(it)
            viewModel.refreshError = null
        }
    }

    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                TopAppBar(
                    title = username,
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Box {
                            TopBarIconButton(onClick = { menuOpen = true }) {
                                Icon(COUIIcons.More, contentDescription = "更多")
                            }
                            MoreMenu(
                                show = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                entries = listOf(
                                    "查看用户" to { navigator.push(Route.User(uid)) },
                                    "屏蔽" to { app.blacklist.blockUser(uid) },
                                    "举报" to { navigator.push(Route.Web(Protocol.reportLink(uid, isUser = true))) },
                                ),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                // The composer rides on whichever is taller, so it clears the keyboard while it is
                // up and the navigation bar while it is down.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            Box(Modifier.weight(1f)) {
                when (val status = viewModel.status) {
                    ListStatus.Loading -> CenterBox { InfiniteProgressIndicator() }

                    ListStatus.Empty -> CenterBox {
                        EmptyState(title = "暂无内容", actionText = "刷新", onActionClick = viewModel::refresh)
                    }

                    is ListStatus.Error -> CenterBox {
                        EmptyState(
                            title = "加载失败",
                            subtitle = status.message,
                            actionText = "重试",
                            onActionClick = viewModel::refresh,
                        )
                    }

                    ListStatus.Content -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .glassSource(backdrop)
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        reverseLayout = true,
                    ) {
                        items(viewModel.messages, key = { it.id.ifEmpty { it.entityId } }) { item ->
                            val pic = item.string("message_pic")
                            LaunchedEffect(item.id, pic) {
                                if (pic.isNotEmpty() && !pic.startsWith("http")) viewModel.resolveImage(item.id)
                            }
                            when (item.entityType) {
                                "message" -> ChatCard(
                                    item = item,
                                    onLongPress = { messageMenu = item },
                                    onImageClick = { navigator.push(Route.ImageView(listOf(it))) },
                                )

                                "messageExtra" -> Text(
                                    item.title,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    fontSize = 13.sp,
                                    color = COUITheme.colorScheme.outline,
                                    textAlign = TextAlign.Center,
                                )

                                else -> Text(
                                    "暂不支持此消息类型",
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        // Reversed layout, so the oldest end carries the paging footer.
                        item(contentType = "footer") {
                            ChatFooter(viewModel.footer, viewModel::retryFooter)
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(COUITheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(onClick = {
                    if (emojiPanel) {
                        emojiPanel = false
                        runCatching { focusRequester.requestFocus() }
                    } else {
                        emojiPanel = true
                        focusManager.clearFocus()
                    }
                }) {
                    Icon(AppIcons.EmojiEmotions, contentDescription = "表情")
                }
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused) emojiPanel = false },
                    backgroundMode = TextFieldMode.Rectangle,
                    label = "写私信...",
                    minLines = 1,
                    maxLines = 4,
                    textStyle = COUITheme.textStyles.body1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )
                IconButton(onClick = { if (canSend) sendText() else pickImage() }) {
                    Icon(
                        if (canSend) COUIIcons.Send else COUIIcons.Photos,
                        contentDescription = if (canSend) "发送" else "图片",
                    )
                }
            }

            if (emojiPanel) {
                EmojiPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(COUITheme.colorScheme.surfaceContainer),
                    onPick = { emoji ->
                        val start = input.selection.start.coerceIn(0, input.text.length)
                        val end = input.selection.end.coerceIn(start, input.text.length)
                        input = TextFieldValue(
                            input.text.replaceRange(start, end, emoji),
                            TextRange(start + emoji.length),
                        )
                    },
                )
            }
        }
    }

    OverlayLoadingDialog(show = progress != null, text = progress)

    messageMenu?.let { item ->
        OverlayBottomSheet(show = true, onDismissRequest = { messageMenu = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (item.message.isNotEmpty()) {
                    ActionSheetItem(COUIIcons.Copy, "复制", onClick = { context.copyText(item.message) })
                }
                ActionSheetItem(COUIIcons.Delete, "删除", onClick = {
                    scope.launch { context.toast(viewModel.delete(item.id)) }
                })
                val pic = item.string("message_pic")
                if (pic.isNotEmpty()) {
                    ActionSheetItem(COUIIcons.Photos, "查看图片", onClick = {
                        scope.launch {
                            val url = if (pic.startsWith("http")) pic else viewModel.imageUrl(item.id).orEmpty()
                            if (url.isNotEmpty()) navigator.push(Route.ImageView(listOf(url)))
                        }
                    })
                }
                ActionSheetItem(COUIIcons.Email, "查看用户", onClick = { navigator.push(Route.User(uid)) })
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ChatFooter(status: FooterStatus, onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        when (status) {
            FooterStatus.Idle, FooterStatus.Loading -> InfiniteProgressIndicator()
            FooterStatus.End -> Text(
                "没有更多了",
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            is FooterStatus.Error -> TextButton(text = "${status.message}，点击重试", onClick = onRetry)
        }
    }
}

/**
 * The conversation, newest message first. `/v6/message/chat` answers oldest first and pages
 * backwards in time, so each page is reversed before it is appended.
 */
private class ChatViewModel(
    private val repository: CoolapkRepository,
    private val ukey: String,
) : ViewModel() {
    var messages by mutableStateOf<List<Entity>>(emptyList())
        private set
    var status by mutableStateOf<ListStatus>(ListStatus.Loading)
        private set
    var footer by mutableStateOf<FooterStatus>(FooterStatus.Idle)
        private set

    /** Set when a pull-to-refresh fails while older messages are still on screen. */
    var refreshError by mutableStateOf<String?>(null)

    private var page = 1
    private var firstItem: String? = null
    private var lastItem: String? = null
    private var isEnd = false
    private var isLoading = false

    init {
        load(isRefresh = true)
    }

    fun refresh() {
        if (isLoading) return
        load(isRefresh = true)
    }

    fun loadMore() {
        if (isLoading || isEnd || status != ListStatus.Content) return
        load(isRefresh = false)
    }

    fun retryFooter() {
        isEnd = false
        loadMore()
    }

    /** Returns the message to show the user; empty on success. */
    suspend fun send(uid: String, text: String, pic: String?): String {
        val result = repository.sendMessage(uid, text, pic)
        if (result.message.isNotEmpty()) return result.message
        val added = (result.data as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::Entity) }
        messages = added + messages
        return ""
    }

    /** Returns the message to show the user; empty when the server said nothing. */
    suspend fun delete(id: String): String {
        val result = repository.deleteMessage(ukey, id)
        if (result.message.isNotEmpty()) return result.message
        messages = messages.filterNot { it.id == id }
        return result.count
    }

    suspend fun imageUrl(id: String): String? = repository.messageImageUrl(id)

    /** Swaps the reserved place of a picture for the link the server redirects to. */
    suspend fun resolveImage(id: String) {
        val url = repository.messageImageUrl(id) ?: return
        messages = messages.map { item ->
            if (item.id == id) Entity(JsonObject(item.json + ("message_pic" to JsonPrimitive(url)))) else item
        }
    }

    private fun load(isRefresh: Boolean) {
        isLoading = true
        val requestPage = if (isRefresh) 1 else page
        val requestFirst = if (isRefresh) null else firstItem
        val requestLast = if (isRefresh) null else lastItem
        if (isRefresh && messages.isEmpty()) status = ListStatus.Loading
        if (!isRefresh) footer = FooterStatus.Loading
        viewModelScope.launch {
            try {
                val result = repository.chat(ukey, requestPage, requestFirst, requestLast)
                if (isRefresh) {
                    page = 1
                    firstItem = null
                    lastItem = null
                    isEnd = false
                }
                if (result.isEmpty()) {
                    isEnd = true
                    if (isRefresh) {
                        messages = emptyList()
                        status = ListStatus.Empty
                    } else {
                        footer = FooterStatus.End
                    }
                } else {
                    firstItem = firstItem ?: result.first().id
                    lastItem = result.last().id
                    val pageItems = result.reversed()
                    val merged = if (isRefresh) pageItems else messages + pageItems
                    val seen = HashSet<String>()
                    messages = merged.filter { it.entityId.isEmpty() || seen.add(it.entityId) }
                    status = ListStatus.Content
                    footer = FooterStatus.Idle
                    page = requestPage + 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = ApiFailure.from(e).message
                when {
                    isRefresh && messages.isEmpty() -> status = ListStatus.Error(message)
                    isRefresh -> refreshError = message
                    else -> {
                        isEnd = true
                        footer = FooterStatus.Error(message)
                    }
                }
            } finally {
                isLoading = false
            }
        }
    }
}
