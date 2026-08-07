# Building a personal AppBlock for Android

Research notes on what AppBlock does and what it would take to rebuild the useful parts
as a personal, sideloaded app.

---

## 1. What AppBlock actually is

AppBlock (MobileSoft, Czech) is a screen-time / distraction blocker on Android and iOS.
The feature set, roughly in order of how much engineering each costs:

| Feature | What it does |
|---|---|
| **Blocking profiles** | Named sets of blocked apps + websites. Multiple profiles, each with its own trigger conditions. |
| **Schedules** | Time-of-day + day-of-week windows per profile ("work hours", "bedtime"). |
| **Quick block** | One-tap "block everything for 30 minutes". |
| **Usage limits** | Block app X after N minutes of use today, or after N launches. |
| **Website blocking** | Per-URL and per-keyword blocking that works across browsers, plus a maintained adult-site blocklist. |
| **Location triggers** | Geofence: activate/deactivate a profile on entering or leaving an area. |
| **Usage statistics** | Time-per-app dashboards, launch counts, trends. |
| **Strict Mode** | The commercial differentiator — see below. |

### Strict Mode (the part that makes it work)

Strict Mode exists to defeat *you*, five minutes after you set it up. Its sub-options:

- **Lock blockings in app** — once a session starts you cannot edit the profile, add
  exceptions, or stop it early.
- **Cooldown** — a 2–10 minute delay before a deactivation request takes effect.
- **Partner approval** — deactivation requires an email approval from someone else.
- **Disable uninstalling** — you can't remove AppBlock while a strict session is live.
- **Block device settings** — you can't reach Settings to strip its permissions.

Everything else is a scheduler over a blocklist. Strict Mode is where the actual
adversarial engineering lives, and it's the part most open-source clones skip.

---

## 2. The two things an app blocker must do

**(a) Know what's on screen right now.** **(b) Get you off it.**

### (a) Foreground-app detection

Three mechanisms, in descending order of quality:

**AccessibilityService** — the mechanism every real blocker uses.
Subscribe to `TYPE_WINDOW_STATE_CHANGED`; each event carries `packageName` and
`className`. Real-time (tens of ms), no polling, no battery drain, and the service is
restarted automatically by the system if killed. It's also the only way to read a
browser's URL bar, which is how cross-browser website blocking works.

```kotlin
class BlockerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (policyEngine.isBlockedNow(pkg)) enforce(pkg)
    }
    override fun onInterrupt() {}
}
```

`res/xml/accessibility_service_config.xml`:
```xml
<accessibility-service
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:packageNames=""  />   <!-- empty = all packages -->
```

**UsageStatsManager** — fallback / stats source.
`queryEvents()` filtered to `ACTIVITY_RESUMED`, polled ~every second from a foreground
service. Higher latency (you see the app for a beat before the block lands), meaningfully
worse battery, and the data is delayed. Requires `PACKAGE_USAGE_STATS`, which the user
grants in Settings → Apps → Special app access → Usage access. Use this for the usage
dashboard, not for enforcement.

**VpnService** — for network-level blocking only.
A local, no-server VPN that intercepts DNS (and optionally TLS SNI) and sinkholes blocked
domains. Covers every app, not just browsers. Downsides: Android allows exactly one VPN
at a time, so it conflicts with a real VPN; DNS-over-HTTPS bypasses it unless you also
block the known DoH endpoints; and you can't block a *path*, only a host.

### (b) Enforcement

Options, best-first:

1. **`DevicePolicyManager.setPackagesSuspended()`** — the OS itself blocks launch and
   shows its own "app is paused" dialog. No detection loop, no overlay, no battery cost,
   nothing to race. Requires device-owner or profile-owner privilege (§4).
2. **`performGlobalAction(GLOBAL_ACTION_HOME)`** from the AccessibilityService — instantly
   kicks you to the launcher. Crude, but nearly impossible to fight.
3. **Full-screen block Activity** — launch your own Activity with
   `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK`. Shows a message, timer, and
   "why you blocked this". This is what most blockers do.
4. **Overlay window** (`TYPE_APPLICATION_OVERLAY` via `SYSTEM_ALERT_WINDOW`) — draws on
   top without a task switch. Note this is now a restricted setting for sideloaded apps
   on Android 15+.

In practice: go HOME *and* show the block screen. HOME wins the race; the block screen
explains what happened.

### Website blocking without a VPN

From the AccessibilityService, read the URL bar node by view ID:

