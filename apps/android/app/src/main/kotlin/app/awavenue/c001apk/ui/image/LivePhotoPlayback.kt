package app.awavenue.c001apk.ui.image

import android.content.Context
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.awavenue.c001apk.C001apkApp
import app.awavenue.c001apk.R
import app.awavenue.c001apk.media.LivePhotoSource
import app.awavenue.c001apk.media.extractEmbeddedVideo
import app.awavenue.c001apk.ui.LocalApp
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.IconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.Pause
import io.github.suqi8.coui.kmp.icon.extended.Play
import io.github.suqi8.coui.kmp.icon.extended.VolumeOff
import io.github.suqi8.coui.kmp.icon.extended.VolumeUp
import io.github.suqi8.coui.kmp.theme.COUITheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LivePhotoChrome = Color.Black.copy(alpha = 0.45f)

/** Everything the viewer's live-photo controls and player share for one page. */
@Stable
internal class LivePhotoState {
    var source by mutableStateOf<LivePhotoSource?>(null)
    var resolving by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var muted by mutableStateOf(false)

    /** Bumped by the replay button; the page turns it into a seek and a play. */
    var playRequest by mutableIntStateOf(0)
}

/**
 * Resolves the MP4 behind [imageUrl]: Coolapk hands out a short-lived redirect for most live
 * photos, while some uploads keep the video inside the JPEG itself.
 */
private suspend fun resolveSource(
    context: Context,
    app: C001apkApp,
    imageUrl: String,
): LivePhotoSource? {
    app.repository.livePhotoVideoUrl(imageUrl)?.let { return LivePhotoSource.Remote(it) }
    return withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "livephoto").apply { mkdirs() }
            val key = imageUrl.hashCode().toUInt().toString(16)
            val image = File(dir, "$key.jpg")
            image.writeBytes(app.apiClient.getImageBytes(imageUrl))
            extractEmbeddedVideo(image, File(dir, "$key.mp4"))
                ?.let(LivePhotoSource::Local)
                ?: run {
                    image.delete()
                    null
                }
        }.getOrNull()
    }
}

/**
 * Owns the ExoPlayer for one page. It starts playing once the page settles, pauses when the page
 * is left or zoomed, and is released with the page.
 */
@Composable
internal fun rememberLivePhotoPlayer(
    url: String,
    isSettled: Boolean,
    zoomed: Boolean,
    state: LivePhotoState,
): ExoPlayer? {
    val context = LocalContext.current
    val app = LocalApp.current

    LaunchedEffect(url, isSettled) {
        if (!isSettled || state.source != null || state.resolving) return@LaunchedEffect
        state.resolving = true
        state.source = resolveSource(context, app, url)
        state.resolving = false
        if (state.source == null) state.failed = true
    }

    val player = remember(state.source) {
        state.source?.let {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                volume = if (state.muted) 0f else 1f
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    state.playing = false
                    player?.pause()
                    player?.seekTo(0)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                state.failed = true
                state.playing = false
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        val source = state.source ?: return@LaunchedEffect
        p.setMediaItem(MediaItem.fromUri(source.uri))
        p.prepare()
    }

    LaunchedEffect(player, state.muted) {
        player?.volume = if (state.muted) 0f else 1f
    }

    LaunchedEffect(player, isSettled) {
        if (!isSettled) {
            player?.pause()
            state.playing = false
        }
    }

    LaunchedEffect(player, zoomed) {
        if (zoomed) {
            player?.pause()
            state.playing = false
        }
    }

    // Each time the page settles, or the video arrives while it is settled, play it once.
    LaunchedEffect(player, state.source, isSettled) {
        val p = player ?: return@LaunchedEffect
        if (isSettled) {
            p.seekTo(0)
            p.play()
            state.playing = true
        }
    }

    LaunchedEffect(player, state.playRequest) {
        val p = player ?: return@LaunchedEffect
        if (state.playRequest == 0) return@LaunchedEffect
        p.seekTo(0)
        p.play()
        state.playing = true
    }

    return player
}

/** The video layer; it sits exactly where the still image is fitted, so the swap is invisible. */
@Composable
internal fun LivePhotoPlayerView(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.live_photo_player, null) as PlayerView).apply {
                useController = false
                isClickable = false
                isFocusable = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}

/** Play/pause and sound for the live photo on screen. */
@Composable
internal fun LivePhotoControls(
    state: LivePhotoState,
    modifier: Modifier = Modifier,
    onTogglePlayback: () -> Unit,
    onToggleMute: () -> Unit,
) {
    val ready = state.source != null
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(LivePhotoChrome)
                .clickable(enabled = ready, onClick = onTogglePlayback)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                if (state.playing) COUIIcons.Pause else COUIIcons.Play,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Text(
                when {
                    state.failed -> "实况不可用"
                    !ready -> "实况加载中"
                    state.playing -> "暂停"
                    else -> "实况"
                },
                color = Color.White,
                style = COUITheme.textStyles.footnote1,
            )
        }
        if (ready) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(LivePhotoChrome)
                    .size(36.dp),
            ) {
                Icon(
                    if (state.muted) COUIIcons.VolumeOff else COUIIcons.VolumeUp,
                    contentDescription = if (state.muted) "打开实况声音" else "关闭实况声音",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
