package app.blockreels.detect

/**
 * Reconstructs [ScreenSignals] from the text a dump file contains.
 *
 * This closes the loop that makes detector work possible without a phone in hand: capture
 * a surface once on-device, commit the dump as a fixture, and from then on the detector
 * can be changed and re-checked offline. It also means a repair to one surface can't
 * quietly break another, because every previously captured screen is still asserted on.
 *
 * Kept free of Android imports for the same reason as the detectors themselves.
 */
object DumpParser {

    private val ID = Regex("""#([A-Za-z0-9_./]+)""")
    private val DESC = Regex("""desc="([^"]*)"""")
    private val QUOTED = Regex("""(text|desc)="[^"]*"""")
    private const val HEADER = "BlockReels dump"

    fun parse(dump: String): ScreenSignals {
        var packageName = "unknown"
        val viewIds = HashSet<String>()
        val selected = HashSet<String>()
        val descriptions = HashSet<String>()
        var nodes = 0

        dump.lineSequence().forEach { line ->
            if (line.startsWith(HEADER)) {
                packageName = line.substringAfter('—').trim().ifEmpty { packageName }
                return@forEach
            }

            DESC.find(line)?.let { descriptions += it.groupValues[1].lowercase() }

            // Quoted text and descriptions are user content and can contain anything —
            // including a '#' or the word SELECTED — so match structure only on what's
            // left once they're removed.
            val structure = QUOTED.replace(line, "")
            val id = ID.find(structure)?.groupValues?.get(1) ?: return@forEach

            nodes++
            val short = id.substringAfterLast('/').lowercase()
            viewIds += short
            if ("SELECTED" in structure) selected += short
        }

        return ScreenSignals(
            packageName = packageName,
            viewIds = viewIds,
            descriptions = descriptions,
            selectedViewIds = selected,
            nodesScanned = nodes,
            // A dump is a full capture, so absence of an id here really is absence.
            truncated = "(truncated)" in dump,
        )
    }
}
