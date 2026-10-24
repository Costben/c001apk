# Flutter → Compose 功能迁移清单

`apps/flutter` 是功能完整的 Flutter 客户端，`apps/android` 是用 coui17 组件库重写的 Compose
客户端。两者是两套 UI 框架（Dart/Material 与 Kotlin/Compose），页面代码无法直接复用，
因此 Compose 端按本清单逐项移植 Flutter 的行为，接口路径、参数、状态语义都以上游为准。

本文件是迁移进度的唯一真源：每完成一项就把状态改成 `已完成`，并写清对应的 Compose 文件。

## 零、移植约定

**目录**

- Compose 应用：`apps/android/app/src/main/kotlin/app/awavenue/c001apk/`
- Flutter 参考：`apps/flutter/lib/`（页面行为、文案、接口参数的唯一真源）
- 编译检查：`cd apps/android && ./gradlew :app:compileDebugKotlin`

**依赖注入与导航**

- `LocalApp.current` 提供 `settings`（设置）、`session`（会话）、`blacklist`（黑名单）、
  `history`（收藏与历史）、`searchHistory`（搜索历史）、`apiClient`（HTTP）、
  `repository`（全部接口调用）。
- 导航用 `LocalNavigator.current`：`push(Route.X)`、`replace(Route.X)`、`pop()`、
  `popToMain()`。路由表在 `ui/nav/Route.kt`，宿主在 `ui/nav/AppNavHost.kt`。
- 路由分支只在 `AppNavHost.kt` 里接线；页面文件不要改这个文件。

**UI**

- 组件一律取自 coui17：`io.github.suqi8.coui.kmp.basic.*`（`Scaffold`、`TopAppBar`、`Card`、
  `Text`、`SmallTitle`、`EmptyState`、`PullToRefresh`、`InfiniteProgressIndicator`、
  `TabRowWithContour`、`SearchBar`、`Chip`、`Badge`、`Divider`、`Icon`、`Button`、
  `FloatingActionButton`、`SideNavigationBar`、`TagView`、`LoadingButton`…）、
  `io.github.suqi8.coui.kmp.overlay.*` 与 `io.github.suqi8.coui.kmp.window.*`（`OverlayBottomSheet`、
  `OverlayAlertDialog`、`OverlayDialog`、`OverlayLoadingDialog`、`OverlayListPopup`…）、
  `io.github.suqi8.coui.kmp.preference.*`（设置项）、`io.github.suqi8.coui.kmp.icon.COUIIcons`
  与 `io.github.suqi8.coui.kmp.icon.extended.*`。
- 毛玻璃：`rememberGlassBackdrop()` 建背景层，`Modifier.glassSource(backdrop)` 标记内容层，
  `CompositionLocalProvider(LocalBackdrop provides backdrop)` 把背景提供给顶栏／底栏。
  三者必须配合使用，否则顶栏退化成纯色。
- 列表：`ui/feed/FeedList.kt` + `ui/feed/FeedListViewModel.kt`（`PageLoader` 游标分页）。
  需要变体时另建文件，不要改动这两个。
- 卡片分发：`ui/feed/EntityItem.kt` 的 `EntityItem(item)` 负责 `entityType`/`entityTemplate`
  到控件的映射。新增卡片类型时在那里加分支。
- 图片：`ui/common/NetworkImage.kt`（`NetworkImage`、`thumbnail`、`imageSize`）。
- 时间：`ui/common/TimeFormat.kt` 的 `fromToday(seconds)`。
- 正文与表情：`ui/text/HtmlText.kt` 的 `HtmlText(html, …)`。
- 系统能力：`ui/common/Platform.kt`（`Context.toast`、`copyText`、`openBrowser`、`shareText`、
  `shareFile`）。

**约定**

- 界面文案用简体中文，与 Flutter 版逐字一致。
- 错误提示统一用 `ApiFailure.from(e).message`，通过 `context.toast(...)` 显示。
- 注释只写「为什么」，不写「做了什么」，不写迁移说明。
- 不改动 `network/`、`data/`、`model/Entity.kt`、`ui/nav/`、`ui/App.kt`、`ui/theme/`、
  `ui/common/`、`ui/feed/FeedList.kt`、`ui/feed/FeedListViewModel.kt`、`ui/main/MainScreen.kt`；
  需要改动时报告，不要自行编辑。

## 一、基础层

