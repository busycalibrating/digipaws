package nethical.digipaws.ui.activity

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import nethical.digipaws.blockers.NfcLockBlocker
import nethical.digipaws.services.AppBlockerService
import nethical.digipaws.utils.SavedPreferencesLoader

class NfcTagActivity : AppCompatActivity() {

    private lateinit var savedPreferencesLoader: SavedPreferencesLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedPreferencesLoader = SavedPreferencesLoader(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            val tagId = tag?.id?.let { bytesToHex(it) }
            toggleNfcLockMode(tagId)
        } else {
            Toast.makeText(this, "Invalid NFC action", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun toggleNfcLockMode(tagId: String?) {
        val data = savedPreferencesLoader.getNfcLockModeData()

        // Validate tag if required
        if (data.requireTagValidation && tagId != null) {
            if (!data.registeredTagIds.contains(tagId)) {
                Toast.makeText(this, "Unregistered NFC tag", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
        }

        val newData: NfcLockBlocker.NfcLockModeData
        if (data.isEnabled) {
            // Disable NFC lock mode
            newData = data.copy(
                isEnabled = false,
                enabledAt = -1,
                autoUnlockAt = -1
            )
            Toast.makeText(this, "NFC Lock disabled", Toast.LENGTH_SHORT).show()
        } else {
            // Enable NFC lock mode
            val autoUnlockAt = if (data.failsafeHours > 0) {
                System.currentTimeMillis() + (data.failsafeHours * 3600000L)
            } else {
                -1L
            }
            newData = data.copy(
                isEnabled = true,
                enabledAt = System.currentTimeMillis(),
                autoUnlockAt = autoUnlockAt
            )
            Toast.makeText(this, "NFC Lock enabled", Toast.LENGTH_SHORT).show()
        }

        savedPreferencesLoader.saveNfcLockModeData(newData)
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_NFC_LOCK_MODE))
        finish()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
