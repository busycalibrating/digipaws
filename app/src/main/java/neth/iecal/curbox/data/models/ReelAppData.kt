package neth.iecal.curbox.data.models

import android.view.accessibility.AccessibilityEvent

/**
 * A shipped reel detector. The script returns comparator text while the reel screen is visible,
 * including an empty string when the screen is visible before its comparator node has loaded.
 * Returning null means the current screen is not a reel screen.
 */
data class ReelAppData(
    val scriptSource: String,
    val comparisonResultCleanser: (String) -> String = { it },
    val eventType: Int = AccessibilityEvent.TYPE_VIEW_SCROLLED
)
