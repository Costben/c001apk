package app.awavenue.c001apk.ui.notification

import app.awavenue.c001apk.model.Entity

/**
 * The parts of one notification the card and the system reminder both show.
 *
 * Coolapk splits a notification across several shapes: an @ reply keeps the sender in
 * `username`/`userAvatar`, a like keeps them in `likeUsername`/`likeAvatar`, a follow uses
 * `from*`, and the reply itself sits in `message` while `note` only carries the action. Reading
 * one pair of fields therefore showed a blank name and hid the comment on some lists.
 */
data class NotificationDisplay(
    val actorName: String,
    val actorUid: String,
    val actorAvatar: String,
    val action: String,
    val content: String,
    val target: String,
    val link: String?,
)

private val linkPattern = Regex("<a[^>]*href=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val tagPattern = Regex("<[^>]+>")
private val breaksPattern = Regex("<br\\s*/?>|</p>|</div>", RegexOption.IGNORE_CASE)
private val spacesPattern = Regex("\\s+")

/** The first non-empty value among [keys], in the order the API versions use them. */
private fun Entity.firstOf(vararg keys: String): String =
    keys.firstNotNullOfOrNull { string(it).takeIf(String::isNotEmpty) }.orEmpty()

/**
 * Normalises one notification row. [action] falls back to a sentence the row itself implies when
 * older lists send only the actor and the payload, so a card never comes out blank.
 */
fun notificationDisplay(item: Entity): NotificationDisplay {
    val note = item.string("note")
    val message = item.string("message")
    val likeUser = item.obj("likeUserInfo")
    val isLike = likeUser != null || item.firstOf("likeUid", "likeUsername", "likeTime").isNotEmpty()
    val feedType = item.firstOf("feedTypeName", "infoHtml").ifEmpty { "动态" }
    val action = when {
        note.isNotEmpty() -> note
        isLike -> "赞了你的${plainNotificationText(feedType)}"
        item.string("type") == "contacts_follow" -> "关注了你"
        else -> ""
    }
    val target = item.firstOf("extra_title", "targetTitle")
        .ifEmpty { item.obj("feedInfo")?.firstOf("message_title", "title", "message").orEmpty() }
    val content = message.takeIf { value ->
        value.isNotEmpty() && !plainNotificationText(action).contains(plainNotificationText(value))
    }.orEmpty()
    return NotificationDisplay(
        actorName = item.firstOf("likeUsername", "fromusername", "username")
            .ifEmpty { likeUser?.string("username").orEmpty() }
            .ifEmpty { item.username.orEmpty() },
        actorUid = item.firstOf("likeUid", "fromuid", "uid")
            .ifEmpty { likeUser?.string("uid").orEmpty() }
            .ifEmpty { item.uid },
        actorAvatar = item.firstOf("likeAvatar", "fromUserAvatar", "userAvatar")
            .ifEmpty { likeUser?.string("userAvatar").orEmpty() }
            .ifEmpty { item.userAvatar },
        action = action,
        content = content,
        target = target.takeIf { value ->
            value.isNotEmpty() && !plainNotificationText(action).contains(plainNotificationText(value))
        }.orEmpty(),
        link = notificationLink(item),
    )
}

/**
 * Where a notification points. The item's own `url` is authoritative; older system rows only
 * carry a link inside [Entity.string] `note`. A reply's first anchor is often the sender's profile,
 * so a content path wins over `/u/` when both are present.
 */
fun notificationLink(item: Entity): String? {
    val direct = item.firstOf("url", "targetUrl").takeIf(String::isNotEmpty)
    val links = linkPattern.findAll(item.string("note"))
        .mapNotNull { it.groupValues.getOrNull(1) }
        .filter(String::isNotEmpty)
        .toList()
    // A reply notification's own `url` is the sender's profile; the post (with the reply id) is
    // the anchor inside the note. Content links therefore win over the profile fallback.
    return links.firstOrNull { URL_CONTENT_PREFIXES.any(it::startsWith) }
        ?: direct?.takeIf { url -> URL_CONTENT_PREFIXES.none(url::startsWith) }
        ?: links.firstOrNull { url -> !url.startsWith("/u/") }
        ?: direct
        ?: links.firstOrNull()
}

private val URL_CONTENT_PREFIXES = listOf("/feed/", "/apk/", "/game/", "/t/", "/product/", "/event/", "/dyh/")

/** Notification HTML is one line in a notification shade; tags and entities are not wanted there. */
fun plainNotificationText(html: String): String =
    breaksPattern.replace(html, " ")
        .replace(tagPattern, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(spacesPattern, " ")
        .trim()

/** The sentence a system reminder shows for [this] row, falling back to [fallback] when empty. */
fun NotificationDisplay.summary(fallback: String): String =
    listOf(plainNotificationText(action), plainNotificationText(content), plainNotificationText(target))
        .filter(String::isNotEmpty)
        .joinToString("：")
        .ifEmpty { fallback }
