package neth.iecal.curbox.hardcoded

import android.view.accessibility.AccessibilityEvent
import neth.iecal.curbox.data.models.ReelAppData

class ReelAppConfig {
    companion object {
        private fun isOnScreenFunction() = """
            fn isOnScreen(node) {
                if node == null or not node.visible { return false }
                return node.right > 0 and node.left < screen.width and
                    node.bottom > 0 and node.top < screen.height
            }
        """.trimIndent()

        private fun instagramScript(packageName: String) = """
            ${isOnScreenFunction()}

            viewer = find(id="$packageName:id/clips_viewer_view_pager")
            controls = find(id="$packageName:id/clips_ufi_component")
            if not isOnScreen(viewer) or not isOnScreen(controls) { return null }

            comparator = ""
            caption = find(id="$packageName:id/clips_captions_component")
            author = find(id="$packageName:id/clips_author_username")
            if caption != null { comparator = comparator + caption.subtreeText(32, 20000) }
            if author != null { comparator = comparator + author.subtreeText(32, 20000) }
            return comparator
        """.trimIndent()

        private fun youtubeScript(packageName: String) = """
            ${isOnScreenFunction()}

            viewer = find(id="$packageName:id/reel_recycler")
            if not isOnScreen(viewer) { return null }

            comparator = find(id="$packageName:id/reel_player_page_content")
            if comparator == null { return "" }
            return comparator.subtreeText(32, 20000)
        """.trimIndent()

        val reelData: Map<String, ReelAppData> = mapOf(
            "com.instagram.android" to ReelAppData(
                scriptSource = instagramScript("com.instagram.android")
            ),

            "com.myinsta.android" to ReelAppData(
                scriptSource = instagramScript("com.myinsta.android")
            ),

            "com.instagram.lite" to ReelAppData(
                scriptSource = """
                    ${isOnScreenFunction()}

                    reel = find(path="android.widget.FrameLayout[0]>androidx.recyclerview.widget.RecyclerView[0]>android.view.ViewGroup[0]>android.view.ViewGroup[0]")
                    if not isOnScreen(reel) or reel.h < screen.height * 0.8 { return null }

                    if event.fromIndex < 0 { return "" }
                    return "position:" + str(event.fromIndex)
                """.trimIndent(),
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                deduplicateComparators = false,
                initialComparator = "position:0"
            ),

            "com.google.android.youtube" to ReelAppData(
                scriptSource = youtubeScript("com.google.android.youtube"),
                comparisonResultCleanser = ::cleanYouTubeComparator,
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ),
            "app.revanced.android.youtube" to ReelAppData(
                scriptSource = youtubeScript("app.revanced.android.youtube"),
                comparisonResultCleanser = ::cleanYouTubeComparator,
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ),
            "app.morphe.android.youtube" to ReelAppData(
                scriptSource = youtubeScript("app.morphe.android.youtube"),
                comparisonResultCleanser = ::cleanYouTubeComparator,
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            ),

            "com.facebook.katana" to ReelAppData(
                scriptSource = """
                    ${isOnScreenFunction()}

                    reel = find(desc="Reels tab details")
                    if not isOnScreen(reel) { return null }

                    comparator = reel.parent()
                    if comparator == null { return "" }
                    return comparator.subtreeText(16, 4000)
                """.trimIndent()
            ),

            "com.snapchat.android" to ReelAppData(
                scriptSource = """
                    ${isOnScreenFunction()}

                    viewer = find(id="com.snapchat.android:id/spotlight_container")
                    if not isOnScreen(viewer) { return null }

                    comparator = find(id="com.snapchat.android:id/opera_viewer")
                    if comparator == null { return "" }
                    return comparator.subtreeText(32, 20000)
                """.trimIndent(),
                eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            )
        )

        private fun cleanYouTubeComparator(value: String): String {
            val compact = value.replace("\n", "")
            if (compact.contains("PostPostPostlike") || compact.length <= 15) return ""
            return compact.replace("Video Progress", "")
                .replace("Tap to watch live", "")
                .replace("Go to channel", "")
                .replace("soundVideo ProgressSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
                .replace("soundSearchMoreHomeHomeShortsShortsCreateSubscriptions", "")
        }
    }
}
