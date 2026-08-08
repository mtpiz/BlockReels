package app.blockreels.detect

enum class Verdict { ALLOW, BLOCK }

/**
 * @param surface human-readable name of the screen, shown on the block overlay and in logs.
 * @param reason which signal fired, so a misfire can be diagnosed without a rebuild.
 */
data class Detection(
    val surface: String,
    val verdict: Verdict,
    val reason: String,
)

/**
 * Tunables a detector reads. Kept out of [ScreenSignals] because these are preferences,
 * not observations, and passing them explicitly keeps detectors pure functions.
 */
data class DetectorConfig(
    /**
     * How many posts into the home feed you may go before it's considered doomscrolling.
     * Friends' posts cluster at the top; the tail is the part worth losing.
     */
    val feedPostLimit: Int = 10,
)

interface SurfaceDetector {
    val packageName: String

    /** Display name for the settings UI. */
    val label: String

    /**
     * True once the view ids below have been confirmed against a dump from a real device.
     * Unverified detectors default to off, because a detector built from guessed ids is
     * more likely to cover your DMs than your Reels.
     */
    val verified: Boolean

    /**
     * Returns null when this detector has no opinion — which is the common case and must
     * be treated as "allow", never as "block".
     *
     * Implementations must check allow-surfaces *first*. A false positive on DMs or
     * Stories makes the phone annoying enough to uninstall the blocker; a false negative
     * just means one reel got through. The asymmetry is the whole design.
     */
    fun detect(signals: ScreenSignals, config: DetectorConfig = DetectorConfig()): Detection?
}
