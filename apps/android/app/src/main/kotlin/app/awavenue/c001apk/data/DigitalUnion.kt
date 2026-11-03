package app.awavenue.c001apk.data

import android.content.Context

/**
 * The 数盟 ID the client sends as `SZLM ID`. Fetching it registers this install with 数盟, which
 * is what the official client does before it talks to Coolapk; a real ID is accepted without the
 * captcha that a random one keeps running into.
 *
 * The manual import in 使用教程 stays available: it reads the ID the official client already
 * earned, which is the better choice on a device that has that client signed in.
 */
object DigitalUnion {
    /**
     * Fetches a new ID, falling back to [previousId] when neither registration host answers. The
     * caller keeps whatever it already had on null, so a failed refresh never clears a good token.
     */
    suspend fun fetch(context: Context, previousId: String = ""): String? =
        DuClient.query(context, previousId.ifBlank { null })
}
