# c001apk Compose 端页面过渡、底栏与内容持久化

本文面向“在另一个 Android Compose 项目里复刻 c001apk”的场景，只描述 `apps/android`
（Compose 主线，2.0.2）的真实实现。

先说结论，避免按错误前提移植：

- Compose 端没有使用 Navigation Compose，返回栈是 `SnapshotStateList<BackStackEntry>`。
- 共享元素由 `ExpandOrigin` + `ExpandFrame` 手工实现，没有 Hero 式的转场框架。
- 没有数据库，也没有信息流磁盘缓存。信息流只存在 ViewModel 的内存里。
- 动态详情是“从被点卡片展开整页”，设置页是“整页从右滑入”，两者不是同一条过渡。

关键文件：

| 职责 | 文件 |
| --- | --- |
| 返回栈与路由条目 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/Navigator.kt` |
| 路由表 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/Route.kt` |
| 路由宿主与预测返回 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/AppNavHost.kt` |
| 卡片展开动画 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/ExpandTransition.kt` |
| 页面自绘过渡进度 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/RouteTransition.kt` |
| 返回时卡片避让顶栏 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/nav/AppBarBounds.kt` |
| 信息流卡片入口 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/feed/FeedCard.kt` |
| 信息流分页 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/feed/FeedListViewModel.kt` |
| 悬浮底栏调用 | `apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/main/MainScreen.kt` |
| 悬浮底栏组件 | `third_party/coui17/coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/basic/BottomFloatingAccessoryNavigationBar.kt` |
| 悬浮底栏默认值 | `third_party/coui17/coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/basic/BottomFloatingNavigationBar.kt` |

依赖版本（`apps/android/gradle/libs.versions.toml`）：

```toml
agp = "9.3.1"
kotlin = "2.4.10"
coui = "1.2.0"
androidx-activity = "1.13.0"
androidx-lifecycle = "2.11.0"
coil = "3.6.3"
```

COUI 在本仓库里是通过 composite build 从源码消费的：

```kotlin
// apps/android/settings.gradle.kts:39
includeBuild("../../third_party/coui17")
```

如果目标项目不能接 composite build，再改用发布坐标：

```kotlin
implementation("io.github.suqi8.coui.kmp:coui-ui:1.2.0")
implementation("io.github.suqi8.coui.kmp:coui-icons:1.2.0")
```

`ExpandFrame` 依赖 Compose UI 1.7+ 的 `rememberGraphicsLayer()` /
`GraphicsLayer.toImageBitmap()`。低于这个版本需要改成 `ImageBitmap` 快照方案。

---

# 一、动态详情：从卡片展开整页

## 1.1 调用链

信息流卡片：

```kotlin
// ui/feed/FeedCard.kt:129
val origin = rememberExpandOrigin(CardDefaults.CornerRadius, CardDefaults.defaultColors().color)

// ui/feed/FeedCard.kt:148
val openFeed = rememberHapticClick {
    navigator.push(Route.Feed(feed.id, preview = feed), origin)
}

// ui/feed/FeedCard.kt:172
Card(
    modifier = modifier.fillMaxWidth().expandOrigin(origin),
    insideMargin = PaddingValues(CardPadding),
    onClick = openFeed,
    ...
)
```

路由定义与入栈：

```kotlin
// ui/nav/Route.kt:48
data class Feed(val id: String, val preview: Entity? = null) : Route

// ui/nav/Navigator.kt:93-95
fun push(route: Route, origin: ExpandOrigin? = null) {
    stack.add(BackStackEntry(route, origin))
}
```

宿主渲染：

```kotlin
// ui/nav/AppNavHost.kt:583
is Route.Feed -> FeedDetailScreen(id = route.id, preview = route.preview)
```

网络接口：

```kotlin
// network/Api.kt:16
const val FEED_DETAIL = "$API/v6/feed/detail"

// data/CoolapkRepository.kt:281
suspend fun feedDetail(id: String, verify: Boolean = false): FeedDetail
```

两个入口差异：

- 酷安链接打开：`ui/common/CoolapkLink.kt:41` 调用 `navigator.push(Route.Feed(...))`，
  没有 `origin`，所以走整页滑入。
- 浏览历史预览行：`ui/message/MessageScreen.kt:542` 传 `rememberExpandOrigin(0.dp, ...)`，
  圆角为 0，因为那一行本身是透明行。

## 1.2 过渡选择状态机

`AppNavHost.kt:246-270` 决定每个路由用哪种过渡：

```kotlin
val pushed = targetState?.takeIf { popped == null }
val transform = when {
    // 手势已经把页面带走，不能再播一次退出。
    popped?.previewed == true ->
        EnterTransition.None togetherWith ExitTransition.None

    // 页面自己画“从卡片长出 / 缩回卡片”，下面的页面不动。
    pushed?.growsFromOrigin() == true || popped?.shrinksToOrigin() == true ->
        EnterTransition.None togetherWith ExitTransition.None

    // push：整页从右滑入，下面页面退 1/4 宽。
    popped == null ->
        slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it } togetherWith
            slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
                -it / RouteParallaxDivisor
            }

    // pop：当前页向右滑出，下面页面从 -1/4 宽回到 0。
    else ->
        slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
            -it / RouteParallaxDivisor
        } togetherWith
            slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it }
}
transform.using(null).apply { if (popped != null) targetContentZIndex = -1f }
```

`growsFromOrigin()` / `shrinksToOrigin()`：

```kotlin
// ExpandTransition.kt:351-355
internal fun BackStackEntry.growsFromOrigin(): Boolean =
    (origin?.isAvailable == true || route is Route.ImageView) && !shown

internal fun BackStackEntry.shrinksToOrigin(): Boolean =
    origin?.hasBounds == true || route is Route.ImageView
```

`shown` 在页面第一次进入后置为 `true`，所以同一个条目再次显示时不会重新展开。

## 1.3 几何与透明度公式

进入时 `progress` 从 0 到 1，返回时从 1 到 0。

### 进入

- 时长：`420ms`（`ExpandDurationMillis`，`ExpandTransition.kt:133`）。
- 几何曲线：`CubicBezierEasing(0.3f, 0f, 0.1f, 1f)`（`ExpandEasing`，`ExpandTransition.kt:132`）。
- 透明度曲线：`LinearEasing`，同样 420ms。
- `translationX = lerp(cardBounds.left, 0, p)`。
- `translationY = lerp(cardBounds.top, 0, p)`。
- `scaleX = scaleY = lerp(cardBounds.width, screenWidth, p) / screenWidth`。
- `transformOrigin = TransformOrigin(0f, 0f)`，左上角锚点等比放大。
- 圆角：`lerp(cardCornerRadius, screenCornerRadius, p) / scale`。
  - 信息流卡片圆角是 `CardDefaults.CornerRadius = 17.dp`
    （`third_party/coui17/coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/basic/Card.kt:301`）。
  - `screenCornerRadius` 读屏幕左上角圆角，Android 12 以下为 0
    （`ExpandTransition.kt:117-129`）。
- 背景遮罩：`Color.Black`，alpha = `0.3f * progress`（`ScrimAlpha = 0.3f`，
  `ExpandTransition.kt:135,209`）。
- 页面内容透明度：`((fade - 0.25) / 0.3).coerceIn(0f, 1f)`。
  - `fade` 从 0 线性到 1。
  - 0 → 105ms 页面全透明；105ms → 231ms 淡入到 1。
- 被点击卡片的绘制层 alpha：`1 - (fade / 0.25).coerceIn(0f, 1f)`。
  - 0ms 时 1；105ms 时 0。
- 页面内容额外上移：`translationY = -statusBarTop * (1 - p)`，让全屏页在卡片大小的框里
  先贴住状态栏位置（`ExpandTransition.kt:316`）。
- 框的填充色：`COUITheme.colorScheme.surface`，不透明（`ExpandTransition.kt:168,259`）。

核心几何：

```kotlin
// ExpandTransition.kt:223-237
val p = progress()
val left = lerp(bounds.left, 0f, p)
val top = lerp(bounds.top, 0f, p)
val width = lerp(bounds.width, size.width, p)
val height = lerp(bounds.height, size.height, p)
val scale = width / size.width
transformOrigin = TransformOrigin(0f, 0f)
translationX = left
translationY = top
scaleX = scale
scaleY = scale
val radius = lerp(source.cornerRadius.toPx(), screenCornerRadius.toPx(), p) / scale
shape = FrameShape(height / scale, (radius / density).dp, squircle)
clip = true
```

遮罩：

```kotlin
// ExpandTransition.kt:209
if (active() != null) drawRect(Color.Black, alpha = ScrimAlpha * progress())
```

页面 alpha：

```kotlin
// ExpandTransition.kt:306-317
alpha = when {
    exitCard != null -> 1f - exitCard
    exiting() -> (progress() / ExitHandoverProgress).coerceIn(0f, 1f)
    else -> ((fade() - CardFadeEnd) / PageFadeLength).coerceIn(0f, 1f)
}
translationY = -statusBars.getTop(this) * (1f - progress())
```

### 返回

