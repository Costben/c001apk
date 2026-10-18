package app.awavenue.c001apk.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.awavenue.c001apk.ui.LocalApp
import app.awavenue.c001apk.ui.common.TopBarIconButton
import app.awavenue.c001apk.ui.common.couiSearchMaterial
import app.awavenue.c001apk.ui.nav.LocalNavigator
import app.awavenue.c001apk.ui.nav.Route
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.FlowLayout
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Scaffold
import io.github.suqi8.coui.kmp.basic.SearchBarDefaults
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.core.ExperimentalCOUIApi
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.ChevronBackward
import io.github.suqi8.coui.kmp.icon.extended.Clear
import io.github.suqi8.coui.kmp.icon.extended.Search
import io.github.suqi8.coui.kmp.overlay.OverlayAlertDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.LocalContentColor

/**
 * The search page's input, wearing the home search capsule: the shared material fill, rim and
 * inner shadow, the 20dp glyph and the placeholder. The COUI [InputField] would hide its label
 * once expanded, which left an empty capsule on this always-expanded page.
 */
@OptIn(ExperimentalCOUIApi::class)
@Composable
private fun SearchCapsuleField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = SearchBarDefaults.searchBarColors()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = modifier
            .height(SearchBarDefaults.InputFieldMinHeight)
            // The header's divider sits right under the field; the drop shadow would smear across
            // it, so this capsule keeps the rim and inner shadow only.
            .couiSearchMaterial(CircleShape, dropShadow = false)
            .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            COUIIcons.Search,
            contentDescription = "搜索",
            tint = colors.iconColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            singleLine = true,
            textStyle = COUITheme.textStyles.main.copy(
                fontSize = SearchBarDefaults.InputFieldFontSize,
                color = LocalContentColor.current,
            ),
            cursorBrush = SolidColor(COUITheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            label,
                            color = colors.labelColor,
                            style = COUITheme.textStyles.main.copy(
                                fontSize = SearchBarDefaults.InputFieldFontSize,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            val clearColor = colors.clearIconColor
            Box(
                modifier = Modifier
                    .padding(start = 4.dp, end = 4.dp)
                    .size(SearchBarDefaults.TrailingIconTouchTargetSize)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .semantics { contentDescription = "清除" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(SearchBarDefaults.TrailingIconVisualSize)) {
                    drawCircle(clearColor)
                    val half = 4.1.dp.toPx()
                    val stroke = 1.6.dp.toPx()
                    drawLine(
                        Color.White,
                        Offset(center.x - half, center.y - half),
                        Offset(center.x + half, center.y + half),
                        stroke,
                    )
                    drawLine(
                        Color.White,
                        Offset(center.x - half, center.y + half),
                        Offset(center.x + half, center.y - half),
                        stroke,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalCOUIApi::class)
@Composable
fun SearchScreen(title: String, pageType: String?, pageParam: String?) {
    val app = LocalApp.current
    val navigator = LocalNavigator.current
    var keyword by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    val history = app.searchHistory.keywords

    fun submit(text: String) {
        if (text.isEmpty()) return
        app.searchHistory.add(text)
        navigator.push(
            Route.SearchResult(
                keyword = text,
                title = title,
                pageType = pageType,
                pageParam = pageParam,
            ),
        )
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopBarIconButton(onClick = { navigator.pop() }) {
                        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                    }
                    SearchCapsuleField(
                        query = keyword,
                        onQueryChange = { keyword = it },
                        onSearch = { submit(keyword.trim()) },
                        label = if (title.isNotEmpty()) "在 $title 中搜索" else "搜索",
                        modifier = Modifier.weight(1f).padding(start = 10.dp),
                    )
                }
                HorizontalDivider()
            }
        },
    ) { innerPadding ->
        if (history.isEmpty()) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "搜索历史",
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { showClearDialog = true }) {
                    Icon(COUIIcons.Clear, contentDescription = "清除全部")
                }
            }
            FlowLayout(
                modifier = Modifier.fillMaxWidth().padding(
                    start = 10.dp,
                    end = 10.dp,
                    bottom = 10.dp + innerPadding.calculateBottomPadding(),
                ),
                itemSpacing = 8.dp,
                lineSpacing = 8.dp,
            ) {
                history.forEach { text ->
                    Card(
                        cornerRadius = 20.dp,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        onClick = {
                            keyword = text
                            submit(text)
                        },
                        onLongPress = { app.searchHistory.remove(text) },
                    ) {
                        Text(text, maxLines = 1, style = COUITheme.textStyles.footnote1)
                    }
                }
            }
        }
    }

    OverlayAlertDialog(
        show = showClearDialog,
        title = "确定清除全部搜索历史？",
        confirmText = "确定",
        cancelText = "取消",
        onCancel = { showClearDialog = false },
        onConfirm = {
            showClearDialog = false
            app.searchHistory.clear()
        },
    )
}
