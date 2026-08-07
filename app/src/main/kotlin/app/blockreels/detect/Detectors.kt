package app.blockreels.detect

/** Every detector the app knows about, keyed by the package it inspects. */
object Detectors {

    val all: List<SurfaceDetector> = listOf(
        YouTubeDetector(),
        InstagramDetector(),
    )

    private val byPackage: Map<String, SurfaceDetector> = all.associateBy { it.packageName }

    val packages: Set<String> = byPackage.keys

    operator fun get(packageName: String): SurfaceDetector? = byPackage[packageName]

    /** Detectors enabled by default: the verified ones, so a fresh install never misfires. */
    val defaultEnabledPackages: Set<String> =
        all.filter { it.verified }.map { it.packageName }.toSet()
}
