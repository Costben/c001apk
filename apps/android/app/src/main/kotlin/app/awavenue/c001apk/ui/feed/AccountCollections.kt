package app.awavenue.c001apk.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.data.CoolapkRepository
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.toast
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Switch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.layout.SheetActionButton
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.CheckboxPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One collection row the picker lists. */
@Immutable
private class AccountCollection(val id: String, val title: String)

/**
 * The signed-in account's collections for one feed, read page by page and written back as a whole.
 * It is kept apart from the local favourites list: nothing here touches the on-disk favourites, and
 * the account's own collection list is left alone.
 *
 * Writes only report success when the server says so, and a request that outlives the sheet — the
 * user dismissed it, or the account changed under it — never writes back into the UI.
 */
@Stable
private class AccountCollectionsState(
    private val feedId: String,
    private val repository: CoolapkRepository,
    private val accountUid: String,
    private val currentUid: () -> String,
    private val scope: CoroutineScope,
    private val notify: (String) -> Unit,
) {
    var items by mutableStateOf<List<AccountCollection>>(emptyList())
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var creating by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var createError by mutableStateOf<String?>(null)
        private set

    /** The collections this feed was already in when the sheet opened. */
    private var original = emptySet<String>()
    private var page = 1
    private var lastItem: String? = null

    /** Bumped on close, so a late answer is recognised as belonging to a dismissed sheet. */
    private var generation = 0

    val busy: Boolean get() = loading || saving || creating

    fun toggle(id: String, checked: Boolean) {
        if (busy) return
        selected = if (checked) selected + id else selected - id
    }

    fun loadMore() {
        if (busy || !hasMore) return
        loading = true
        error = null
        val request = generation
        scope.launch {
            try {
                val rows = repository.feedCollections(feedId, page, lastItem)
                if (request != generation) return@launch
                if (!sameAccount()) return@launch
                val known = items.mapTo(HashSet()) { it.id }
                val added = rows.mapNotNull { row ->
                    val id = row.id
                    if (id.isEmpty() || !known.add(id)) return@mapNotNull null
                    AccountCollection(id, row.title.ifBlank { "默认收藏夹" })
                }
                val checked = rows.filter { it.string("isBeCollected") == "1" }.map { it.id }.toSet()
                original = original + checked
                selected = selected + checked
                items = items + added
                hasMore = added.isNotEmpty()
                lastItem = rows.lastOrNull()?.let { it.string("entityId").ifEmpty { it.id } }
                page += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) error = ApiFailure.from(e).message
            } finally {
                if (request == generation) loading = false
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (busy || error != null) return
        if (selected == original) {
            onSaved()
            return
        }
        saving = true
        val request = generation
        scope.launch {
            try {
                val result = repository.saveFeedCollections(feedId, selected, original - selected)
                if (request != generation) return@launch
                val collect = result.collect
                if (collect != 0 && collect != 1) {
                    throw ApiFailure(ApiFailureKind.InvalidResponse, "未收到收藏状态，请重试")
                }
                notify(if (collect == 1) "已保存到账号收藏夹" else "已取消账号收藏")
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) notify(ApiFailure.from(e).message)
            } finally {
                if (request == generation) saving = false
            }
        }
    }

    fun create(title: String, isOpen: Boolean, onCreated: () -> Unit) {
        if (busy || title.isBlank()) return
        creating = true
        createError = null
        val request = generation
        scope.launch {
            try {
                val row = repository.createCollection(title.trim(), isOpen)
                val id = row.id
                if (id.isEmpty()) throw ApiFailure(ApiFailureKind.InvalidResponse, "未收到收藏夹信息，请重试")
                if (request != generation) return@launch
                // The new collection is ticked at once; saving submits the relation with it.
                items = listOf(AccountCollection(id, row.title.ifBlank { title.trim() })) + items.filterNot { it.id == id }
                selected = selected + id
                onCreated()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (request == generation) createError = ApiFailure.from(e).message
            } finally {
                if (request == generation) creating = false
            }
        }
    }

    fun close() {
        generation += 1
    }

    private fun sameAccount(): Boolean {
        if (currentUid() == accountUid) return true
        error = "账号已切换，请重新打开收藏夹"
        return false
    }
}

/**
 * The account collection picker for [feedId]. Ticking writes to the account when 保存 is pressed;
 * a new collection is created private by default and ticked straight away.
 */
@Composable
fun AccountCollectionsSheet(feedId: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(feedId) {
        AccountCollectionsState(
            feedId = feedId,
            repository = app.repository,
            accountUid = app.session.uid,
            currentUid = { app.session.uid },
            scope = scope,
            notify = { context.toast(it) },
        )
    }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(feedId) { state.loadMore() }

    fun dismiss() {
        state.close()
        onDismiss()
    }

    OverlayBottomSheet(
        show = true,
        title = "收藏到",
        endAction = {
            SheetActionButton(
                onClick = { creating = true },
                enabled = !state.busy && state.error == null,
            ) {
                Icon(COUIIcons.Add, contentDescription = "新建收藏夹")
            }
        },
        onDismissRequest = ::dismiss,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> InfiniteProgressIndicator()
                        state.error != null -> TextButton(
                            text = "${state.error}，点击重试",
                            onClick = state::loadMore,
                        )
                        else -> Text(
                            "还没有收藏夹，先新建一个吧",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(state.items, key = { it.id }) { collection ->
                        CheckboxPreference(
                            title = collection.title,
                            checked = collection.id in state.selected,
                            enabled = !state.busy,
                            insideMargin = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            onCheckedChange = { state.toggle(collection.id, it) },
                        )
                    }
                    item(key = "footer") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                state.loading -> InfiniteProgressIndicator(size = 22.dp)
                                state.error != null -> TextButton(
                                    text = "${state.error}，点击重试",
                                    onClick = state::loadMore,
                                )
                                state.hasMore -> LaunchedEffect(state.items.size) { state.loadMore() }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = ::dismiss,
                    enabled = !state.saving && !state.creating,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = { state.save { dismiss() } },
                    enabled = !state.busy && state.error == null && state.items.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (state.saving) "保存中…" else "保存")
                }
            }
        }
    }

    if (creating) {
        CreateCollectionDialog(
            state = state,
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun CreateCollectionDialog(state: AccountCollectionsState, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var isOpen by remember { mutableStateOf(false) }

    OverlayDialog(
        show = true,
        title = "新建收藏夹",
        onDismissRequest = { if (!state.creating) onDismiss() },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = "收藏夹名称",
                useLabelAsPlaceholder = true,
                enabled = !state.creating,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("公开收藏夹", style = COUITheme.textStyles.body1)
                    Text(
                        "关闭后仅自己可见",
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Switch(
                    checked = isOpen,
                    onCheckedChange = { isOpen = it },
                    enabled = !state.creating,
                )
            }
            state.createError?.let {
                Text(
                    it,
                    modifier = Modifier.padding(top = 8.dp),
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.error,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(text = "取消", onClick = { if (!state.creating) onDismiss() })
                TextButton(
                    text = if (state.creating) "创建中…" else "创建",
                    enabled = !state.busy && title.isNotBlank(),
                    onClick = { state.create(title, isOpen) { onDismiss() } },
                )
            }
        }
    }
}
