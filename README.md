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
| Node dumper | runs on device; captures used to build the detectors below |
| Instagram detector | ids captured from a real device, replayed by fixtures, **enabled** |
| YouTube Shorts detector | unit tested against synthetic fixtures only — `reel_progress_bar` is confirmed by other working blockers, not yet by a capture here |
| Overlay, service, watchdog | compile and run; not yet confirmed firing on a real feed |

CI builds the APK, runs the tests and publishes the APK on every push. What CI still cannot
settle is whether a detector fires on the real app — only a phone answers that.

### What the captures changed

Three of the guessed Instagram ids were wrong (`explore_tab`, `profile_header`,
`discover_recycler` don't exist), and one guess was actively dangerous: matching
`clips_viewer` blocks Stories, because Instagram keeps the Reels pager loaded offscreen the
whole time a Story is open. See [`docs/content-blocking.md`](docs/content-blocking.md) §2.

## Install

Download and open on the phone — no adb, no unzipping:

**[`app-debug.apk`](https://github.com/mtpiz/BlockReels/releases/download/dev/app-debug.apk)**

Rebuilt on every push, same link each time. Debug builds are signed with the checked-in
key in `keystore/`, so a new build installs straight over the old one and keeps its
permissions. The app footer shows `build 0.1.0+<sha>` so you can tell whether an update
actually landed.

Or build locally, if you have the Android SDK:

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

## Adding or repairing a detector

This is the loop for covering a new app, and the loop for fixing detection after an
Instagram or YouTube update breaks it.

1. Turn on **Dump mode** in the app. A notification appears with a *Dump screen* action.
   (It's posted by the accessibility service, so that has to be on first.)
2. Open the screen you care about. Swipe down from the top of the screen, tap **Dump**.
3. Repeat for the neighbouring screens that must *not* be treated the same way — the
   diff between them is the detector.
4. In the app, **Label** each capture from the dropdown, then **Save all to Downloads**.
5. Upload them to `app/src/test/resources/fixtures/` — the filename carries the expected
   verdict (`instagram-reels.BLOCK.txt`, `instagram-story.ALLOW.txt`, …).
6. Edit the constants in the relevant detector until `./gradlew testDebugUnitTest` is
   green.

Step 5 is what makes step 6 tractable. `FixtureTest` replays every captured dump through
its detector, so once a surface has been captured **the detector can be rewritten and
re-checked without a phone** — and no later repair can silently break a screen that was
already working. Adding a regression is dropping in a file; no test code to write.

Read a dump before uploading it and compare visible against offscreen: the offscreen half
is where the traps live (§2 of the design notes).

Dump mode stays in release builds on purpose. When an Instagram update breaks detection,
you want to re-diff on the phone in five minutes, not rebuild from a laptop you don't have.

A caution on what dumps contain: view ids and class names, but also on-screen **text** —
which for a DM capture means the messages. Read one before committing it and trim anything
you'd rather not have in git history. The ids are the part that matters.

### Two traps, both confirmed on device

**The naming is inverted.** Instagram's ids predate the Reels product:

- `reel_viewer_*` → **Stories** → allow
- `clips_*` → **Reels** → block

A detector matching the substring `"reel"` blocks Stories and allows Reels, exactly
backwards. Group ids by product, never by name.

**Presence is not visibility.** While a Story is open, `clips_viewer_view_pager` is still
in the tree — parked offscreen at `[1440,127][1440,3064]`, because Instagram preloads the
Reels page of its pager. Matching it without checking `isVisibleToUser` covers every
Story with the block screen. `NodeScanner` drops offscreen subtrees, and `DumpParser`
mirrors that so fixtures exercise what the live scan actually sees.

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
  open, so it should have no way to send that anywhere. That's also why dumps are exported
  by hand rather than uploaded from the app.
- The debug signing key in `keystore/` is committed to a public repo, so it is public.
  That's the price of updates installing in place instead of forcing an uninstall and a
  re-grant of accessibility on every iteration. It must never sign a real release.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```
