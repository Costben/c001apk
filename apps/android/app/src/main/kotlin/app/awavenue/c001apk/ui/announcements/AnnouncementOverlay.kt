package app.awavenue.c001apk.ui.announcements

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.data.Announcement
import app.awavenue.c001apk.data.AnnouncementFailure
import app.awavenue.c001apk.data.AnnouncementService
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.text.MarkdownBody
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CircularProgressIndicator
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.TextButton
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Close
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

fun announcementDate(epochMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }.getOrDefault("")

/**
 * The announcement reader. It draws above the whole app instead of opening a route, so the same
 * card serves the automatic prompt, the inbox and the expanded reading view; back collapses the
 * expanded card before it dismisses the notice.
 */
@Composable
fun AnnouncementOverlay(
    service: AnnouncementService,
    item: Announcement,
    automatic: Boolean,
    initialBody: String? = null,
    onDismiss: () -> Unit,
) {
    var body by remember(item.cacheKey) { mutableStateOf(initialBody) }
    var error by remember(item.cacheKey) { mutableStateOf<String?>(null) }
    var loading by remember(item.cacheKey) { mutableStateOf(initialBody == null) }
    var attempt by remember(item.cacheKey) { mutableIntStateOf(0) }
    var expanded by remember(item.cacheKey) { mutableStateOf(false) }
    var recorded by remember(item.cacheKey) { mutableStateOf(false) }
    // Subscribes this overlay to table changes, so a withdrawn notice stops reading as current.
    val revision = service.revision
    val current = remember(revision) { service.isCurrent(item) }

    LaunchedEffect(item.cacheKey, attempt) {
        if (body != null) return@LaunchedEffect
        loading = true
        error = null
        try {
            body = service.loadBody(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = (e as? AnnouncementFailure)?.message ?: "公告正文加载失败，请稍后重试"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(body, current) {
        if (body == null || !current || recorded) return@LaunchedEffect
        recorded = true
        runCatching {
            if (automatic) service.markPrompted(item) else service.markRead(item)
        }
    }

    BackHandler {
        if (expanded) expanded = false else onDismiss()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = null, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        val cardModifier = if (expanded) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(max = maxHeight * 0.82f)
        }
        Card(
            modifier = cardModifier.clickable(interactionSource = null, indication = null) {},
            insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Column(if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistantAvatar(36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "c001apk 小助手",
                        modifier = Modifier.weight(1f),
                        style = COUITheme.textStyles.footnote1,
                        color = COUITheme.colorScheme.onSurfaceVariantSummary,
                    )
                    TopBarIconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) COUIIcons.Close else AnnouncementIcons.OpenInFull,
                            contentDescription = if (expanded) "收起" else "展开阅读",
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp),
                ) {
                    Text(
                        item.title,
                        style = COUITheme.textStyles.title3.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Row(
                        Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (item.important) ImportantTag()
                        Text(
                            "发布于 ${announcementDate(item.publishedAt)}",
                            style = COUITheme.textStyles.footnote1,
                            color = COUITheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                            .height(1.dp)
                            .background(COUITheme.colorScheme.dividerLine),
                    )
                    when {
                        !current -> Text("公告已更新或撤回，请刷新列表")
                        loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null -> Column {
                            Text(error!!, color = COUITheme.colorScheme.error)
                            TextButton(text = "重试", onClick = { attempt++ })
                        }
                        body != null -> MarkdownBody(
                            body!!,
                            explicit = true,
                            coolapk = false,
                            fontSize = 14.sp,
                            showEmoji = false,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(text = "知道了", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
fun AssistantAvatar(size: androidx.compose.ui.unit.Dp) {
    val scheme = COUITheme.colorScheme
    Box(
        Modifier.size(size).clip(CircleShape).background(scheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AnnouncementIcons.AllInclusive,
            contentDescription = null,
            modifier = Modifier.size(size * 0.64f),
            tint = scheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ImportantTag() {
    Text(
        "重要公告",
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .background(COUITheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = COUITheme.textStyles.footnote2,
        color = COUITheme.colorScheme.onSecondaryContainer,
    )
}
