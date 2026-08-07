package app.blockreels.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * These encode the *intent* of the Instagram detector against plausible view ids. They
 * will need updating alongside the real ids once dump mode has been used on a device —
 * at which point they become the regression suite that stops a repair to one surface from
 * quietly breaking another.
 */
class InstagramDetectorTest {

    private val detector = InstagramDetector()

    // --- the ones that must never be blocked ---------------------------------------

    @Test
    fun `allows a direct message thread`() {
        // An explicit ALLOW rather than merely "no opinion" — DMs are the surface this
        // whole app is designed not to touch, so they get a positive match of their own.
        val detection = detector.detect(signals("direct_thread_toggle", "row_thread_composer"))
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    /**
     * The trap: Instagram's `reel_` prefix means Stories, not Reels. A detector that
     * matched on the substring "reel" would block this and allow the actual Reels player.
     */
    @Test
    fun `allows the stories viewer despite its reel-prefixed ids`() {
        val detection = detector.detect(signals("reel_viewer_texture_view", "reel_viewer_progress_bar"))
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    @Test
    fun `allows stories opened from the tray on top of the feed`() {
        // Both surfaces are momentarily present during the transition; the allow-rule wins.
        val detection = detector.detect(
            signals("reel_viewer_texture_view", "feed_recycler_view", selected = setOf("feed_tab")),
        )
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    @Test
    fun `allows a profile`() {
        val detection = detector.detect(signals("profile_header_avatar", "user_detail_header"))
        assertEquals(Verdict.ALLOW, detection?.verdict)
    }

    // --- the ones that must be blocked ----------------------------------------------

    @Test
    fun `blocks the reels player`() {
        val detection = detector.detect(signals("clips_viewer_view_pager", "clips_video_container"))
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    @Test
    fun `blocks the reels tab`() {
        val detection = detector.detect(signals("feed_tab", "clips_tab", selected = setOf("clips_tab")))
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    @Test
    fun `blocks explore`() {
        val detection = detector.detect(signals("discover_recycler_view", selected = setOf("explore_tab")))
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    @Test
    fun `blocks the home feed`() {
        val detection = detector.detect(signals("feed_recycler_view", "feed_tab", selected = setOf("feed_tab")))
        assertEquals(Verdict.BLOCK, detection?.verdict)
    }

    /** A single post opened from a link is not the infinite feed. */
    @Test
    fun `allows a single post when no feed tab is selected`() {
        assertNull(detector.detect(signals("row_feed_photo_imageview")))
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