- 时长：`220ms`（`ExitDurationMillis`，`ExpandTransition.kt:447`）。
- 几何曲线：同样是 `CubicBezier(0.3, 0, 0.1, 1)`。
- 透明度曲线：`LinearEasing`，220ms。
- `progress` 从 1 到 0，几何参数完全反向。
- 卡片层 alpha：`((0.4f - p) / (0.4f - 0.08f)).coerceIn(0f, 1f)`。
  - p=1 → p=0.4（返回开始后的 0 → 132ms）卡片不出现。
  - p=0.4 → p=0.08（132ms → 202.4ms）卡片淡入。
  - p<0.08 卡片完全盖住页面。
- 页面内容 alpha：`1 - 卡片层 alpha`，所以画面始终有页面或卡片，不会露出空白框。
- 没有卡片快照时的兜底：页面保持到 `p <= 0.02`，最后 4.4ms 用 `p / 0.02` 淡出
  （`ExitHandoverProgress = 0.02f`）。
- 返回时被弹出的页面是静止画面，下面的列表可以同时滚动，缩小框会跟着列表走。

### 数值表

| 参数 | 进入 | 返回 |
| --- | --- | --- |
| 触发 | 点击卡片，`origin != null`，`shown == false` | `pop()`，`shrinksToOrigin()` |
| 总时长 | 420ms | 220ms |
| 几何 Curve | `CubicBezier(0.3, 0, 0.1, 1)` | 同左 |
| 透明度 Curve | `LinearEasing` | `LinearEasing` |
| progress | 0 → 1 | 1 → 0 |
| translationX | `lerp(card.left, 0, p)` | 反向 |
| translationY | `lerp(card.top, 0, p)` | 反向 |
| scaleX / scaleY | `lerp(card.width, screenW, p) / screenW` | 反向 |
| transformOrigin | `(0, 0)` | 同左 |
| 圆角 | `lerp(17dp, screenCorner, p) / scale` | 反向 |
| 页面 alpha | `(fade - 0.25) / 0.3`，105ms 开始、231ms 到 1 | `1 - cardAlpha` |
| 卡片层 alpha | `1 - fade / 0.25`，0ms 到 1、105ms 到 0 | `(0.4 - p) / 0.32`，132ms 开始、202.4ms 到 1 |
| 遮罩 | 黑色，alpha `0.3 * p` | 同左 |
| 页面额外 translationY | `-statusBarTop * (1 - p)` | 同左 |
| 框填充 | `colorScheme.surface`，不透明 | 同左 |
| 无 origin 兜底 | 320ms 整页滑入 | 320ms 整页滑出 |

## 1.4 可复制骨架

下面是压缩后的骨架，省略了空值兜底、squircle 分支和卡片快照的完整绘制；公式与仓库一致。
完整实现直接对照 `ExpandTransition.kt`。

### 常量

```kotlin
private val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
private const val ExpandDurationMillis = 420
private const val ExitDurationMillis = 220
private const val RouteSlideMillis = 320
private const val RouteParallaxDivisor = 4
private const val GestureSettleMillis = 200
private const val GestureCommitMillis = 150
private const val SnapshotSettleMillis = 220L

private const val ScrimAlpha = 0.3f
private const val CardFadeEnd = 0.25f
private const val PageFadeLength = 0.3f
private const val ExitCardStart = 0.4f
private const val ExitCardEnd = 0.08f
private const val ExitHandoverProgress = 0.02f
```

### ExpandOrigin

```kotlin
@Stable
class ExpandOrigin(internal val layer: GraphicsLayer) {
    internal var cornerRadius: Dp = 0.dp
    internal var color: Color = Color.Transparent
    internal var coordinates: LayoutCoordinates? = null
    internal var lastBounds: Rect? = null
    internal var picture: ImageBitmap? = null

    val isAvailable: Boolean
        get() = coordinates?.isAttached == true && !layer.isReleased

    val hasBounds: Boolean
        get() = isAvailable || lastBounds != null
}

@Composable
fun rememberExpandOrigin(cornerRadius: Dp, color: Color): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val origin = remember(layer) { ExpandOrigin(layer) }
    SideEffect {
        origin.cornerRadius = cornerRadius
        origin.color = color
    }
    return origin
}

fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier =
    onPlaced { origin.coordinates = it }
        .drawWithContent {
            origin.layer.record { this@drawWithContent.drawContent() }
            drawLayer(origin.layer)
        }
```

### ExpandFrame

```kotlin
@Composable
fun ExpandFrame(
    origin: ExpandOrigin?,
    progress: () -> Float,
    fade: () -> Float,
    exiting: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val pageColor = MaterialTheme.colorScheme.surface
    val screenCornerRadius = rememberScreenCornerRadius()
    val statusBarTop = WindowInsets.statusBars.getTop(LocalDensity.current)
    var host by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun bounds(): Rect? {
        val h = host?.takeIf { it.isAttached } ?: return origin?.lastBounds
        val c = origin?.coordinates?.takeIf { it.isAttached } ?: return origin?.lastBounds
        return h.localBoundingBoxOf(c, clipBounds = false)
            .takeIf { it.width > 0f && it.height > 0f }
            ?.also { origin?.lastBounds = it }
    }

    fun active(): ExpandOrigin? = origin?.takeIf { progress() < 1f && it.hasBounds }

    Box(
        Modifier
            .fillMaxSize()
            .onPlaced { host = it }
            .drawBehind {
                if (active() != null) drawRect(Color.Black, alpha = ScrimAlpha * progress())
            }
            .graphicsLayer {
                val source = active()
                val b = source?.let { bounds() }
                if (source == null || b == null) {
                    translationX = 0f
                    translationY = 0f
                    scaleX = 1f
                    scaleY = 1f
                    shape = RectangleShape
                    clip = false
                    return@graphicsLayer
                }
                val p = progress()
                val left = lerp(b.left, 0f, p)
                val top = lerp(b.top, 0f, p)
                val width = lerp(b.width, size.width, p)
                val height = lerp(b.height, size.height, p)
                val scale = width / size.width
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = left
                translationY = top
                scaleX = scale
                scaleY = scale
                val radius = lerp(source.cornerRadius.toPx(), screenCornerRadius.toPx(), p) / scale
                shape = RoundedCornerShape((radius / density).dp)
                clip = true
            }
            .drawWithContent {
                val source = active()
                val b = source?.let { bounds() }
                if (source == null || b == null) {
                    drawContent()
                    return@drawWithContent
                }
                val f = fade()
                val cardAlpha = if (exiting()) {
                    ((ExitCardStart - progress()) / (ExitCardStart - ExitCardEnd)).coerceIn(0f, 1f)
                } else {
                    1f - (f / CardFadeEnd).coerceIn(0f, 1f)
                }
                drawRect(pageColor)
                drawContent()
                if (cardAlpha > 0f && (source.isAvailable || source.picture != null)) {
                    val k = size.width / b.width
                    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(
                                Rect(Offset.Zero, b.size),
                                Paint().apply { alpha = cardAlpha },
                            )
                            drawRect(source.color.compositeOver(pageColor), size = b.size)
                            if (source.isAvailable) {
                                drawLayer(source.layer)
                            } else {
                                source.picture?.let {
                                    drawImage(it, dstSize = IntSize(b.width.roundToInt(), b.height.roundToInt()))
                                }
                            }
                            canvas.restore()
                        }
                    }
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = when {
                        exiting() -> (progress() / ExitHandoverProgress).coerceIn(0f, 1f)
                        else -> ((fade() - CardFadeEnd) / PageFadeLength).coerceIn(0f, 1f)
                    }
                    translationY = -statusBarTop * (1f - progress())
                },
        ) { content() }
    }
}
```

这段骨架省略了“返回时卡片层不可用”的兜底。完整实现里 `exitCard` 为 `null` 时，页面 alpha 和
框的填充 alpha 都改用 `(progress() / ExitHandoverProgress).coerceIn(0f, 1f)`，让页面保持到
最后 2% 再交还给真实卡片，避免出现空白矩形（`ExpandTransition.kt:178-183, 250-270`）。

注意三个容易抄错的点：

1. `transformOrigin` 必须是 `(0, 0)`，否则缩放锚点会跑到中心，卡片边界对不上。
2. 页面 alpha 不是从 0 开始，而是从 `fade = 0.25` 开始；卡片在同一时刻正好消失。
3. 返回时页面和卡片是交叉淡化，不是先淡出页面再淡入卡片；否则手指停在中间会看到空白框。

## 1.5 共享元素与快照

没有 Hero，没有 tag，没有 `flightShuttleBuilder`。对应关系：

| 共享元素概念 | 本仓库实现 |
| --- | --- |
| Hero tag | 不需要字符串 tag；`ExpandOrigin` 对象随 `Route.Feed` 一起入栈，按对象身份关联 |
| flightShuttleBuilder | `ExpandFrame` 的 `graphicsLayer` + `drawWithContent` |
| 起始元素快照 | `ExpandOrigin.layer`，由 `Modifier.expandOrigin` 持续录制 |
| 上一页快照 | `LeavingPage` 的 `picture.record {}`；预测返回用 `BackStackEntry.snapshot` |

### 起始卡片

`ExpandOrigin` 持有 `GraphicsLayer`，`expandOrigin` 每次绘制都会把卡片内容录进去，
同时把这一层画出来：

