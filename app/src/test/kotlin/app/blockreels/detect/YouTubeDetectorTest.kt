package app.blockreels.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeDetectorTest {

    private val detector = YouTubeDetector()

    @Test
    fun `blocks the fullscreen shorts player`() {
        val detection = detector.detect(
            signals("reel_progress_bar", "reel_recycler", "reel_player_page_container"),
        )
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    @Test
    fun `allows the long-form watch page`() {
        assertNull(
            detector.detect(
                signals("watch_player", "player_control_play_pause_replay_button", "player_overflow_button"),
            ),
        )
    }

    /**
     * The home feed carries Shorts thumbnails but no player, and blocking it would take
     * the whole app down with it. This is the regression that matters most.
     */
    @Test
    fun `allows the home feed even with a shorts shelf on it`() {
        assertNull(
            detector.detect(signals("results", "rich_grid_shelf", "shorts_shelf_title", "pivot_bar")),
        )
    }

    @Test
    fun `allows search and library`() {
        assertNull(detector.detect(signals("search_edit_text", "results")))
        assertNull(detector.detect(signals("library_recycler", "pivot_bar")))
    }

    private fun signals(vararg ids: String) = ScreenSignals(
        packageName = "com.google.android.youtube",
        viewIds = ids.toSet(),
        descriptions = emptySet(),
        selectedViewIds = emptySet(),
        nodesScanned = ids.size,
        truncated = false,
    )
}
