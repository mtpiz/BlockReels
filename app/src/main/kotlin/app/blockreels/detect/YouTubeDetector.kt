package app.blockreels.detect

/**
 * Blocks the fullscreen YouTube Shorts player, and nothing else.
 *
 * The Shorts *shelf* on the home feed is deliberately left alone — the shelf carries no
 * player progress bar, so the signal below cannot match it. Long-form watch, search,
 * subscriptions and library are all untouched.
 *
 * `reel_progress_bar` is the confirmed signal: YouTube's own name for the segmented
 * progress bar that only exists while a Short is playing fullscreen. (Note YouTube's
 * "reel" here means Shorts — unlike Instagram, where "reel" means Stories. See
 * [InstagramDetector].)
 */
class YouTubeDetector : SurfaceDetector {

    override val packageName = "com.google.android.youtube"
    override val label = "YouTube Shorts"
    override val verified = true

    override fun detect(signals: ScreenSignals): Detection? {
        if (signals.hasAnyId(*SHORTS_PLAYER_IDS)) {
            return Detection(
                surface = "YouTube Shorts",
                verdict = Verdict.BLOCK,
                reason = "matched ${signals.viewIds.first { id -> SHORTS_PLAYER_IDS.any { it in id } }}",
            )
        }
        return null
    }

    private companion object {
        /**
         * Confirmed working against YouTube 18.x–20.x. If Shorts stops being caught after
         * an update, dump the Shorts player and the long-form watch page and diff them —
         * the replacement signal is whatever appears in the first and not the second.
         *
         * Candidates seen in the wild but not confirmed here, worth trying in that order:
         * `reel_player_page_container`, `reel_watch_fragment_root`, `reel_recycler`.
         * Be careful with `reel_recycler` specifically — it may also back the home-feed
         * shelf, which would block the home feed as collateral.
         */
        val SHORTS_PLAYER_IDS = arrayOf("reel_progress_bar")
    }
}
