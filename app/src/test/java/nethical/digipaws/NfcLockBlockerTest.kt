package nethical.digipaws

import nethical.digipaws.blockers.NfcLockBlocker
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NfcLockBlockerTest {

    private lateinit var blocker: NfcLockBlocker

    @Before
    fun setUp() {
        blocker = NfcLockBlocker()
    }

    @Test
    fun testDisabledMode_blocksNothing() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = false,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED
        )
        blocker.selectedApps = hashSetOf("com.example.blocked")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertFalse(result.isBlocked)
        assertFalse(result.isRequestingToUpdateSPData)
    }

    @Test
    fun testBlockSelectedMode_blocksSelectedApp() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED
        )
        blocker.selectedApps = hashSetOf("com.example.blocked", "com.example.blocked2")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertTrue(result.isBlocked)
    }

    @Test
    fun testBlockSelectedMode_allowsUnselectedApp() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED
        )
        blocker.selectedApps = hashSetOf("com.example.blocked")

        val result = blocker.doesAppNeedToBeBlocked("com.example.allowed")

        assertFalse(result.isBlocked)
    }

    @Test
    fun testBlockAllExceptMode_blocksUnselectedApp() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED
        )
        blocker.selectedApps = hashSetOf("com.example.allowed")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertTrue(result.isBlocked)
    }

    @Test
    fun testBlockAllExceptMode_allowsSelectedApp() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED
        )
        blocker.selectedApps = hashSetOf("com.example.allowed")

        val result = blocker.doesAppNeedToBeBlocked("com.example.allowed")

        assertFalse(result.isBlocked)
    }

    @Test
    fun testFailsafe_autoDisablesWhenExpired() {
        val pastTime = System.currentTimeMillis() - 1000 // 1 second in the past
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED,
            autoUnlockAt = pastTime
        )
        blocker.selectedApps = hashSetOf("com.example.blocked")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertFalse(result.isBlocked)
        assertTrue(result.isRequestingToUpdateSPData)
        assertFalse(blocker.nfcLockModeData.isEnabled)
    }

    @Test
    fun testFailsafeDisabled_neverAutoDisables() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED,
            autoUnlockAt = -1 // Failsafe disabled
        )
        blocker.selectedApps = hashSetOf("com.example.blocked")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertTrue(result.isBlocked)
        assertFalse(result.isRequestingToUpdateSPData)
        assertTrue(blocker.nfcLockModeData.isEnabled)
    }

    @Test
    fun testFailsafe_stillActiveBeforeExpiry() {
        val futureTime = System.currentTimeMillis() + 3600000 // 1 hour in the future
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED,
            autoUnlockAt = futureTime
        )
        blocker.selectedApps = hashSetOf("com.example.blocked")

        val result = blocker.doesAppNeedToBeBlocked("com.example.blocked")

        assertTrue(result.isBlocked)
        assertEquals(futureTime, result.autoUnlockAt)
        assertFalse(result.isRequestingToUpdateSPData)
    }

    @Test
    fun testEmptySelectedApps_blockSelectedMode_blocksNothing() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_SELECTED
        )
        blocker.selectedApps = hashSetOf()

        val result = blocker.doesAppNeedToBeBlocked("com.example.app")

        assertFalse(result.isBlocked)
    }

    @Test
    fun testEmptySelectedApps_blockAllExceptMode_blocksAll() {
        blocker.nfcLockModeData = NfcLockBlocker.NfcLockModeData(
            isEnabled = true,
            modeType = Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED
        )
        blocker.selectedApps = hashSetOf()

        val result = blocker.doesAppNeedToBeBlocked("com.example.app")

        assertTrue(result.isBlocked)
    }
}
