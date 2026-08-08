package app.blockreels.service

import android.view.accessibility.AccessibilityNodeInfo
import app.blockreels.detect.ScreenSignals

/**
 * Flattens a live accessibility tree into [ScreenSignals].
 *
 * Breadth-first on purpose: the ids that fingerprint a surface (tab bars, player
 * containers, fragment roots) sit near the root, while the deep tail of the tree is
 * recycler content that changes on every frame. Going breadth-first means the node budget
 * is spent on the part that identifies the screen rather than on the part that doesn't.
 */
object NodeScanner {

    /**
     * Deliberately small. This runs on the main thread on every accepted accessibility
     * event, and a scrolling video feed emits a great many of those — an unbounded walk
     * of a RecyclerView is the difference between idle and a warm phone.
     */
    const val DEFAULT_MAX_NODES = 220

    fun scan(
        root: AccessibilityNodeInfo,
        packageName: String,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): ScreenSignals {
        val viewIds = HashSet<String>()
        val descriptions = HashSet<String>()
        val selectedViewIds = HashSet<String>()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var scanned = 0

        while (queue.isNotEmpty() && scanned < maxNodes) {
            val node = queue.removeFirst()
            scanned++

            // Offscreen subtrees are skipped entirely, and this is load-bearing rather than
            // an optimisation. Instagram parks the Reels page of its pager just off the
            // right edge, so `clips_viewer_view_pager` is present in the tree the whole time
            // you are watching a Story — matching it would block Stories, which is the one
            // thing this app must never do.
            //
            // Pruning can in principle drop a visible child of an invisible parent. That
            // costs a missed block, never a wrong one, which is the direction this whole
            // design errs in anyway.
            if (!node.isVisibleToUser) continue

            node.viewIdResourceName?.let { raw ->
                val id = raw.substringAfterLast('/').lowercase()
                viewIds += id
                if (node.isSelected) selectedViewIds += id
            }
            node.contentDescription?.let { descriptions += it.toString().lowercase() }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::addLast)
            }
        }

        return ScreenSignals(
            packageName = packageName,
            viewIds = viewIds,
            descriptions = descriptions,
            selectedViewIds = selectedViewIds,
            nodesScanned = scanned,
            truncated = queue.isNotEmpty(),
        )
    }
}