```kotlin
// ExpandTransition.kt:97-114
@Composable
fun rememberExpandOrigin(cornerRadius: Dp, color: Color): ExpandOrigin {
    val layer = rememberGraphicsLayer()
    val origin = remember(layer) { ExpandOrigin(layer) }
    SideEffect {
        origin.cornerRadius = cornerRadius
        origin.color = color
    }
    return origin
}

fun Modifier.expandOrigin(origin: ExpandOrigin): Modifier =
    onPlaced { origin.coordinates = it }
        .drawWithContent {
            origin.layer.record { this@drawWithContent.drawContent() }
            drawLayer(origin.layer)
        }
```

### 快照时机

第一帧拍一张，`SnapshotSettleMillis = 220ms` 后再拍一张。第二张是为了避开点击按压态的
淡出：点击和 push 在同一帧发生，第一张照片里还带着按压高亮，直接拿它做返回快照会把高亮
带回来。

```kotlin
// ExpandTransition.kt:186-201
LaunchedEffect(origin) {
    withFrameNanos { }
    if (origin.isAvailable && origin.layer.size != IntSize.Zero) {
        origin.picture = origin.layer.toImageBitmap()
    }
    delay(SnapshotSettleMillis)
    if (origin.isAvailable && origin.layer.size != IntSize.Zero) {
        origin.picture = origin.layer.toImageBitmap()
    }
}
```

### 上一页在进入 / 退出时的状态

- 进入时：下面的页面仍然组合、仍然活着，所以卡片是实时绘制的，动画期间列表可以更新。
- 退出时：`LeavingPage` 把页面切换成 `GraphicsLayer` 静止画面，并把实时内容裁到不可见、
  不可点击。这样下面的列表可以滚动，缩小框会跟着列表走。

```kotlin
// AppNavHost.kt:510-562（节选）
val leaving = !navigator.stack.contains(entry)
val picture = rememberGraphicsLayer()
...
if (leaving) {
    Modifier.drawWithContent { drawLayer(picture) }
}
...
Modifier.graphicsLayer {
    clip = true
    shape = OutOfReach
}
```

`OutOfReach` 是一个空矩形 Outline，既裁掉内容，也把节点移出命中测试
（`AppNavHost.kt:565-568`）。

### 预测返回时的上一页

预测返回不重新组合下面的页面，而是直接画 `BackStackEntry.snapshot`：

```kotlin
// AppNavHost.kt:462-478
@Composable
private fun RevealedRoute(entry: BackStackEntry) = key(entry) {
    val snapshot = entry.snapshot
    if (snapshot != null) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                if (snapshot.width == 0 || snapshot.height == 0) return@drawBehind
                drawImage(
                    snapshot,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                )
            },
        )
    } else {
        ComposedRevealedRoute(entry)
    }
}
```

### 图片查看器是另一套

图片查看器按图片 URL 建索引，不是 tag 字符串：

```kotlin
// ui/image/ImageOrigins.kt:30-37
/** Keyed by URL rather than by route argument ... */
class ImageOrigins {
    private val slots = HashMap<String, ThumbSlot>()
    internal fun boundsOf(url: String, host: LayoutCoordinates, density: Float): ThumbBounds?
}
```

`ImageViewScreen` 自己画从缩略图到全屏的帧（`ui/image/ImageViewScreen.kt:350-377`）：

```kotlin
val scale = lerp(cover, 1f, progress)
translationX = lerp(source.center.x, width / 2f, progress) - width / 2f
translationY = lerp(source.center.y, height / 2f, progress) - height / 2f
shape = CenteredWindow(
    width = lerp(source.width, width, progress) / scale,
    height = lerp(source.height, height, progress) / scale,
    radius = lerp(thumb.cornerRadius, 0f, progress) / scale,
)
```

没有缩略图时，图片查看器的兜底是 `alpha = progress`、`scale 0.92 → 1`
（`ImageViewScreen.kt:352-356`）。动态详情不使用这套。

## 1.6 返回时的卡片定位

返回不只是缩放。`AppNavHost.kt:408-428` 的 `alignCardBelowBar` 会把来源列表滚动到卡片顶部
刚好露出顶栏：

```kotlin
val delta = target - coordinates.positionInRoot().y
if (delta <= 0.5f) return
state.scrollBy(-delta)
```

`AppBarBounds` 里还有：

- `ReturnLiftTtlMillis = 1500L`：返回请求超过 1.5 秒就作废。
- `alignReturnedRow`：最多 8 帧，等列表布局完成后再对齐。
- `pendingReturn` / `clearReturn`：页面被重建后，由重建的卡片自己领取对齐任务。

对应文件：`ui/nav/AppBarBounds.kt:10-80`。

## 1.7 详情页自身有没有入场动画

没有独立的页面入场动画、没有内容 stagger、没有评论逐条淡入。详情页整体的透明度完全由
`ExpandFrame` 的页面 alpha 驱动。

- 加载中先用列表里的 `preview` 实体画正文，不显示空白页或转圈：
  `FeedDetailScreen.kt:230-247`。
- `preview` 到真实 `feed` 的切换是直接 `when` 替换，没有 `AnimatedContent`，没有 crossfade。
- 图片渐显来自 Coil：`NetworkImage` 默认 `crossfade = true`（`ui/common/NetworkImage.kt:27,37`）。
  Coil 3 的 `crossfade(true)` 默认时长是 100ms。
- 列表状态切换：`FeedList.kt:138`，`fadeIn(tween(200)) togetherWith fadeOut(tween(150))`。
- 列表底部状态：`FeedList.kt:208`，`fadeIn(tween(350)) togetherWith fadeOut(tween(175))`。
- 评论列表：普通 `itemsIndexed`，没有 `Modifier.animateItem()`，没有 stagger，没有逐条 delay。
- 顶栏：`SmallTopAppBar(gradientGlass = true, ...)`，没有额外入场动画。

| 项目 | 数值 | 代码位置 |
| --- | --- | --- |
| 页面整体 alpha | 105ms → 231ms，0 → 1 | `ExpandTransition.kt:309-313` |
| 图片渐显 | Coil 默认 100ms | `NetworkImage.kt:27,37` |
| 列表状态切换 | 200ms 入 / 150ms 出 | `FeedList.kt:138` |
| Footer 状态切换 | 350ms 入 / 175ms 出 | `FeedList.kt:208` |
| 评论 stagger | 无 | `FeedDetailScreen.kt` 的 `itemsIndexed` |
| 详情页独立入场 | 无 | `FeedDetailScreen.kt:99` 起 |

## 1.8 预测返回对详情页的影响

详情页的 `shrinksToOrigin()` 为 true，所以预测返回时它不水平位移，而是直接驱动缩小：

```kotlin
// AppNavHost.kt:326-331
val pageProgress: () -> Float = remember(progress, entryGesture) {
    { if (entry.previewed) 0f else minOf(progress.value, entryGesture()) }
}
val pageFade: () -> Float = remember(fade, entryGesture) {
    { if (entry.previewed) 0f else minOf(fade.value, entryGesture()) }
}
```

- 手指拖动：`entryGesture` 从 1 降到 0，`pageProgress` 跟着降，页面按 1:1 缩回卡片。
- 松手未过阈值：`animate(gestureProgress, 1f, tween(200))`，200ms 回到原位。
- 松手过阈值：`animate(gestureProgress, 0f, tween(150))`，150ms 完成缩小，然后 `pop()`。
- 两个 `tween` 没有指定 easing，使用 Compose 默认的 `FastOutSlowInEasing`。

这段逻辑在 `AppNavHost.kt:149-203`。

---

# 二、设置页：整页从右滑入

## 2.1 调用链

设置页从“我的”页右上角齿轮打开：

```kotlin
// ui/message/MessageScreen.kt:235
TopBarIconButton(
    onClick = rememberHapticClick { navigator.push(Route.Settings) },
    modifier = Modifier.padding(start = 10.dp),
) {
    Icon(COUIIcons.Settings, contentDescription = "设置")
}
```

路由宿主：

```kotlin
// ui/nav/AppNavHost.kt:580
Route.Settings -> SettingsScreen()
```

设置页自身是完整的 `Scaffold` + `TopAppBar`，不是 bottom sheet，也不是 dialog：

```kotlin
// ui/settings/SettingsScreen.kt:113
@Composable
fun SettingsScreen() {
    ...
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                gradientGlass = true,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    TopBarIconButton(onClick = rememberHapticClick { navigator.pop() }) {
                        Icon(COUIIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                ...
            )
        },
    )
}
```

## 2.2 与动态详情的差异

| 项目 | 动态详情 | 设置页 |
| --- | --- | --- |
| push 参数 | `Route.Feed(id, preview)` + `origin` | `Route.Settings`，无 origin |
| 过渡类型 | `ExpandFrame` 从卡片边界展开 | `AnimatedContent` 整页水平滑动 |
| 方向 | 卡片位置 → 全屏 | 屏幕右侧 → 屏幕 |
| 起始 scale | `cardWidth / screenWidth` | 1 |
| 结束 scale | 1 | 1 |
| 页面 alpha | 105ms → 231ms 淡入 | 始终 1 |
| translationX | `card.left → 0` | `+screenWidth → 0` |
| translationY | `card.top → 0`，另有状态栏补偿 | 0 |
| 圆角 | 17dp → 屏幕圆角 | 屏幕圆角，全程 clip |
| 遮罩 | 黑色 0.3 × progress | 无 |
| 时长 | 420ms 进入 / 220ms 返回 | 320ms 进入 / 320ms 返回 |
| Curve | `CubicBezier(0.3, 0, 0.1, 1)` | 同左 |
| 全屏 modal | 否，返回栈中的普通整页 | 否，返回栈中的普通整页 |
| 返回手势 | 卡片缩回，跟手 | 整页右移，跟手 |