| Browser | URL bar view ID |
|---|---|
| Chrome | `com.android.chrome:id/url_bar` |
| Firefox | `org.mozilla.firefox:id/mozac_browser_toolbar_url_view` |
| Samsung Internet | `com.sec.android.app.sbrowser:id/location_bar_edit_text` |
| Brave | `com.brave.browser:id/url_bar` |

On a match, fire `GLOBAL_ACTION_BACK` or redirect the tab. This is fragile — the IDs
change with browser releases and the table needs maintaining. It's also why AppBlock's
site blocking sometimes breaks after a Chrome update. The VPN approach is more robust
and less maintenance; the accessibility approach is more precise. Real products ship both.

---

## 3. Making it survive

An app blocker that dies is worse than no blocker. What's required:

- **Foreground service**, `START_STICKY`, with a persistent notification. On Android 14+
  you must declare a `foregroundServiceType` — `specialUse` is the right one here.
- **Boot receiver** (`RECEIVE_BOOT_COMPLETED`) to restart after reboot.
- **Battery optimization exemption** — prompt via
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **OEM autostart whitelists** — Xiaomi, Oppo, Vivo, OnePlus, and Samsung all kill
  background work aggressively and each has its own buried settings screen. There is no
  API for this; you deep-link the user to the screen and hope. This is the single
  biggest source of "your app stopped working" reports for every blocker on the market.
- **Clock-change resistance** — a strict session must not end because the user set the
  clock forward. Store both wall-clock end time and an `SystemClock.elapsedRealtime()`
  anchor, take the later of the two, and listen for `ACTION_TIME_CHANGED`.

---

## 4. The shortcut a personal build gets: device owner

This is the most important finding for a *personal* version.

Commercial blockers fight the platform because they must be installable from Play by
anyone. You don't have that constraint. You can make your own app a **device owner**,
which unlocks `DevicePolicyManager` and turns most of the hard parts into one-line API
calls:

```kotlin
dpm.setPackagesSuspended(admin, arrayOf("com.instagram.android"), true)
dpm.setUninstallBlocked(admin, packageName, true)          // real uninstall protection
dpm.setApplicationHidden(admin, "com.zhiliaoapp.musically", true)
dpm.setUserControlDisabledPackages(admin, listOf(packageName))  // can't force-stop it
dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
```

That is Strict Mode, done properly, with the OS enforcing it instead of an accessibility
service racing the user.

**Setup cost:** device owner can only be provisioned when the device has no accounts on
it. In practice: factory reset, skip Google sign-in, then

```
adb shell dpm set-device-owner com.yourname.blockreels/.AdminReceiver
```

then add your accounts back. It survives reboots and can only be removed by
`dpm remove-active-admin` (which your app can refuse to allow) or a factory reset.

If a factory reset is too much, the fallback is **profile owner** on a work profile —
provisionable without a reset, but it only controls apps inside the work profile, which
means the apps you want to block have to live there. Worth considering: a work profile
containing exactly Instagram/TikTok/Reddit, blocked by policy, is a very clean design.

Otherwise, `DeviceAdminReceiver` (non-owner) still prevents uninstall while active, but
the user can deactivate it in Settings — so it only works if you're also blocking the
Settings app via accessibility, which is exactly the cat-and-mouse AppBlock plays.

---

## 5. Platform headwinds as of 2026

- **Android 13+ Restricted Settings.** A sideloaded app cannot be granted accessibility
  or notification-listener access through the normal Settings toggle. The user must go to
  App info → ⋮ → *Allow restricted settings* first. One-time, but non-obvious.
- **Android 15 expanded this** to overlay (`SYSTEM_ALERT_WINDOW`), notification listening,
  device admin, and default dialer/SMS roles. Installing via `adb install` generally
  avoids the "sideloaded" classification; installing by tapping an APK in a file manager
  does not.
- **Developer verification.** From 30 Sept 2026 (Brazil, Indonesia, Singapore, Thailand
  first; global during 2027) apps on certified devices need a verified developer.
  **ADB installs are explicitly exempt**, and there's an advanced 24-hour flow for
  unverified apps. For a personal app you install over `adb`, this is a non-issue — but it
  does mean "just send the APK to a friend" gets harder.
- **Play Store policy** would reject most of this feature set anyway (accessibility use
  for non-accessibility purposes, `QUERY_ALL_PACKAGES`, uninstall prevention). Sideloading
  isn't a compromise here; it's the only viable channel.

---

## 6. Permissions you'll declare

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
                 tools:ignore="ProtectedPermissions"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>
