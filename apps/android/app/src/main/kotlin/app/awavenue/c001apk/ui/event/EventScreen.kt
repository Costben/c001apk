package app.awavenue.c001apk.ui.event

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.common.rememberLinkOpener
import app.awavenue.c001apk.ui.common.rememberSquircleShape
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageListState
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import app.awavenue.c001apk.ui.text.HtmlText
import io.github.suqi8.coui.kmp.basic.Button
import io.github.suqi8.coui.kmp.basic.ButtonDefaults
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.EmptyState
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.InfiniteProgressIndicator
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Alarm
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Community
import io.github.suqi8.coui.kmp.icon.extended.Favorites
import io.github.suqi8.coui.kmp.icon.extended.Info
import io.github.suqi8.coui.kmp.icon.extended.Stopwatch
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventScreen(id: String) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current

    var event by remember(id) { mutableStateOf<Entity?>(null) }
    var failure by remember(id) { mutableStateOf<String?>(null) }
    var attempts by remember(id) { mutableIntStateOf(0) }
    val backdrop = rememberGlassBackdrop()

    LaunchedEffect(id, attempts) {
        event = null
        failure = null
        try {
            val data = app.repository.eventDetail(id)["data"] as? JsonObject
            if (data == null) failure = "活动不存在或已下线" else event = Entity(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = ApiFailure.from(e).message
        }
    }

    val loaded = event
    Scaffold(
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = "活动详情",
                    gradientGlass = true,
                    navigationIcon = {
                        TopBarIconButton(onClick = { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val modifier = Modifier.fillMaxSize().glassSource(backdrop).padding(innerPadding)
            when {
                loaded != null -> EventContent(
                    event = loaded,
                    modifier = modifier,
                    onOpenUser = { navigator.push(Route.User(it)) },
                )

                failure != null -> Box(modifier, contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "加载失败",
                        subtitle = failure,
                        actionText = "重试",
                        onActionClick = { attempts++ },
                    )
                }

                else -> Box(modifier, contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun EventContent(event: Entity, modifier: Modifier, onOpenUser: (String) -> Unit) {
    val tabs = remember(event) { event.list("tabList") }
    val titles = remember(tabs) {
        if (tabs.isEmpty()) emptyList() else listOf("详情") + tabs.map { it.string("title").ifEmpty { "内容" } }
    }
    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("event", fallback = 0),
    ) { titles.size.coerceAtLeast(1) }
    rememberPageMemoryRecorder("event", pagerState.currentPage)
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("event")

    Column(modifier) {
        if (titles.isNotEmpty()) {
            TabRowWithContour(
                tabs = titles,
                selectedTabIndex = pagerState.currentPage,
                onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                colors = appTabRowColors(),
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 1,
        ) { page ->
            val contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
            if (page == 0) {
                EventHeaderPage(
                    event = event,
                    contentPadding = contentPadding,
                    scrollBehavior = scrollBehavior,
                    onOpenUser = onOpenUser,
                )
            } else {
                EventTabPage(tab = tabs[page - 1], contentPadding = contentPadding, scrollBehavior = scrollBehavior)
            }
        }
    }
}

@Composable
private fun EventHeaderPage(
    event: Entity,
    contentPadding: PaddingValues,
    scrollBehavior: ScrollBehavior,
    onOpenUser: (String) -> Unit,
) {
    val openLink = rememberLinkOpener()
    val title = event.string("title")
    val actionUrl = event.string("action_url")
    val signup = signupState(event, System.currentTimeMillis() / 1000)
    val onSignUp = rememberHapticClick(enabled = signup.enabled) {
        if (actionUrl.isNotBlank()) openLink(actionUrl, title)
    }
    val notice = event.string("notice_rule").trim()
    val sponsors = event.list("sponsorUser")
    val prizes = event.list("sponsorPrize")

    LazyColumn(
        state = rememberPageListState("event-header"),
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "logo") {
            NetworkImage(
                url = event.string("logo"),
                modifier = Modifier.fillMaxWidth().aspectRatio(1080f / 608f),
                contentDescription = title,
            )
        }
        if (title.isNotEmpty()) {
            item(key = "title") {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = COUITheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item(key = "signup") {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                EventInfoRow(COUIIcons.Alarm, "报名时间", registrationPeriod(event))
                Spacer(Modifier.height(10.dp))
                EventInfoRow(COUIIcons.Stopwatch, "活动结束", formatEventTime(event.long("time_end")))
                Spacer(Modifier.height(10.dp))
                EventInfoRow(COUIIcons.Info, "报名状态", signup.label)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onSignUp,
                    enabled = signup.enabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = if (signup.enabled) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
                ) {
                    Text(signup.label)
                }
            }
        }
        if (notice.isNotEmpty()) {
            item(key = "notice") {
                EventSection("报名须知", COUIIcons.Info) { HtmlText(notice) }
            }
        }
        if (sponsors.isNotEmpty()) {
            item(key = "sponsors") {
                EventSection("主办方", COUIIcons.Community) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(sponsors) { _, sponsor ->
                            SponsorItem(sponsor = sponsor, onOpenUser = onOpenUser)
                        }
                    }
                }
            }
        }
        if (prizes.isNotEmpty()) {
            item(key = "prizes") {
                EventSection("活动奖品", COUIIcons.Favorites) {
                    prizes.forEachIndexed { index, prize ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            NetworkImage(
                                url = prize.string("logo"),
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                contentDescription = prize.string("title"),
                            )
                            Text(
                                text = prize.string("title"),
                                modifier = Modifier.weight(1f),
                                style = COUITheme.textStyles.body1,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (index != prizes.lastIndex) {
                            HorizontalDivider(Modifier.padding(start = 76.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventTabPage(tab: Entity, contentPadding: PaddingValues, scrollBehavior: ScrollBehavior) {
    val app = LocalApp.current
    val title = tab.string("title")
    val url = tab.string("url")
    val dataUrl = tabDataUrl(url)
    when {
        dataUrl != null -> {
            val viewModel = viewModel(key = "event-tab-$dataUrl-$title") {
                FeedListViewModel(
                    PageLoader { page, first, last ->
                        app.repository.dataList(dataUrl, title, page, first, last)
                    },
                    app::contentGate,
                )
            }
            FeedList(viewModel = viewModel, scrollBehavior = scrollBehavior, contentPadding = contentPadding)
        }

        url.isNotEmpty() -> {
            val openLink = rememberLinkOpener()
            Box(
                Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = title.ifEmpty { "相关内容" },
                    subtitle = "点击下方按钮查看内容",
                    actionText = "打开链接",
                    onActionClick = { openLink(url, title) },
                )
            }
        }

        else -> Box(
            Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(title = "暂无内容")
        }
    }
}

@Composable
private fun EventSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = COUITheme.colorScheme.primary,
            )
            Text(title, style = COUITheme.textStyles.title3, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun EventInfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = COUITheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = COUITheme.textStyles.footnote1,
                color = COUITheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(text = value, style = COUITheme.textStyles.body1)
        }
    }
}

@Composable
private fun SponsorItem(sponsor: Entity, onOpenUser: (String) -> Unit) {
    val uid = sponsor.string("uid")
    val name = sponsor.string("displayUsername").ifEmpty { "酷友" }
    val onClick = rememberHapticClick(enabled = uid.isNotBlank()) { onOpenUser(uid) }
    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(rememberSquircleShape(12.dp))
            .clickable(enabled = uid.isNotBlank(), onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NetworkImage(
            url = sponsor.string("userAvatar"),
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentDescription = name,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = name,
            style = COUITheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private enum class SignupState(val label: String, val enabled: Boolean) {
    Open("立即报名", true),
    NotStarted("报名未开始", false),
    Closed("报名已结束", false),
    Ended("活动已结束", false),
    Unavailable("暂不可报名", false),
}

private fun signupState(event: Entity, nowSeconds: Long): SignupState {
    val stage = event.int("stage_status")
    val start = event.long("time_reg_start")
    val end = event.long("time_reg_end")
    val eventEnd = event.long("time_end")
    return when {
        stage == 3 || (eventEnd != null && nowSeconds > eventEnd) -> SignupState.Ended
        start != null && nowSeconds < start -> SignupState.NotStarted
        stage == 2 || (end != null && nowSeconds > end) -> SignupState.Closed
        event.string("action_url").isNotBlank() -> SignupState.Open
        else -> SignupState.Unavailable
    }
}

/** Tab urls are page paths the dataList endpoint already understands; `/page?url=` wraps one more. */
private fun tabDataUrl(url: String): String? = when {
    url.startsWith("/page?url=") -> url.removePrefix("/page?url=").takeIf { it.isNotEmpty() }
    url.startsWith("/") && !url.startsWith("//") -> url
    else -> null
}

private fun registrationPeriod(event: Entity): String {
    val start = event.long("time_reg_start")
    val end = event.long("time_reg_end")
    return if (start == null && end == null) "待定" else "${formatEventTime(start)} 至 ${formatEventTime(end)}"
}

private fun formatEventTime(seconds: Long?): String =
    seconds?.let { SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault()).format(Date(it * 1000)) } ?: "待定"