## 2.3 transitionSpec 骨架

```kotlin
// AppNavHost.kt:258-266
AnimatedContent(
    targetState = overlay,
    transitionSpec = {
        val popped = initialState?.takeIf { it.popped }
        val pushed = targetState?.takeIf { popped == null }

        val transform = when {
            popped?.previewed == true ->
                EnterTransition.None togetherWith ExitTransition.None

            pushed?.growsFromOrigin() == true || popped?.shrinksToOrigin() == true ->
                EnterTransition.None togetherWith ExitTransition.None

            popped == null ->
                slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it } togetherWith
                    slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
                        -it / RouteParallaxDivisor
                    }

            else ->
                slideInHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) {
                    -it / RouteParallaxDivisor
                } togetherWith
                    slideOutHorizontally(tween(RouteSlideMillis, easing = ExpandEasing)) { it }
        }

        // 页面都铺满屏幕，默认的 size 动画只会让第一页从左上角零尺寸长出来。
        transform.using(null).apply { if (popped != null) targetContentZIndex = -1f }
    },
    label = "route",
) { entry -> ... }
```

参数：

- 时长 320ms。
- `ExpandEasing = CubicBezier(0.3, 0, 0.1, 1)`。
- 进入：设置页 `+W → 0`；下面的页面 `0 → -W/4`。
- 返回：设置页 `0 → +W`；下面的页面 `-W/4 → 0`。
- 两页 alpha 都是 1，没有淡入淡出。
- `targetContentZIndex = -1f`，让正在退出的设置页盖在下面那页之上。

## 2.4 圆角与裁剪

设置页没有 `origin`，所以 `growsFromOrigin()` 为 false，`slides = !grows` 为 true：

```kotlin
// AppNavHost.kt:359-362
if (slides) {
    shape = slideShape
    clip = true
}
```

`slideShape` 是屏幕物理圆角：

```kotlin
// AppNavHost.kt:122-123
val screenCornerRadius = rememberScreenCornerRadius()
val slideShape = remember(screenCornerRadius) { RoundedCornerShape(screenCornerRadius) }
```

`rememberScreenCornerRadius()` 读 `display.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)`
（Android 12+），低于 Android 12 返回 0（`ExpandTransition.kt:117-129`）。静止时圆角与屏幕
圆角一致，所以看不到裁剪边。

## 2.5 预测返回

设置页的预测返回是水平跟手，不缩放：

- 手势进行中：`translationX = (1 - gestureProgress) * width`。
- 松手未过阈值：200ms 回位。
- 松手过阈值：150ms 完成退出，然后 `pop()`。
- 两个 `tween` 使用默认 `FastOutSlowInEasing`。
- 下面的页面用 `BackStackEntry.snapshot` 直接绘制，不会重新组合导致列表跳回顶部。

Manifest 需要开启预测返回：

```xml
<!-- apps/android/app/src/main/AndroidManifest.xml:22 -->
android:enableOnBackInvokedCallback="true"
```

Activity 开了 edge-to-edge：

```kotlin
// MainActivity.kt:44
enableEdgeToEdge(...)
```

## 2.6 数值表

| 参数 | 进入 | 返回 |
| --- | --- | --- |
| 时长 | 320ms | 320ms |
| Curve | `CubicBezier(0.3, 0, 0.1, 1)` | 同左 |
| 设置页 translationX | `+W → 0` | `0 → +W` |
| 下层 translationX | `0 → -W/4` | `-W/4 → 0` |
| scale | 1 | 1 |
| alpha | 1 | 1 |
| 圆角 | 屏幕圆角，clip | 同左 |
| 遮罩 | 无 | 无 |
| zIndex | 默认 | 退出页在上，目标页 `-1` |
| 手势回位 | 200ms | — |
| 手势提交 | 150ms | — |

---

# 三、悬浮底栏

## 3.1 组件与依赖

Compose 主线用的是 COUI 17 的悬浮胶囊底栏 `BottomFloatingAccessoryNavigationBar`，不是
Material3 的普通 `NavigationBar`。

组件文件：

- `third_party/coui17/coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/basic/BottomFloatingAccessoryNavigationBar.kt`
- `third_party/coui17/coui-ui/src/commonMain/kotlin/io/github/suqi8/coui/kmp/basic/BottomFloatingNavigationBar.kt`

依赖：

```kotlin
implementation("io.github.suqi8.coui.kmp:coui-ui:1.2.0")
implementation("io.github.suqi8.coui.kmp:coui-icons:1.2.0")
```

本仓库用 composite build 从源码消费：

```kotlin
// apps/android/settings.gradle.kts:39
includeBuild("../../third_party/coui17")
```

如果目标项目不能接 COUI，普通 Material3 `NavigationBar` 没有等价物。要 1:1 复刻需要自己
实现胶囊玻璃、MetaBall 融合、按压形变和模糊采样，工作量远大于接一个依赖。建议直接接
COUI，或者只复刻“悬浮胶囊 + 三项 + 无配件”的子集。

## 3.2 实际调用

`apps/android/app/src/main/kotlin/app/awavenue/c001apk/ui/main/MainScreen.kt:231-285`：

```kotlin
Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
    BottomFloatingAccessoryNavigationBar(
        startAccessoryVisible = onApp,
        startAccessory = { AppFilterBall(filter = appFilter) },
        endAccessoryVisible = composeBall || updateBall,
        endAccessory = {
            val action: () -> Unit = if (updateBall) {
                { navigator.push(Route.AppUpdate) }
            } else {
                { compose = true }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        role = Role.Button,
                        onClick = action,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (updateBall) {
                    Icon(
                        imageVector = COUIIcons.Update,
                        contentDescription = "更新",
                        tint = COUITheme.colorScheme.onSurface,
                    )
                } else {
                    Icon(
                        imageVector = COUIIcons.Add,
                        contentDescription = "发布动态",
                        tint = COUITheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) {
        MainTab.entries.forEachIndexed { index, tab ->
            BottomFloatingNavigationBarItem(
                selected = pagerState.currentPage == index,
                onClick = {
                    if (pagerState.currentPage == index) {
                        when (tab) {
                            MainTab.Home -> returnTop.requestReturn()
                            MainTab.Follow -> followReturnTop.requestReturn()
                            MainTab.Mine -> Unit
                        }
                    } else {
                        switchTo(index)
                    }
                },
                icon = tab.icon,
                selectedIcon = tab.selectedIcon,
                label = tab.label,
            )
        }
    }
}
```

外层：

```kotlin
// MainScreen.kt:210-221
Scaffold(
    modifier = Modifier.fillMaxSize(),
    bottomBar = {
        val barDensity = rememberChromeDensity()
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalDensity provides barDensity,
        ) {
            Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
                ...
            }
        }
    },
)
```

## 3.3 布局与尺寸

两个配件槽都配置时，每侧固定占 `56dp + 12dp = 68dp`。胶囊宽度：

```text
capsuleWidth = availableWidth - 136dp
itemWidth = (capsuleWidth - 2 * 12dp - (n - 1) * 8dp) / n
```

3 个导航项时：

| 屏幕宽度 | 可用宽度（减 32dp 外边距） | 胶囊宽度 | 单项宽度 |
| --- | --- | --- | --- |
| 360dp | 328dp | 192dp | 50.67dp |
| 412dp | 380dp | 244dp | 68dp |
| ≥430dp | ≥398dp | 250dp（达到 3×70dp + 24 + 16） | 70dp |

注意：`startAccessoryVisible = false` 只是隐藏配件，不会释放宽度。要让胶囊恢复满宽，必须把
`startAccessory` / `endAccessory` 传 `null`，让槽位不再配置。

尺寸表：

| 参数 | 值 | 来源 |
| --- | --- | --- |
| 胶囊高度 | 56dp | `BarHeight` |
| 底栏外层最小高度 | 88dp | `MinHeightWithoutPaddingVertical` |
| 胶囊圆角 | 28dp | `CornerRadius` |
| 小屏左右外边距 | 16dp | `PaddingHorizontal` |
| 中屏/大屏左右外边距 | 0dp | `COUIResponsiveUtils.pick(...)` |
| 胶囊距底栏盒子底部 | 8dp | `MenuToScreenNaviDistance` |
| 系统导航栏安全区 | `navigationBarsPadding()` | `MainScreen.kt:221` |
| 胶囊距屏幕底部 | 8dp + 系统导航栏 inset | 8dp + inset |
| 配件直径 | 56dp | `AccessorySize` |
| 配件与胶囊间距 | 12dp | `SideSpacing` |
| 导航项高度 | 48dp | `ItemHeight` |
| 导航项最大宽度 | 70dp | `ItemMaxWidth` |
| 导航项间距 | 8dp | `ItemSpacing` |
| 胶囊两端内边距 | 12dp | `ItemInnerPadding` |
| 图标尺寸 | 24dp | `IconSize` |
| 图标顶部间距 | 4dp | `IconTopMargin` |
| 图标与文字间距 | 2dp | `IconTitleGapVertical` |
| 文字尺寸 | 10sp 的值按 dp 绘制，不跟随系统字体缩放 | `LabelFontSize` |
| 文字行高 | 13.6dp | `LabelLineHeight` |
| 字体粗细 | Medium | `FontWeight.Medium` |

