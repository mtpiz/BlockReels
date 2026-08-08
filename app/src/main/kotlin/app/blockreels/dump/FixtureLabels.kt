package app.blockreels.dump

/**
 * The surfaces worth capturing, and the filename each one needs to become a fixture.
 *
 * `FixtureTest` reads the expected verdict off the end of the filename, which means a
 * capture is only useful once it's named exactly right — and renaming a file on a phone is
 * miserable. So the names live here and get picked from a menu instead of typed.
 */
object FixtureLabels {

    data class Label(val app: String, val surface: String, val fileName: String) {
        val display: String get() = "$app · $surface"
        val blocks: Boolean get() = ".BLOCK." in fileName
    }

    val all = listOf(
        Label("Instagram", "Reels", "instagram-reels.BLOCK.txt"),
        Label("Instagram", "Home feed", "instagram-home-feed.BLOCK.txt"),
        Label("Instagram", "Explore", "instagram-explore.BLOCK.txt"),
        Label("Instagram", "DM thread", "instagram-dm-thread.ALLOW.txt"),
        Label("Instagram", "Story", "instagram-story.ALLOW.txt"),
        Label("Instagram", "Profile", "instagram-profile.ALLOW.txt"),
        Label("YouTube", "Shorts player", "youtube-shorts.BLOCK.txt"),
        Label("YouTube", "Long-form watch", "youtube-watch.ALLOW.txt"),
        Label("YouTube", "Home feed", "youtube-home.ALLOW.txt"),
    )

    fun forFileName(name: String): Label? = all.firstOrNull { it.fileName == name }
}
