package neth.iecal.curbox.blockers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import neth.iecal.curbox.blockers.BaseBlocker
import androidx.core.net.toUri
import neth.iecal.curbox.hardcoded.URL_BAR_ID_LIST

class BrowserBlocker(val service: AccessibilityService) : BaseBlocker() {

    // Cache for packages CONFIRMED as browsers
    private val cacheBlockedBrowserApps: HashSet<String> = hashSetOf()

    // Cache for packages CONFIRMED as non-browsers
    private val cacheNotBlockedBrowserApps: HashSet<String> = hashSetOf()

    var isTurnedOn = false
    fun isAppBrowser(event: AccessibilityEvent?): Boolean {
        if(!isTurnedOn || event == null) return false
        val packageName = event.packageName?.toString() ?: return false

        if (cacheBlockedBrowserApps.contains(packageName)) {
            return true
        }
        if (cacheNotBlockedBrowserApps.contains(packageName)) {
            return false
        }

        val isBrowser = resolveIsBrowser(service, packageName) && !URL_BAR_ID_LIST.containsKey(packageName)

        if (isBrowser) {
            cacheBlockedBrowserApps.add(packageName)
        } else {
            cacheNotBlockedBrowserApps.add(packageName)
        }

        return isBrowser
    }

    /**
     * True only for real browsers, meaning apps that can open *any* web address.
     *
     * The probe is a host-less "http://" URI plus CATEGORY_BROWSABLE, so apps that only register
     * deep links for their own domain no longer match. Probing a concrete host instead matched
     * every such app, which made ordinary apps get treated as unsupported browsers and sent home.
     */
    private fun resolveIsBrowser(context: Context, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, "http://".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
        intent.setPackage(packageName)

        val pm = context.packageManager
        val activities = pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER
        )

        return activities.any { handlesAllWebUris(it.filter) }
    }

    /**
     * Mirrors the platform's own "handles all web data URIs" test, which is not public API. A
     * browser accepts every http(s) address, so its filter carries no host of its own.
     */
    private fun handlesAllWebUris(filter: IntentFilter?): Boolean {
        if (filter == null) return false
        if (!filter.hasCategory(Intent.CATEGORY_BROWSABLE)) return false
        if (!filter.hasDataScheme("http") && !filter.hasDataScheme("https")) return false
        return filter.countDataAuthorities() == 0
    }
}