## 3.4 材质与动效

| 参数 | 值 | 来源 |
| --- | --- | --- |
| 背景色 | `COUITheme.colorScheme.bar`，浅色 `#FAFAFA` / 深色 `#1E1E1E` | `Colors.kt:641,871` |
| 有模糊时的容器 alpha | 浅色 0.8 / 深色 0.65 | `containerAlpha(isDark)` |
| 模糊半径 | 25px sigma，dp 半径 = `25 / 0.45 / density` | `NATIVE_BLUR_RADIUS_PX` |
| 浅色模糊叠色 | `#B3666666` ColorDodge，再 `#99F5F5F5` Luminosity | `BlurColorsLight` |
| 深色模糊叠色 | `#80666666` Overlay，再 `#B3262626` Luminosity | `BlurColorsDark` |
| 边缘高光 | 2.5dp，浅色 alpha 0.25 / 深色 alpha 0.8 | `RIM_ALPHA_LIGHT/DARK` |
| 阴影 | radius 6dp，黑色 alpha `0x1E/255`，offset `(0, 2dp)`，spread 0 | `BarShadow` |
| 底部焦散光 | `#42FFFFFF`（配件栏用 `White 66/255`） | `CausticColor` |
| 按压 scale 范围 | 0.85 – 0.97 | `PressScaleMin/Max` |
| 70×48 导航项按压 scale | 约 0.866 | `pressScale(70.dp, 48.dp)` |
| 按压缩放弹簧 | response 0.3 / bounce 0 | `PressScalePressSpring` |
| 松手回弹弹簧 | response 0.5 / bounce 0.5 | `PressScaleReleaseSpring` |
| 配件起始 scale | 0.5 | `MinScale` |
| 配件 alpha 弹簧 | response 0.35 / bounce 0 | `AlphaSpring` |
| 配件 scale 弹簧 | response 0.55 / bounce 0.35 | `ScaleSpring` |
| 配件 translationX 弹簧 | response 0.55 / bounce 0.35 | `TranslationXSpring` |

模糊半径的换算：

```kotlin
// BottomFloatingNavigationBar.kt:937-940
@Composable
fun blurRadius(): Float = with(LocalDensity.current) {
    (NATIVE_BLUR_RADIUS_PX / TEXTURE_BLUR_SIGMA_SCALE).toDp().value
}

// BottomFloatingNavigationBar.kt:1181
private const val TEXTURE_BLUR_SIGMA_SCALE = 0.45f
```

所以 3.5x 密度下约为 `25 / 0.45 / 3.5 = 15.87dp`，3x 密度下约为 `18.52dp`。不要直接写
25dp，原生给的是像素 sigma。

## 3.5 选中指示器与滚动隐藏

### 选中指示器

没有滑动选中指示器，也没有指示器动画。选中态只做两件事：

- 图标从 `icon` 切到 `selectedIcon`（实心/描边切换）。
- tint 从 `labelPrimaryVariant` 切到 `onSurface`。

两者都是瞬时切换，没有 `animateColorAsState`，没有位移胶囊。导航项自身的反馈是
`couiTouchMotion` 的按压缩放和聚光，不是选中指示器。

```kotlin
// BottomFloatingNavigationBar.kt:670-674
val tint = when {
    !enabled -> colorScheme.onSurfaceVariantActions
    selected -> colorScheme.onSurface
    else -> colorScheme.labelPrimaryVariant
}

// BottomFloatingNavigationBar.kt:730
imageVector = selectedIcon.takeIf { selected } ?: icon,
```

顶部 `TabRowWithContour` 有自己的指示器材质，但那是首页分类栏，不是底栏。

### 滚动隐藏

没有滚动隐藏逻辑，底栏始终显示。首页滚动时折叠的是顶栏的 52dp 顶部行，不是底栏：

```kotlin
// ui/home/HomeScreen.kt:117,265
private val TOP_BAR_ROW_HEIGHT = 52.dp
val topRowHeightPx = with(LocalDensity.current) { TOP_BAR_ROW_HEIGHT.toPx() }
```

```kotlin
// HomeScreen.kt:357-365
.layout { measurable, constraints ->
    val full = TOP_BAR_ROW_HEIGHT.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minHeight = full, maxHeight = full),
    )
    val visible = (full * (1f - collapseFraction())).roundToInt().coerceIn(0, full)
    layout(placeable.width, visible) { placeable.placeRelative(0, 0) }
}
```

详情页的回复 FAB 会根据滚动方向隐藏（`FeedDetailScreen.kt:139-149`），但那是 FAB，不是底栏。

### Tab 切换

```kotlin
// MainScreen.kt:73-75
private const val TabFadeOutMillis = 60
private const val TabFadeInMillis = 100

// MainScreen.kt:136-145
fade.animateTo(0f, tween(TabFadeOutMillis, easing = LinearEasing))
pagerState.scrollToPage(index)
fade.animateTo(1f, tween(TabFadeInMillis, easing = LinearEasing))
```

先 60ms 线性淡出整个 pager，瞬时 `scrollToPage`，再 100ms 线性淡入。页面本身不位移。

## 3.6 内容列表的 padding / inset

各页底部 padding：

```kotlin
// ui/home/HomeScreen.kt:461
bottom = outerPadding.calculateBottomPadding() + 12.dp

// ui/follow/FollowScreen.kt:211
bottom = outerPadding.calculateBottomPadding() + 12.dp

// ui/message/MessageScreen.kt:195
bottom = outerPadding.calculateBottomPadding() + 10.dp
```

`outerPadding` 来自 `MainScreen` 的 `Scaffold`，数值等于底栏盒子高度：

```text
outerPadding.bottom ≈ 88dp（底栏最小高度） + navigationBars inset
```

所以列表最后一行的底部留白：

- 首页 / 关注：`88dp + navInset + 12dp`
- 我的：`88dp + navInset + 10dp`

胶囊本身在 88dp 盒子里距底部 8dp，所以最后一行距离胶囊顶部还有：

```text
88 - 56 - 8 + 12 = 36dp（首页/关注）
```

内容不会透到栏下。`LazyColumn` 虽然 `fillMaxSize()`，但 `contentPadding` 把 item 全部挡在
底栏上方，底栏后面的区域只有页面背景。

模糊采样仍然工作：`MainScreen` 的 pager 上挂了 `.glassSource(backdrop)`，底栏通过
`LocalBackdrop` 采样它后面的页面背景（`MainScreen.kt:146, 217-220, 294-298`）。如果以后想让
内容从胶囊下面透出来，需要去掉底部 `contentPadding` 并自己加一层渐隐/遮罩；仓库当前没有
这么做。

## 3.7 可复制示例

仓库里已经有悬浮底栏，不需要 fallback。下面是最小可复制版本：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.suqi8.coui.kmp.basic.BottomFloatingAccessoryNavigationBar
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBarItem

@Composable
fun FloatingBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    showStart: Boolean,
    showEnd: Boolean,
) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding()) {
        BottomFloatingAccessoryNavigationBar(
            startAccessoryVisible = showStart,
            startAccessory = { /* 56dp 圆形内容，例如筛选球 */ },
            endAccessoryVisible = showEnd,
            endAccessory = { /* 56dp 圆形内容，例如发布按钮 */ },
        ) {
            BottomFloatingNavigationBarItem(
                selected = selected == 0,
                onClick = { onSelect(0) },
                icon = MyIcons.HomeOutlined,
                selectedIcon = MyIcons.HomeFilled,
                label = "主页",
            )
            BottomFloatingNavigationBarItem(
                selected = selected == 1,
                onClick = { onSelect(1) },
                icon = MyIcons.FollowOutlined,
                selectedIcon = MyIcons.FollowFilled,
                label = "关注",
            )
            BottomFloatingNavigationBarItem(
                selected = selected == 2,
                onClick = { onSelect(2) },
                icon = MyIcons.PersonOutlined,
                selectedIcon = MyIcons.PersonFilled,
                label = "我的",
            )
        }
    }
}
```

不需要两侧配件时：

```kotlin
import io.github.suqi8.coui.kmp.basic.BottomFloatingNavigationBar

