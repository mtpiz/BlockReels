package app.blockreels.detect

/**
 * A flattened, Android-free snapshot of what is currently on screen.
 *
 * Separating the (expensive, Android-coupled) tree walk from the (cheap, pure) decision
 * means every detector is a plain function over data and can be unit tested on the JVM
 * without an emulator. Given that detectors are the part that breaks whenever Instagram
 * or YouTube ships an update, being able to test them from a saved dump matters more here
 * than in most codebases.
 *
 * @param viewIds short view ids, lowercased — `com.instagram.android:id/clips_tab`
 *   arrives here as `clips_tab`.
 * @param truncated true if the scan hit its node budget before exhausting the tree, so
 *   absence of an id is not proof it isn't on screen. Detectors must not treat a negative
 *   match as authoritative when this is set.
 */
data class ScreenSignals(
    val packageName: String,
    val viewIds: Set<String>,
    val descriptions: Set<String>,
    val selectedViewIds: Set<String>,
    val nodesScanned: Int,
    val truncated: Boolean,
    /**
     * First visible adapter position of the list last scrolled, or null when nothing has
     * been scrolled since the screen appeared. This is how far down a feed you are — the
     * difference between reading the few posts at the top and falling into the infinite
     * part below them.
     */
    val scrollIndex: Int? = null,
) {
    fun hasId(fragment: String): Boolean = viewIds.any { fragment in it }

    fun hasAnyId(vararg fragments: String): Boolean = fragments.any { hasId(it) }

    fun hasSelectedId(fragment: String): Boolean = selectedViewIds.any { fragment in it }

    fun hasDescription(fragment: String): Boolean = descriptions.any { fragment in it }

    companion object {
        fun empty(packageName: String) = ScreenSignals(
            packageName = packageName,
            viewIds = emptySet(),
            descriptions = emptySet(),
            selectedViewIds = emptySet(),
            nodesScanned = 0,
            truncated = false,
            scrollIndex = null,
        )
    }
}
