package app.blockreels.detect

/**
 * Reduces Instagram to messaging and Stories: DMs, friends' Stories and profiles you
 * navigate to on purpose stay usable; the Reels player, the infinite home feed and
 * Explore do not.
 *
 * ## The naming trap
 *
 * Instagram's resource ids invert the words you'd expect, because the codebase predates
 * the Reels product:
 *
 *  - `reel_*`  → **Stories** (from the original story-tray "reel") → allow
 *  - `clips_*` → **Reels** (the short-form product) → block
 *
 * A detector that greps for the substring `"reel"` therefore blocks Stories and allows
 * Reels — exactly backwards. Every id below is grouped by *product*, not by name.
 *
 * ## Status: UNVERIFIED
 *
 * Only `clips_tab` and `feed_tab` are confirmed from working third-party code. Everything
 * else is a hypothesis and this detector ships disabled ([verified] = false). Enable dump
 * mode, capture each surface named below, diff them, and correct the constants before
 * turning it on. Guessing here is not a shortcut — a wrong allow-id silently covers your
 * DMs with a blue rectangle.
 */
class InstagramDetector : SurfaceDetector {

    override val packageName = "com.instagram.android"
    override val label = "Instagram Reels, feed & Explore"
    override val verified = false

    override fun detect(signals: ScreenSignals): Detection? {
        // Allow-rules run first and win outright. See SurfaceDetector.detect.
        allowedSurface(signals)?.let { return it }

        if (signals.hasAnyId(*REELS_IDS) || signals.hasSelectedId("clips_tab")) {
            return Detection("Instagram Reels", Verdict.BLOCK, "reels player")
        }
        if (signals.hasSelectedId("explore_tab") || signals.hasAnyId(*EXPLORE_IDS)) {
            return Detection("Instagram Explore", Verdict.BLOCK, "explore grid")
        }
        if (signals.hasSelectedId("feed_tab") && signals.hasAnyId(*HOME_FEED_IDS)) {
            return Detection("Instagram feed", Verdict.BLOCK, "home feed")
        }
        return null
    }

    private fun allowedSurface(signals: ScreenSignals): Detection? = when {
        signals.hasAnyId(*DIRECT_MESSAGE_IDS) ->
            Detection("Instagram DMs", Verdict.ALLOW, "direct messages")

        // Must be checked before the feed rule: the Stories viewer is launched from the
        // tray at the top of the home feed, so both can be present during the transition.
        signals.hasAnyId(*STORIES_IDS) ->
            Detection("Instagram Stories", Verdict.ALLOW, "stories viewer")

        signals.hasAnyId(*PROFILE_IDS) ->
            Detection("Instagram profile", Verdict.ALLOW, "profile")

        else -> null
    }

    private companion object {
        // --- allow -------------------------------------------------------------------
        val DIRECT_MESSAGE_IDS = arrayOf(
            "direct_thread", "thread_message", "direct_inbox", "row_thread_composer",
        )

        /** Stories. Remember: Instagram's `reel_` prefix means Stories, not Reels. */
        val STORIES_IDS = arrayOf(
            "reel_viewer", "reel_progress", "story_progress", "reel_header",
        )

        val PROFILE_IDS = arrayOf(
            "profile_header", "user_detail_header",
        )

        // --- block -------------------------------------------------------------------
        val REELS_IDS = arrayOf(
            "clips_viewer", "clips_video_container", "clips_swipe",
        )

        val EXPLORE_IDS = arrayOf(
            "discover_recycler", "explore_grid",
        )

        /**
         * Paired with a selected `feed_tab` — on its own a feed row id can appear inside
         * a single-post view opened from a link, which should stay allowed.
         */
        val HOME_FEED_IDS = arrayOf(
            "feed_recycler", "main_feed_recycler", "row_feed_",
        )
    }
}
