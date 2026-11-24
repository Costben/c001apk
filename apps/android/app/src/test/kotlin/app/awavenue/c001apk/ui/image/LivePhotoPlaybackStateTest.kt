package app.awavenue.c001apk.ui.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePhotoPlaybackStateTest {

    @Test
    fun autoPlayPlaysOnceEndsAndRequiresLongPressAfterwards() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)
        assertTrue(state.isPlaying)
        assertTrue(state.autoPlayedForVisit)

        assertFalse(state.tryAutoPlay(enabled = true))

        state.onPlaybackEnded()
        assertFalse(state.autoPlaying)
        assertFalse(state.isPlaying)
        assertTrue(state.autoPlayedForVisit)
        assertFalse(state.tryAutoPlay(enabled = true))

        state.onLongPressStart()
        assertTrue(state.holding)
        assertTrue(state.isPlaying)
        assertEquals(1, state.holdSession)

        state.onLongPressEnd()
        assertFalse(state.holding)
        assertFalse(state.isPlaying)
    }

    @Test
    fun leavingAndReturningResetsAutoPlayForNewSwipeVisit() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        state.onPlaybackEnded()
        assertFalse(state.tryAutoPlay(enabled = true))

        state.onLeftPage()
        assertFalse(state.autoPlayedForVisit)
        assertFalse(state.isPlaying)

        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)
        assertTrue(state.isPlaying)
    }

    @Test
    fun disabledAutoPlayNeverAutoPlaysButAllowsLongPress() {
        val state = LivePhotoPlaybackState()

        assertFalse(state.tryAutoPlay(enabled = false))
        assertFalse(state.isPlaying)
        assertFalse(state.autoPlayedForVisit)

        state.onLongPressStart()
        assertTrue(state.holding)
        assertTrue(state.isPlaying)

        state.onLongPressEnd()
        assertFalse(state.isPlaying)
    }

    @Test
    fun longPressDuringAutoPlayTransfersControlToHold() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        assertTrue(state.autoPlaying)

        state.onLongPressStart()
        assertFalse(state.autoPlaying)
        assertTrue(state.holding)
        assertTrue(state.isPlaying)
        assertEquals(1, state.holdSession)

        state.onLongPressEnd()
        assertFalse(state.isPlaying)
    }

    @Test
    fun interruptionStopsPlaybackWithoutResettingVisitFlag() {
        val state = LivePhotoPlaybackState()

        assertTrue(state.tryAutoPlay(enabled = true))
        state.onInterrupted()

        assertFalse(state.isPlaying)
        assertFalse(state.autoPlaying)
        assertFalse(state.holding)
        assertTrue(state.autoPlayedForVisit)
        assertFalse(state.tryAutoPlay(enabled = true))
    }
}
