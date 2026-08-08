package app.blockreels.detect

/**
 * Reduces Instagram to messaging and Stories: DMs, friends' Stories and profiles you
 * navigate to on purpose stay usable; the Reels player, the infinite home feed and
 * Explore do not.
 *
 * ## The naming trap, now confirmed
 *
 * Instagram's resource ids invert the words you'd expect, because the codebase predates
 * the Reels product. Captured from a real device:
 *
 *  - Stories → `reel_viewer_root`, `reel_viewer_header`, `reel_viewer_title` → allow
 *  - Reels   → `clips_viewer_view_pager`, `clips_video_container`            → block
 *
 * A detector matching the substring `"reel"` would allow Reels and block Stories, exactly
 * backwards. Everything below is grouped by product, never by name.
 *
 * ## Visibility is not optional
 *
 * While a Story is open, `clips_viewer_view_pager` is *still in the tree*, parked offscreen
 * at `[1440,127][1440,3064]` — Instagram preloads the Reels page of its pager. Matching it
 * without checking visibility blocks Stories. [app.blockreels.service.NodeScanner] drops
 * offscreen subtrees for exactly this reason, and these rules depend on that.
 *
 * ## Which tab is which
 *
 * Explore lives under `search_tab`, not an `explore_tab` — the bottom nav is
 * `feed_tab · clips_tab · search_tab · direct_tab · profile_tab`, and the selected one is
 * the cleanest signal for the three scrolling surfaces. Screens reached by navigating
 * deliberately (a profile, a Story, a DM thread) show no bottom nav at all, so they fall
 * through to "no opinion" and stay allowed.
 */
class InstagramDetector : SurfaceDetector {

    override val packageName = "com.instagram.android"
    override val label = "Instagram Reels, feed & Explore"
    override val verified = true

    override fun detect(signals: ScreenSignals, config: DetectorConfig): Detection? {
        // Allow-rules run first and win outright. See SurfaceDetector.detect.
        allowedSurface(signals)?.let { return it }

        if (signals.hasAnyId(*REELS_PLAYER_IDS)) {
            return Detection("Instagram Reels", Verdict.BLOCK, "reels player")
        }
        if (signals.hasSelectedId("clips_tab")) {
            return Detection("Instagram Reels", Verdict.BLOCK, "reels tab selected")
        }
        if (signals.hasSelectedId("search_tab")) {
            return Detection("Instagram Explore", Verdict.BLOCK, "explore tab selected")
        }
        if (signals.hasSelectedId("feed_tab")) return homeFeed(signals, config)
        return null
    }

    /**
     * The home feed is judged by *position*, not treated as one forbidden place.
     *
     * Blocking it outright made Instagram unusable: it opens on the feed, so the block
     * screen appeared before you could reach messages. Worse, it's a trap — you can't
     * navigate out of a screen that's covered the moment it appears.
     *
     * Position also matches what's actually wrong with the feed. The first handful of
     * posts are the people you follow; past that it's recommendations and the infinite
     * tail. So the top stays open and the depth is what's blocked.
     *
     * Crucially this reads *current* depth rather than a cumulative counter: scroll back
     * up and you're free again. That's what keeps it from being a trap, and it's why the
     * block screen offers to return you to the top.
     */
    private fun homeFeed(signals: ScreenSignals, config: DetectorConfig): Detection? {
        // Null means nothing has scrolled since this screen appeared — you're at the top,
        // which is exactly the case that must stay open so DMs remain reachable.
        val depth = signals.scrollIndex ?: return null
        if (depth <= config.feedPostLimit) return null
        return Detection(
            surface = "Instagram feed",
            verdict = Verdict.BLOCK,
            reason = "scrolled to post $depth, past the ${config.feedPostLimit} allowed",
        )
    }

    private fun allowedSurface(signals: ScreenSignals): Detection? = when {
        // Checked before anything else: the Stories viewer is launched from the tray on top
        // of the home feed, so a feed signal can still be around mid-transition.
        signals.hasAnyId(*STORIES_IDS) ->
            Detection("Instagram Stories", Verdict.ALLOW, "stories viewer")

        signals.hasAnyId(*DIRECT_MESSAGE_IDS) ->
            Detection("Instagram DMs", Verdict.ALLOW, "direct messages")

        else -> null
    }

    private companion object {
        /** Confirmed on-device. Instagram's `reel_` prefix means Stories. */
        val STORIES_IDS = arrayOf(
            "reel_viewer_root", "reel_viewer_header", "reel_viewer_title",
            "reel_viewer_text_container",
        )

        /**
         * Not yet confirmed against a capture — the DM dump came back as the notification
         * shade. Defensive only: a DM thread shows no bottom nav, and the inbox selects
         * `direct_tab` which no block-rule looks at, so messaging is already allowed by
         * construction rather than by this list.
         */
        val DIRECT_MESSAGE_IDS = arrayOf(
            "direct_thread", "thread_message", "row_thread_composer", "direct_inbox",
        )

        /**
         * Confirmed on-device, and present *only* when the player is actually on screen.
         * Both appear offscreen on every other surface, so these are safe solely because
         * NodeScanner filters by visibility.
         */
        val REELS_PLAYER_IDS = arrayOf(
            "clips_viewer_view_pager", "clips_video_container",
        )
    }
}
