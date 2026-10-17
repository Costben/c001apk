package app.awavenue.c001apk.ui.nav

import app.awavenue.c001apk.data.BlacklistType
import app.awavenue.c001apk.data.HistoryType
import app.awavenue.c001apk.model.Entity

/** The list kinds the `/ffflist` route serves, matching the Flutter enum of the same name. */
enum class FFFListType(val label: String) {
    Feed("我的动态"),
    Follow("我的关注"),
    Apk("应用关注"),
    UserFollow("TA关注的人"),
    Fan("TA的粉丝"),
    Recent("我的常去"),
    Like("我的赞"),
    Reply("我的回复"),
    ReplyMe("我收到的回复"),
    Fav("本地收藏"),
    History("浏览历史"),
    Collection("我的收藏"),
    CollectionItem("收藏夹"),
}

/** The five notification lists behind `/notification`. */
enum class NotificationType(val label: String) {
    At("@我的动态"),
    Comment("@我的评论"),
    Like("我收到的赞"),
    Follow("好友关注"),
    Message("私信"),
}

/**
 * Every destination in the app, mirroring the Flutter router's route table. Arguments are typed
 * fields instead of the two ad-hoc maps GetX uses.
 */
sealed interface Route {
    data object Main : Route
    data object Login : Route
    data object AppUpdate : Route
    data object Params : Route
    data object About : Route
    /** [preview] is the list entry the post was opened from, drawn while the full post loads. */
    data class Feed(val id: String, val preview: Entity? = null) : Route
    data class Reply2Reply(val id: String, val replyId: String = "", val originUid: String = "") : Route
    data class User(val uid: String) : Route
    data class Apk(val packageName: String) : Route
    data class Topic(val tag: String) : Route
    data class Product(val id: String) : Route
    data class Search(val title: String, val pageType: String? = null, val pageParam: String? = null) : Route
    data class SearchResult(
        val keyword: String,
        val title: String,
        val pageType: String? = null,
        val pageParam: String? = null,
    ) : Route
    data class Web(val url: String, val isLogin: Boolean = false) : Route
    data class Carousel(val url: String, val title: String = "", val isInit: Boolean = true) : Route
    data class Dyh(val id: String, val title: String) : Route
    data class CoolPic(val title: String) : Route
    data class ImageView(val images: List<String>, val initialPage: Int = 0) : Route
    data class Notification(val type: NotificationType) : Route
    data class FFFList(
        val type: FFFListType,
        val uid: String? = null,
        val id: String? = null,
        val title: String? = null,
    ) : Route
    data class History(val type: HistoryType) : Route
    data class Blacklist(val type: BlacklistType) : Route
    data class Chat(val ukey: String, val uid: String, val username: String) : Route
    data class Copy(val text: String) : Route
}
