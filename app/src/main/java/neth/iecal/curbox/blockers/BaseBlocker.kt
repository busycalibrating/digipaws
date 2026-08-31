package neth.iecal.curbox.blockers

import android.content.Context
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import neth.iecal.curbox.utils.CustomTabOwnerResolver


abstract class BaseBlocker{

    private var ownerResolver: CustomTabOwnerResolver? = null

    /** Called once a blocker has a context, so a screen can resolve to the app behind it. */
    protected fun attachOwnerResolver(context: Context) {
        if (ownerResolver == null) {
            ownerResolver = CustomTabOwnerResolver(context.applicationContext)
        }
    }

    /**
     * The app this event should be acted on as.
     *
     * A browser drawing an installed web app reports its own package, which would block
     * that web app whenever the browser is blocked and leave it unblockable by name. The
     * owner of the task is the app actually in use, so it is preferred where it can be
     * read; everywhere else, including every ordinary app and any device where usage
     * access is not granted, the event's own package stands.
     */
    protected fun attributedPackage(event: AccessibilityEvent?): String? {
        val packageName = event?.packageName?.toString() ?: return null
        return ownerResolver?.ownerOf(packageName) ?: packageName
    }
}
