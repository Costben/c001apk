package app.awavenue.c001apk.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.awavenue.c001apk.model.Entity
import app.awavenue.c001apk.ui.common.NetworkImage
import app.awavenue.c001apk.ui.common.fromToday
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.theme.COUITheme

/** Entity kinds the row serves, matching the Flutter client's `AppCardType` triple. */
val UserRowTypes = setOf("user", "contacts", "recentHistory")

/**
 * The user, contacts and recently-visited rows of `components/cards/app_card.dart`. A contact
 * carries its counterpart in `userInfo` or `fUserInfo` depending on who followed whom.
 */
@Composable
fun UserRow(item: Entity, onClick: () -> Unit) {
    val type = item.entityType
    val userInfo = item.obj("userInfo")
    val fUserInfo = item.obj("fUserInfo")
    val avatar = when (type) {
        "user" -> item.userAvatar
        "contacts" -> userInfo?.string("userAvatar").orEmpty().ifEmpty { fUserInfo?.string("userAvatar").orEmpty() }
        else -> item.logo
    }
    val circle = type != "recentHistory" || item.string("target_type") == "user"
    val title = when (type) {
        "user" -> item.username
        "contacts" -> userInfo?.string("username").orEmpty().ifEmpty { fUserInfo?.string("username").orEmpty() }
        else -> "${item.string("target_type_title")}: ${item.title}"
    }
    val follow = when (type) {
        "user" -> item.string("follow")
        "contacts" -> userInfo?.string("follow").orEmpty().ifEmpty { fUserInfo?.string("follow").orEmpty() }
        else -> item.string("follow_num")
    }
    val trailing = when (type) {
        "user" -> "${item.string("fans")}粉丝"
        "contacts" -> {
            val fans = userInfo?.string("fans").orEmpty().ifEmpty { fUserInfo?.string("fans").orEmpty() }
            "${fans}粉丝"
        }
        else -> if (item.string("target_type") == "user") "${item.string("fans_num")}粉丝" else "${item.string("comment_num")}讨论"
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        insideMargin = PaddingValues(10.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NetworkImage(
                avatar,
                Modifier.size(40.dp).clip(if (circle) CircleShape else RoundedCornerShape(8.dp)),
                contentDescription = title,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetaText("${follow}关注")
                    MetaText(trailing)
                    if (type == "user") MetaText("${fromToday(item.long("logintime"))}活跃")
                }
            }
        }
    }
}

@Composable
private fun MetaText(value: String) {
    Text(
        value,
        style = COUITheme.textStyles.footnote1,
        color = COUITheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
