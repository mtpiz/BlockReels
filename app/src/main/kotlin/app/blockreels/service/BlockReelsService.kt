package app.blockreels.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import app.blockreels.R
import app.blockreels.data.Settings
import app.blockreels.detect.Detection
import app.blockreels.detect.Detectors
import app.blockreels.detect.Verdict
import app.blockreels.dump.DumpStore
import app.blockreels.dump.NodeDumper
import app.blockreels.overlay.BlockOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class BlockReelsService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val settings by lazy { Settings(applicationContext) }
    private val dumpStore by lazy { DumpStore(applicationContext) }
    private val overlay by lazy { BlockOverlay(this) }

    @Volatile private var enabledPackages: Set<String> = emptySet()
    @Volatile private var dumpMode: Boolean = false

    private var lastScanAt = 0L
    private var pendingBlock: Runnable? = null
    private var overlayShownAt = 0L

    private val dumpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Tapping a notification action collapses the shade, but not instantly — and
            // while it's open the shade *is* the focused window, so dumping immediately
            // captures System UI instead of the app underneath.
            main.postDelayed(::captureDump, SHADE_CLOSE_DELAY_MS)
        }
    }

    // region lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        createNotificationChannel()

        ContextCompat.registerReceiver(
            this,
            dumpReceiver,
            IntentFilter(ACTION_DUMP),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        settings.enabledPackages
            .onEach { packages ->
                enabledPackages = packages
                applyServiceInfo(packages)
            }
            .launchIn(scope)

        settings.dumpMode
            .onEach { enabled ->
                dumpMode = enabled
                // Dump mode widens the package filter to everything, so you can capture
                // any app's tree while working out what to match on.
                applyServiceInfo(enabledPackages)
                if (enabled) showDumpNotification() else cancelDumpNotification()
            }
            .launchIn(scope)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        main.removeCallbacksAndMessages(null)
        overlay.hide()
        cancelDumpNotification()
        runCatching { unregisterReceiver(dumpReceiver) }
        scope.cancel()
    }

    override fun onInterrupt() = Unit

    private fun applyServiceInfo(packages: Set<String>) {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // FLAG_REPORT_VIEW_IDS is load-bearing: without it viewIdResourceName is null
            // on every node and every detector silently matches nothing.
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            // Scoping to the tracked apps is the single biggest battery win available:
            // without it the system wakes this process for every content change on the
            // device. Leaving a tracked app is handled by the watchdog instead.
            packageNames = if (dumpMode) null else packages.toTypedArray()
            notificationTimeout = 100
        }
    }

    // endregion

    // region detection

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        if (pkg !in enabledPackages) {
            if (overlay.isShowing) dismiss()
            return
        }

        // Window-state changes are rare and mark exactly the transitions we care about, so
        // they bypass the throttle. Content changes fire continuously inside a video feed
        // and must not.
        val isTransition = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (!isTransition) {
            val now = SystemClock.uptimeMillis()
            if (now - lastScanAt < SCAN_THROTTLE_MS) return
            lastScanAt = now
        }

        when (evaluate()?.takeIf { it.verdict == Verdict.BLOCK }) {
            null -> dismiss()
            else -> requestBlock()
        }
    }

    /** Walks the current screen and asks the relevant detector about it. */
    private fun evaluate(): Detection? {
        val root = currentAppRoot() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        if (pkg !in enabledPackages) return null
        val detector = Detectors[pkg] ?: return null
        return detector.detect(NodeScanner.scan(root, pkg))
    }

    private fun currentAppRoot(): AccessibilityNodeInfo? {
        windows.forEach { window ->
            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                (window.isActive || window.isFocused)
            ) {
                window.root?.let { return it }
            }
        }
        return rootInActiveWindow
    }

    // endregion

    // region overlay lifecycle

    /**
     * Short-form surfaces can flash on screen during ordinary navigation, so a single
     * positive reading isn't enough — confirm it still holds after a grace period before
     * covering anything.
     */
    private fun requestBlock() {
        if (overlay.isShowing || pendingBlock != null) return
        val confirm = Runnable {
            pendingBlock = null
            val detection = evaluate()?.takeIf { it.verdict == Verdict.BLOCK } ?: return@Runnable
            Log.i(TAG, "blocking ${detection.surface} (${detection.reason})")
            overlay.show(detection.surface)
            overlayShownAt = SystemClock.uptimeMillis()
            scope.launch { settings.recordBlock() }
            main.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
        }
        pendingBlock = confirm
        main.postDelayed(confirm, GRACE_MS)
    }

    /**
     * The overlay must never outlive the surface it covers — a stuck one is a bricked
     * phone. Rather than trusting that a dismissal event will arrive, re-check the screen
     * on a timer for as long as it's up, with a hard ceiling as the last line of defence.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!overlay.isShowing) return

            if (SystemClock.uptimeMillis() - overlayShownAt > MAX_OVERLAY_MS) {
                Log.w(TAG, "overlay hit its ceiling, dismissing")
                dismiss()
                return
            }

            if (evaluate()?.verdict != Verdict.BLOCK) {
                dismiss()
                return
            }
            main.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun dismiss() {
        pendingBlock?.let(main::removeCallbacks)
        pendingBlock = null
        main.removeCallbacks(watchdog)
        overlay.hide()
    }

    // endregion

    // region dump mode

    private fun captureDump() {
        val root = currentAppRoot()
        if (root == null) {
            Log.w(TAG, "dump requested but no application window is focused")
            return
        }
        val pkg = root.packageName?.toString() ?: "unknown"
        val signals = NodeScanner.scan(root, pkg, maxNodes = Int.MAX_VALUE)
        val detection = Detectors[pkg]?.detect(signals)

        val header = buildString {
            append("BlockReels dump — ").append(pkg)
            appendLine()
            append("verdict: ").append(detection?.let { "${it.verdict} ${it.surface} (${it.reason})" } ?: "none")
        }

        val file = dumpStore.write(pkg, NodeDumper.dump(root, header))
        Log.i(TAG, "wrote dump ${file.name}")
        showDumpNotification(lastFile = file.name)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.dump_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun showDumpNotification(lastFile: String? = null) {
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_DUMP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.dump_notification_title))
            .setContentText(lastFile ?: getString(R.string.dump_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    // Explicitly typed: the (Icon, ...) and deprecated (int, ...) overloads
                    // are otherwise ambiguous for a bare null.
                    null as Icon?,
                    getString(R.string.dump_action),
                    pendingIntent,
                ).build(),
            )
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun cancelDumpNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    // endregion

    private companion object {
        const val TAG = "BlockReels"
        const val CHANNEL_ID = "dump"
        const val NOTIFICATION_ID = 1
        const val ACTION_DUMP = "app.blockreels.DUMP"

        const val SCAN_THROTTLE_MS = 300L
        const val GRACE_MS = 350L
        const val WATCHDOG_INTERVAL_MS = 400L
        const val MAX_OVERLAY_MS = 5 * 60 * 1000L
        const val SHADE_CLOSE_DELAY_MS = 1200L
    }
}
