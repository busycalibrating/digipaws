package nethical.digipaws.blockers

import nethical.digipaws.Constants

class NfcLockBlocker : BaseBlocker() {

    var nfcLockModeData = NfcLockModeData()
    var selectedApps: HashSet<String> = hashSetOf()

    /**
     * Check if app needs to be blocked for reasons related to NFC lock mode
     *
     * @param packageName
     * @return NfcLockResult
     */
    fun doesAppNeedToBeBlocked(packageName: String): NfcLockResult {
        if (!nfcLockModeData.isEnabled) {
            return NfcLockResult(isBlocked = false)
        }

        // Check failsafe timer - auto-unlock if expired
        if (nfcLockModeData.autoUnlockAt != -1L &&
            System.currentTimeMillis() > nfcLockModeData.autoUnlockAt
        ) {
            nfcLockModeData.isEnabled = false
            return NfcLockResult(isBlocked = false, isRequestingToUpdateSPData = true)
        }

        // Check if app should be blocked based on mode type
        when (nfcLockModeData.modeType) {
            Constants.NFC_LOCK_MODE_BLOCK_SELECTED -> {
                if (selectedApps.contains(packageName)) {
                    return NfcLockResult(
                        isBlocked = true,
                        autoUnlockAt = nfcLockModeData.autoUnlockAt
                    )
                }
            }

            Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED -> {
                if (!selectedApps.contains(packageName)) {
                    return NfcLockResult(
                        isBlocked = true,
                        autoUnlockAt = nfcLockModeData.autoUnlockAt
                    )
                }
            }
        }
        return NfcLockResult(isBlocked = false)
    }

    /**
     * Stores information related to NFC lock mode
     *
     * @property isEnabled Whether NFC lock mode is currently active
     * @property modeType Can either be [Constants.NFC_LOCK_MODE_BLOCK_SELECTED] or [Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED]
     * @property enabledAt Timestamp when NFC lock mode was enabled, -1 if not enabled
     * @property autoUnlockAt Timestamp when failsafe auto-unlock should occur, -1 if failsafe disabled
     * @property failsafeHours Number of hours for failsafe timer (0 = disabled, max 168)
     * @property emergencyPassword Password for emergency unlock
     * @property requireTagValidation Whether to validate tag UID before toggling
     * @property registeredTagIds Set of registered NFC tag UIDs for validation
     */
    data class NfcLockModeData(
        var isEnabled: Boolean = false,
        val modeType: Int = Constants.NFC_LOCK_MODE_BLOCK_SELECTED,
        val enabledAt: Long = -1,
        val autoUnlockAt: Long = -1,
        val failsafeHours: Int = 24,
        val emergencyPassword: String = "",
        val requireTagValidation: Boolean = false,
        val registeredTagIds: HashSet<String> = hashSetOf()
    )

    /**
     * NFC lock mode blocker check result
     *
     * @property isBlocked Whether the app should be blocked
     * @property autoUnlockAt Timestamp when auto-unlock will occur, -1 if failsafe disabled
     * @property isRequestingToUpdateSPData Returns true if nfcLockModeData needs to be saved because lock mode has ended
     */
    data class NfcLockResult(
        val isBlocked: Boolean,
        val autoUnlockAt: Long = -1,
        val isRequestingToUpdateSPData: Boolean = false
    )
}