| 状态 | 项 | Flutter 来源 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | 全部接口端点与主机 | `logic/network/api.dart` | `network/Api.kt` |
| 已完成 | GET/POST/表单/多部分/重定向控制 | `logic/network/request.dart` | `network/ApiClient.kt` |
| 已完成 | 账号域请求头与 Cookie | `logic/network/interceptor.dart` | `network/ApiClient.kt` |
| 已完成 | 会话持久化（uid/username/token/SESSID） | `utils/global_data.dart` | `data/SessionStore.kt` |
| 已完成 | 设置项全集与默认值 | `utils/storage_util.dart` | `data/AppSettings.kt` |
| 已完成 | 黑名单（用户精确、话题子串） | `utils/storage_util.dart` | `data/BlacklistStore.kt` |
| 已完成 | 浏览历史与收藏 | `logic/model/fav_history/` | `data/HistoryStore.kt` |
| 已完成 | 搜索历史 | `pages/search/` | `data/SearchHistoryStore.kt` |
| 已完成 | 列表预处理（listCard 展开、黑名单过滤、白名单） | `network_repo.dart:548-604` | `data/CoolapkRepository.kt` |
| 已完成 | 验证码挑战（NEC v2）与重放 | `logic/network/captcha_challenge.dart` | `network/CaptchaChallenge.kt` |
| 已完成 | 验证码 Cookie 协议：`captchaField` 为空时验证结果以 `validate` Cookie 随两个 API 主机发送，只存内存、换会话或设备即失效，重放仍被挑战即丢弃；验证期间设备标识变更不重放（上游 1.2.6） | `logic/network/interceptor.dart`、`request.dart`、`captcha_challenge.dart` | `network/ApiClient.kt`、`network/CaptchaChallenge.kt` |
| 已完成 | 路由图与返回栈 | `router/app_pages.dart` | `ui/nav/` |

## 二、页面

| 状态 | 路由 | Flutter 页面 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | `/` | `pages/main/main_page.dart` | `ui/main/MainScreen.kt` |
| 已完成 | `/` 首页 7 分段 | `pages/home/home_page.dart` | `ui/home/HomeScreen.kt` |
| 已完成 | `/` 首页 话题 / 数码 | `pages/home/topic/` | `ui/home/HomeTopicPage.kt` |
| 已完成 | `/` 首页 应用列表 | `pages/home/app/` | `ui/app/AppListPage.kt` |
| 已完成 | `/feed/:id` | `pages/feed/feed_page.dart` | `ui/feed/detail/FeedDetailScreen.kt` |
| 已完成 | 人机验证页 | `logic/network/captcha_challenge.dart` | `ui/feed/detail/CaptchaScreen.kt` |
| 已完成 | 回复编辑器 | `pages/feed/reply/reply_page.dart` | `ui/feed/reply/ReplyComposerSheet.kt` |
| 已完成 | 楼中楼 | `pages/feed/reply2reply/` | `ui/feed/detail/Reply2ReplyScreen.kt` |
| 已完成 | `/u/:uid` | `pages/user/user_page.dart` | `ui/user/UserScreen.kt` |
| 已完成 | `/apk/:packageName` | `pages/app/app_page.dart` | `ui/app/ApkScreen.kt` |
| 已完成 | `/search` | `pages/search/search_page.dart` | `ui/search/SearchScreen.kt` |
| 已完成 | `/searchResult` | `pages/search/search_result_page.dart` | `ui/search/SearchResultScreen.kt` |
| 已完成 | `/t/:tag` `/product/:id` | `pages/topic/topic_page.dart` | `ui/topic/TopicScreen.kt` |
| 已完成 | `/webview` | `pages/webview/webview_page.dart` | `ui/feed/detail/WebScreen.kt` |
| 已完成 | `/appUpdate` | `pages/home/app/update/` | `ui/app/AppUpdateScreen.kt` |
| 已完成 | `/carousel` | `pages/carousel/` | `ui/carousel/CarouselScreen.kt` |
| 已完成 | `/dyh` | `pages/dyh/` | `ui/dyh/DyhScreen.kt` |
| 已完成 | `/coolpic` | `pages/coolpic/` | `ui/coolpic/CoolPicScreen.kt` |
| 已完成 | `/imageview` | `pages/others/imageview_page.dart` | `ui/image/ImageViewScreen.kt` |
| 已完成 | `/login` | `pages/login/login_page.dart` | `ui/login/LoginScreen.kt` |
| 已完成 | `/notification` | `pages/noitfication/` | `ui/notification/NotificationScreen.kt` |
| 已完成 | `/ffflist` | `pages/ffflist/` | `ui/ffflist/FFFListScreen.kt` |
| 已完成 | `/history` | `pages/history/` | `ui/history/HistoryScreen.kt` |
| 已完成 | `/blacklist` | `pages/blacklist/` | `ui/blacklist/BlacklistScreen.kt` |
| 已完成 | `/chat` | `pages/chat/` | `ui/chat/ChatScreen.kt` |
| 已完成 | `/params` | `pages/settings/params_page.dart` | `ui/settings/ParamsScreen.kt` |
| 已完成 | `/about` | `pages/settings/about_page.dart` | `ui/settings/AboutScreen.kt` |
| 已完成 | `/copy` | `pages/others/copy_page.dart` | `ui/common/CopyScreen.kt` |
| 已完成 | 消息页 | `pages/message/message_page.dart` | `ui/message/MessageScreen.kt` |
| 已完成 | 设置页 | `pages/settings/settings_page.dart` | `ui/settings/SettingsScreen.kt` |
| 已完成 | 话题 / 数码 发布动态 | `pages/topic/topic_page.dart:148-177` | `ui/topic/TopicScreen.kt`（复用 `ui/feed/reply/ReplyComposerSheet.kt`） |

## 三、组件