<!-- only if you want geofencing -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
```

Plus `BIND_ACCESSIBILITY_SERVICE` on the service and `BIND_VPN_SERVICE` if you add the
VPN path.

---

## 7. Proposed architecture

```
app/
  ui/            Compose: profile list, app picker, schedule editor, stats, block screen
  domain/
    PolicyEngine        isBlockedNow(pkg, now) -> Decision   (pure, unit-testable)
    Profile, Schedule, StrictConfig
  data/
    Room: profiles, blocked_packages, sessions, usage_events
    DataStore: settings, strict-lock state
  service/
    BlockerAccessibilityService   detection + enforcement
    BlockerForegroundService      keepalive, session timers
    BootReceiver
    admin/DevicePolicyController  suspend/hide/uninstall-block (device-owner path)
  stats/
    UsageStatsRepository          UsageStatsManager queries for the dashboard
```

**Stack:** Kotlin, Jetpack Compose, Room, DataStore, Coroutines/Flow, `AlarmManager`
exact alarms for schedule boundaries (not WorkManager — you need minute precision),
Hilt if you like DI. minSdk 26, targetSdk latest.

The one design rule that matters: `PolicyEngine.isBlockedNow()` must be a pure function
of (package, timestamp, stored profiles, today's usage). Everything else is plumbing, and
that function is the only part you can meaningfully unit-test.

---

## 8. Effort

Assuming you're comfortable with Kotlin and Android but haven't done accessibility
services before:

| Milestone | Scope | Estimate |
|---|---|---|
| **Spike** | Accessibility service logging foreground packages; confirm permissions flow on your actual phone | 2–4 h |
| **MVP** | Hardcoded blocklist + block screen + one schedule. Actually usable. | 10–15 h |
| **v1** | App picker UI, multiple profiles, schedules, Room persistence, boot survival, usage dashboard | 30–50 h |
| **Strict Mode** | Session locking, cooldown, clock-tamper resistance, uninstall protection via device owner | 10–20 h |
| **Website blocking** | Accessibility URL-bar reading for 3–4 browsers, or a local VPN DNS sinkhole | 15–30 h |
| **Nice-to-have** | Geofencing, usage limits, launch counts, partner approval | 20–40 h |

**A weekend gets you something you'd actually use.** The MVP — detect, block, schedule —
is genuinely small. The long tail is OEM battery-killer workarounds and browser-specific
website blocking, and those are exactly the parts a personal build can defer or skip: you
only have to work on *your* phone and *your* browser.

---

## 9. Prior art worth reading

- [UltraFocus](https://github.com/Binondi/UltraFocus) — small Kotlin blocker,
  accessibility + overlay. Good first read.
- [Focus-Modes-App](https://github.com/adnanrangrej/Focus-Modes-App) — Compose, Hilt,
  Room, accessibility-based blocking. Closest to the architecture above.
- [foqos-android](https://github.com/nish261/foqos-android) — NFC/QR tag to start and stop
  a block session. Interesting Strict Mode alternative: put the tag somewhere annoying.
- [personalDNSfilter](https://f-droid.org/en/packages/dnsfilter.android/),
  [Block This](https://github.com/ggsava/block-this) — local VpnService DNS filtering,
  if you go the network route.
- [RethinkDNS](https://github.com/celzero/rethink-app) — production-grade local VPN +
  per-app firewall. Heavier reference, but the most complete.

---

## 10. Recommendation

For a personal build, don't clone AppBlock's architecture — it's shaped by Play Store
constraints you don't have.

1. Start with the **AccessibilityService + block-screen** MVP. One weekend, immediately
   useful, no special provisioning.
2. If you find yourself defeating it (you will), escalate to **device owner** via ADB and
   move enforcement to `setPackagesSuspended` / `setUninstallBlocked`. That's Strict Mode
   with the OS on your side, and it's the single highest-leverage step in this whole
   document.
3. Add website blocking last, and only for the specific browsers and sites you actually
   use.

## Sources

- [AppBlock — Android](https://appblock.app/android/) ·
  [Strict Mode](https://appblock.app/help/android/strict-mode/) ·
  [Help](https://appblock.app/help/android/)
- [Android developer verification](https://developer.android.com/developer-verification)
- [Device admin deprecation](https://developers.google.com/android/work/device-admin-deprecation)
- [Android 15 restricted settings / ECM](https://www.androidauthority.com/android-15-restricted-settings-sideloading-3481098/)
- [How Android app verification will work](https://www.androidauthority.com/how-android-app-verification-works-3603559/)
- [Foreground activity detection: UsageStatsManager vs AccessibilityService](https://github.com/ngdathd/ForegroundActivity)
