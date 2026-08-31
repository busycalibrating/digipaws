package neth.iecal.curbox.utils

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Finds the app that owns what is currently on screen.
 *
 * A Trusted Web Activity has no UI of its own: the browser draws the whole app, so its
 * accessibility events carry the browser's package name. Treated at face value the web
 * app is blocked whenever the browser is, and can never be blocked under its own name.
 *
 * The system does record the owner, as the task root of the foreground activity, but it
 * is reachable only through usage access. That permission is optional and the accessor
 * for the task root is not public API, so every failure resolves to null and leaves the
 * caller with the package the event already carried.
 */
class CustomTabOwnerResolver(private val context: Context) {

    private companion object {
        /** Far enough back to cover the resume that brought the screen up, no further. */
        const val LOOKBACK_MS = 10_000L

        /** The foreground app cannot change without an event, so a short hold is safe. */
        const val CACHE_MS = 1_000L
    }

    /**
     * Resolved once: a missing accessor stays missing for the life of the process, and
     * reflecting on every event would be wasteful.
     */
    private val taskRootAccessor = runCatching {
        UsageEvents.Event::class.java.getMethod("getTaskRootPackageName")
    }.getOrNull()

    private var cachedFor: String? = null
    private var cachedOwner: String? = null
    private var cachedAt = 0L

    /** True when the user has granted usage access, which is never assumed. */
    fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** True when owner resolution can actually run, for reporting it in the UI. */
    fun isAvailable(): Boolean = taskRootAccessor != null && hasUsageAccess()

    /**
     * The app owning the task [packageName] is drawing, or null when that is [packageName]
     * itself, which is the ordinary case, or when it cannot be read at all.
     *
     * Repeated events for one foreground app answer from the cache rather than querying
     * again. Nothing is retained beyond that: the events are read and dropped.
     */
    fun ownerOf(packageName: String): String? {
        val accessor = taskRootAccessor ?: return null

        val now = System.currentTimeMillis()
        if (cachedFor == packageName && now - cachedAt < CACHE_MS) return cachedOwner
        if (!hasUsageAccess()) return null

        val owner = runCatching {
            val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usage.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var latest: String? = null
            while (events.getNextEvent(event)) {
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue
                if (event.packageName != packageName) continue
                latest = accessor.invoke(event) as? String
            }
            latest?.takeIf { it != packageName }
        }.getOrNull()

        cachedFor = packageName
        cachedOwner = owner
        cachedAt = now
        return owner
    }
}