| 状态 | 项 | Flutter 来源 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | 动态卡片（转发、热评、更多菜单、置顶、关联行） | `components/cards/feed_card.dart` | `ui/feed/FeedCard.kt` |
| 已完成 | 回复卡片 | `components/cards/feed_reply_card.dart` | `ui/feed/ReplyCard.kt` |
| 已完成 | 点赞通知卡片 | `components/cards/like_card.dart` | `ui/feed/LikeCard.kt` |
| 已完成 | 通知卡片 | `components/cards/notification_card.dart` | `ui/notification/NotificationCard.kt` |
| 已完成 | 私信卡片与气泡 | `components/cards/message_card.dart`、`chat_card.dart` | `ui/chat/ChatCard.kt` |
| 已完成 | 应用/话题/用户/联系人行卡片 | `components/cards/app_card.dart` | `ui/feed/EntityItem.kt`、`ui/user/UserListRow.kt` |
| 已完成 | 用户资料头 | `components/cards/user_info_card.dart` | `ui/user/UserInfoCard.kt` |
| 已完成 | 收藏卡片 | `components/cards/collection_card.dart` | `ui/feed/EntityItem.kt` |
| 已完成 | 图片九宫格与单图 | `components/imageview.dart` | `ui/feed/ImageGrid.kt` |
| 已完成 | HTML 文本（表情、链接路由） | `components/html_text.dart` | `ui/text/HtmlText.kt` |
| 已完成 | 站内链接分发 | `utils/utils.dart` 的 `onOpenLink` | `ui/common/CoolapkLink.kt` |
| 已完成 | 分页列表骨架 | `components/common_body.dart` | `ui/feed/FeedList.kt` |
| 已完成 | 页脚三态 | `components/footer.dart` | `ui/feed/FeedList.kt` |
| 已完成 | 长文正文 | `components/feed_article_body.dart` | `ui/feed/detail/ArticleBody.kt` |
| 已完成 | 表情面板 | `pages/feed/reply/emoji_panel.dart` | `ui/feed/reply/ReplyComposerSheet.kt`、`ui/text/EmojiFiles.kt` |
| 已完成 | 设置项组件 | `components/settings/` | `ui/settings/` |
| 已完成 | 弹窗（字号滑块、清缓存、关于） | `components/dialog.dart` | `ui/settings/SettingsScreen.kt`、`ui/settings/AboutScreen.kt` |
| 已完成 | 再点当前分段回到顶部 | `pages/main/main_page.dart` | `ui/common/ReturnTopController.kt` |
| 已完成 | 信息流图片点击进大图查看器 | `components/imageview.dart:41-50` | `ui/feed/FeedCard.kt`、`ui/feed/ReplyCard.kt` |
| 已完成 | 列表回复卡的更多菜单与点赞 | `components/common_body.dart:113-124` | `ui/feed/EntityItem.kt`、`ui/feed/FeedList.kt` |
| 已完成 | 历史 / 收藏行复用动态卡片 | `pages/history/history_page.dart:86-92` | `ui/history/HistoryScreen.kt`（`FeedCard(isHistory = true)`） |
| 已完成 | 横向滚动卡三种版式与轮播卡首尾循环 | `components/cards/image_square_scroll_card.dart` 等 | `ui/feed/EntityItem.kt` |
| 已完成 | 表情开关与 `[图片]` 表情码 | `components/html_text.dart:20-35,83-88` | `ui/text/HtmlText.kt` |
| 已完成 | 楼中楼可从列表直接打开 | `pages/feed/reply2reply/` | `ui/nav/Route.kt` 的 `Route.Reply2Reply` |
| 已完成 | 楼中楼首行为原回复、标题取原回复的回复数（帖子详情内打开） | `pages/feed/reply2reply/reply_2_reply_page.dart:38`、`reply_2_reply_controller.dart:19-26` | `ui/feed/detail/Reply2ReplyScreen.kt` |
| 已完成 | 帖子详情正文、热评、转发源长按进复制页 | `components/cards/feed_card.dart:52-53,128-130,258-259` | `ui/feed/detail/FeedDetailScreen.kt` |
| 已完成 | 屏蔽 / 删除的动态从直接调用 `EntityItem` 的列表数据里移除 | `components/common_body.dart` | `ui/feed/FeedCard.kt` 的 `FeedActions.isHidden`，用户 / 应用 / 话题 / 通知 / 消息页 |
| 已完成 | 设置页右上角溢出菜单（意见反馈 / 关于） | `pages/settings/settings_page.dart:101-125` | `ui/settings/SettingsScreen.kt` |
| 已完成 | 设置页分组与顺序、深色模式选项顺序、字号「x」后缀、打开即显示缓存大小 | `pages/settings/settings_page.dart:134-347` | `ui/settings/SettingsScreen.kt` |

## 四、平台能力

