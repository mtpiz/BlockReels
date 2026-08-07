# BlockReels

Blocks short-form feeds without blocking the app around them. Instagram DMs and Stories
stay usable; Reels, the home feed and Explore don't. YouTube long-form stays; Shorts
doesn't.

Unlike an app blocker, which only needs to know *which app* is in the foreground,
BlockReels has to know *which screen inside the app* you're on. It does that by reading
the live accessibility tree and fingerprinting surfaces by their view ids.

Research and design notes: [`docs/content-blocking.md`](docs/content-blocking.md).
Background on AppBlock and Android blocking generally:
[`docs/appblock-research.md`](docs/appblock-research.md).

---

## Status

| Piece | State |
|---|---|
| Node dumper (milestone 0) | written, not yet run on a device |
| YouTube Shorts detector | logic verified by unit tests; `reel_progress_bar` is a signal confirmed by other working blockers |
| Overlay, service, watchdog | written, not yet run on a device |
| Instagram detector | **view ids are unverified guesses** — ships disabled, see below |

**The Android code in this repo has never been compiled.** The environment it was written
in blocks `dl.google.com`, so neither the Android SDK nor the Android Gradle Plugin could
be downloaded and `./gradlew assembleDebug` was never run. Expect to fix some import or
resource errors on the first build.

What *is* verified: the detectors are deliberately pure functions over a
[`ScreenSignals`](app/src/main/kotlin/app/blockreels/detect/ScreenSignals.kt) snapshot with
no Android dependencies, so they were compiled and unit-tested on the JVM — 14 tests, all
passing. That's the logic most likely to be wrong and most annoying to debug on a phone.

## Build and install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Install with **adb**, not by tapping the APK. Android 13+ classifies a file-manager
install as sideloaded and greys out the accessibility toggle; adb installs avoid that. If
you hit it anyway: App info → ⋮ → *Allow restricted settings*.

Then open the app and enable the accessibility service. There's no second permission
prompt — the block screen uses `TYPE_ACCESSIBILITY_OVERLAY`, which an accessibility
service can draw without `SYSTEM_ALERT_WINDOW`.

## Getting Instagram working

The Instagram detector ships **off**, because its view ids are educated guesses and a
wrong *allow* id silently covers your DMs with a blue rectangle. Correcting them is the
first real job:

1. Turn on **Dump mode** in the app. A notification appears with a *Dump screen* action.
2. Open Instagram → Reels. Pull down the shade, tap **Dump**.
3. Repeat for: a DM thread, a friend's Story, the home feed, Explore, a profile.
4. Share the dumps out of the app and diff them.
   The ids present in Reels and absent from Stories *are* your detector.
5. Correct the constants in
   [`InstagramDetector.kt`](app/src/main/kotlin/app/blockreels/detect/InstagramDetector.kt),
   update `InstagramDetectorTest` to match, flip `verified` to `true`.

Dump mode stays in release builds on purpose. When an Instagram update breaks detection,
you want to re-diff on the phone in five minutes, not rebuild from a laptop you don't have.

### The naming trap

Instagram's resource ids invert the words you'd expect, because the code predates the
Reels product:

- `reel_*` → **Stories** → allow
- `clips_*` → **Reels** → block

A detector matching the substring `"reel"` blocks Stories and allows Reels — exactly
backwards. Group ids by product, not by name.

## Layout

```
detect/     Pure Kotlin. ScreenSignals + one detector per app. No Android imports,
            fully unit tested — this is where surface logic lives.
service/    BlockReelsService (accessibility events, throttling, overlay lifecycle,
            watchdog) and NodeScanner (tree -> ScreenSignals).
overlay/    BlockOverlay. The blue screen.
dump/       NodeDumper + DumpStore. The tool everything else depends on.
data/       DataStore settings.
ui/         Compose settings screen.
```

Two invariants worth preserving:

**Allow-rules beat block-rules.** A false positive on DMs makes the phone annoying enough
that you uninstall the blocker; a false negative just means one reel got through. Every
detector checks allow-surfaces first.

**The overlay must never outlive the surface it covers.** A stuck overlay is a bricked
phone. `BlockReelsService` re-checks the screen every 400ms while the overlay is up rather
than trusting a dismissal event to arrive, with a five-minute hard ceiling behind that.

## Known limitations

- Detection reads private implementation details of apps that ship every two weeks.
  Expect to repair detectors a few times a year — that's the standing cost of this
  approach, not a bug.
- Instagram and YouTube migrating surfaces to Jetpack Compose would remove view ids
  entirely; the fallback is `contentDescription` matching, which is localised and weaker.
- The YouTube Shorts *shelf* on the home feed is deliberately left alone. Only the
  fullscreen player is blocked.
- No strict mode. Nothing stops you turning the accessibility service off. See
  `docs/appblock-research.md` §4 for the device-owner route if that becomes necessary.
- The app requests no internet permission and never will — it can read every screen you
  open, so it should have no way to send that anywhere.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```
