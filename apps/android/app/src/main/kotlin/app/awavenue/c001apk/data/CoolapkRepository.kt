package app.awavenue.c001apk.data

import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.network.Api
import app.awavenue.c001apk.network.ApiClient
import app.awavenue.c001apk.network.ApiFailure
import app.awavenue.c001apk.network.ApiFailureKind
import app.awavenue.c001apk.network.Body
import app.awavenue.c001apk.network.FilePart
import app.awavenue.c001apk.network.HttpResponse
import app.awavenue.c001apk.network.readApiObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A feed detail plus the two states only the detail endpoint can report. */
class FeedDetail(val feed: Entity, val isPublic: Boolean, val captchaVerification: String?)

/** The shape the write endpoints answer with: a message, a payload, or both. */
class ActionResult(val message: String, val data: JsonElement?) {
    // The feed like endpoints answer `{"count": n, ...}`; the reply ones a bare number.
    val count: String get() = when (data) {
        is JsonPrimitive -> data.content
        is JsonObject -> (data["count"] as? JsonPrimitive)?.content.orEmpty()
        else -> ""
    }
}

/** What the login page needs before it can submit: the SESSID cookie and the form's hash. */
class LoginParams(val requestHash: String?)

class LoginOutcome(val success: Boolean, val message: String)

/**
 * Every Coolapk call the client makes. Endpoint paths, parameters and defaults are copied from
 * the Flutter client's `NetworkRepo` and the controllers that call the API directly.
 */