| 状态 | 项 | Flutter 来源 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | 分享文本与图片 | `share_plus` | `ui/common/Platform.kt` |
| 已完成 | 下载 APK 与图片保存 | `MainActivity.kt`、`download_util.dart` | `platform/Download.kt`、`ui/common/ImageSaver.kt` |
| 已完成 | 剪贴板 | `services` | `ui/common/Platform.kt` |
| 已完成 | 外部浏览器 | `url_launcher` | `ui/common/Platform.kt` |
| 已完成 | 内嵌 WebView 与登录取 Cookie | `webview_page.dart`、`utils/web_session.dart` | `ui/feed/detail/WebScreen.kt` |
| 已完成 | 图片选择 | `image_picker` | `ui/feed/detail/ImagePicker.kt` |
| 已完成 | 已安装应用列表 | `MainActivity.kt` | `platform/InstalledApps.kt`、`ui/app/AppListPage.kt` |
| 已完成 | 黑名单导入导出 | `MainActivity.kt` | `ui/blacklist/BlacklistScreen.kt` |
| 已完成 | 提示条与加载遮罩 | `flutter_smart_dialog` | `ui/common/Platform.kt` |
| 已完成 | 动态取色 | `dynamic_color` | `ui/theme/AppTheme.kt` |
| 已完成 | 字号缩放 | `main.dart` | `ui/theme/AppTheme.kt` |
| 已完成 | 返回键语义（先回首页再退到桌面） | `main_page.dart` | `ui/nav/AppNavHost.kt` |
| 已完成 | OSS 图片上传 | `utils/oss/` | `ui/feed/detail/OssUpload.kt` |

## 五、上游 1.2.x 新增

上游 1.2.0–1.2.6 的 Flutter 改动，已随合并进入 `apps/flutter`，Compose 端逐项补齐。

| 状态 | 项 | Flutter 来源 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | 验证码 Cookie 协议 | 见第一节 | `network/ApiClient.kt` |
| 已完成 | 主导航改为「主页 / 关注 / 我的」，首页去掉「关注」分段，设置从「我的」右上角进入 | `pages/main/main_page.dart`、`pages/home/home_page.dart` | `ui/main/MainScreen.kt`、`ui/home/HomeScreen.kt`、`Route.Settings` |
| 已完成 | 关注页：用户 / 话题 / 数码 / 应用 / 全部 / 悄悄关注 切换并记住选择，游客只显示本地关注 | `pages/local_follow/local_follow_page.dart` | `ui/follow/FollowScreen.kt` |
| 已完成 | 本地关注与悄悄关注：本机名单、多用户时间线合并（并发 4、失败可重试）、用户页关注按钮短按 / 长按、名单管理页 | `logic/local_follow/`、`pages/local_follow/`、`components/cards/user_info_card.dart` | `data/LocalFollowStore.kt`、`data/LocalFollowPager.kt`、`ui/follow/LocalFollowFeed.kt`、`ui/follow/LocalFollowListScreen.kt`、`ui/user/UserInfoCard.kt` |
| 已完成 | 多账号：本机保存、切换、添加、移除、游客模式，切换后重建页面并丢弃旧请求 | `logic/accounts/account_store.dart`、`pages/accounts/accounts_page.dart` | `data/AccountStore.kt`、`ui/account/AccountsScreen.kt`、`ui/main/MainScreen.kt`（按会话代次重建） |
| 已完成 | 「我的」页：账号管理与设置入口、「私信与信息」、酷安小秘书会话 | `pages/message/`、`pages/noitfication/` | `ui/message/MessageScreen.kt`、`ui/notification/NotificationScreen.kt` |
| 未开始 | 设置页分类：外观与字号 / 关注与内容 / 浏览与隐私 / 存储与更新 / 协议与设备 | `pages/settings/settings_page.dart` | |
| 未开始 | 私信：发送动态、逐条发送状态与重试、动态链接卡片、会话置顶与删除 | `pages/chat/`、`components/cards/chat_card.dart`、`message_card.dart` | |
| 未开始 | 活动页 `/event/:id` 与站内活动链接 | `pages/event/event_page.dart`、`utils/utils.dart` | |
| 已完成 | 32 个牛牛表情：正文与输入框内联渲染、面板“常用 / 牛牛 / 酷币”分栏 | `assets/emojis/niuniu_*`、`utils/emoji_util.dart`、`pages/feed/reply/emoji_panel.dart` | `assets/emojis/niuniu_emotion_*.png`、`ui/text/EmojiFiles.kt`（`EmojiSections`）、`ui/feed/reply/ReplyComposerSheet.kt`（`EmojiPanel`） |
| 已完成 | 自身更新检查：每次冷启动查一次最新正式版、按 ABI 选包、跳过版本；来源为本仓库 `Costben/c001apk` 的 Release | `logic/app_update/`、`components/update_check_tile.dart` | `data/AppUpdate.kt`、`ui/settings/AppUpdateDialog.kt` |
| 已完成 | 用户动态 `includeTop`、登录页提示网页登录并在遇人机验证时打开、网页登录不带入当前账号（可添加其他账号） | `logic/network/network_repo.dart`、`pages/login/login_page.dart`、`pages/webview/webview_page.dart` | `data/CoolapkRepository.kt`、`ui/login/LoginScreen.kt`、`ui/feed/detail/WebScreen.kt` |
| 未开始 | 零碎修复：分享链接去掉查询串再取动态 id、字号与系统缩放叠加 | `utils/utils.dart`、`utils/app_theme.dart` | |

## 五之二、上游 1.3.5 新增（2026-10-23）

上游 `98526cc` 的三项功能已移植到 Compose 端。接口、限制与文案以 `docs/MARKDOWN_SUPPORT.md`、
`docs/CONTENT_EXPORT.md`、`docs/ANNOUNCEMENTS.md` 为准。

