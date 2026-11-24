package app.awavenue.c001apk.ui.image

import android.content.Context
import android.view.LayoutInflater
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.R
import app.awavenue.c001apk.media.extractEmbeddedVideo
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** 按住这么久、期间手指没滑开，才算要播实况。短于左右滑的判断，长于一次点按。 */
internal const val LiveHoldMillis = 280L

/** 实况盖上静图、以及松手回到静图，两边用同一段淡入淡出。 */
internal const val LiveFadeMillis = 600

/**
 * 单张实况图在看图页当次访问中的播放状态机（直接取自 Cobo COUI）：
 * - 开启自动播放时，页面停稳后触发一次播放，播完自动收起且不再循环；
 * - 播完（或被打断）后留在当前页只能靠长按重新触发；
 * - 划离当前页后重置访问标记，再次划回可重新自动播放一次。
 */
@Stable
internal class LivePhotoPlaybackState {
    var holding by mutableStateOf(false)
        private set
    var autoPlaying by mutableStateOf(false)
        private set
    var autoPlayedForVisit by mutableStateOf(false)
        private set
    var holdSession by mutableIntStateOf(0)
        private set

    val isPlaying: Boolean
        get() = holding || autoPlaying

    fun tryAutoPlay(enabled: Boolean): Boolean {
        if (!enabled || autoPlayedForVisit || holding) return false
        autoPlayedForVisit = true
        autoPlaying = true
        return true
    }

    fun onPlaybackEnded() {
        autoPlaying = false
    }

    fun onLongPressStart() {
        autoPlaying = false
        autoPlayedForVisit = true
        holding = true
        holdSession++
    }

    fun onLongPressEnd() {
        holding = false
    }

    fun onInterrupted() {
        holding = false
        autoPlaying = false
    }

    fun onLeftPage() {
        holding = false
        autoPlaying = false
        autoPlayedForVisit = false
    }
}

/**
 * 实况图的点按：点一下切换顶栏，连点两下放大。按住 [LiveHoldMillis] 且手指没离开、
 * 也没滑过触摸阈值，才回调 [onPlay]；一松手或一开始滑就 [onStop]。
 */
internal suspend fun PointerInputScope.detectLivePress(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var moved = false
        var released = false
        val held = withTimeoutOrNull(LiveHoldMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    released = true
                    break
                }
                if ((change.position - down.position).getDistance() > slop) {
                    moved = true
                    break
                }
            }
        }
        if (released && !moved) {
            // 很快抬起来的是点击。两次落在双击间隔里，交给放大。
            val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                val next = awaitFirstDown(requireUnconsumed = false)
                next.position
            }
            if (second != null) onDoubleTap(second) else onTap()
            return@awaitEachGesture
        }
        if (held != null) return@awaitEachGesture
        onPlay()
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            onStop()
        }
    }
}

/** 看图页打开时顺手下的静图和实况视频，按地址哈希落在应用缓存里，清缓存时一起删。 */
internal fun mediaCacheDir(context: Context): File =
    File(context.cacheDir, "live").apply { mkdirs() }

internal fun mediaCacheFile(context: Context, url: String, ext: String): File {
    val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
    val name = digest.joinToString("") { "%02x".format(it) }
    return File(mediaCacheDir(context), "$name.$ext")
}

private fun writeAtomically(dest: File, bytes: ByteArray): File? {
    if (bytes.isEmpty()) return null
    val tmp = File(dest.parentFile, dest.name + ".part")
    return runCatching {
        tmp.writeBytes(bytes)
        if (tmp.length() <= 0L) {
            tmp.delete()
            return null
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        dest
    }.getOrElse {
        tmp.delete()
        null
    }
}

/** 缓存静态原图/大图用于超长图分块解码，优先复用 Coil 磁盘缓存，缺失时走 Coolapk 客户端头拉取。 */
internal suspend fun cacheImageFile(context: Context, app: C001apkApp, url: String): File? =
    withContext(Dispatchers.IO) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return@withContext File(url).takeIf { it.exists() && it.length() > 0L }
        }
        val dest = mediaCacheFile(context, url, "img")
        if (dest.length() > 0L) return@withContext dest
        val fromDiskCache = runCatching {
            context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot ->
                val src = snapshot.data.toFile()
                if (src.length() > 0L) {
                    val tmp = File(dest.parentFile, dest.name + ".part")
                    src.copyTo(tmp, overwrite = true)
                    if (!tmp.renameTo(dest)) {
                        tmp.copyTo(dest, overwrite = true)
                        tmp.delete()
                    }
                    dest
                } else {
                    null
                }
            }
        }.getOrNull()
        if (fromDiskCache != null) return@withContext fromDiskCache
        runCatching { app.apiClient.getImageBytes(url) }
            .getOrNull()
            ?.let { writeAtomically(dest, it) }
    }

