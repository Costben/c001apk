package app.awavenue.c001apk.ui.common

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Badge
import io.github.suqi8.coui.kmp.basic.BadgeBox
import io.github.suqi8.coui.kmp.basic.BadgeDefaults
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Email

/**
 * The 收到的消息 entry every main page carries. [BadgeBox] reserves its overhang on the end and
 * bottom sides, which leaves the icon itself off-centre; the half-overhang shift puts it back.
 */
@Composable
fun NotificationBell(modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    TopBarIconButton(
        onClick = rememberHapticClick {
            if (app.session.isLogin) {
                navigator.push(Route.NotificationCenter)
            } else {
                navigator.push(Route.Login)
            }
        },
        modifier = modifier,
    ) {
        BadgeBox(
            badge = {
                if (app.unread.total > 0) Badge(count = app.unread.total)
            },
            overhang = BadgeDefaults.CountOverhang,
        ) {
            Icon(
                COUIIcons.Email,
                contentDescription = "收到的消息",
                modifier = Modifier.offset(
                    x = BadgeDefaults.CountOverhang / 2,
                    y = -BadgeDefaults.CountOverhang / 2,
                ),
            )
        }
    }
}