| 状态 | 项 | Flutter 来源 | Compose 落点 |
| :--- | :--- | :--- | :--- |
| 已完成 | Markdown 正文：保守自动识别、标题/列表/表格/代码/引用/任务列表、长代码与宽表格横向滚动、信息流与转发卡片限行预览、详情「查看原文 / 格式化显示」、更新说明按 Markdown 显示 | `utils/markdown_content.dart`、`components/markdown_body.dart`、`pages/feed/feed_page.dart`、`components/update_dialog.dart` | `ui/text/Markdown.kt`、`ui/text/MarkdownText.kt`、`ui/feed/FeedCard.kt`、`ui/feed/detail/FeedDetailScreen.kt`、`ui/feed/detail/ArticleBody.kt`、`ui/settings/AppUpdateDialog.kt` |
| 已完成 | 保存动态与文章：Markdown / 离线 HTML、作者/时间/来源/转发/最多三条评论、置顶优先、遵守本地屏蔽、图片 8 MiB / 32 MP / 32 MiB / 60 张 / 45 秒限制、失败确认、系统文件选择器 | `utils/content_export.dart`、`utils/content_export_service.dart`、`components/content_export_button.dart` | `ui/feed/detail/ContentExport.kt`、`ui/feed/detail/ContentExportButton.kt` |
| 已完成 | 公告与 c001apk 小助手：索引/正文校验、版本与修订冲突拒绝、SHA-256、正文缓存 20 篇 / 2 MiB、未读与提醒修订、重要公告自动弹窗、冷启动/回前台/列表/手动同步、设置开关、正文缓存清理 | `logic/announcements/`、`components/announcement_*.dart`、`pages/announcements/` | `data/Announcements.kt`、`ui/announcements/`、`ui/message/MessageScreen.kt`、`ui/notification/NotificationScreen.kt`、`ui/settings/SettingsScreen.kt`、`ui/nav/`（`Route.Announcements` 与监控挂载）、`C001apkApp.kt` |

实现说明与差异（本轮按功能需要改动了禁改目录 `ui/nav/` 的 `Route.kt` 与
`AppNavHost.kt`：新增 `Route.Announcements` 并挂载公告监控，按约定在此报告）：

| 差异 | 原因 |
| :--- | :--- |
| Markdown 解析器为项目内 Kotlin 实现，不引入 `markdown` 包 | Compose 端没有 Flutter 的 `markdown`/`flutter_html` 组合；解析、渲染、导出共用同一棵 AST，避免两套规则 |
| 标题不生成锚点；行内图片渲染为可点击的图片占位符，独立成段的图片按比例展示 | Compose 文本不支持 HTML 锚点跳转；行内占位符避免在文字行里塞入未测量的图片 |
| 正文中的任意内嵌 HTML 只保留文字与链接，不执行也不原样渲染 | 与上游「不执行任意内嵌 HTML」一致；导出同样只写转义后的文本 |
| 公告来源固定为 `TG-Twilight/c001apk` 的 `announcements` 分支 | 该分支是上游公告的唯一发布点；本仓库尚未建立自己的公告分支 |
| 公告阅读页是覆盖全屏的展开卡片，不是独立路由；没有桌面端上下键/Esc 快捷键 | Compose 端只发布 Android；返回键先收起再关闭 |
| 自动公告只检查路由栈、更新弹窗和前台状态，不感知每个 COUI 菜单/面板 | COUI 没有全局「有弹窗」状态；根页、无二级路由、无更新弹窗时才提醒 |
| 离线 HTML 不读取 Coil 磁盘缓存，图片重新下载 | Coil 3 的缓存键与导出请求不同；直接下载并做同样的体积/解码校验，结果一致 |
| Markdown 由模型直接生成，不做 HTML→Markdown 往返 | 保留作者原文，避免往返转换丢失格式 |
| 公告状态保存在 SharedPreferences 的单个 JSON 键 | 与项目其余持久化一致，不引入 Hive |

## 六、接口对照

主机：`api.coolapk.com`、`api2.coolapk.com`、`account.coolapk.com`、`www.coolapk.com`。
端点常量见 `network/Api.kt`，调用封装见 `data/CoolapkRepository.kt`。

## 七、已知差异

记录移植中与 Flutter 版不一致的地方，避免被当成遗漏。

