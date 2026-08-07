# BlockReels: blocking the feed, not the app

The goal is **surface-level** blocking, not app-level. Instagram DMs and Stories stay
usable; the Reels scroll does not. YouTube long-form stays; Shorts does not.

This is a meaningfully different product from AppBlock's core, and a harder one. AppBlock
answers *"which app is in the foreground?"* — one string comparison. BlockReels has to
answer *"which screen inside this app am I on?"*, which means reading the app's live view
hierarchy and fingerprinting it.

---

## 1. The mechanism (confirmed)

An `AccessibilityService` with `canRetrieveWindowContent="true"` and
`FLAG_REPORT_VIEW_IDS` can walk the on-screen node tree and read each node's
`viewIdResourceName` — the same `com.instagram.android:id/clips_viewer_view_pager` string
the app's own developers wrote in their layout XML. Certain IDs only ever exist on
certain screens. That's the fingerprint.

This is exactly how the existing blockers work. From
[atick-faisal/Shorts-Blocker](https://github.com/atick-faisal/Shorts-Blocker)
(Apache-2.0), the YouTube detector in full:

```kotlin
override fun isShortFormContent(event, rootNode, resources): Boolean {
    val queue = ArrayDeque<AccessibilityNodeInfo>()
    queue.add(rootNode)
    var nodeCount = 0
    while (queue.isNotEmpty() && nodeCount < 120) {
        val node = queue.removeFirst()
        nodeCount++
        val id = node.viewIdResourceName?.lowercase()
        if (id != null && "reel_progress_bar" in id) return true   // Shorts player
        for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
    }
    return false
}
```

Bounded BFS, one string match. That's the whole trick. Note it deliberately does *not*
match the Shorts shelf on the YouTube home feed — `reel_progress_bar` only exists in the
fullscreen player.

The service config that makes it work:

```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

…plus, set at runtime in `onServiceConnected()`, the two flags that matter:

```kotlin
serviceInfo = AccessibilityServiceInfo().apply {
    eventTypes = TYPE_WINDOW_STATE_CHANGED or TYPE_WINDOW_CONTENT_CHANGED
    flags = FLAG_REPORT_VIEW_IDS or FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    packageNames = arrayOf("com.instagram.android", "com.google.android.youtube")
    notificationTimeout = 100
}
```

`FLAG_REPORT_VIEW_IDS` is required — without it `viewIdResourceName` returns null and
nothing works. Setting `packageNames` is the single biggest battery win: the service is
then only woken for apps you actually care about.

---

## 2. The hard part: telling Stories from Reels

Here is where every off-the-shelf blocker fails our requirements. The same reference
project's Instagram detector:

```kotlin
if (id != null && ("clips_tab" in id && n.isSelected)) return true   // fine
...
if (feedTabCount == 0) return true    // "no bottom tab bar visible => fullscreen => block"
```

That second rule blocks **anything fullscreen with no bottom nav** — which includes the
Stories viewer. It would break the exact thing you want to keep. It also only scans 10
nodes deep, which is far too shallow to be reliable.

So the detector has to be genuinely more precise. The target matrix:

| App | Surface | Verdict |
|---|---|---|
| Instagram | DM inbox, DM thread | **allow** |
| Instagram | Stories viewer (friends' stories) | **allow** |
| Instagram | Profile, search, single post from a link | **allow** |
| Instagram | Reels tab | **block** |
| Instagram | Reels opened from Explore or a profile grid | **block** |
| Instagram | Home feed infinite scroll | *your call — see §8* |
| YouTube | Long-form watch page | **allow** |
| YouTube | Search, subscriptions, library | **allow** |
| YouTube | Shorts fullscreen player | **block** |
| YouTube | Shorts shelf on the home feed | *your call — it's the entry point* |
| TikTok | (whole app is the feed) | app-level block is fine |

### The naming trap

Instagram's internal resource names are historically confusing in a way that bites
precisely here: **Stories are called "reel" in the code** (from the original story-tray
"reel"), while **Reels are called "clips"**. So:

- `reel_viewer_*`, `reel_tray_*` → **Stories** → allow
- `clips_viewer_*`, `clips_video_container`, `clips_tab` → **Reels** → block

If you write a detector that greps for the substring `"reel"`, you will block Stories and
allow Reels — exactly backwards. This alone justifies §3.

Treat every specific ID below as a **hypothesis to verify on your own device**, not a
fact. Only `reel_progress_bar` (YouTube Shorts) and `clips_tab` / `feed_tab` (Instagram)
are confirmed from working code; the rest need checking, and all of them drift between app
versions anyway.

---

## 3. Build the node-tree dumper first

You cannot write these detectors from documentation, because no documentation exists —
these are private resource IDs in someone else's app. You need ground truth from your own
phone, and you'll need it again every time Instagram ships an update.

So **milestone zero is a debug tool, not a feature**: an accessibility service that dumps
the full node tree of the current screen — `viewIdResourceName`, `className`,
`contentDescription`, `text`, bounds, `isSelected` — to a file, triggered by a
notification action or a floating button.

Then the workflow for adding any new surface is: open Instagram → navigate to Stories →
dump → navigate to Reels → dump → diff the two. The IDs present in one and absent in the
other *are* your detector.

`adb shell uiautomator dump` works for a quick look, but it drops `isSelected` and is
awkward for anything transient, so an in-app dumper pays for itself within an hour.

Ship this in the release build behind a hidden toggle. When a Meta update breaks
detection — and it will — you want to re-diff in five minutes on the device, not rebuild
from a laptop you don't have with you.

---

## 4. Make the rules data, not code

Because detection breaks on other people's release schedule, the detectors should be a
JSON ruleset stored in DataStore, hot-reloadable, and editable in-app — not `when` blocks
compiled into the APK. A broken detector then becomes a two-minute edit instead of a
rebuild-and-reinstall cycle.

```jsonc
{
  "package": "com.instagram.android",
  "surfaces": [
    {
      "name": "reels",
      "verdict": "block",
      "allOf":  [{ "idContains": "clips_viewer" }],
      "noneOf": [{ "idContains": "reel_viewer" }]   // never fire on Stories
    },
    {
      "name": "stories",
      "verdict": "allow",
      "priority": 100,                               // allow-rules win ties
      "anyOf": [{ "idContains": "reel_viewer" },
                { "idContains": "story_progress" }]
    },
    { "name": "dm", "verdict": "allow", "priority": 100,
      "anyOf": [{ "idContains": "direct_thread" }, { "idContains": "thread_message" }] }
  ]
}
```

Two design rules that matter:

1. **Allow-rules beat block-rules.** A false positive on Stories or DMs makes the app
   unusable and you'll uninstall it by Thursday. A false negative just means one reel
   slipped through. Bias hard toward allowing.
2. **Match on presence *and* absence.** A single positive ID is not enough when two
   surfaces are structurally similar — the negative condition is what separates Stories
   from Reels.

Beyond view IDs, other usable signals: `contentDescription` (readable, but localized —
don't match English strings if you ever switch locale), node `className`
(`ViewPager2` etc.), and `event.className` on window-state changes. Longer term, note
that **Jetpack Compose surfaces have no view IDs at all** — only `testTag` and
`contentDescription` — so as these apps migrate, ID-based detection degrades and
description-based detection becomes the fallback.

---

## 5. Enforcement: the overlay

AppBlock's blue screen is a `TYPE_APPLICATION_OVERLAY` window, and it's the right choice.
It's better than the reference implementation's `GLOBAL_ACTION_BACK` because BACK is
unpredictable — from Shorts opened via a notification, BACK exits YouTube entirely; on
Instagram it can drop you somewhere you weren't.

```kotlin
val params = WindowManager.LayoutParams(
    MATCH_PARENT, MATCH_PARENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    // no FLAG_NOT_TOUCHABLE -> the overlay eats swipes, which is the point
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
    PixelFormat.OPAQUE,
)
windowManager.addView(blockView, params)
```

Requires `SYSTEM_ALERT_WINDOW`, granted via `ACTION_MANAGE_OVERLAY_PERMISSION`. Since
Android 15 this is a *restricted setting* for sideloaded apps — install with
`adb install` to avoid that classification, or clear it once via App info → ⋮ → Allow
restricted settings.

Design notes for the overlay itself:

- **Remove it promptly** when the surface goes away, or you've bricked the phone. Tear
  down on any window-state change that no longer matches, and add a hard watchdog timeout.
- **Give it an exit** — a "back" button that fires `GLOBAL_ACTION_BACK`, so the user
  isn't stuck poking at a blue rectangle.
- Consider a **grace delay**: don't fire for ~400ms. Reels sometimes flash during
  navigation transitions, and instant-firing produces flickers on screens you're only
  passing through.
- One caveat: an app can call `setHideOverlayWindows(true)` (Android 12+) to suppress
  overlays. Banking apps do this; Instagram and YouTube currently do not. If that ever
  changes, the fallback is `GLOBAL_ACTION_BACK`.

---

## 6. Performance

`TYPE_WINDOW_CONTENT_CHANGED` fires *constantly* inside a video feed — potentially
hundreds of events per second — and a naive tree walk on each one will visibly heat the
phone and wreck battery. Four mitigations, all necessary:

1. Set `packageNames` so the service isn't woken for other apps at all.
2. **Debounce** to at most one scan per ~300ms, and rate-limit repeat actions
   (the reference uses a 1.5s action cooldown).
3. **Bound the BFS** — 120 nodes and a depth cap. Fingerprint IDs live near the top of
   the tree; a full walk of a scrolling RecyclerView is wasted work.
4. **Cache the verdict per window ID** and only re-scan on `TYPE_WINDOW_STATE_CHANGED`
   or after the debounce window expires.

Also: only inspect the focused, active window (`win.isFocused && win.isActive`) — the
reference does this and it discards a lot of noise from system overlays.

---

## 7. What this costs to maintain

Be clear-eyed: this is the real price of the product. Detection depends on private
implementation details of apps that ship every two weeks and have some incentive to make
feed-avoidance harder. Expect to fix detectors a few times a year, per app.

Things that reduce the pain, in order of value: the in-app dumper (§3), data-driven rules
(§4), a bias toward allow-on-uncertainty (§4), and a visible "detector last matched: 2h
ago" indicator so you find out it broke from the UI rather than by noticing you've been
scrolling for twenty minutes.

For YouTube specifically there's a stronger option: **ReVanced** patches Shorts out of the
APK entirely — no overlay, no accessibility service, no battery cost, no false positives.
It has to be re-applied on every YouTube update and it doesn't help for Instagram, so it's
a complement rather than a replacement. Worth doing for YouTube if the detector proves
annoying.

---

## 8. Scope, decided

- **Instagram** — block Reels, the home feed **and** Explore. Only DMs, Stories and
  profiles you navigate to deliberately stay. Effectively: Instagram as a messaging app
  with Stories attached.
- **YouTube** — block the fullscreen Shorts player only. The Shorts shelf on the home feed
  stays, which conveniently is also the safer detector: `reel_progress_bar` cannot match
  the shelf, so there's no risk of taking the home feed down as collateral.
- **Always-on**, no schedules or strict mode in v1.
- Other apps (TikTok, Snapchat Spotlight, Facebook Reels) deferred — TikTok in particular
  is entirely feed, so plain app-level blocking covers it.

Note what the Instagram decision does to the detector: blocking the home feed as well as
Reels means the Stories-vs-Reels discriminator is no longer sufficient on its own, because
Stories launch from a tray sitting on top of the home feed and both are briefly present
during the transition. The allow-rule for Stories therefore has to be evaluated *before*
the feed block-rule, not merely alongside it.

---

## 9. Roadmap

| Milestone | Scope | Estimate |
|---|---|---|
| **0 — Dumper** | Accessibility service that dumps node trees to a file. Diff Stories vs Reels vs DMs on your own phone. | 4–6 h |
| **1 — Vertical slice** | Hardcoded YouTube Shorts detector + overlay. `reel_progress_bar` is known-good, so this validates the whole pipeline. | 6–8 h |
| **2 — Instagram** | Reels detector with Stories/DM allow-rules. The hard one; budget for iteration on-device. | 10–15 h |
| **3 — Rule engine** | Move detectors to JSON + DataStore, hot reload, in-app editor. | 10–15 h |
| **4 — Polish** | Onboarding/permissions flow, per-app toggles, debounce tuning, stats ("47 reels blocked"). | 10–15 h |
| **5 — Optional** | More apps, schedules, strict mode, streaks. | open-ended |

**Milestones 0 and 1 are a weekend and prove the concept end-to-end.** Milestone 2 is
where the actual work is, and it's iterative device work rather than architecture.

---

## 10. Prior art

- [atick-faisal/Shorts-Blocker](https://github.com/atick-faisal/Shorts-Blocker) —
  Apache-2.0, Compose, clean detector interface. Closest starting point; its Instagram
  detector is the part to replace. Reference code in §1 and §2 is from here.
- [sahedalomsumit/reels-blocker](https://github.com/sahedalomsumit/reels-blocker) —
  covers Facebook Reels too.
- [Curbox](https://curbox.app/) — GPL-3.0, blocks single features inside apps; the
  closest thing to this product that already exists.
- [Nudge](https://raeduslabs.com/blog/block-youtube-shorts-instagram-reels-android-without-blocking-app)
  — covers Shorts, Reels, Explore and TikTok For You; requests no internet permission at
  all, which is a nice trust property to copy given the service can read every screen.
- [AppBlock's own writeup](https://appblock.app/block-reels-shorts-stories-and-spotlight-on-android-with-appblock/)
  of the feature you tested.

## Recommendation

Start at milestone 0. The dumper is unglamorous and it's the thing that determines whether
this project is maintainable or abandoned in a month — every detector you'll ever write,
and every fix after an app update, comes out of it.

Then do YouTube first even though Instagram is the bigger problem: `reel_progress_bar` is
confirmed working, so it validates the service, the overlay, the permissions flow and the
debouncing against a known-good target. Fight Instagram's Stories-vs-Reels ambiguity once
the rest of the pipeline is proven.
