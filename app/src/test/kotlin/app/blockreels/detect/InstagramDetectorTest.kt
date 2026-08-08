package app.blockreels.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-level cases for the Instagram rules. The ids here are taken from real captures; the
 * captures themselves are replayed end-to-end by [FixtureTest].
 */
class InstagramDetectorTest {

    private val detector = InstagramDetector()

    // --- the ones that must never be blocked ---------------------------------------

    /**
     * The trap: Instagram's `reel_` prefix means Stories, not Reels. A detector matching
     * the substring "reel" would block this and allow the actual Reels player.
     */
    @Test
    fun `allows the stories viewer despite its reel-prefixed ids`() {
        val detection = detector.detect(signals("reel_viewer_root", "reel_viewer_header"))
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    /**
     * The regression that matters most. While a Story is open, Instagram keeps the Reels
     * pager in the tree parked offscreen — so the scanner's visibility filter is the only
     * thing standing between this and a blue rectangle over your friends' Stories.
     */
    @Test
    fun `allows a story even though the reels pager is loaded offscreen`() {
        // clips_viewer_view_pager deliberately absent: NodeScanner drops it as offscreen.
        val detection = detector.detect(
            signals("reel_viewer_root", "reel_viewer_title", "swipeable_tab_view_pager"),
        )
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    @Test
    fun `allows a direct message thread`() {
        val detection = detector.detect(signals("direct_thread_toggle", "row_thread_composer"))
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    /** A profile shows no bottom nav, so nothing claims it. */
    @Test
    fun `allows a profile`() {
        assertNull(
            detector.detect(
                signals("action_bar_title", "row_feed_photo_profile_name", "carousel_viewpager"),
            ),
        )
    }

    /** DMs are allowed by construction: direct_tab is not a block signal. */
    @Test
    fun `allows the direct inbox`() {
        assertNotEquals(
            Verdict.BLOCK,
            detector.detect(signals("direct_tab", "feed_tab", selected = setOf("direct_tab")))?.verdict,
        )
    }

    // --- the ones that must be blocked ----------------------------------------------

    @Test
    fun `blocks the reels player`() {
        val detection = detector.detect(
            signals("clips_viewer_view_pager", "clips_video_container", "clips_ufi_component"),
        )
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    @Test
    fun `blocks the reels tab`() {
        val detection = detector.detect(
            signals("feed_tab", "clips_tab", "search_tab", selected = setOf("clips_tab")),
        )
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    /** Explore is the search tab — there is no explore_tab. */
    @Test
    fun `blocks explore under the search tab`() {
        val detection = detector.detect(
            signals("action_bar_search_edit_text", "pill_bar_rv", selected = setOf("search_tab")),
        )
        assertEquals(Verdict.BLOCK, detection?.verdict)
        assertEquals("Instagram Explore", detection?.surface)
    }

    // --- the home feed is judged by depth, not treated as forbidden ------------------

    /**
     * The regression behind this: blocking the feed outright made Instagram unusable,
     * because it opens on the feed and the block screen appeared before you could reach
     * messages. A screen covered the instant it opens cannot be navigated out of.
     */
    @Test
    fun `allows the top of the home feed so the app stays navigable`() {
        assertNull(
            detector.detect(signals("list", "row_feed_profile_header", selected = setOf("feed_tab"))),
        )
    }

    @Test
    fun `allows the feed within the post limit`() {
        val signals = signals("list", selected = setOf("feed_tab")).copy(scrollIndex = 6)
        assertNull(detector.detect(signals, DetectorConfig(feedPostLimit = 10)))
    }

    @Test
    fun `blocks the feed past the post limit`() {
        val signals = signals("list", selected = setOf("feed_tab")).copy(scrollIndex = 24)
        val detection = detector.detect(signals, DetectorConfig(feedPostLimit = 10))
        assertEquals(Verdict.BLOCK, detection?.verdict)
        assertEquals("Instagram feed", detection?.surface)
    }

    /** Scrolling back up must release the block, or the block screen is a trap. */
    @Test
    fun `releases the feed when scrolled back toward the top`() {
        val config = DetectorConfig(feedPostLimit = 10)
        val deep = signals("list", selected = setOf("feed_tab")).copy(scrollIndex = 40)
        assertEquals(Verdict.BLOCK, detector.detect(deep, config)?.verdict)

        val backUp = deep.copy(scrollIndex = 2)
        assertNull(detector.detect(backUp, config))
    }

    @Test
    fun `respects a custom post limit`() {
        val signals = signals("list", selected = setOf("feed_tab")).copy(scrollIndex = 7)
        assertNull(detector.detect(signals, DetectorConfig(feedPostLimit = 20)))
        assertEquals(
            Verdict.BLOCK,
            detector.detect(signals, DetectorConfig(feedPostLimit = 5))?.verdict,
        )
    }

    @Test
    fun `has no opinion about an unrecognised screen`() {
        assertNull(detector.detect(signals("some_unknown_container")))
    }

    private fun signals(vararg ids: String, selected: Set<String> = emptySet()) = ScreenSignals(
        packageName = "com.instagram.android",
        viewIds = ids.toSet() + selected,
        descriptions = emptySet(),
        selectedViewIds = selected,
        nodesScanned = ids.size,
        truncated = false,
    )
}
