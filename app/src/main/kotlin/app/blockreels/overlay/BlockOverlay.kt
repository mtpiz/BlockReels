package app.blockreels.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import app.blockreels.R

/**
 * The blue screen. Covers a blocked surface without touching the app underneath.
 *
 * Uses `TYPE_ACCESSIBILITY_OVERLAY` rather than `TYPE_APPLICATION_OVERLAY`, which is the
 * one genuine perk of already being an accessibility service: it needs no
 * `SYSTEM_ALERT_WINDOW` permission, so there's one less grant to walk the user through and
 * one less thing caught by Android 15's restricted-settings rules for sideloaded apps.
 *
 * The single most important property here is that it comes *down* reliably. An overlay
 * that leaks is a bricked phone, so removal is idempotent and the caller
 * ([app.blockreels.service.BlockReelsService]) re-checks the screen on a timer while this
 * is up rather than trusting that it will be told when to dismiss.
 */
class BlockOverlay(
    private val service: AccessibilityService,
    /**
     * Invoked before an escape action runs. The overlay comes down immediately rather than
     * waiting for the watchdog to notice, so the button responds instantly, and the caller
     * uses this to stop re-blocking mid-navigation.
     */
    private val onEscape: () -> Unit = {},
) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(surfaceName: String, escapeToTop: (() -> Boolean)? = null) {
        if (view != null) {
            updateLabel(surfaceName)
            return
        }

        val inflated = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)

        val topButton = inflated.findViewById<Button>(R.id.overlay_top)
        if (escapeToTop == null) {
            topButton.visibility = View.GONE
        } else {
            topButton.setOnClickListener {
                escape()
                // Falls back to BACK if the app won't scroll — never leave the user stuck
                // behind a button that silently did nothing.
                if (!escapeToTop()) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }
        }

        inflated.findViewById<Button>(R.id.overlay_back).setOnClickListener {
            escape()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
        inflated.findViewById<Button>(R.id.overlay_home).setOnClickListener {
            escape()
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not FLAG_NOT_TOUCHABLE: swallowing swipes is the entire point, otherwise the
            // feed keeps scrolling invisibly underneath.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.OPAQUE,
        )

        runCatching { windowManager.addView(inflated, params) }
            .onSuccess {
                view = inflated
                updateLabel(surfaceName)
            }
    }

    private fun escape() {
        hide()
        onEscape()
    }

    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeView(current) }
    }

    private fun updateLabel(surfaceName: String) {
        view?.findViewById<TextView>(R.id.overlay_subtitle)
            ?.text = service.getString(R.string.overlay_subtitle, surfaceName)
    }
}