| 差异 | 原因 |
| :--- | :--- |
| 本机持久化用 SharedPreferences，不用 Hive | 只在 Android 上跑，不需要 Hive 的跨平台存储 |
| 路由用 Compose 返回栈，不用 GetX | GetX 是 Flutter 生态；返回语义在本文件第二节逐条对齐 |
| 内嵌浏览器用系统 WebView | `flutter_inappwebview` 无 Compose 对应物 |
| 首页分段用 coui17 的 `TabRowWithContour` | Flutter 用 Material `TabBar`，外观换成 ColorOS 规格 |
| 「设备标识（SZLM ID）」放在协议参数页 | Flutter 放在设置页首屏；改设备标识要同时重建设备参数，和该页其余字段同处一屏更合适 |
| 没有「退出登录」入口 | 与上游 1.2.x 一致：改为在账号管理里移除本机登录或切换游客模式 |
| 关注页标题菜单不显示「账号关注」小标题与分隔线 | coui17 的 `ListPopupColumn` 没有不可选的标题项；六个选项与顺序同 Flutter |
| 自身更新只认 `c001apk-coui-<版本>[+构建号][-<ABI>].apk` 资源，tag 为 `x.y.z` 或 `x.y.z-release` | Flutter 版的 `c001apk-<版本>-android-<ABI>.apk` 是另一个应用；找不到匹配资源时打开发布页面。当前版本带 `-dev` 等后缀时视为低于同号正式版 |
| 设置页尚未分类，「关注」「更新」「账号管理」按现有分组插入 | 分类改版是第五节单独一项 |
| 设置页「关于」进独立页面 | Flutter 溢出菜单里的「关于」弹 `MAboutDialog`，「其他」分组里的「关于」进 `/about`；Compose 两处都进关于页，内容相同 |
| 「发布动态」悬浮按钮随滚动方向收起，进出场用底栏「加号」同款融合球 | Flutter 用 `SlideTransition` 直接滑走；Compose 侧从屏幕右缘鼓出、掐断后落位，返回时反向融回边缘，滚动收起的方向与 Flutter 一致 |
| 应用列表的「更新」球并入底栏，占与「加号」相同的展开位 | Flutter 把它作为 Scaffold 悬浮按钮浮在列表右下角；Compose 侧由 `BottomFloatingNavigationBar` 的 expansion 承载，与底栏同一条水平线，滚动时常显 |
| 点赞与删除只在登录后可用 | 与 Flutter 的 `GlobalData().isLogin` 判断一致，非差异，列此以免被当作缺失 |
| 长文正文的 `title` 段与 `text` 段同样走 `HtmlText` | 两端的解析与渲染口径一致 |
| 匿名读 `/v6/feed/detail` 会弹酷安人机验证 | 服务端对匿名客户端的行为，Flutter 版请求形状完全相同（`skipCaptcha=!verify`） |
| `www.coolapk.com/feed/<id>` 的公开兜底在部分网络不可达 | 该域名走 EdgeOne，会对 OkHttp/Conscrypt 的 TLS 指纹做拦截（同机 `curl` 同一 URL 返回 200）；Flutter 侧同一兜底拿回的也是 HTML，同样解析失败 |
| 从列表（「我的回复」、通知等）经路由打开的楼中楼，首行不是原回复，标题按已加载条数计 | `Route.Reply2Reply` 只带 id，带上原回复或回复数要改 `ui/nav/`（禁改，待确认）；帖子详情内打开的楼中楼已与 Flutter 一致 |
| 底栏用 coui17 的 `BottomFloatingNavigationBar`，标签常显，未选中线性图标、选中换实心 | Flutter 用 Material `NavigationBar`（只显示选中项标签、选中换实心图标）；外观换成 ColorOS 规格，主页与我的用 Material 成对图标，关注用 coui17 的 `Notes` / `NotesFill` |
| 底栏毛玻璃按 ColorOS 系统底栏材质绘制 | 系统 `TYPE_FRAMEWORK_BOTTOM_BAR` 配方（25px 模糊 + ColorDodge / Overlay + Luminosity 着色层、`TYPE_FRAMEWORK_CAPSULE_3` 边缘光、焦散光），由 coui17 复刻；未接系统的 `OplusViewBackgroundRenderEffect` 后端 |
| 切换底栏分页没有横向滑动动画 | Flutter 用 `animateToPage`（300ms，`Curves.ease`）；改动落在 `ui/main/MainScreen.kt`（禁改，待确认） |
| 横屏不切换为侧边导航栏 | Flutter 横屏用 `NavigationRail`；改动落在 `ui/main/MainScreen.kt`（禁改，待确认） |
| 打开浏览器失败提示「没有可用的浏览器」 | Flutter 为「无法打开链接，请检查浏览器设置」；文案在 `ui/common/Platform.kt`（禁改，待确认） |
| 未知 `card` 模板显示「暂不支持此内容」占位行 | 与 Flutter 的 `ListTile('暂不支持此内容')` 同义，样式换成 coui 的脚注行 |
| 帖子从所点卡片向外展开成详情页，返回时缩回卡片，全程不透明；其余二级页整页横向滑入滑出 | Flutter 用 GetX 默认转场。按用户要求：动态行保留卡片展开，设置等普通二级页改为不透明整页滑动。实现在 `ui/nav/AppNavHost.kt` 与 `ui/nav/ExpandTransition.kt`（`ui/nav/` 为禁改目录，本次按用户要求改动并已报告）。图片查看器仍从所点缩略图展开、返回时缩回 |
| 信息流的「接近底部即加载下一页」仍是旧触发方式 | 帖子详情回复区因同样的触发方式卡在转圈，已改为照 Flutter「页脚出现即加载」；`ui/feed/FeedList.kt` 同一写法在单页条目很少、页脚一直可见时也可能停在转圈，改动落在禁改文件（待确认） |

## 八、真机验证记录

设备：OnePlus 8 Pro（IN2020 / Android 16，`192.168.31.216`），安装方式 `install -r --no-incremental`。

已实机走通（1.1.0 移植时）：首页 7 分段（关注 / 应用 / 动态 / 热门 / 话题 / 数码 / 酷图）、
底栏三屏（首页 / 消息 / 设置）、搜索与结果页、帖内详情入口（不跳浏览器，匿名时弹人机验证）、
登录页与网页登录、应用更新、用户 / 话题 / 主题黑名单、协议参数、关于、浏览历史，
信息流卡片的「更多」面板（复制 / 屏蔽 / 举报）与话题实体的统计行。

