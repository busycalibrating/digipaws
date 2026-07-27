package neth.iecal.curbox.ui.fragments.main.reducers.blockertools.shared

import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import neth.iecal.curbox.R
import neth.iecal.curbox.data.models.AppBlockerWarningScreenConfig
import neth.iecal.curbox.databinding.FragmentWarningConfigBinding

internal class WarningConfigFormController(
    private val fragment: Fragment,
    private val binding: FragmentWarningConfigBinding,
    private val onEachOpenChanged: () -> Unit
) {
    private var supportsOnEachOpen = false

    fun bind(
        config: AppBlockerWarningScreenConfig,
        isNew: Boolean,
        supportsOnEachOpen: Boolean
    ) {
        this.supportsOnEachOpen = supportsOnEachOpen
        val isOnEachOpenEnabled = supportsOnEachOpen && config.isOnOpenConfig
        binding.switchOnEachOpen.isVisible = supportsOnEachOpen
        binding.switchOnEachOpen.isChecked = isOnEachOpenEnabled
        binding.unlockChallengeDropdown.setAdapter(
            WarningUnlockOptionAdapter(fragment.requireContext(), warningChallengeOptions)
        )

        if (isNew) {
            hideUnlockConfiguration()
        } else {
            val selection = WarningUnlockSelectionMapper.fromConfig(
                config,
                isOnEachOpenEnabled
            )
            binding.unlockChallengeDropdown.setText(
                warningChallengeOptions[selection.challengeIndex].title,
                false
            )
            updateSecondaryDropdown(selection.challengeIndex)
            if (selection.secondaryIndex != -1) {
                binding.secondaryBehaviorDropdown.setText(
                    secondaryOptions(selection.challengeIndex)[selection.secondaryIndex].title,
                    false
                )
            }
            updateUiVisibility(
                selection.challengeIndex,
                selection.secondaryIndex,
                animate = isOnEachOpenEnabled
            )
        }

        binding.typingSentenceEdit.setText(config.typingSentence)
        binding.intentMinLengthSlider.value = config.minIntentLength.toFloat().coerceIn(1f, 100f)
        updateIntentMinLengthTitle(binding.intentMinLengthSlider.value.toInt())

        binding.fixedTimeSlider.value =
            (config.timeInterval / 60_000).toFloat().coerceIn(1f, 120f)
        updateFixedTimeTitle(binding.fixedTimeSlider.value.toInt())

        binding.proceedDelaySlider.value =
            config.proceedDelayInSecs.toFloat().coerceIn(0f, 60f)
        updateProceedDelayTitle(binding.proceedDelaySlider.value.toInt())

        binding.proceedLimitSwitch.isChecked = config.proceedLimitEnabled
        binding.proceedLimitContainer.isVisible = config.proceedLimitEnabled

        binding.allowedProceedsSlider.value =
            config.allowedProceeds.toFloat().coerceIn(1f, 20f)
        updateAllowedProceedsTitle(binding.allowedProceedsSlider.value.toInt())

        val totalMinutes = config.proceedsTimeWindowMn
        val (initialUnitIndex, initialSliderValue) = when {
            totalMinutes > 0 && totalMinutes % MINUTES_PER_DAY == 0 ->
                DAYS_INDEX to (totalMinutes / MINUTES_PER_DAY).toFloat().coerceIn(1f, 30f)
            totalMinutes > 0 && totalMinutes % MINUTES_PER_HOUR == 0 ->
                HOURS_INDEX to (totalMinutes / MINUTES_PER_HOUR).toFloat().coerceIn(1f, 24f)
            else -> MINUTES_INDEX to totalMinutes.toFloat().coerceIn(1f, 60f)
        }
        binding.proceedWindowUnitBtn.text = unitOptions()[initialUnitIndex]
        updateProceedWindowSliderBounds(initialUnitIndex)
        binding.proceedWindowSlider.value = initialSliderValue
        updateProceedWindowTitle(initialUnitIndex, initialSliderValue.toInt())

        binding.warningMsgEdit.setText(config.message)
        binding.switchVibrateBrightness.isChecked = config.vibrateAndIncBrightness
    }

    fun setupListeners(onSave: () -> Unit) {
        binding.unlockChallengeDropdown.setOnItemClickListener { _, _, position, _ ->
            updateSecondaryDropdown(position)
            val isOnEachOpenEnabled = isOnEachOpenEnabled()
            if (!(isOnEachOpenEnabled &&
                    position == WarningUnlockSelectionMapper.NO_EFFORT_INDEX)) {
                binding.secondaryBehaviorDropdown.setText("", false)
            }
            val secondaryIndex =
                if (isOnEachOpenEnabled &&
                    position == WarningUnlockSelectionMapper.NO_EFFORT_INDEX) {
                    WarningUnlockSelectionMapper.FIXED_TIME_INDEX
                } else {
                    -1
                }
            updateUiVisibility(position, secondaryIndex, animate = true)
        }

        binding.secondaryBehaviorDropdown.setOnItemClickListener { _, _, position, _ ->
            updateUiVisibility(selectedChallengeIndex(), position, animate = true)
        }

        binding.fixedTimeSlider.addOnChangeListener { _, value, _ ->
            updateFixedTimeTitle(value.toInt())
        }
        binding.proceedDelaySlider.addOnChangeListener { _, value, _ ->
            updateProceedDelayTitle(value.toInt())
        }
        binding.proceedLimitSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.proceedLimitContainer.isVisible = isChecked
        }
        binding.switchOnEachOpen.setOnCheckedChangeListener { _, isChecked ->
            val challengeIndex = selectedChallengeIndex()
            if (isChecked && challengeIndex == WarningUnlockSelectionMapper.NO_EFFORT_INDEX) {
                binding.secondaryBehaviorDropdown.setText(
                    warningNoEffortOptions[WarningUnlockSelectionMapper.FIXED_TIME_INDEX].title,
                    false
                )
            }
            updateSecondaryDropdown(challengeIndex)
            updateUiVisibility(
                challengeIndex,
                selectedSecondaryIndex(challengeIndex),
                animate = true
            )
            onEachOpenChanged()
        }
        binding.allowedProceedsSlider.addOnChangeListener { _, value, _ ->
            updateAllowedProceedsTitle(value.toInt())
        }
        binding.proceedWindowSlider.addOnChangeListener { _, value, _ ->
            updateProceedWindowTitle(selectedUnitIndex(), value.toInt())
        }
        binding.proceedWindowUnitBtn.setOnClickListener { button ->
            showProceedWindowUnitMenu(button)
        }
        binding.intentMinLengthSlider.addOnChangeListener { _, value, _ ->
            updateIntentMinLengthTitle(value.toInt())
        }
        binding.advancedSettingsHeader.setOnClickListener {
            val isCurrentlyVisible = binding.advancedSettingsContent.isVisible
            TransitionManager.beginDelayedTransition(
                binding.mainContentContainer,
                AutoTransition()
            )
            binding.advancedSettingsContent.isVisible = !isCurrentlyVisible
            binding.advancedSettingsArrow.animate()
                .rotation(if (isCurrentlyVisible) 0f else 90f)
                .start()
        }
        binding.saveconfigs.setOnClickListener { onSave() }
    }

    fun createConfig(
        qrKeys: Map<String, Long>,
        nfcKeys: Map<String, Long>
    ): AppBlockerWarningScreenConfig {
        val flags = WarningUnlockSelectionMapper.toFlags(
            selectedChallengeIndex(),
            selectedSecondaryIndex(selectedChallengeIndex())
        )
        return AppBlockerWarningScreenConfig(
            message = binding.warningMsgEdit.text.toString(),
            timeInterval = binding.fixedTimeSlider.value.toInt() * 60_000,
            isDynamicIntervalSettingAllowed = flags.isDynamicIntervalSettingAllowed,
            isProceedDisabled = flags.isProceedDisabled,
            isWarningDialogHidden = false,
            isQrUnlockRequirementEnabled = flags.isQrUnlockRequirementEnabled,
            qrKeys = if (flags.isQrUnlockRequirementEnabled) qrKeys else emptyMap(),
            isNfcUnlockRequirementEnabled = flags.isNfcUnlockRequirementEnabled,
            nfcKeys = if (flags.isNfcUnlockRequirementEnabled) nfcKeys else emptyMap(),
            isTypingRequirementEnabled = flags.isTypingRequirementEnabled,
            typingSentence = binding.typingSentenceEdit.text.toString(),
            isIntentRequirementEnabled = flags.isIntentRequirementEnabled,
            minIntentLength = binding.intentMinLengthSlider.value.toInt(),
            proceedDelayInSecs = binding.proceedDelaySlider.value.toInt(),
            vibrateAndIncBrightness = binding.switchVibrateBrightness.isChecked,
            proceedLimitEnabled = binding.proceedLimitSwitch.isChecked,
            allowedProceeds = binding.allowedProceedsSlider.value.toInt(),
            proceedsTimeWindowMn = selectedProceedWindowMinutes(),
            isOnOpenConfig = isOnEachOpenEnabled()
        )
    }

    fun isOnEachOpenEnabled(): Boolean {
        return supportsOnEachOpen && binding.switchOnEachOpen.isChecked
    }

    private fun hideUnlockConfiguration() {
        binding.secondaryBehaviorLayout.isVisible = false
        binding.timingContainer.isVisible = false
        binding.proceedDelayContainer.isVisible = false
        binding.qrSetupContainer.isVisible = false
        binding.nfcSetupContainer.isVisible = false
        binding.typingSetupContainer.isVisible = false
    }

    private fun updateSecondaryDropdown(challengeIndex: Int) {
        if (isOnEachOpenEnabled() &&
            challengeIndex == WarningUnlockSelectionMapper.NO_EFFORT_INDEX) {
            binding.secondaryBehaviorLayout.isVisible = false
            binding.secondaryBehaviorDropdown.setText(
                warningNoEffortOptions[WarningUnlockSelectionMapper.FIXED_TIME_INDEX].title,
                false
            )
            return
        }
        val options = when (challengeIndex) {
            WarningUnlockSelectionMapper.EFFORT_INDEX -> warningEffortOptions
            WarningUnlockSelectionMapper.NO_EFFORT_INDEX -> warningNoEffortOptions
            else -> null
        }
        if (options == null) {
            binding.secondaryBehaviorLayout.isVisible = false
        } else {
            binding.secondaryBehaviorDropdown.setAdapter(
                WarningUnlockOptionAdapter(fragment.requireContext(), options)
            )
            binding.secondaryBehaviorLayout.isVisible = true
        }
    }

    private fun updateUiVisibility(
        challengeIndex: Int,
        secondaryIndex: Int,
        animate: Boolean
    ) {
        if (animate) {
            TransitionManager.beginDelayedTransition(
                binding.mainContentContainer,
                AutoTransition()
            )
        }
        val usesSharedTiming = when (challengeIndex) {
            WarningUnlockSelectionMapper.NO_EFFORT_INDEX ->
                secondaryIndex == WarningUnlockSelectionMapper.FIXED_TIME_INDEX
            WarningUnlockSelectionMapper.EFFORT_INDEX ->
                secondaryIndex == 1 || secondaryIndex == 2
            else -> false
        }
        binding.timingContainer.isVisible = !isOnEachOpenEnabled() && usesSharedTiming
        binding.proceedDelayContainer.isVisible =
            challengeIndex != WarningUnlockSelectionMapper.NEVER_UNLOCK_INDEX &&
                challengeIndex != -1
        binding.qrSetupContainer.isVisible =
            challengeIndex == WarningUnlockSelectionMapper.EFFORT_INDEX &&
                secondaryIndex == 0
        binding.typingSetupContainer.isVisible =
            challengeIndex == WarningUnlockSelectionMapper.EFFORT_INDEX &&
                secondaryIndex == 1
        binding.intentSetupContainer.isVisible =
            challengeIndex == WarningUnlockSelectionMapper.EFFORT_INDEX &&
                secondaryIndex == 2
        binding.nfcSetupContainer.isVisible =
            challengeIndex == WarningUnlockSelectionMapper.EFFORT_INDEX &&
                secondaryIndex == 3
    }

    private fun selectedChallengeIndex(): Int {
        val selectedTitle = binding.unlockChallengeDropdown.text.toString()
        return warningChallengeOptions.indexOfFirst { it.title == selectedTitle }
    }

    private fun selectedSecondaryIndex(challengeIndex: Int): Int {
        val selectedTitle = binding.secondaryBehaviorDropdown.text.toString()
        return secondaryOptions(challengeIndex).indexOfFirst { it.title == selectedTitle }
    }

    private fun secondaryOptions(challengeIndex: Int): List<WarningUnlockOption> {
        return when (challengeIndex) {
            WarningUnlockSelectionMapper.EFFORT_INDEX -> warningEffortOptions
            WarningUnlockSelectionMapper.NO_EFFORT_INDEX -> warningNoEffortOptions
            else -> emptyList()
        }
    }

    private fun showProceedWindowUnitMenu(anchor: View) {
        val options = unitOptions()
        PopupMenu(fragment.requireContext(), anchor).apply {
            options.forEachIndexed { index, option ->
                menu.add(0, index, index, option)
            }
            setOnMenuItemClickListener { item ->
                binding.proceedWindowUnitBtn.text = item.title
                updateProceedWindowSliderBounds(item.itemId)
                updateProceedWindowTitle(
                    item.itemId,
                    binding.proceedWindowSlider.value.toInt()
                )
                true
            }
            show()
        }
    }

    private fun updateProceedWindowSliderBounds(unitIndex: Int) {
        val (minimum, maximum) = when (unitIndex) {
            HOURS_INDEX -> 1f to 24f
            DAYS_INDEX -> 1f to 30f
            else -> 1f to 60f
        }
        val currentValue = binding.proceedWindowSlider.value
        val newValue = currentValue.coerceIn(minimum, maximum)
        if (currentValue < minimum || currentValue > maximum) {
            binding.proceedWindowSlider.value = minimum
        }
        binding.proceedWindowSlider.valueFrom = minimum
        binding.proceedWindowSlider.valueTo = maximum
        binding.proceedWindowSlider.value = newValue
    }

    private fun selectedProceedWindowMinutes(): Int {
        val value = binding.proceedWindowSlider.value.toInt()
        return when (selectedUnitIndex()) {
            HOURS_INDEX -> value * MINUTES_PER_HOUR
            DAYS_INDEX -> value * MINUTES_PER_DAY
            else -> value
        }
    }

    private fun selectedUnitIndex(): Int {
        return unitOptions().indexOf(binding.proceedWindowUnitBtn.text.toString())
            .coerceAtLeast(MINUTES_INDEX)
    }

    private fun updateProceedWindowTitle(unitIndex: Int, value: Int) {
        binding.proceedWindowTitle.text = fragment.getString(
            R.string.warning_time_window,
            value,
            unitOptions()[unitIndex]
        )
    }

    private fun updateFixedTimeTitle(value: Int) {
        binding.timingTitle.text = fragment.getString(
            R.string.warning_fixed_unlock_duration,
            value
        )
    }

    private fun updateProceedDelayTitle(value: Int) {
        binding.proceedDelayTitle.text = fragment.getString(
            R.string.warning_wait_before_unlock,
            value
        )
    }

    private fun updateAllowedProceedsTitle(value: Int) {
        binding.allowedProceedsTitle.text = fragment.getString(
            R.string.warning_allowed_proceeds,
            value
        )
    }

    private fun updateIntentMinLengthTitle(value: Int) {
        binding.intentMinLengthTitle.text = fragment.resources.getQuantityString(
            R.plurals.warning_intent_min_length,
            value,
            value
        )
    }

    private fun unitOptions(): List<String> {
        return listOf(
            fragment.getString(R.string.unit_minutes),
            fragment.getString(R.string.unit_hours),
            fragment.getString(R.string.unit_days)
        )
    }

    private companion object {
        const val MINUTES_INDEX = 0
        const val HOURS_INDEX = 1
        const val DAYS_INDEX = 2
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 1_440
    }
}
