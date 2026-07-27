package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig

internal data class WarningUnlockOption(
    val title: String,
    val subtext: String,
    val isRecommended: Boolean = false
) {
    override fun toString(): String = title
}

internal val warningChallengeOptions = listOf(
    WarningUnlockOption("Never Unlock", "Total lockdown. No bypassing allowed."),
    WarningUnlockOption(
        "Require effort to unlock",
        "In behavioral psychology, adding physical friction breaks the automatic habit loop, giving your brain a necessary pause to reconsider.",
        true
    ),
    WarningUnlockOption("Wait to unlock", "Allows immediate access after a short wait.")
)

internal val warningEffortOptions = listOf(
    WarningUnlockOption(
        "Unlock requires QR/Barcode scanning",
        "Scan any code (like a product box) to unlock.",
        true
    ),
    WarningUnlockOption(
        "Unlock requires typing a sentence",
        "Precisely type a long sentence to prove focus."
    ),
    WarningUnlockOption(
        "Unlock requires stating an intent",
        "Briefly describe your goal before accessing."
    ),
    WarningUnlockOption(
        "Unlock requires scanning an NFC tag",
        "Tap a physical NFC tag to unlock."
    )
)

internal val warningNoEffortOptions = listOf(
    WarningUnlockOption(
        "Ask me how much time I need",
        "You choose the duration during each unlock."
    ),
    WarningUnlockOption(
        "Only give me a fixed amount of time",
        "Automatically locks after a set duration.",
        true
    )
)

internal data class WarningUnlockSelection(
    val challengeIndex: Int,
    val secondaryIndex: Int
)

internal data class WarningUnlockFlags(
    val isProceedDisabled: Boolean = false,
    val isQrUnlockRequirementEnabled: Boolean = false,
    val isTypingRequirementEnabled: Boolean = false,
    val isIntentRequirementEnabled: Boolean = false,
    val isNfcUnlockRequirementEnabled: Boolean = false,
    val isDynamicIntervalSettingAllowed: Boolean = false
)

internal object WarningUnlockSelectionMapper {
    const val NEVER_UNLOCK_INDEX = 0
    const val EFFORT_INDEX = 1
    const val NO_EFFORT_INDEX = 2
    const val FIXED_TIME_INDEX = 1

    fun fromConfig(
        config: AppBlockerWarningScreenConfig,
        isOnEachOpenEnabled: Boolean
    ): WarningUnlockSelection {
        val selection = when {
            config.isProceedDisabled -> WarningUnlockSelection(NEVER_UNLOCK_INDEX, -1)
            config.isQrUnlockRequirementEnabled -> WarningUnlockSelection(EFFORT_INDEX, 0)
            config.isTypingRequirementEnabled -> WarningUnlockSelection(EFFORT_INDEX, 1)
            config.isIntentRequirementEnabled -> WarningUnlockSelection(EFFORT_INDEX, 2)
            config.isNfcUnlockRequirementEnabled -> WarningUnlockSelection(EFFORT_INDEX, 3)
            config.isDynamicIntervalSettingAllowed -> WarningUnlockSelection(NO_EFFORT_INDEX, 0)
            else -> WarningUnlockSelection(NO_EFFORT_INDEX, FIXED_TIME_INDEX)
        }
        return if (isOnEachOpenEnabled && selection.challengeIndex == NO_EFFORT_INDEX) {
            selection.copy(secondaryIndex = FIXED_TIME_INDEX)
        } else {
            selection
        }
    }

    fun toFlags(challengeIndex: Int, secondaryIndex: Int): WarningUnlockFlags {
        return when (challengeIndex) {
            NEVER_UNLOCK_INDEX -> WarningUnlockFlags(isProceedDisabled = true)
            EFFORT_INDEX -> when (secondaryIndex) {
                0 -> WarningUnlockFlags(isQrUnlockRequirementEnabled = true)
                1 -> WarningUnlockFlags(isTypingRequirementEnabled = true)
                2 -> WarningUnlockFlags(isIntentRequirementEnabled = true)
                3 -> WarningUnlockFlags(isNfcUnlockRequirementEnabled = true)
                else -> WarningUnlockFlags()
            }
            NO_EFFORT_INDEX -> WarningUnlockFlags(
                isDynamicIntervalSettingAllowed = secondaryIndex == 0
            )
            else -> WarningUnlockFlags()
        }
    }
}

internal class WarningUnlockOptionAdapter(
    context: Context,
    options: List<WarningUnlockOption>
) : ArrayAdapter<WarningUnlockOption>(
    context,
    R.layout.item_dropdown_with_subtext,
    options
) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_dropdown_with_subtext, parent, false)
        val optionTitle = view.findViewById<TextView>(R.id.option_title)
        val optionSubtext = view.findViewById<TextView>(R.id.option_subtext)
        val recommendedBadge = view.findViewById<View>(R.id.recommended_badge)

        getItem(position)?.let { option ->
            optionTitle.text = option.title
            optionSubtext.text = option.subtext
            recommendedBadge.isVisible = option.isRecommended
        }
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return getView(position, convertView, parent)
    }
}