BottomFloatingNavigationBar {
    BottomFloatingNavigationBarItem(
        selected = selected == 0,
        onClick = { onSelect(0) },
        icon = MyIcons.HomeOutlined,
        selectedIcon = MyIcons.HomeFilled,
        label = "主页",
    )
}
```

## 3.8 数值表

| 参数 | 值 |
| --- | --- |
| 胶囊高度 | 56dp |
| 底栏外层最小高度 | 88dp |
| 胶囊圆角 | 28dp |
| 小屏左右外边距 | 16dp |
| 中屏/大屏左右外边距 | 0dp |
| 胶囊距底栏盒子底部 | 8dp |
| 胶囊距屏幕底部 | 8dp + navInset |
| 配件直径 | 56dp |
| 配件与胶囊间距 | 12dp |
| 导航项高度 | 48dp |
| 导航项最大宽度 | 70dp |
| 导航项间距 | 8dp |
| 胶囊两端内边距 | 12dp |
| 图标 | 24dp |
| 图标顶部间距 | 4dp |
| 图标与文字间距 | 2dp |
| 文字 | 10dp 等效，行高 13.6dp |
| 背景 | `#FAFAFA` / `#1E1E1E` |
| 容器 alpha | 0.8 浅 / 0.65 深 |
| 模糊 | 25px sigma |
| 边缘高光 | 2.5dp，0.25 / 0.8 |
| 阴影 | 6dp，`0x1E` 黑，y 2dp |
| 按压 scale | 0.85 – 0.97 |
| Tab 切换 | 60ms 淡出 / 100ms 淡入 |

---

# 四、内容持久化

## 4.1 结论：没有数据库，没有信息流磁盘缓存

Compose 端：

- 没有 Room，没有 SQLite，没有 DataStore。
- 信息流、详情、评论、用户主页、搜索、消息列表都没有磁盘缓存。
- 唯一的“缓存”是 ViewModel 内存状态和 `CoolapkRepository` 里 60 秒的详情缓存。
- 持久化只用 `SharedPreferences`，存设置、收藏、历史、搜索历史、黑名单、会话、账号。

`grep` 结果可以确认：`apps/android/app/src/main/kotlin` 下没有 `Room`、`SQLite`、
`DataStore`。只有 Coil 的图片磁盘缓存和 `SharedPreferences`。

## 4.2 信息流的生命周期

首页：

```kotlin
// ui/home/HomeScreen.kt:464-467
val key = "home-${tab.name}-${app.session.uid}"
val loader = tab.loader(app.repository)
val viewModel = viewModel(key = key) { FeedListViewModel(loader!!) }
```

关注：

```kotlin
// ui/follow/FollowScreen.kt:219-224
val viewModel = viewModel(key = "follow-${selection.name}-${app.session.uid}") {
    FeedListViewModel(
        PageLoader { page, first, last ->
            app.repository.dataList(followUrl(selection), selection.label, page, first, last)
        },
    )
}
```

这些是 ViewModel key，不是磁盘 key。它们只保证同一账号、同一分类复用同一个 ViewModel 实例。

`FeedListViewModel` 只在构造时自动请求一次：

```kotlin
// ui/feed/FeedListViewModel.kt:59-61
init {
    load(isRefresh = true)
}
```

之后：

- 切 tab：`MainScreen` 的外层 pager `beyondViewportPageCount = MainTab.entries.size`，三个主
  tab 一直组合；首页内层 pager 是 `beyondViewportPageCount = 1`，但 ViewModel 存在
  `MainContent` 的 `ViewModelStoreOwner` 里，不随页面组合销毁。回到同一个 tab 时直接显示
  旧 `items`。
- 从信息流点开详情再返回：`AppNavHost` 把 `MainScreen()` 一直放在栈底组合，主页面不会被
  销毁，所以列表和 ViewModel 都还在。
- 账号切换：`MainScreen` 用 `session.generation` 做 `key`，旧 `ViewModelStore` 被 `clear()`，
  新账号重新创建 ViewModel 并请求网络（`MainScreen.kt:96-121`）。
- 进程被杀：全部内存丢失，冷启动时 `init` 请求网络。

只有以下条件会重新请求：

1. ViewModel 第一次创建（冷启动、账号切换、进入一个从未打开过的分类）。
2. 手动下拉刷新 `refresh()`。
3. 触底加载更多 `loadMore()`。
4. Footer 重试 `retryFooter()`。
5. 详情页的 `loadFeed()` / `refreshReplies()` / 切换回复排序。
6. 用户主页等页面的显式 `reload`。

## 4.3 刷新与失败语义

```kotlin
// ui/feed/FeedListViewModel.kt:63-67
fun refresh() {
    if (isLoading) return
    isRefreshing = items.isNotEmpty()
    load(isRefresh = true)
}
```

```kotlin
// ui/feed/FeedListViewModel.kt:96-153（节选）
val result = loader.load(requestPage, requestFirst, requestLast)
if (isRefresh) {
    page = 1
    firstItem = null
    lastItem = null
    isEnd = false
}
if (result.isEmpty()) {
    isEnd = true
    if (isRefresh) {
        items = emptyList()
        status = ListStatus.Empty
    } else {
        footer = FooterStatus.End
    }
} else {
    firstItem = firstItem ?: result.first().id
    lastItem = result.last().id
    val merged = if (isRefresh) result else items + result
    items = merged.filter { ... }
    status = ListStatus.Content
    footer = FooterStatus.Idle
    page = requestPage + 1
}
```

异常分支：

```kotlin
// ui/feed/FeedListViewModel.kt:137-147
when {
    isRefresh && items.isEmpty() -> status = ListStatus.Error(message)
    isRefresh -> refreshError = message
    else -> {
        isEnd = true
        footer = FooterStatus.Error(message)
    }
}
```

所以：

- 刷新成功：旧列表被新结果整体替换，`page` 重置为 1，游标清空。
- 刷新失败且旧列表非空：`items` 不变，`refreshError` 被 `FeedList` 用 Toast 弹出
  （`FeedList.kt:118-123`）。
- 刷新失败且旧列表为空：进入 `ListStatus.Error`。
- 刷新返回空数组：旧列表被清空，进入 `ListStatus.Empty`。
- 没有磁盘缓存需要失效。

## 4.4 各页面缓存表

| 页面 | 内存缓存 | 磁盘缓存 | key / 位置 |
| --- | --- | --- | --- |
| 动态详情 | `detailCache[id]`，TTL 60s，最多 32 条 | 无 | `CoolapkRepository.kt:49-74,612` |
| 动态详情 ViewModel | `feed`、`feedStatus`、`isPublic` 等 | 无 | `viewModel(key = "feed-detail-$id")`，`FeedDetailScreen.kt:103` |
| 评论 | `replies`、`replyStatus`、`page`、`firstItem`、`lastItem`、`replySort` | 无 | `FeedDetailViewModel`，随路由 entry 存活 |
| 用户主页头部 | `UserHeaderState`：`user`、`error`、`loading`、`loaded` | 无 | `viewModel(key = "user-header-$uid")`，`UserScreen.kt:127` |
| 用户主页动态 | `FeedListViewModel` | 无 | `viewModel(key = "user-feed-$uid")`，`UserScreen.kt:294` |
| 搜索结果 | `FeedListViewModel` 或对应列表 VM | 无 | `viewModel(key = "search-$keyword-$pageType-$pageParam-$contentType-$feedType-$sort-$isStrict")`，`SearchResultScreen.kt:189` |
| 搜索历史 | 无 | SharedPreferences `searchHistory` / key `searchHistory`，最多 20 条 | `SearchHistoryStore.kt:14,31-37` |
| 消息列表 | `profile`、`recent`、`follows`，普通 `remember` 状态 | 无 | `MessageScreen.kt:113-119,150-158` |
| 本地收藏 | `favFeeds` | SharedPreferences `history` / key `favFeed`，JSON map，key 是动态 id | `HistoryStore.kt:73-75,105-112` |
| 浏览历史 | `historyFeeds` | SharedPreferences `history` / key `historyFeed`，JSON map，key 是动态 id | 同上 |
| 图片 | Coil memory cache | Coil disk cache | 设置页可清空，`SettingsScreen.kt:538-550` |

动态详情缓存：

```kotlin
// CoolapkRepository.kt:49-74
private class CachedDetail(val detail: FeedDetail, val at: Long)

private val detailCache = HashMap<String, CachedDetail>()
private val detailCacheLock = Any()

private fun cachedDetail(id: String, now: Long): FeedDetail? = synchronized(detailCacheLock) {
    val hit = detailCache[id]
    if (hit == null || now - hit.at > FeedDetailTtlMillis) {
        detailCache.remove(id)
        null
    } else {
        hit.detail
    }
}

private fun cacheDetail(id: String, detail: FeedDetail, now: Long) = synchronized(detailCacheLock) {
    if (detailCache.size >= 32) {
        detailCache.entries.removeAll { now - it.value.at > FeedDetailTtlMillis }
        if (detailCache.size >= 32) detailCache.clear()
    }
    detailCache[id] = CachedDetail(detail, now)
}
```

```kotlin
// CoolapkRepository.kt:612
private const val FeedDetailTtlMillis = 60_000L
```

失效条件：

- 超过 60 秒自动过期。
- 点赞/取消点赞动态时主动移除：`CoolapkRepository.kt:359`
  `if (isFeed) synchronized(detailCacheLock) { detailCache.remove(id) }`。
- `verify = true` 时绕过缓存（`feedDetail` 里 `if (!verify) cachedDetail(...)`）。
- 超过 32 条时先清理过期项，仍然超限就整体清空。

## 4.5 滚动位置、当前 tab、已加载页数

