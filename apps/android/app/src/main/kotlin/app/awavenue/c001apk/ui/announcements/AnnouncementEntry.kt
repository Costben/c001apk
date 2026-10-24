package app.awavenue.c001apk.ui.announcements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronForward
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The c001apk 小助手 row. A guest can open it too: announcements are public and do not depend on
 * the Coolapk account.
 */
@Composable
fun AnnouncementEntryCard(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val service = app.announcements
    // Subscribes the row to table and receipt changes.
    val revision = service.revision
    val items = remember(revision) { service.items }
    val unread = remember(revision) { service.unreadCount }
    val summary = items.firstOrNull()?.summary?.takeIf { it.isNotEmpty() } ?: "应用公告与使用提醒"

    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        onClick = rememberHapticClick { navigator.push(Route.Announcements) },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantAvatar(42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("c001apk 小助手", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = COUITheme.textStyles.footnote1,
                    color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (unread > 0) {
                Badge(count = unread, stroke = true)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                COUIIcons.ChevronForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}
