package app.awavenue.c001apk.ui.text

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.suqi8.coui.kmp.theme.COUITheme

/**
 * The composer's text buffer. The field keeps the raw `[emoji]` codes the server expects; the
 * displayed text is an [Editable] whose codes are covered by [ImageSpan]s.
 */
@Stable
class EmojiInputState internal constructor(initialText: String) {
    internal var editText: EmojiSpanEditText? = null

    var text by mutableStateOf(initialText)
        internal set

    fun insert(code: String) {
        val edit = editText
        if (edit == null) {
            text += code
            return
        }
        val editable = edit.text ?: return
        val start = edit.selectionStart.coerceIn(0, editable.length)
        val end = edit.selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, code)
        edit.setSelection((start + code.length).coerceAtMost(editable.length))
    }

    fun focus() {
        val edit = editText ?: return
        edit.requestFocus()
        edit.post {
            val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun clearFocus() {
        val edit = editText ?: return
        edit.clearFocus()
        val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(edit.windowToken, 0)
    }
}

@Composable
fun rememberEmojiInputState(initialText: String = ""): EmojiInputState =
    remember { EmojiInputState(initialText) }

/**
 * A multi-line editor that draws Coolapk emoji codes as the bundled images, the way the native
 * client does. Compose text fields cannot host inline content, so the field is a real [EditText]
 * and the codes stay untouched underneath the spans.
 */
@Composable
fun EmojiEditText(
    state: EmojiInputState,
    modifier: Modifier = Modifier,
    hint: String = "",
    maxCount: Int = 500,
    minLines: Int = 4,
    maxLines: Int = 8,
    textStyle: TextStyle = COUITheme.textStyles.body1,
    onFocused: (() -> Unit)? = null,
) {
    val textColor = COUITheme.colorScheme.onSurface.toArgb()
    val hintColor = COUITheme.colorScheme.onSurfaceVariantSummary.toArgb()
    val fontSizeSp = textStyle.fontSize.let { if (it.type == TextUnitType.Sp) it.value else 16f }
    val emojiSizePx = with(LocalDensity.current) { (fontSizeSp * 1.15f).sp.toPx() }.toInt()
    val currentOnFocused by rememberUpdatedState(onFocused)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            EmojiSpanEditText(context).apply {
                val verticalPadding = (15 * resources.displayMetrics.density).toInt()
                setBackgroundColor(AndroidColor.TRANSPARENT)
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_FLAG_NO_ENTER_ACTION
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                setLineSpacing(0f, 1.158f)
                setTextColor(textColor)
                setHintTextColor(hintColor)
                this.hint = hint
                this.minLines = minLines
                this.maxLines = maxLines
                filters = arrayOf(InputFilter.LengthFilter(maxCount))
                setPadding(0, verticalPadding, 0, verticalPadding)
                isFocusableInTouchMode = true
                includeFontPadding = false
                setText(state.text)
                setSelection(state.text.length)
                renderEmoji(text, emojiSizePx)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (s == null) return
                        state.text = s.toString()
                        renderEmoji(s, emojiSizePx)
                    }
                })
                setOnFocusChangeListener { _, focused ->
                    if (focused) currentOnFocused?.invoke()
                }
                state.editText = this
            }
        },
        update = { view ->
            if (view.text.toString() != state.text) {
                val caret = view.selectionStart.coerceAtLeast(0)
                view.setText(state.text)
                view.setSelection(caret.coerceIn(0, view.text.length))
            }
            // The theme can change while the view is alive; the factory only runs once.
            view.setTextColor(textColor)
            view.setHintTextColor(hintColor)
            state.editText = view
        },
    )

    DisposableEffect(state) {
        onDispose { state.editText = null }
    }
}

/** Draws each known `[emoji]` code as its bundled image; unknown codes stay as plain text. */
internal fun EditText.renderEmoji(editable: Editable?, sizePx: Int) {
    if (editable == null) return
    runCatching {
        editable.getSpans(0, editable.length, ImageSpan::class.java).forEach(editable::removeSpan)
        if (editable.isEmpty()) return
        for (match in emojiCodePattern.findAll(editable)) {
            val file = EmojiFiles[match.value] ?: continue
            val bitmap = emojiBitmap(context, file) ?: continue
            val drawable = BitmapDrawable(resources, bitmap).apply {
                setBounds(0, 0, sizePx, sizePx)
            }
            editable.setSpan(
                ImageSpan(drawable, DynamicDrawableSpan.ALIGN_CENTER),
                match.range.first,
                match.range.last + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}

private val emojiCodePattern = Regex("\\[[^\\]]+\\]")

private object EmojiBitmaps {
    private val cache = HashMap<String, Bitmap?>()

    fun get(context: Context, file: String): Bitmap? {
        if (cache.containsKey(file)) return cache[file]
        val bitmap = runCatching {
            context.assets.open("emojis/$file").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        cache[file] = bitmap
        return bitmap
    }
}

private fun emojiBitmap(context: Context, file: String): Bitmap? = EmojiBitmaps.get(context, file)

/** The emoji code whose characters cover [offset], if any. */
private fun emojiRangeAt(text: CharSequence, offset: Int): IntRange? {
    for (match in emojiCodePattern.findAll(text)) {
        if (EmojiFiles[match.value] == null) continue
        if (offset > match.range.first && offset <= match.range.last + 1) return match.range
    }
    return null
}

/**
 * Backspace has to remove a whole emoji code: the caret only ever sits on character boundaries,
 * so deleting one character would leave a broken code behind.
 */
internal class EmojiSpanEditText(context: Context) : EditText(context) {

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL && selectionStart == selectionEnd && selectionStart > 0) {
            val range = emojiRangeAt(text ?: return super.onKeyDown(keyCode, event), selectionStart)
            if (range != null) {
                text?.delete(range.first, range.last + 1)
                setSelection(range.first.coerceAtMost(text?.length ?: 0))
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (deleteWholeEmoji(beforeLength)) return true
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                if (deleteWholeEmoji(beforeLength)) return true
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }
        }
    }

    private fun deleteWholeEmoji(beforeLength: Int): Boolean {
        if (beforeLength <= 0 || selectionStart != selectionEnd || selectionStart <= 0) return false
        val editable = text ?: return false
        val caret = selectionStart
        val range = emojiRangeAt(editable, caret) ?: return false
        // Only take over when the deletion actually reaches into the code.
        if (caret - beforeLength > range.first) {
            editable.delete(range.first, caret)
            setSelection(range.first.coerceAtMost(editable.length))
            return true
        }
        return false
    }
}