/**
 * 解析并缓存酷安实况图背后的 MP4 到本地文件：
 * 优先走酷安 livePhoto 重定向接口下载并落盘；失败则回退拉取 JPEG 并提取内嵌 Motion Photo 视频。
 */
internal suspend fun cacheLivePhotoVideo(
    context: Context,
    app: C001apkApp,
    imageUrl: String,
): File? {
    val dest = mediaCacheFile(context, imageUrl, "mp4")
    if (dest.length() > 0L) return dest

    val remoteVideoUrl = app.repository.livePhotoVideoUrl(imageUrl)
    if (!remoteVideoUrl.isNullOrBlank()) {
        val cached = withContext(Dispatchers.IO) {
            runCatching { app.apiClient.getImageBytes(remoteVideoUrl) }
                .getOrNull()
                ?.let { writeAtomically(dest, it) }
        }
        if (cached != null) return cached
    }

    return withContext(Dispatchers.IO) {
        runCatching {
            val tmpImg = File(dest.parentFile, dest.name + ".src.jpg")
            val tmpMp4 = File(dest.parentFile, dest.name + ".part")
            try {
                tmpImg.writeBytes(app.apiClient.getImageBytes(imageUrl))
                val extracted = extractEmbeddedVideo(tmpImg, tmpMp4) ?: return@runCatching null
                if (extracted.length() <= 0L) return@runCatching null
                if (!extracted.renameTo(dest)) {
                    extracted.copyTo(dest, overwrite = true)
                    extracted.delete()
                }
                dest
            } finally {
                tmpImg.delete()
                if (tmpMp4.exists()) tmpMp4.delete()
            }
        }.getOrNull()
    }
}

/**
 * 实况图 TextureView 浮层（直接取自 Cobo COUI）：
 * 跟随视觉层一起缩放与拖拽，自身不抢触摸，按静态封面尺寸居中裁切并支持逐帧淡入淡出。
 */
@Composable
internal fun BoxScope.FittedVideoTextureOverlay(
    player: ExoPlayer,
    alpha: Float,
    rest: ComposeSize,
    box: IntSize,
    scale: Float,
    offset: Offset,
) {
    val density = LocalDensity.current
    val fitted = if (rest.width > 0f && rest.height > 0f && box != IntSize.Zero) {
        Modifier
            .requiredSize(
                with(density) { rest.width.toDp() },
                with(density) { rest.height.toDp() },
            )
            .align(Alignment.Center)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .clipToBounds()
    } else {
        Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }
    }
    AndroidView(
        factory = { ctx ->
            livePlayerView(ctx, player).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = fitted,
        update = { view ->
            view.player = player
            val format = player.videoFormat
            if (format != null && format.height > 0) {
                val turned = format.rotationDegrees % 180 != 0
                view.findViewById<AspectRatioFrameLayout>(
                    androidx.media3.ui.R.id.exo_content_frame,
                )?.setAspectRatio(
                    if (turned) format.height.toFloat() / format.width
                    else format.width.toFloat() / format.height,
                )
            }
        },
    )
}

/**
 * 实况播放器视图：走布局里指定的 TextureView，透明度才能逐帧平滑变化，且默认藏住控件避免闪烁。
 */
private fun livePlayerView(context: Context, player: ExoPlayer): PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.live_photo_player, null) as PlayerView).apply {
        useController = false
        controllerAutoShow = false
        setControllerShowTimeoutMs(0)
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        hideController()
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        setEnableComposeSurfaceSyncWorkaround(true)
        isClickable = false
        isFocusable = false
        this.player = player
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                hideController()
            }
        })
    }
