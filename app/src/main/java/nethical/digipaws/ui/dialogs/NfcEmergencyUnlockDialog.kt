package nethical.digipaws.ui.dialogs

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nethical.digipaws.R
import nethical.digipaws.databinding.DialogNfcEmergencyUnlockBinding
import nethical.digipaws.services.AppBlockerService
import nethical.digipaws.utils.SavedPreferencesLoader

class NfcEmergencyUnlockDialog(
    savedPreferencesLoader: SavedPreferencesLoader,
    private val onUnlocked: () -> Unit
) : BaseDialog(savedPreferencesLoader) {

    private var _binding: DialogNfcEmergencyUnlockBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNfcEmergencyUnlockBinding.inflate(layoutInflater)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.emergency_unlock_title))
            .setView(binding.root)
            .setPositiveButton(getString(R.string.unlock)) { _, _ ->
                attemptUnlock()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun attemptUnlock() {
        val enteredPassword = binding.password.text.toString()
        val savedData = savedPreferencesLoader?.getNfcLockModeData() ?: return

        if (enteredPassword == savedData.emergencyPassword) {
            // Disable NFC lock mode
            val newData = savedData.copy(
                isEnabled = false,
                enabledAt = -1,
                autoUnlockAt = -1
            )
            savedPreferencesLoader?.saveNfcLockModeData(newData)

            // Send refresh broadcast
            context?.sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_NFC_LOCK_MODE))

            Toast.makeText(requireContext(), getString(R.string.nfc_lock_unlocked), Toast.LENGTH_SHORT).show()
            onUnlocked()
        } else {
            Toast.makeText(requireContext(), getString(R.string.incorrect_password), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