本轮（补齐卡片交互与 HTML 分支后）复测：

- 信息流图片点开是**全屏图片查看器**（页码 1/5），整组图一并传入，不再进帖子详情。
- 「更多」面板最后一项「举报」可点击，**未被底栏遮挡**（面板覆盖在底栏之上）；
  落地为本应用内 WebView 打开酷安举报页，匿名会被重定向到 `coolapk.com/account/login`，
  返回 JSON 404，URL 构造与 Flutter 逐字一致。
- 「显示表情」开关生效：关闭后表情图节点消失、正文出现 `[doge]` 文本，打开后恢复；
  prefs 记录 true→false→true，测完已恢复原值。
- 消息页与设置页重测通过；7 分段全部出内容；`logcat -b crash` 无记录。

工具注意：`uiautomator dump` 会偶发失败并保留上一次的 `d.xml`，必须校验输出里的
`UI hierchary dumped to` 再用，否则会把旧页面的节点当成本次结果（本轮踩过一次，
表现为「消息页和设置页的截图是同一张」）。

未实机验证：

- 登录后才出现的入口：发布动态悬浮按钮、点赞、删除、以及「我的回复」列表里的回复卡菜单。
- 需要特定数据的卡片形态：置顶徽章、点赞通知卡片、`relationRows` 关联行 chips。
- 帖子详情正文与回复渲染：匿名读 `/v6/feed/detail` 多数情形被人机验证拦住（本轮有一次成功加载）。
- 浏览历史与本地收藏当时为空，历史行复用 `FeedCard` 后的菜单未实测。
- 未知 `card` 模板的占位行、热评的「查看图片(N)」链接需要特定数据，本轮未命中。


代码审查修复后复测（2026-10-15）：

- 每个路由有独立的 ViewModel 存储，出栈即清理：帖子详情出栈后重开会重新加载；
  详情上叠用户页再返回，详情状态（含「热门」排序）保持。
- 连续两次搜索不同关键词，第二次显示自己的结果，不再复用第一次的列表。
- 图片查看器长按出操作面板（保存 / 保存全部 / 分享 / 复制 / 浏览器打开），
  「分享」能下载原图并拉起系统分享面板。此前面板放在页面 Scaffold 之外、根本不渲染；
  现在每个路由外包一层透明 Scaffold 承载浮层，帖子详情「更多」、搜索页清除历史对话框同样恢复。
- 搜索页清除历史对话框按钮改为「取消 / 确定」（原为库默认的英文）。
- 「保存图片」未实机点击（会写入用户相册），与分享共用同一下载路径。
- 带图发帖 / 私信、点赞计数、人机验证重放、登录验证码图片均需登录或触发验证，未实机验证。

底栏材质对齐系统相册（2026-10-15，测试机 PLK110 / ColorOS 17，`192.168.31.165`）：

- 对照基准是系统自己的底栏材质：在私有虚拟屏上用系统接口 `OplusViewBackgroundRenderEffect`
  按 `TYPE_FRAMEWORK_BOTTOM_BAR` 配方（与系统相册所用 COUI 库的调用一致）绘制胶囊，
  与 Compose 底栏在同一张彩色背景上逐像素比较（胶囊内部平均每通道绝对差，0–255）。
- 修改前：深色 22.3、浅色 12.6；背景色彩只保留 22–37% 的饱和度（系统为 71–86%），
  4dp 条纹的残留起伏 0.7（系统 2.2，说明模糊宽了约三倍）；深色下胶囊外圈是白色外发光，
  系统是下方焦散光加投影。
- 修改后：深色 2.5、浅色 3.5；饱和度与系统一致，条纹残留 2.3；胶囊外的投影与焦散光逐行吻合。
  展开态（发布按钮融出）几何一致，平均差 2.5 / 3.1。
- 尚存差异：顶边高光比系统宽约 1px；系统胶囊内部下沿约 15dp 有约 4–11 级的提亮
  （系统背景采样包含焦散投影与 `OplusMaterialShadowParams`，Compose 背景层不含），
  这部分只有接入系统后端才能消除，实测嵌入 Compose 的系统后端与系统材质差 0.5。
- 真机同屏对比（深色、手势导航）：系统相册与本应用都只有一个窗口层，没有 SurfaceView，
  也没有 SurfaceFlinger 窗口模糊，相册底栏的毛玻璃是应用内 RenderNode 级效果。
  相册按约 565dpi 绘制（不受「显示大小」影响），本应用按 620dpi，所以同为 56dp 的胶囊，本应用在像素上大约大 10%。
- 胶囊图标与文字之外的上下沿：亮度相册 48.4，修改前 57.9，修改后 47.5；横向起伏相册 7.0，
  修改前 4.0（近乎纯色板），修改后 7.3。图片滚到底栏下方时，颜色能透出胶囊（列饱和度最高 0.44，修改前为 0）。
- 底栏标签：原生 `applyNaviVerticalStyle` 用 `dp2px(10f)` 定字号；相册两字标签宽 18.4dp，
  修改前 25.5dp（14sp），修改后 18.1dp；标签字形顶到导航项顶部的距离相册 32.9dp、修改后 32.8dp
  （补上了原生 TextView 字体内边距撑出的行高）。图标与文字的墨迹间距仍差约 0.9dp，来自两套图标的留白不同。
