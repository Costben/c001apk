package app.awavenue.c001apk.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.TwilightRule
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SmallTitle
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.utils.overScrollVertical

/**
 * The imported 秋风 snapshot, one switch per rule. Turning a rule off keeps it in the snapshot so
 * a later import does not bring it back on; the import only replaces the remote revision.
 */
@Composable
fun TwilightRulesScreen() {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val prefs = app.contentFilter.preferences
    val snapshot = prefs.twilight
    val scrollBehavior = rememberPageScrollBehavior("twilight-rules")
    val backdrop = rememberGlassBackdrop()

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "秋风规则明细",
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = rememberPageListState("twilight-rules"),
            modifier = Modifier
                .fillMaxSize()
                .glassSource(backdrop)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            ),
        ) {
            if (snapshot == null) {
                item {
                    Text(
                        "尚未导入秋风规则。回到内容过滤页更新远程规则后，这里会列出每一条。",
                        style = COUITheme.textStyles.body2,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            } else {
                item {
                    SmallTitle("版本 ${snapshot.revision}")
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        Text(
                            "共 ${snapshot.rules.size} 条 · 发布于 ${snapshot.publishedAt}",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                items(snapshot.rules, key = { it.key }) { rule ->
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 6.dp)) {
                        SwitchPreference(
                            title = rule.reason,
                            summary = ruleSummary(rule),
                            checked = rule.key !in prefs.disabledTwilightRules,
                            onCheckedChange = { enabled -> app.contentFilter.setTwilightRuleEnabled(rule.key, enabled) },
                        )
                    }
                }
            }
        }
    }
}

private fun ruleSummary(rule: TwilightRule): String {
    val scopes = rule.scopes.joinToString("、") {
        when (it) {
            "feed" -> "动态"
            "comment" -> "评论"
            else -> "用户卡片"
        }
    }
    val matching = if (rule.kind == "text") {
        rule.groups.joinToString(" 且 ") { group -> group.joinToString(" / ") }
    } else {
        rule.values.joinToString("、")
    }
    return buildString {
        append(if (rule.level == "strict") "严格" else "标准")
        append(" · ").append(scopes)
        append("\n").append(matching)
        if (rule.kind == "text") append("\n同句内间距不超过 ${rule.maxDistance} 字符")
        if (rule.exceptions.isNotEmpty()) append("\n例外：").append(rule.exceptions.joinToString("、"))
    }
}
