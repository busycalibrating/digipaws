package neth.iecal.curbox.blockers

import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent


abstract class BaseBlocker{

    companion object {
        /**
         * Stands in for a browser window that is rendering another app, so that a
         * blocker's "has the foreground app changed" tracking does not collapse the tab
         * into the browser itself and then miss the browser being opened straight after.
         */
        const val CUSTOM_TAB_PACKAGE = "#custom-tab"
    }

    /**
     * Activities a browser uses to render *another* app's web content: a Trusted Web
     * Activity (an installed web app, whose whole UI is drawn by the browser) or an
     * in-app link preview. These events still carry the browser's package name, so
     * treating them as the browser acts on an app the user never selected.
     *
     * Listed by class because the platform gives an accessibility service no way to ask
     * which app owns the task, so the activity name is the only signal available.
     */
    private val customTabActivities = setOf(
        // Chrome, and the Chromium forks that keep the upstream class names.
        "org.chromium.chrome.browser.customtabs.CustomTabActivity",
        "org.chromium.chrome.browser.customtabs.TranslucentCustomTabActivity",
        // Firefox and other Fenix builds.
        "org.mozilla.fenix.customtabs.ExternalAppBrowserActivity"
    )

    /** Browser package whose foreground window is currently a custom tab, or null. */
    private var customTabPackage: String? = null

    /**
     * Window the verdict was reached for. Without it a stale verdict would also match the
     * browser's own window, letting real browsing through whenever a content change
     * arrives before the window-state change that would have cleared it.
     */
    private var customTabWindowId = -1

    /**
     * True when the browser is merely rendering another app, rather than being used as a
     * browser. Call it for every event so the window state stays current, and skip the
     * blocker's own work when it returns true.
     *
     * Only window-state changes carry the activity class, while the content changes that
     * follow in the same window do not, so the verdict is remembered per window instead
     * of recomputed per event.
     */
    protected fun isBrowserRenderingAnotherApp(event: AccessibilityEvent?): Boolean {
        val packageName = event?.packageName?.toString() ?: return false
        // Null on the synthetic events used to force a re-check, which must not be read
        // as "this window is no longer a custom tab".
        val className = event.className?.toString()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            className != null && namesAnActivity(className)
        ) {
            if (customTabActivities.contains(className)) {
                customTabPackage = packageName
                customTabWindowId = event.windowId
            } else if (event.windowId == customTabWindowId) {
                // Only the tab's own window can retire the verdict. Browsers raise
                // transient windows while a tab is open, and clearing on those left the
                // tab still on screen looking like ordinary browsing.
                customTabPackage = null
                customTabWindowId = -1
            }
        }
        return customTabPackage == packageName && customTabWindowId == event.windowId
    }

    /**
     * A window-state change reports an activity only when the window really did switch to
     * one. Popups, dropdowns and soft keyboards raise the same event naming the framework
     * view they are built from, and a browser fires one for its own window whenever a
     * search box or menu opens over the page. Those must not retire a tab's verdict.
     */
    private fun namesAnActivity(className: String) =
        !className.startsWith("android.widget.") &&
            !className.startsWith("android.view.") &&
            !className.startsWith("android.inputmethodservice.")
}
