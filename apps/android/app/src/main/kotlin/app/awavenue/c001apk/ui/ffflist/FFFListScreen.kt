package app.awavenue.c001apk.ui.ffflist

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.carousel.CarouselContent
import app.awavenue.c001apk.ui.common.appTabRowColors
import app.awavenue.c001apk.ui.common.glassSource
import app.awavenue.c001apk.ui.common.rememberGlassBackdrop
import app.awavenue.c001apk.ui.common.rememberHapticClick
import app.awavenue.c001apk.ui.feed.FeedList
import app.awavenue.c001apk.ui.feed.FeedListViewModel
import app.awavenue.c001apk.ui.feed.PageLoader
import app.awavenue.c001apk.ui.history.HistoryScreen
import app.awavenue.c001apk.ui.nav.FFFListType
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.pageMemoryPage
import app.awavenue.c001apk.ui.nav.rememberPageMemoryRecorder
import app.awavenue.c001apk.ui.nav.rememberPageScrollBehavior
import io.github.suqi8.coui.kmp.basic.Icon
import app.awavenue.c001apk.ui.common.TopBarIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.TabRowWithContour
import io.github.suqi8.coui.kmp.basic.SmallTopAppBar
import io.github.suqi8.coui.kmp.blur.LocalBackdrop
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import kotlinx.coroutines.launch

private val FollowTabs = listOf("用户", "话题", "数码", "应用")
private val ReplyTabs = listOf("我的回复", "我收到的回复")

/** The endpoint each list kind reads, copied from the Flutter client's `FFFListContent`. */
private fun fffUrl(type: FFFListType): String = when (type) {
    FFFListType.Feed -> "/v6/user/feedList?showAnonymous=0&isIncludeTop=1"
    FFFListType.Follow, FFFListType.UserFollow -> "/v6/user/followList"
    FFFListType.Apk -> "/v6/user/apkFollowList"
    FFFListType.Fan -> "/v6/user/fansList"
    FFFListType.Recent -> "/v6/user/recentHistoryList"
    FFFListType.Like -> "/v6/user/likeList"
    FFFListType.Reply -> "/v6/user/replyList"
    FFFListType.ReplyMe -> "/v6/user/replyToMeList"
    FFFListType.Collection -> "/v6/collection/list?showDefault=0"
    FFFListType.CollectionItem -> "/v6/collection/itemList"
    FFFListType.Fav, FFFListType.History -> ""
}

@Composable
fun FFFListScreen(type: FFFListType, uid: String?, id: String?, title: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = rememberPageScrollBehavior("fff-list")
    val backdrop = rememberGlassBackdrop()

    if (type == FFFListType.Fav || type == FFFListType.History) {
        HistoryScreen(if (type == FFFListType.Fav) HistoryType.Fav else HistoryType.History)
        return
    }

    val effectiveUid = uid ?: app.session.uid
    val isMe = app.session.uid == effectiveUid
    val listUid = if (type == FFFListType.Collection) null else effectiveUid
    val titleText = title ?: when (type) {
        FFFListType.Feed -> "我的动态"
        FFFListType.Follow -> "我的关注"
        FFFListType.UserFollow -> if (isMe) "我关注的人" else "TA关注的人"
        FFFListType.Fan -> if (isMe) "关注我的人" else "TA的粉丝"
        FFFListType.Recent -> "我的常去"
        FFFListType.Like -> "我的赞"
        FFFListType.Reply -> "我的回复"
        FFFListType.Collection -> "我的收藏"
        else -> ""
    }
    val tabs = when (type) {
        FFFListType.Follow -> FollowTabs
        FFFListType.Reply -> ReplyTabs
        else -> null
    }

    val pagerState = rememberPagerState(
        initialPage = pageMemoryPage("fff-list", fallback = 0),
    ) { tabs?.size ?: 0 }
    rememberPageMemoryRecorder("fff-list", pagerState.currentPage)

    Scaffold(
        edgeToEdge = true,
        topBar = {
            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                SmallTopAppBar(
                    title = titleText,
                    gradientGlass = true,
                    scrollBehavior = scrollBehavior,
                    navigationIcon = {
                        TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                            Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    bottomContent = {
                        if (tabs != null) {
                            TabRowWithContour(
                                tabs = tabs,
                                selectedTabIndex = pagerState.currentPage,
                                onTabSelected = { scope.launch { pagerState.animateScrollToPage(it) } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = appTabRowColors(),
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 12.dp,
            )
            if (tabs == null) {
                FFFContent(
                    type = type,
                    uid = listUid,
                    id = id,
                    scrollBehavior = scrollBehavior,
                    contentPadding = contentPadding,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().glassSource(backdrop),
                ) { page ->
                    if (type == FFFListType.Follow) {
                        when (page) {
                            0 -> FFFContent(
                                type = FFFListType.Follow,
                                uid = listUid,
                                id = id,
                                scrollBehavior = scrollBehavior,
                                contentPadding = contentPadding,
                            )
                            1 -> CarouselContent(
                                url = "#/topic/userFollowTagList",
                                title = "我关注的话题",
                                contentPadding = contentPadding,
                                scrollBehavior = scrollBehavior,
                            )
                            2 -> CarouselContent(
                                url = "#/product/followProductList",
                                title = "我关注的数码吧",
                                contentPadding = contentPadding,
                                scrollBehavior = scrollBehavior,
                            )
                            else -> FFFContent(
                                type = FFFListType.Apk,
                                uid = listUid,
                                id = id,
                                scrollBehavior = scrollBehavior,
                                contentPadding = contentPadding,
                            )
                        }
                    } else {
                        FFFContent(
                            type = if (page == 0) FFFListType.Reply else FFFListType.ReplyMe,
                            uid = listUid,
                            id = id,
                            scrollBehavior = scrollBehavior,
                            contentPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FFFContent(
    type: FFFListType,
    uid: String?,
    id: String?,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
) {
    val app = LocalApp.current
    val viewModel = viewModel(key = "${type.name}-$uid-$id") {
        FeedListViewModel(
            PageLoader { page, first, last ->
                app.repository.userList(fffUrl(type), uid, id, page, first, last)
            },
            app::contentGate,
        )
    }
    FeedList(viewModel = viewModel, scrollBehavior = scrollBehavior, contentPadding = contentPadding)
}
