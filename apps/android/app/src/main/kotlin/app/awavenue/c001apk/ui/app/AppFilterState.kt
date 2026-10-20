package app.awavenue.c001apk.ui.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** How the 应用 tab orders its rows. */
enum class AppSort(val label: String) {
    Name("名称"),
    Recent("最近更新"),
    Size("占用空间"),
}

/**
 * The filter the 应用 tab's ball edits and the list reads. It lives in the main screen because the
 * ball sits in the bottom bar while the list is one pager page deeper.
 */
@Stable
class AppFilterState {
    var query by mutableStateOf("")

    /** The criterion. 最近更新 starts descending, which is the list's newest-first default. */
    var sort by mutableStateOf(AppSort.Recent)

    /** Whether [sort] runs ascending; 倒序 otherwise. */
    var ascending by mutableStateOf(false)

    var menuOpen by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)

    val active: Boolean get() = query.isNotBlank() || sort != AppSort.Recent || ascending
}

val LocalAppFilter = staticCompositionLocalOf { AppFilterState() }
