package app.awavenue.c001apk.network

import app.awavenue.c001apk.data.Protocol

/** Every Coolapk endpoint the client uses, with the host each one lives on. */
object Api {
    private const val API = Protocol.URL_API_SERVICE
    private const val API2 = Protocol.URL_API2_SERVICE
    private const val ACCOUNT = Protocol.URL_ACCOUNT_SERVICE

    fun onApi(path: String) = "$API$path"

    const val HOME_FEED = "$API2/v6/main/indexV8"
    const val DATA_LIST = "$API/v6/page/dataList"

    const val FEED_DETAIL = "$API/v6/feed/detail"
    const val FEED_REPLY = "$API2/v6/feed/replyList"
    const val FEED_REPLY_FROM_API = "$API/v6/feed/replyList"
    const val FEED_LIKE = "$API/v6/feed/like"
    const val FEED_UNLIKE = "$API/v6/feed/unlike"
    const val FEED_LIKE_REPLY = "$API/v6/feed/likeReply"
    const val FEED_UNLIKE_REPLY = "$API/v6/feed/unLikeReply"
    const val FEED_REPLY_POST = "$API/v6/feed/reply"
    const val FEED_CREATE = "$API/v6/feed/createFeed"
    const val FEED_DELETE = "$API/v6/feed/deleteFeed"
    const val FEED_DELETE_REPLY = "$API/v6/feed/deleteReply"
    const val FEED_FOLLOW_TAG = "$API/v6/feed/followTag"
    const val FEED_UNFOLLOW_TAG = "$API/v6/feed/unFollowTag"

    const val USER_SPACE = "$API/v6/user/space"
    const val USER_FEED = "$API/v6/user/feedList"
    const val USER_FOLLOW = "$API/v6/user/follow"
    const val USER_UNFOLLOW = "$API/v6/user/unfollow"
    const val USER_FOLLOW_LIST = "$API/v6/user/followList"
    const val USER_APK_FOLLOW_LIST = "$API/v6/user/apkFollowList"
    const val USER_FANS_LIST = "$API/v6/user/fansList"
    const val USER_RECENT_HISTORY_LIST = "$API/v6/user/recentHistoryList"
    const val USER_LIKE_LIST = "$API/v6/user/likeList"
    const val USER_REPLY_LIST = "$API/v6/user/replyList"
    const val USER_REPLY_TO_ME_LIST = "$API/v6/user/replyToMeList"
    const val USER_PROFILE = "$API/v6/user/profile"

    const val COLLECTION_LIST = "$API/v6/collection/list"
    const val COLLECTION_ITEM_LIST = "$API/v6/collection/itemList"

    const val APK_DETAIL = "$API/v6/apk/detail"
    const val APK_DOWNLOAD = "$API/v6/apk/download"
    const val APK_CHECK_UPDATE = "$API/v6/apk/checkUpdate"
    const val APK_FOLLOW = "$API/v6/apk/follow"
    const val APK_UNFOLLOW = "$API/v6/apk/unFollow"

    const val SEARCH = "$API/v6/search"

    const val TOPIC_DETAIL = "$API/v6/topic/newTagDetail"
    const val PRODUCT_DETAIL = "$API/v6/product/detail"
    const val PRODUCT_CATEGORY_LIST = "$API/v6/product/categoryList"
    const val PRODUCT_CHANGE_FOLLOW = "$API/v6/product/changeFollowStatus"
    const val PRODUCT_CONFIG_INFO = "https://m.coolapk.com/mp/product/configInfo"

    const val DYH_LIST = "$API/v6/dyhArticle/list"
    const val PICTURE_LIST = "$API/v6/picture/list"

    const val NOTIFICATION_CHECK_COUNT = "$API/v6/notification/checkCount"
    const val NOTIFICATION_LIST = "$API/v6/notification/list"
    const val NOTIFICATION_DELETE = "$API/v6/notification/delete"
    const val NOTIFICATION_AT_ME = "$API/v6/notification/atMeList"
    const val NOTIFICATION_AT_COMMENT_ME = "$API/v6/notification/atCommentMeList"
    const val NOTIFICATION_FEED_LIKE = "$API/v6/notification/feedLikeList"
    const val NOTIFICATION_CONTACTS_FOLLOW = "$API/v6/notification/contactsFollowList"

    const val MESSAGE_LIST = "$API/v6/message/list"
    const val MESSAGE_SEND = "$API/v6/message/send"
    const val MESSAGE_CHAT = "$API/v6/message/chat"
    const val MESSAGE_DELETE = "$API/v6/message/delete"
    const val MESSAGE_SHOW_IMAGE = "$API/v6/message/showImage"

    const val LIVE_PHOTO_VIDEO = "$API/v6/livePhoto/showVideo"
    const val EVENT_DETAIL = "$API2/v6/event/detail"

    const val ACCOUNT_CHECK_LOGIN = "$API/v6/account/checkLoginInfo"
    const val ACCOUNT_CAPTCHA_IMAGE = "$API/v6/account/captchaImage"
    const val ACCOUNT_REQUEST_VALIDATE = "$API/v6/account/requestValidate"

    const val LOGIN_PAGE = "$ACCOUNT/auth/login?type=mobile"
    const val LOGIN_BY_COOLAPK = "$ACCOUNT/auth/loginByCoolApk"
    const val LOGIN_CAPTCHA_IMAGE = "$ACCOUNT/auth/showCaptchaImage"

    const val OSS_UPLOAD_PREPARE = "$API/v6/upload/ossUploadPrepare"
    const val OSS_UPLOAD_CALLBACK = "$API/v6/callback/mobileOssUploadSuccessCallback"

    fun publicFeed(id: String) = "${Protocol.URL_COOLAPK}/feed/$id"
}
