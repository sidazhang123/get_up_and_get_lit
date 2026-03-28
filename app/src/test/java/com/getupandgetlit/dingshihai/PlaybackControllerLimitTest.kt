package com.getupandgetlit.dingshihai

import com.getupandgetlit.dingshihai.domain.player.computeMaxPlaybackDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackControllerLimitTest {
    @Test
    fun `returns null for zero minutes`() {
        assertNull(computeMaxPlaybackDurationMs(0))
    }

    @Test
    fun `returns null for negative minutes`() {
        assertNull(computeMaxPlaybackDurationMs(-3))
    }

    @Test
    fun `converts minutes to milliseconds`() {
        assertEquals(180_000L, computeMaxPlaybackDurationMs(3))
    }
}