- 设置页：分组顺序、「1.00x」、打开即显示缓存大小（31.57M）、右上角菜单（意见反馈 / 关于）实机确认；
  帖子详情正文长按进复制页实机确认；`logcat -b crash` 无记录。

帖子展开动画与回复区状态（2026-10-16，`192.168.31.216`）：

- 回复区一直转圈的原因有两处：一是页面不渲染 `replyStatus`，空列表时只剩页脚，而页脚的 `Idle`
  画成转圈，所以零回复一直转；二是加载更多靠「可见末项接近列表底部」触发，帖子正文在回复返回前
  就已占满列表，这个标志先变成 true，回复加载中 `loadMoreReplies` 直接返回，之后标志不再变化，
  于是最后一条回复下面一直转。现照 Flutter `_buildFeedReply`：加载中转圈、空列表「暂无内容」、
  失败显示错误且点击重试，页脚出现即触发下一页；楼中楼同样处理。
- 打开帖子：画面从卡片原位、带圆角长出，背景压暗，无黑白闪屏、无左上角直角展开；正文加载期间显示
  卡片里的内容，不再是空白页加转圈。返回：页面缩回卡片原位，末帧卡片位置与点击前一致、无跳动。卡片与详情
  两套排版不同时显示，交接处有约 1–2 帧只剩底色；试过让两者短暂重叠，会出现双重文字，已退回。
- 人机验证页叠在整个应用之上、不在返回栈里，此前按返回会把下面的帖子一起关掉；现在返回只关验证页
  （与 Flutter `Get.to` 一致）；被验证拦下时回复区显示「请求需要验证码验证，请完成验证后重试」，不再转圈。
- 回复区实机复测（填入真实 SZLM ID 之后）：2 条回复的帖子末尾显示「没有更多了」，0 回复的帖子显示
  「暂无内容」，两者都不再转圈，也没有弹人机验证。此前一直弹验证的原因是设备标识为空时随机生成的
  32 位十六进制 ID 不是真实的数盟 ID，登录与否都不影响；这和本次改动无关。
- 尚存问题：卡片延伸到悬浮底栏下方时，展开第一帧起底栏就被卡片副本盖住，缩回到最后一帧底栏才重新出现。
- `logcat -b crash` 无记录。

上游 1.2.x 移植复测（2026-10-17，`192.168.31.165`，`install -r --no-incremental` 覆盖旧版）：

- 升级后原有登录自动写入账号库，`activeUid` 指向原账号，仍为登录状态；首页分段为应用 / 动态 / 热门 / 话题 / 数码 / 酷图，底栏为主页 / 关注 / 我的。
- 关注页：标题下拉六项齐全，「用户关注」加载服务端关注流；切到「悄悄关注」显示说明行、「管理」与空状态文案。
- 设置开启「悄悄关注」后，在用户主页长按「关注」1 秒：本机名单写入该用户，按钮出现「已悄悄关注」图标，账号关注未被触发；关注页随即合并出该用户的动态。
- 账号管理切到游客：账号管理页自动关闭、停在「我的」、显示「点击登录」并隐藏统计卡；游客的关注页仍读同一份本机名单。再点已保存账号切回，资料与等级恢复。
- 「我的 → 私信与信息」首行为酷安小秘书，点开为系统通知流；会话列表正常。
- 手动「检查更新」：本仓库尚无 Release，接口返回 404，应用提示且不崩溃。
- 测完已恢复：本机名单清空、悄悄关注关闭、关注页回到「用户关注」、原账号登录。`logcat -b crash` 无记录。
- 未实机验证：密码登录与网页登录添加第二个账号（需要真实账号）、有 Release 时的更新弹窗与下载（单元测试覆盖解析与选包）。

上游 1.3.5 移植复测（2026-10-24，OnePlus 8 Pro `192.168.31.216`，release 2.0.20-pre）：

- 冷启动后「我的」顶部出现「c001apk 小助手」，副标题为线上公告摘要，未读徽标 1；
  点开列表显示 1 篇有效公告（撤回的测试公告不显示），点开弹窗正文按 Markdown 渲染
  （标题、列表、加粗），「展开阅读」铺满全屏，返回先收起再关闭。
- 读完返回列表与「我的」，未读徽标消失；公告正文缓存清理与「自动检查公告」开关在
  设置 → 更新 中实机确认，开关来回切换后恢复开启。
- 帖子详情右上角出现「保存内容」，选择 Markdown 后经系统文件选择器写入
  `Download/<标题>-<id>.md`；文件含标题、作者、发布时间、两个来源链接、正文与热门评论，
  话题锚点转为 Markdown 链接，正文中的 `[cos滑稽]` 被转义为字面文本。
- 普通动态（含话题锚点与表情码）不会被误判为 Markdown；信息流、详情、长文与转发卡片
  的既有渲染未出现回归；`logcat -b crash` 无记录。
- 未实机验证：重要公告自动弹窗（线上表当前没有 `important=true` 的公告）、
  离线 HTML 的图片内嵌（本轮打开的动态没有图片）、「查看原文」（未命中 Markdown 动态）。
