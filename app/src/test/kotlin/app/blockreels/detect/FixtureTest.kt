package app.blockreels.detect

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Runs every captured dump in `src/test/resources/fixtures` through its detector and
 * asserts the verdict encoded in the filename: `<name>.BLOCK.txt` or `<name>.ALLOW.txt`.
 *
 * Adding a real screen to the regression suite is therefore just dropping a file in —
 * no test code to write. That is deliberate: the fixtures are captured on a phone,
 * often in a hurry, at the moment an app update has broken something.
 *
 * ALLOW means "not blocked", covering both an explicit allow-rule and no detector opinion
 * at all. Only BLOCK is an assertion about a specific rule firing.
 */
class FixtureTest {

    @Test
    fun `every fixture gets its expected verdict`() {
        val fixtures = fixtureDir().listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
        check(fixtures.isNotEmpty()) { "no fixtures found in ${fixtureDir()}" }

        fixtures.forEach { file ->
            val expected = file.name.substringAfterLast('.', "").let {
                file.name.removeSuffix(".txt").substringAfterLast('.')
            }
            val signals = DumpParser.parse(file.readText())
            val detector = Detectors[signals.packageName]

            assertNotNull(
                "${file.name}: no detector for package '${signals.packageName}' — is the " +
                    "dump header intact?",
                detector,
            )

            val verdict = detector!!.detect(signals)?.verdict
            when (expected) {
                "BLOCK" -> assertEquals("${file.name} should be blocked", Verdict.BLOCK, verdict)
                "ALLOW" -> assertNotEquals("${file.name} must not be blocked", Verdict.BLOCK, verdict)
                else -> error("${file.name}: name must end .BLOCK.txt or .ALLOW.txt")
            }
        }
    }

    @Test
    fun `parser recovers ids, selection and package from a dump`() {
        val signals = DumpParser.parse(
            """
            BlockReels dump — com.instagram.android
            verdict: none
            ====

            FrameLayout  #content  [0,0][1080,2400]
              ViewPager2  #clips_viewer_view_pager  [0,0][1080,2400]
              TabLayout  #tab_bar  [0,2200][1080,2400]
                ImageView  #clips_tab  desc="Reels"  SELECTED  [400,2200][500,2400]
            """.trimIndent(),
        )

        assertEquals("com.instagram.android", signals.packageName)
        assertEquals(setOf("clips_tab"), signals.selectedViewIds)
        assertEquals(true, signals.hasId("clips_viewer"))
        assertEquals(true, signals.hasDescription("reels"))
    }

    /** Quoted content is arbitrary and must not be mistaken for structure. */
    @Test
    fun `parser is not fooled by ids or SELECTED inside quoted text`() {
        val signals = DumpParser.parse(
            """
            BlockReels dump — com.google.android.youtube
            TextView  #comment_body  text="check out #reel_progress_bar SELECTED lol"  [0,0][10,10]
            """.trimIndent(),
        )

        assertEquals(setOf("comment_body"), signals.viewIds)
        assertEquals(emptySet<String>(), signals.selectedViewIds)
    }

    private fun fixtureDir(): File {
        val url = requireNotNull(javaClass.classLoader.getResource("fixtures")) {
            "fixtures directory missing from test resources"
        }
        return File(url.toURI())
    }
}