| 状态 | 是否持久化 | 存储方式 | 恢复方式 |
| --- | --- | --- | --- |
| 列表滚动位置 | 仅内存 / 组合状态，不落盘 | `rememberLazyListState()`，例如 `FeedList.kt:62` | 页面保持组合时直接保留；路由压栈时存入 `BackStackEntry.savedState`，返回时恢复 |
| 当前主 tab | 是 | `rememberSaveable` + SharedPreferences `settings` / `homeTabName` | 启动时读 `AppSettings.homeTab` |
| 首页当前分类 | 是 | SharedPreferences `settings` / `homeTabName` | `HomeScreen.kt:294-305` 把 pager 滚到该分类 |
| 关注当前分类 | 是 | SharedPreferences `settings` / `followSelection` | `AppSettings.kt` 读取 |
| 已加载页数 / 游标 | 否 | `FeedListViewModel` 的私有 `page` / `firstItem` / `lastItem` | 进程重启后从第 1 页重新请求 |
| 评论分页游标 | 否 | `FeedDetailViewModel` 私有字段 | 重新打开详情后从第 1 页请求 |
| 路由栈 | 否 | `SnapshotStateList<BackStackEntry>` | 进程重启后回到 `Route.Main` |

当前 tab 的持久化：

```kotlin
// MainScreen.kt:98
var tab by rememberSaveable { mutableIntStateOf(MainTab.Home.ordinal) }

// HomeScreen.kt:297-305
snapshotFlow { pagerState.settledPage }.collect { page ->
    val tab = tabs.getOrNull(page)
    if (tab == null) {
        pagerState.scrollToPage(tabs.lastIndex)
    } else {
        settings.updateHomeTab(tab.name)
    }
}
```

```kotlin
// data/AppSettings.kt:41
var homeTab by mutableStateOf(
    prefs.getString(KEY_HOME_TAB_NAME, null)
        ?: LEGACY_HOME_TABS.getOrNull(prefs.getInt(KEY_HOME_TAB, 2))?.takeIf { it != "Follow" }
        ?: "Feed",
)

// AppSettings.kt:131
fun updateHomeTab(value: String) = put { putString(KEY_HOME_TAB_NAME, value) }.also { homeTab = value }

// AppSettings.kt:228
const val KEY_HOME_TAB_NAME = "homeTabName"
```

滚动位置的保存链路：

- `FeedList` 用 `rememberLazyListState()`，它是 `rememberSaveable`。
- 路由压栈时，`AppNavHost` 给每个 `BackStackEntry` 建
  `SaveableStateRegistry(entry.savedState)`，页面离开组合时 `performSave()`，返回时恢复
  （`AppNavHost.kt:340-392`）。
- 这个 `savedState` 只存在内存的 `BackStackEntry` 里，不写入 Bundle / 磁盘，所以进程被杀后
  不保留。

已加载页数没有持久化：

```kotlin
// FeedListViewModel.kt:53
private var page = 1
```

## 4.6 如果产品要求磁盘缓存

仓库没有这套实现。要加的话，最小改动点如下，不要改现有刷新语义：

1. 在 `FeedListViewModel` 构造参数里加一个 `FeedCache` 接口，不要让 ViewModel 直接依赖
   Room 之类的存储实现。
2. `init` 先读快照，读到就先设 `items` / `status = Content`，再发网络请求；网络成功后覆盖。
3. `load(isRefresh = true)` 成功后写快照；失败不写，保留旧快照。
4. key 必须包含 `uid + tab/category`，例如 `home-Feed-<uid>`、`follow-User-<uid>`。
5. 下拉刷新成功时删除旧快照再写新快照；失败时不动旧快照。
6. 详情缓存继续用现有的 60 秒内存 TTL，不要落盘；详情接口容易触发验证码，长期缓存会放大
   脏数据问题。

这些是新增设计，不是仓库现状。要 1:1 复刻，直接保持“无磁盘缓存”。

---

# 五、发布面板：跟着键盘走的面板高度

`ReplyComposerSheet` 是唯一一个“高度由键盘决定”的界面。它的问题不是动画没做，而是
**有一段几何量在收尾那一帧才变**，于是看起来像“先回落、再闪现长高”。

## 5.1 面板怎么跟键盘

- 面板是 `OverlayBottomSheet`，贴窗口底边；`insideMargin = DpSize(16.dp, bottomGap)` 里的
  `bottomGap` 就是内容（发布胶囊）与面板底边之间的留白。
- 库在 sheet 的 `Column` 上加了 `imePadding()`，所以键盘起来时整块面板骑在键盘上方，
  面板底边 = 窗口底边 − IME 高度。
- 编辑区高度不写死：`BoxWithConstraints` 拿到 sheet 剩下的高度，减掉图片行、表情面板、
  工具行和发布胶囊，得到 `editorMax`，卡片再 `heightIn(max = editorMax)`。键盘越高卡片越矮，
  最低 `MinEditorHeight = 88dp`（再低就没法打字了）。

## 5.2 收尾跳变：12dp / 40dp 不能瞬间切

原实现是：

```kotlin
val bottomGap = if (keyboard > 0.dp) KeyboardBottomInset else BottomInset  // 12dp / 40dp
```

`keyboard` 是 `WindowInsets.ime` 的当前值，键盘收起时它会逐帧掉到 0，所以这个 `if` 会在
**最后一帧**翻转：面板的回落已经结束、`imePadding` 已经是 0，才把留白从 12dp 换成 40dp，
整块面板连同编辑卡片、发布胶囊在同一帧里向上跳 28dp。

实测（1240×2772，620dpi，即 3.875px/dp；微信输入法，键盘高 322dp）：

| 帧 | 面板顶边 y | 编辑卡片高 | 发布胶囊顶 y |
| --- | --- | --- | --- |
| 键盘在（静止） | 140 | 96.0dp | 1318 |
| 回落中 | 307 → 442 → 489 | 87 → 113dp | 2372 → 2508 → 2554 |
| 落定后约 100ms | **381（−108px = −28dp）** | 114.3dp | **2446（−108px）** |

回落本身是逐帧连续的（4 帧走完 349px），只有最后那一跳是离散的。观感就是“先回落到底、
过一会儿闪现展开”。

修法：让 `bottomGap` 变成 IME inset 的**连续函数**，在键盘行程的最后 80dp 里从 40dp 插值到
12dp。这样面板的脚在整个回落过程里一直在动，没有“落定后补一帧”的机会。

```kotlin
val keyboardGapProgress = (keyboard / ComposerPanel.KeyboardGapRange).coerceIn(0f, 1f)
val bottomGap = ComposerPanel.BottomInset +
    (ComposerPanel.KeyboardBottomInset - ComposerPanel.BottomInset) * keyboardGapProgress
```

键盘完全收起时 `keyboard = 0`，留白正好是 40dp；键盘完全起来时大于 80dp，留白正好是 12dp。
中间那 80dp 是过渡区，和键盘自己的动画同帧推进。

## 5.3 为什么不用 animateDpAsState

试过用 `animateDpAsState` 把 12/40 的切换补成动画，但它是在键盘**已经收完**之后才开始跑，
问题从“跳变”变成“落定之后再长高”，还是两段；而且曲线和 IME 的动画对不齐，中途会看到
面板和键盘脱节。绑到 inset 上，时序天然一致。

同理，编辑卡片的高度不需要额外补动画：它由约束驱动，约束由 IME inset 逐帧驱动，本来就是
连续的（上表里 87 → 113 → 114.3dp 就是逐帧长出来的）。

## 5.4 怎么量

1. `adb shell screenrecord --time-limit 5 /sdcard/rec.mp4`，操作触发过渡，然后 `adb pull`。
2. `ffmpeg -i rec.mp4 -vsync 0 f/%03d.png` 拆帧。
3. 用 PIL 按行扫描：面板表面（x 1050 附近）亮度 239/240，编辑卡片是纯白 253，发布胶囊是
   蓝色（R<130、B>170）。打印每帧的 `panelTop / cardTop / cardHeight / capTop` 和逐帧 Δ。
4. 判据：所有 Δ 应该分散在多帧上，任何一帧都不该出现 ≈28dp（108px）的单帧位移。

坑：卡片和面板表面都是“白”，阈值不分开会量到面板背景，卡片高度会算成整个面板。

---

# 六、验收清单

## 6.1 构建与安装

按本仓库约定，165 设备只装 release 包，且必须非增量安装：

```bash
cd apps/android
KEYSTORE="$HOME/.android/debug.keystore" KEY_ALIAS=androiddebugkey \
  KEYSTORE_PASSWORD=android KEY_PASSWORD=android ./gradlew :app:assembleRelease

adb -s 192.168.31.165:5555 install -r --no-incremental \
  app/build/outputs/apk/release/app-release.apk
```

安装后核对落点，避免 IncFS 导致 LSPosed/系统钩子静默失效：

```bash
adb -s 192.168.31.165:5555 shell "su -c 'cat /proc/mounts'" | grep incremental-fs
adb -s 192.168.31.165:5555 shell "pm path app.awavenue.c001apk.coui"
```

`pm path` 的路径若落在 `incremental-fs` 挂载点下，必须用 `--no-incremental` 重装。

## 6.2 逐项测量

### 动态详情

