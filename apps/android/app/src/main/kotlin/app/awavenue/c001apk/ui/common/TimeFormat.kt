package app.awavenue.c001apk.ui.common

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ONE_MINUTE = 60L
private const val ONE_HOUR = 3600L
private const val ONE_DAY = 86400L
private const val ONE_MONTH = 2592000L

/** Relative time for a Unix timestamp in seconds, worded like the Flutter client. */
fun fromToday(seconds: Long?, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    if (seconds == null) return ""
    val ago = nowSeconds - seconds
    return when {
        ago == 0L -> "刚刚"
        ago < 60 -> "${ago}秒前"
        ago <= ONE_HOUR -> "${ago / ONE_MINUTE}分钟前"
        ago <= ONE_DAY -> "${ago / ONE_HOUR}小时前"
        ago <= ONE_DAY * 2 -> "1天前"
        ago <= ONE_DAY * 3 -> "2天前"
        ago <= ONE_MONTH -> "${ago / ONE_DAY}天前"
        else -> {
            val date = Date(seconds * 1000)
            val year = Calendar.getInstance().apply { time = date }.get(Calendar.YEAR)
            val thisYear = Calendar.getInstance().apply { timeInMillis = nowSeconds * 1000 }.get(Calendar.YEAR)
            val pattern = if (year == thisYear) "MM月dd日" else "yyyy年MM月dd日"
            SimpleDateFormat(pattern, Locale.CHINA).format(date)
        }
    }
}