class CoolapkRepository(
    private val api: ApiClient,
    private val settings: AppSettings,
    private val blacklist: BlacklistStore,
    private val session: SessionStore,
) {
    // ---- lists -------------------------------------------------------------------------------

    suspend fun homeFeed(page: Int, firstItem: String?, lastItem: String?): List<Entity> = parse(
        api.get(
            Api.HOME_FEED,
            mapOf(
                "page" to page,
                "firstLaunch" to 1,
                "installTime" to settings.installTime,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
                "ids" to "",
            ),
        ),
    )

    suspend fun dataList(
        url: String,
        title: String,
        page: Int,
        firstItem: String?,
        lastItem: String?,
        includeConfigCard: Boolean = false,
    ): List<Entity> = parse(
        api.get(
            Api.DATA_LIST,
            mapOf(
                "url" to url,
                "title" to title,
                "subTitle" to "",
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
                "page" to page,
            ),
        ),
        includeConfigCard = includeConfigCard,
    )

    /**
     * A list endpoint named by a path the caller owns, e.g. `/v6/page/dataList?url=...` or
     * `/v6/notification/list`. The path is appended to the API host verbatim, query string and all.
     */
    suspend fun listFromUrl(url: String, params: Map<String, Any?> = emptyMap()): List<Entity> =
        parse(api.get(Api.onApi(url), params))

    /** Any GET whose `data` is a single object. */
    suspend fun fromUrl(url: String, params: Map<String, Any?> = emptyMap(), skipCaptcha: Boolean = false): Entity {
        val json = readApiObject(api.get(Api.onApi(url), params, skipCaptcha))
        return dataObject(json)
    }

    suspend fun userFeed(uid: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> = parse(
        api.get(
            Api.USER_FEED,
            mapOf(
                "uid" to uid,
                "page" to page,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
                "showAnonymous" to 0,
                "isIncludeTop" to 1,
                "showDoing" to 0,
            ),
        ),
    )

    suspend fun feedReply(
        id: String,
        listType: String,
        page: Int,
        firstItem: String?,
        lastItem: String?,
        discussMode: Int = 1,
        feedType: String = "feed",
        blockStatus: Int = 0,
        fromFeedAuthor: Int = 0,
    ): List<Entity> = parse(
        api.get(
            Api.FEED_REPLY,
            mapOf(
                "id" to id,
                "listType" to listType,
                "page" to page,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
                "discussMode" to discussMode,
                "feedType" to feedType,
                "blockStatus" to blockStatus,
                "fromFeedAuthor" to fromFeedAuthor,
            ),
        ),
    )

    /** Sub-replies of one reply; same endpoint, different defaults. */
    suspend fun replyToReply(id: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> = parse(
        api.get(
            Api.FEED_REPLY_FROM_API,
            mapOf(
                "id" to id,
                "page" to page,
                "listType" to "",
                "discussMode" to 0,
                "feedType" to "feed_reply",
                "blockStatus" to 0,
                "fromFeedAuthor" to 0,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
            ),
        ),
    )

    suspend fun search(
        type: String,
        feedType: String,
        sort: String,
        searchValue: String,
        isStrict: Int,
        pageType: String?,
        pageParam: String?,
        page: Int,
        firstItem: String?,
        lastItem: String?,
    ): List<Entity> = parse(
        api.get(
            Api.SEARCH,
            mapOf(
                "type" to type,
                "feedType" to feedType,
                "sort" to sort,
                "searchValue" to searchValue,
                "isStrict" to isStrict,
                "pageType" to pageType?.ifEmpty { null },
                "pageParam" to pageParam?.ifEmpty { null },
                "page" to page,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
                "showAnonymous" to -1,
            ),
        ),
    )

    suspend fun coolPic(tag: String, type: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> = parse(
        api.get(
            Api.PICTURE_LIST,
            mapOf(
                "tag" to tag,
                "type" to type,
                "page" to page,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
            ),
        ),
    )

    suspend fun dyhDetail(dyhId: String, type: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> = parse(
        api.get(
            Api.DYH_LIST,
            mapOf(
                "dyhId" to dyhId,
                "type" to type,
                "page" to page,
                "firstItem" to firstItem?.ifEmpty { null },
                "lastItem" to lastItem?.ifEmpty { null },
            ),
        ),
    )

    suspend fun messageList(page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl("/v6/message/list", pageParams(page, firstItem, lastItem))

    suspend fun notificationList(url: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl(url, pageParams(page, firstItem, lastItem))

    suspend fun chat(ukey: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl("/v6/message/chat", pageParams(page, firstItem, lastItem) + ("ukey" to ukey))

    suspend fun collectionList(page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl("/v6/collection/list", pageParams(page, firstItem, lastItem) + ("showDefault" to 0))

    suspend fun collectionItems(id: String, page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl("/v6/collection/itemList", pageParams(page, firstItem, lastItem) + ("id" to id))

    suspend fun userList(url: String, uid: String?, id: String?, page: Int, firstItem: String?, lastItem: String?): List<Entity> =
        listFromUrl(
            url,
            pageParams(page, firstItem, lastItem) +
                mapOfNotNull(uid?.let { "uid" to it }, id?.let { "id" to it }),
        )

    suspend fun productCategoryList(): List<Entity> = parse(api.get(Api.PRODUCT_CATEGORY_LIST))

    // ---- details -----------------------------------------------------------------------------

    /**
     * The feed body. Access challenges fall back to the official public representation, which
     * cannot carry this account's like state — the caller is told through [FeedDetail.isPublic].
     */
    suspend fun feedDetail(id: String, verify: Boolean = false): FeedDetail {
        if (!Regex("^[0-9]+$").matches(id)) {
            throw ApiFailure(ApiFailureKind.InvalidResponse, "动态编号无效")
        }
        val response = api.get(Api.FEED_DETAIL, mapOf("id" to id), skipCaptcha = !verify)
        // The challenge arrives as HTTP 200 with a captcha envelope, so the envelope check is what
        // has to fall back — not the transport.
        val json = try {
            readApiObject(response)
        } catch (failure0: Throwable) {
            val failure = ApiFailure.from(failure0)
            if (verify) throw failure
            if (failure.kind !in setOf(ApiFailureKind.Captcha, ApiFailureKind.Authentication)) throw failure
            // The public page stands in for an anonymous reader, but it is not reachable on every
            // network; when it fails the caller still needs the challenge reason to offer the check.
            val public = runCatching { readApiObject(api.get(Api.publicFeed(id))) }.getOrElse { throw failure }
            return FeedDetail(dataObject(public), isPublic = true, captchaVerification = null)
        }
        return FeedDetail(dataObject(json), isPublic = false, captchaVerification = response.captchaVerification)
    }

    suspend fun userSpace(uid: String): Entity = fromUrl("/v6/user/space?uid=$uid")

    suspend fun appDetail(id: String): Entity = fromUrl("/v6/apk/detail", mapOf("id" to id, "installed" to 1))

    suspend fun topicDetail(tag: String): Entity = fromUrl("/v6/topic/newTagDetail", mapOf("tag" to tag))

    suspend fun productDetail(id: String): Entity = fromUrl("/v6/product/detail", mapOf("id" to id))

    suspend fun profile(uid: String): Entity = fromUrl("/v6/user/profile", mapOf("uid" to uid), skipCaptcha = true)

    suspend fun checkLoginInfo(): Entity = fromUrl("/v6/account/checkLoginInfo", skipCaptcha = true)

    suspend fun checkCount(): Entity = fromUrl("/v6/notification/checkCount")

    // ---- writes ------------------------------------------------------------------------------

    suspend fun like(id: String, like: Boolean, isFeed: Boolean): ActionResult {
        val path = if (isFeed) {
            if (like) Api.FEED_UNLIKE else Api.FEED_LIKE
        } else {
            if (like) Api.FEED_UNLIKE_REPLY else Api.FEED_LIKE_REPLY
        }
        val response = api.post(
            path,
            query = mapOf("id" to id, "detail" to 0),
            body = Body.Form(listOf("trace" to "")),
        )
        return actionResult(readApiObject(response))
    }

    suspend fun deleteFeed(id: String): ActionResult = actionResult(readApiObject(api.post(Api.FEED_DELETE, mapOf("id" to id))))

    suspend fun deleteReply(id: String): ActionResult = actionResult(readApiObject(api.post(Api.FEED_DELETE_REPLY, mapOf("id" to id))))

    suspend fun deleteNotification(id: String): ActionResult =
        actionResult(readApiObject(api.post(Api.NOTIFICATION_DELETE, mapOf("id" to id))))

    suspend fun followUser(uid: String, follow: Boolean): ActionResult =
        actionResult(readApiObject(api.post(if (follow) Api.USER_UNFOLLOW else Api.USER_FOLLOW, mapOf("uid" to uid))))

    suspend fun followTag(tag: String, follow: Boolean): ActionResult =
        actionResult(readApiObject(api.post(if (follow) Api.FEED_UNFOLLOW_TAG else Api.FEED_FOLLOW_TAG, mapOf("tag" to tag))))

    suspend fun followApk(id: String, follow: Boolean): ActionResult =
        actionResult(readApiObject(api.post(if (follow) Api.APK_UNFOLLOW else Api.APK_FOLLOW, mapOf("id" to id))))

    /** The product follow state travels in a form body, not the query string. */
    suspend fun changeProductFollow(id: String, follow: Boolean): ActionResult {
        val response = api.post(
            Api.PRODUCT_CHANGE_FOLLOW,
            body = Body.Form(listOf("id" to id, "status" to if (follow) "0" else "1")),
        )
        return actionResult(readApiObject(response))
    }

    suspend fun postReply(id: String, type: String, fields: List<Pair<String, String>>, files: List<FilePart>): ActionResult {
        val response = api.post(
            Api.FEED_REPLY_POST,
            query = mapOf("id" to id, "type" to type),
            body = if (files.isEmpty()) Body.Form(fields) else Body.Multipart(fields, files),
        )
        return actionResult(readApiObject(response))
    }

    suspend fun createFeed(fields: List<Pair<String, String>>, files: List<FilePart>): ActionResult {
        // The server expects status to be 1 and a derived publish_status, as the Flutter client sends.
        val normalized = fields.toMap().toMutableMap().apply {
            putIfAbsent("publish_status", if (get("status") == "-1") "1" else "0")
            put("status", "1")
        }.toList()
        val response = api.post(
            Api.FEED_CREATE,
            body = if (files.isEmpty()) Body.Form(normalized) else Body.Multipart(normalized, files),
        )
        return actionResult(readApiObject(response))
    }

    suspend fun sendMessage(uid: String, message: String, pic: String?): ActionResult {
        val fields = listOfNotNull("message" to message, pic?.let { "message_pic" to it })
        val response = api.post(Api.MESSAGE_SEND, query = mapOf("uid" to uid), body = Body.Form(fields))
        return actionResult(readApiObject(response))
    }

    suspend fun deleteMessage(ukey: String, id: String): ActionResult =
        actionResult(readApiObject(api.get(Api.MESSAGE_DELETE, mapOf("ukey" to ukey, "id" to id))))

    suspend fun appsUpdate(pkgs: String): JsonObject =
        api.postJson(Api.APK_CHECK_UPDATE, query = mapOf("coolmarket_beta" to 0), body = Body.Multipart(listOf("pkgs" to pkgs), emptyList()))

    // ---- redirect readers --------------------------------------------------------------------

    /** The APK download endpoint answers 302; the caller wants the target, not the body. */
    suspend fun appDownloadUrl(packageName: String, id: Int, versionCode: Int): String? = runCatching {
        api.get(
            Api.APK_DOWNLOAD,
            mapOf("pn" to packageName, "aid" to id, "vc" to versionCode, "extra" to ""),
            followRedirects = false,
        ).redirectTarget
    }.getOrNull()

    suspend fun messageImageUrl(id: String): String? = runCatching {
        api.get(Api.MESSAGE_SHOW_IMAGE, mapOf("id" to id, "type" to "s"), followRedirects = false).redirectTarget
    }.getOrNull()

    // ---- captcha and upload ------------------------------------------------------------------

    suspend fun captchaImage(): ByteArray = api.getBytes(
        Api.ACCOUNT_CAPTCHA_IMAGE,
        mapOf("_" to System.currentTimeMillis(), "w" to 270, "h" to 113),
    )

    suspend fun requestValidate(code: String): String {
        val response = api.post(Api.ACCOUNT_REQUEST_VALIDATE, body = Body.Form(listOf("type" to "err_request_captcha", "code" to code)))
        val json = readApiObject(response)
        return (json["data"] as? JsonPrimitive)?.content.orEmpty()
    }

    suspend fun ossUploadPrepare(fields: List<Pair<String, String>>): JsonObject =
        api.postJson(Api.OSS_UPLOAD_PREPARE, body = Body.Multipart(fields, emptyList()))

    // ---- login -------------------------------------------------------------------------------

    /**
     * Walks the two-step login page handshake: the first response carries SESSID, the second
     * the HTML whose `data-request-hash` the password form must echo.
     */
    suspend fun loginParams(): LoginParams {
        val first = api.get(Api.LOGIN_PAGE)
        first.setCookies.firstOrNull()?.let { session.updateSessid(it.substringBefore(';')) }
        val page = api.get(Api.LOGIN_BY_COOLAPK)
        return LoginParams(requestHash = REQUEST_HASH.find(page.body)?.groupValues?.getOrNull(1))
    }

    suspend fun loginCaptchaImage(): ByteArray =
        api.getBytes("${Api.LOGIN_CAPTCHA_IMAGE}?${System.currentTimeMillis()}")

    suspend fun login(requestHash: String, account: String, password: String, captcha: String): LoginOutcome {
        val response = api.post(
            Api.LOGIN_BY_COOLAPK,
            body = Body.Form(
                listOf(
                    "submit" to "1",
                    "randomNumber" to TokenUtils.createRandomNumber(),
                    "requestHash" to requestHash,
                    "login" to account,
                    "password" to password,
                    "captcha" to captcha,
                    "code" to "",
                ),
            ),
        )
        val json = runCatching { Json.parseToJsonElement(response.body) as? JsonObject }.getOrNull()
            ?: throw ApiFailure(ApiFailureKind.InvalidResponse, "登录响应格式异常，请稍后重试")
        val status = (json["status"] as? JsonPrimitive)?.content.orEmpty()
        val message = (json["message"] as? JsonPrimitive)?.content.orEmpty()
        if (status != "1") return LoginOutcome(false, message)
        val uid = cookieParam(response.setCookies, "uid")
        val username = cookieParam(response.setCookies, "username")
        val token = cookieParam(response.setCookies, "token")
        if (uid.isNullOrEmpty() || username.isNullOrEmpty() || token.isNullOrEmpty()) {
            return LoginOutcome(false, message)
        }
        session.saveLogin(uid, username, token)
        return LoginOutcome(true, message)
    }

    /** The cookies are re-seeded by the web login flow, which reads them off the browser. */
    fun saveWebLogin(uid: String, username: String, token: String) = session.saveLogin(uid, username, token)

    // ---- parsing -----------------------------------------------------------------------------

    private fun parse(response: HttpResponse, includeConfigCard: Boolean = false): List<Entity> =
        parseList(readApiObject(response), blacklist, includeConfigCard)

    private fun mapOfNotNull(vararg pairs: Pair<String, Any?>?): Map<String, Any?> =
        pairs.filterNotNull().toMap()

    private fun pageParams(page: Int, firstItem: String?, lastItem: String?): Map<String, Any?> = mapOf(
        "page" to page,
        "firstItem" to firstItem?.ifEmpty { null },
        "lastItem" to lastItem?.ifEmpty { null },
    )

    companion object {
        private val supportedTypes = setOf(
            "feed", "apk", "product", "user", "topic", "notification", "productBrand", "contacts",
            "recentHistory", "feed_reply", "message", "collection",
        )
        private val supportedTemplates = setOf(
            "imageCarouselCard_1", "iconLinkGridCard", "iconMiniScrollCard", "iconMiniGridCard",
            "imageSquareScrollCard", "titleCard", "iconScrollCard", "imageTextScrollCard",
            "iconTabLinkGridCard", "verticalColumnsFullPageCard", "noMoreDataCard", "time", "messageCard",
        )

        /** `data-request-hash` sits on the login page's `<Body>` element. */
        private val REQUEST_HASH = Regex("data-request-hash=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

        /** Reads `name=value` out of the last Set-Cookie carrying it, as the Flutter client does. */
        fun cookieParam(cookies: List<String>, name: String): String? = cookies
            .filter { it.contains("$name=") }
            .lastOrNull()
            ?.substringBefore(';')
            ?.removePrefix("$name=")
            ?.trim()

        fun dataObject(json: JsonObject): Entity {
            val data = json["data"]
            if (data == null || data is JsonNull) throw ApiFailure(ApiFailureKind.InvalidResponse, "服务器响应缺少数据字段，请稍后重试")
            return Entity(data as? JsonObject ?: JsonObject(emptyMap()))
        }

        fun actionResult(json: JsonObject): ActionResult {
            val message = (json["message"] as? JsonPrimitive)?.content.orEmpty()
            return ActionResult(message, json["data"])
        }

        /**
         * Flattens nested list cards, then keeps the entity kinds the client can render and
         * drops anything on the user's block list.
         */
        fun parseList(response: JsonObject, blacklist: BlacklistStore? = null, includeConfigCard: Boolean = false): List<Entity> {
            val data = response["data"] as? JsonArray ?: return emptyList()
            val templates = if (includeConfigCard) supportedTemplates + "configCard" else supportedTemplates
            return expandListCards(data)
                .mapNotNull { (it as? JsonObject)?.let(::Entity) }
                .filter { item ->
                    if (item.entityTemplate == "refreshCard" && "没有" in item.title) return@filter true
                    (item.entityType in supportedTypes || item.entityTemplate in templates) &&
                        blacklist?.isUserBlocked(item.uid) != true &&
                        blacklist?.isTopicBlocked(
                            item.tags,
                            item.ttitle,
                            item.relationRows.firstOrNull()?.title.orEmpty(),
                        ) != true
                }
        }

        private fun expandListCards(items: List<JsonElement>, depth: Int = 0): List<JsonElement> {
            if (depth > 20) throw ApiFailure(ApiFailureKind.InvalidResponse, "服务器响应结构异常，请稍后重试")
            return items.flatMap { item ->
                val obj = item as? JsonObject
                val isListCard = (obj?.get("entityType") as? JsonPrimitive)?.content == "card" &&
                    (obj["entityTemplate"] as? JsonPrimitive)?.content == "listCard"
                if (isListCard) {
                    (obj["entities"] as? JsonArray)?.let { expandListCards(it, depth + 1) }.orEmpty()
                } else {
                    listOf(item)
                }
            }
        }
    }
}