| 检查项 | 预期 | 方法 |
| --- | --- | --- |
| 进入时长 | 420ms | 120Hz 屏幕录屏，约 50.4 帧 |
| 返回时长 | 220ms | 约 26.4 帧 |
| 进入 Curve | `CubicBezier(0.3, 0, 0.1, 1)` | 前段快、后段软；对比 `Linear` 明显不同 |
| 页面开始淡入 | 105ms | 前 105ms 只看到卡片层，之后页面出现 |
| 页面完全淡入 | 231ms | 231ms 后卡片层为 0 |
| 遮罩 | 黑色 30% × progress | 动画中点约 15% 黑 |
| 起始圆角 | 17dp | 与信息流卡片圆角一致 |
| 结束圆角 | 屏幕物理圆角 | 与系统卡片一致，静止时不可见 |
| 返回卡片出现 | 132ms 开始，202.4ms 完成 | 前 132ms 只看到缩小页面 |
| 返回后卡片位置 | 卡片顶部露出顶栏 | `alignCardBelowBar` 生效 |
| 预测返回 | 1:1 跟手，回位 200ms，提交 150ms | 慢速侧滑，松手观察 |

### 设置页

| 检查项 | 预期 |
| --- | --- |
| 进入时长 | 320ms |
| 返回时长 | 320ms |
| 进入方向 | 从右边缘滑入 |
| 下层位移 | 屏宽的 1/4 |
| alpha | 全程 1，无淡入淡出 |
| 圆角 | 屏幕物理圆角，滑动时可见 |
| 遮罩 | 无 |
| 预测返回 | 整页水平跟手，回位 200ms，提交 150ms |

### 底栏

| 检查项 | 预期 |
| --- | --- |
| 360dp 屏胶囊宽度 | 192dp |
| 360dp 屏单项宽度 | 50.67dp |
| 412dp 屏胶囊宽度 | 244dp |
| 412dp 屏单项宽度 | 68dp |
| 胶囊高度 | 56dp |
| 胶囊圆角 | 28dp |
| 胶囊距屏幕底部 | 8dp + navInset |
| 底栏外层高度 | 88dp + navInset |
| 图标 | 24dp |
| 文字 | 10dp 等效，不随系统字体放大 |
| 选中反馈 | 图标实心/描边 + tint 切换，无滑动指示器 |
| 滚动 | 底栏不隐藏；只有顶栏 52dp 顶部行折叠 |
| Tab 切换 | 60ms 淡出 / 100ms 淡入 |

### 持久化

| 检查项 | 预期 |
| --- | --- |
| 切 tab 再回来 | 不重新请求，列表和滚动位置保持 |
| 点开详情再返回 | 不重新请求，列表和滚动位置保持 |
| 下拉刷新成功 | 列表替换，`page` 重置为 1 |
| 下拉刷新失败 | 旧列表保留，Toast 显示错误 |
| 60 秒内重复打开同一动态 | 第二次不请求详情接口 |
| 超过 60 秒再打开 | 重新请求 |
| 点赞后立刻重开 | 详情缓存被移除，重新请求 |
| 杀进程再启动 | 信息流重新请求；当前 tab 恢复；滚动位置不恢复 |
| 账号切换 | 旧 ViewModelStore 清空，新账号重新请求 |

## 6.3 常见坑

1. **不要把 `ExpandFrame` 换成 `AnimatedContent` 的 `scaleIn`。**
   `scaleIn` 默认以中心为锚点，而且不会把卡片边界、圆角、遮罩和页面 alpha 串起来。
2. **`transformOrigin` 必须是 `(0, 0)`。**
   否则缩放锚点跑到中心，起始位置和卡片对不上。
3. **不要提前淡出页面。**
   返回时如果先淡出页面再淡入卡片，手指停在中间会看到框的纯色填充。仓库是在
   `p=0.4` 到 `p=0.08` 之间交叉淡化。
4. **不要把页面放进会改变尺寸的容器。**
   所有页面都 `fillMaxSize()`；否则 `AnimatedContent` 默认的 size 动画会让第一页从左上角
   零尺寸长出来。仓库用 `transform.using(null)` 关掉 size 动画。
5. **进入时下面的页面必须保持组合。**
   否则卡片无法实时绘制，只能退化成静态快照；返回时下面的列表也无法滚动。
6. **退出时被弹出的页面必须变成静止画面。**
   否则它会继续响应触摸，挡住下面列表的滚动。
7. **快照要等 220ms 再拍第二张。**
   第一张会带点击按压高亮，返回时会把高亮带回来。
8. **底栏的配件槽是“配置即占位”。**
   `*Visible = false` 不会释放宽度；要释放必须传 `null`。
9. **文字 10dp 不是 10sp。**
   COUI 的导航项标题按像素尺寸绘制，不跟随系统字体缩放；直接写 `10.sp` 会在大字模式下
   变大，和原生不一致。
10. **模糊半径不是 25dp。**
    原生给的是 25px sigma，换算成 dp 是 `25 / 0.45 / density`。

---

# 附录 A：常量速查

```kotlin
// 路由滑动
private const val RouteSlideMillis = 320
private const val RouteParallaxDivisor = 4

// 预测返回
private const val GestureSettleMillis = 200
private const val GestureCommitMillis = 150

// 卡片展开
private val ExpandEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
private const val ExpandDurationMillis = 420
private const val ExitDurationMillis = 220
private const val ScrimAlpha = 0.3f
private const val CardFadeEnd = 0.25f
private const val PageFadeLength = 0.3f
private const val ExitCardStart = 0.4f
private const val ExitCardEnd = 0.08f
private const val ExitHandoverProgress = 0.02f
private const val SnapshotSettleMillis = 220L

// 卡片圆角
val cardCornerRadius = 17.dp

// 发布面板
val composerBottomInset = 40.dp          // 面板内容离窗口底边
val composerKeyboardBottomInset = 12.dp  // 骑在键盘上时
val composerKeyboardGapRange = 80.dp     // 上面两个值在这段键盘行程里插值
val composerMinEditorHeight = 88.dp

// 底栏
val barHeight = 56.dp
val barMinHeight = 88.dp
val barCornerRadius = 28.dp
val barPaddingHorizontal = 16.dp
val menuToScreen = 8.dp
val accessorySize = 56.dp
val sideSpacing = 12.dp
val itemHeight = 48.dp
val itemMaxWidth = 70.dp
val itemSpacing = 8.dp
val itemInnerPadding = 12.dp
val iconSize = 24.dp
val iconTopMargin = 4.dp
val iconTitleGap = 2.dp
val labelSize = 10.dp
val labelLineHeight = 13.6.dp
```

# 附录 B：文件与行号索引

| 内容 | 文件 | 行 |
| --- | --- | --- |
| 卡片入口 | `ui/feed/FeedCard.kt` | 129, 148, 172 |
| `Route.Feed` | `ui/nav/Route.kt` | 48 |
| `Navigator.push` | `ui/nav/Navigator.kt` | 93-95 |
| 路由宿主 | `ui/nav/AppNavHost.kt` | 583 |
| 过渡选择 | `ui/nav/AppNavHost.kt` | 246-270 |
| 预测返回 | `ui/nav/AppNavHost.kt` | 149-203 |
| 页面 alpha / 位移 | `ui/nav/AppNavHost.kt` | 281-363 |
| 上一页快照 | `ui/nav/AppNavHost.kt` | 510-562 |
| 卡片避让顶栏 | `ui/nav/AppNavHost.kt` | 408-428 |
| `ExpandOrigin` | `ui/nav/ExpandTransition.kt` | 58-114 |
| `ExpandFrame` | `ui/nav/ExpandTransition.kt` | 160-321 |
| 常量 | `ui/nav/ExpandTransition.kt` | 131-152, 431-453 |
| 屏幕圆角 | `ui/nav/ExpandTransition.kt` | 117-129 |
| 设置入口 | `ui/message/MessageScreen.kt` | 235 |
| 设置宿主 | `ui/nav/AppNavHost.kt` | 580 |
| 设置页 | `ui/settings/SettingsScreen.kt` | 113 |
| 发布面板：留白插值 | `ui/feed/reply/ReplyComposerSheet.kt` | 378-390 |
| 发布面板：几何常量 | `ui/feed/reply/ReplyComposerSheet.kt` | 684-790 |
| 底栏调用 | `ui/main/MainScreen.kt` | 210-288 |
| Tab 切换 | `ui/main/MainScreen.kt` | 73-75, 136-145 |
| 首页 padding | `ui/home/HomeScreen.kt` | 461 |
| 关注 padding | `ui/follow/FollowScreen.kt` | 211 |
| 我的 padding | `ui/message/MessageScreen.kt` | 195 |
| 信息流 VM | `ui/feed/FeedListViewModel.kt` | 40-153 |
| 详情缓存 | `data/CoolapkRepository.kt` | 49-74, 281-305, 612 |
| 点赞失效 | `data/CoolapkRepository.kt` | 359 |
| 当前 tab | `data/AppSettings.kt` | 41, 131, 228 |
| 收藏/历史 | `data/HistoryStore.kt` | 73-75, 105-140 |
| 搜索历史 | `data/SearchHistoryStore.kt` | 14, 31-37 |
| 路由保存状态 | `ui/nav/AppNavHost.kt` | 340-392 |
