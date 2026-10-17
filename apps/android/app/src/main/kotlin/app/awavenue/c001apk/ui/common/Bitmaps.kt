package app.awavenue.c001apk.ui.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Rasterises a drawable. Adaptive launcher icons are XML and cannot go through `painterResource`,
 * so they are drawn onto a bitmap instead.
 */
internal fun Drawable.toImageBitmap(size: Int = 96): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}
