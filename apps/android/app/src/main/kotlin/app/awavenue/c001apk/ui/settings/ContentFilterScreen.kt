package app.awavenue.c001apk.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.ContentFilterPreferences
import app.awavenue.c001apk.model.ContentRule
import app.awavenue.c001apk.model.FilterKind
import app.awavenue.c001apk.model.FilterScope
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.toast
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import app.awavenue.c001apk.ui.nav.rememberPageScrollState
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Chip
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.basic.TextField
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Add
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * The content rules: a master switch, the scopes they may act in, and the rules themselves.
 *
 * A rule hides a list row when it matches. Text rules compare one sentence at a time and skip a
 * sentence that holds an exception; user rules compare the uid; topic rules compare the topic
 * names a post carries. Rows hidden this way are counted by the list, which offers to continue
 * loading when a whole batch was filtered.
 */
@Composable
fun ContentFilterScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val store = app.contentFilter
    val prefs = store.preferences
    val scrollBehavior = rememberPageScrollBehavior("content-filter")
    val backdrop = rememberGlassBackdrop()

    // null closes the editor; the second flag tells a new rule from an edited one.
    var editing by remember { mutableStateOf<ContentRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun save(next: ContentRule) {
        val exists = prefs.rules.any { it.id == next.id }
        if (!exists && prefs.rules.size >= ContentFilterPreferences.MaxRules) {
            context.toast("最多保存 ${ContentFilterPreferences.MaxRules} 条规则")
            return
        }
        store.update { current ->
            current.copy(
                rules = if (exists) {
                    current.rules.map { if (it.id == next.id) next else it }
                } else {
                    current.rules + next
                },
            )
        }
        editing = null
        creating = false
    }

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "内容过滤",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TopBarIconButton(onClick = { creating = true }) {
                            Icon(COUIIcons.Add, contentDescription = "新建规则")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberPageScrollState("content-filter"))
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding(),
                ),
        ) {
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                SwitchPreference(
                    title = "启用内容过滤",
                    summary = "命中规则的动态、评论与用户卡片会从列表里移除",
                    checked = prefs.enabled,
                    onCheckedChange = { enabled -> store.update { it.copy(enabled = enabled) } },
                )
            }

            SmallTitle("生效范围")
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                FilterScope.entries.forEach { scope ->
                    SwitchPreference(
                        title = scope.label,
                        summary = when (scope) {
                            FilterScope.Feed -> "首页、话题、搜索等列表里的动态"
                            FilterScope.Comment -> "动态详情页里的评论"
                            FilterScope.User -> "搜索与关注列表里的用户卡片"
                        },
                        checked = scope in prefs.scopes,
                        enabled = prefs.enabled,
                        onCheckedChange = { on ->
                            store.update { current ->
                                current.copy(
                                    scopes = if (on) current.scopes + scope else current.scopes - scope,
                                )
                            }
                        },
                    )
                }
            }

            SmallTitle("规则")
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                if (prefs.rules.isEmpty()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text("还没有规则", style = COUITheme.textStyles.body1)
                        Text(
                            "点右上角新建：关键词、用户 UID 或话题名称",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    prefs.rules.forEach { rule ->
                        ArrowPreference(
                            title = rule.title,
                            summary = ruleSummary(rule),
                            onClick = { editing = rule },
                        )
                    }
                }
            }
        }
    }

    val open = editing ?: if (creating) ContentRule(id = "", title = "", terms = emptyList()) else null
    if (open != null) {
        val existing = editing
        RuleEditor(
            rule = existing,
            initial = open,
            onDismiss = {
                editing = null
                creating = false
            },
            onSave = ::save,
            onDelete = { rule ->
                store.update { current -> current.copy(rules = current.rules.filterNot { it.id == rule.id }) }
                editing = null
                creating = false
            },
        )
    }
}

private fun ruleSummary(rule: ContentRule): String {
    val terms = when (rule.kind) {
        FilterKind.Author -> rule.terms.take(2).joinToString("、")
        else -> rule.terms.take(3).joinToString("、")
    }
    val more = if (rule.terms.size > 3) "…" else ""
    val scopes = rule.scopes.sortedBy { it.ordinal }.joinToString("/") { it.label }
    val head = if (rule.enabled) "" else "已停用 · "
    return "$head${rule.kind.label} · $terms$more · $scopes"
}

/**
 * The rule form. [rule] is the row being edited, or null for a new one; [initial] carries the
 * values the fields start from.
 */
@Composable
private fun RuleEditor(
    rule: ContentRule?,
    initial: ContentRule,
    onDismiss: () -> Unit,
    onSave: (ContentRule) -> Unit,
    onDelete: (ContentRule) -> Unit,
) {
    var title by remember(initial) { mutableStateOf(initial.title) }
    var kind by remember(initial) { mutableStateOf(initial.kind) }
    var terms by remember(initial) { mutableStateOf(initial.terms.joinToString("\n")) }
    var exceptions by remember(initial) { mutableStateOf(initial.exceptions.joinToString("\n")) }
    var scopes by remember(initial) { mutableStateOf(initial.scopes) }
    var enabled by remember(initial) { mutableStateOf(initial.enabled) }
    var error by remember(initial) { mutableStateOf<String?>(null) }

    fun submit() {
        val next = ContentRule(
            id = rule?.id ?: "rule-${System.currentTimeMillis()}",
            title = title.trim(),
            kind = kind,
            terms = terms.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
            exceptions = exceptions.lineSequence().map(String::trim).filter(String::isNotEmpty).toList(),
            scopes = scopes,
            enabled = enabled,
        )
        val problem = next.validate()
        if (problem != null) {
            error = problem
            return
        }
        onSave(next)
    }

    OverlayDialog(
        show = true,
        title = if (rule == null) "新建规则" else "编辑规则",
        onDismissRequest = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            TextField(
                value = title,
                onValueChange = { title = it },
                label = "规则名称",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("类型")
            FlowLayout(itemSpacing = 8.dp, lineSpacing = 8.dp) {
                FilterKind.entries.forEach { option ->
                    Chip(
                        selected = option == kind,
                        onClick = { kind = option },
                        label = option.label,
                        minWidth = 0.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TextField(
                value = terms,
                onValueChange = { terms = it },
                label = when (kind) {
                    FilterKind.Author -> "用户 UID，每行一个"
                    FilterKind.Topic -> "话题名称，每行一个"
                    FilterKind.Text -> "匹配词，每行一个"
                },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == FilterKind.Text) {
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = exceptions,
                    onValueChange = { exceptions = it },
                    label = "例外词，每行一个（含例外词的句子不隐藏）",
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            SectionLabel("生效范围")
            FlowLayout(itemSpacing = 8.dp, lineSpacing = 8.dp) {
                FilterScope.entries.forEach { scope ->
                    Chip(
                        selected = scope in scopes,
                        onClick = { scopes = if (scope in scopes) scopes - scope else scopes + scope },
                        label = scope.label,
                        minWidth = 0.dp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            SwitchPreference(
                title = "启用此规则",
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            error?.let {
                Text(
                    it,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (rule != null) {
                    TextButton(text = "删除", onClick = { onDelete(rule) })
                }
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(text = "保存", onClick = ::submit)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}
