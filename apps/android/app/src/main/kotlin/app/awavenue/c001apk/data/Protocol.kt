package app.awavenue.c001apk.data

/** Client identity the Coolapk API expects; values follow the Flutter app's Constants. */
object Protocol {
    const val REQUEST_WITH = "XMLHttpRequest"
    const val LOCALE = "zh-CN"
    const val APP_ID = "com.coolapk.market"
    const val CHANNEL = "coolapk"
    const val MODE = "universal"
    const val VERSION_NAME = "16.2.0"
    const val API_VERSION = "16"
    const val VERSION_CODE = "2604201"

    const val URL_API_SERVICE = "https://api.coolapk.com"
    const val URL_API2_SERVICE = "https://api2.coolapk.com"
    const val URL_ACCOUNT_SERVICE = "https://account.coolapk.com"
    const val URL_COOLAPK = "https://www.coolapk.com"

    /** Share links resolve through the mirror the Flutter client uses. */
    fun shareLink(type: String, id: String) = "https://www.coolapk1s.com/$type/$id"

    fun reportLink(id: String, isUser: Boolean = false, isReply: Boolean = false): String {
        val target = if (isUser) "user" else "feed"
        val type = when {
            isUser -> ""
            isReply -> "&type=feed_reply"
            else -> "&type=feed"
        }
        return "https://m.coolapk.com/mp/do?c=$target&m=report$type&id=$id"
    }
    const val URL_SOURCE_CODE = "https://github.com/TG-Twilight/c001apk"
    const val URL_UPSTREAM = "https://github.com/bggRGjQaUbCoE/c001apk-flutter"
    const val URL_COUI = "https://github.com/Costben/coui17"
}
