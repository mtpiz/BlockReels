package app.blockreels.dump

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Renders a live accessibility tree as indented text.
 *
 * This is the tool the whole project rests on. The view ids that identify Reels, Stories
 * and DMs are private implementation details of someone else's app — they aren't
 * documented anywhere and they drift between releases, so the only way to write a detector
 * (or repair one after an update) is to capture the surface you care about, capture the
 * surface next to it, and diff the two.
 */
object NodeDumper {

    /** Generous — a dump is a deliberate, one-off action, unlike the hot detection path. */
    private const val MAX_NODES = 4000

    fun dump(root: AccessibilityNodeInfo, header: String): String = buildString {
        appendLine(header)
        appendLine("=".repeat(header.length))
        appendLine()
        var count = 0
        val bounds = Rect()

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (count >= MAX_NODES) return
            count++

            val indent = "  ".repeat(depth)
            node.getBoundsInScreen(bounds)

            append(indent)
            append(node.className?.toString()?.substringAfterLast('.') ?: "?")

            node.viewIdResourceName?.let { append("  #").append(it.substringAfterLast('/')) }
            node.text?.takeIf { it.isNotBlank() }?.let { append("  text=\"").append(it).append('"') }
            node.contentDescription?.takeIf { it.isNotBlank() }
                ?.let { append("  desc=\"").append(it).append('"') }

            // isSelected is what separates a focused tab from a merely present one, and it
            // is exactly what `adb shell uiautomator dump` drops — the reason to build this
            // in-app rather than lean on adb.
            if (node.isSelected) append("  SELECTED")
            if (node.isChecked) append("  CHECKED")
            if (!node.isVisibleToUser) append("  (offscreen)")
            append("  ").append(bounds.toShortString())
            appendLine()

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { walk(it, depth + 1) }
            }
        }

        walk(root, 0)

        appendLine()
        appendLine("-- $count nodes" + if (count >= MAX_NODES) " (truncated)" else "")
    }
}
