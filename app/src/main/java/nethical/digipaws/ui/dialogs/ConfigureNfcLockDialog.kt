package nethical.digipaws.ui.dialogs

import android.app.Dialog
import android.app.PendingIntent
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nethical.digipaws.Constants
import nethical.digipaws.R
import nethical.digipaws.blockers.NfcLockBlocker
import nethical.digipaws.databinding.DialogNfcLockConfigBinding
import nethical.digipaws.utils.SavedPreferencesLoader

class ConfigureNfcLockDialog(
    savedPreferencesLoader: SavedPreferencesLoader,
    private val onDismissed: () -> Unit
) : BaseDialog(savedPreferencesLoader) {

    private var _binding: DialogNfcLockConfigBinding? = null
    private val binding get() = _binding!!

    private var nfcAdapter: NfcAdapter? = null
    private var isRegisteringTag = false
    private var registeredTagIds: HashSet<String> = hashSetOf()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNfcLockConfigBinding.inflate(layoutInflater)

        val previousData = savedPreferencesLoader?.getNfcLockModeData()
            ?: NfcLockBlocker.NfcLockModeData()
        registeredTagIds = HashSet(previousData.registeredTagIds)

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        setupViews(previousData)

        return MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                saveConfiguration()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }

    private fun setupViews(data: NfcLockBlocker.NfcLockModeData) {
        // Mode type
        when (data.modeType) {
            Constants.NFC_LOCK_MODE_BLOCK_SELECTED -> binding.blockSelected.isChecked = true
            Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED -> binding.blockAll.isChecked = true
        }

        // Failsafe hours picker
        binding.failsafeHoursPicker.minValue = 0
        binding.failsafeHoursPicker.maxValue = 168
        binding.failsafeHoursPicker.setValue(data.failsafeHours)
        binding.failsafeHoursPicker.setUnit("hours")

        // Emergency password
        binding.emergencyPassword.setText(data.emergencyPassword)

        // Tag validation
        binding.requireTagValidation.isChecked = data.requireTagValidation
        updateTagValidationViews(data.requireTagValidation)

        binding.requireTagValidation.setOnCheckedChangeListener { _, isChecked ->
            updateTagValidationViews(isChecked)
        }

        binding.btnRegisterTag.setOnClickListener {
            startTagRegistration()
        }

        updateRegisteredTagsCount()
    }

    private fun updateTagValidationViews(isEnabled: Boolean) {
        binding.btnRegisterTag.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.registeredTagsCount.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun updateRegisteredTagsCount() {
        binding.registeredTagsCount.text =
            getString(R.string.registered_tags, registeredTagIds.size)
    }

    private fun startTagRegistration() {
        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
            Toast.makeText(requireContext(), "NFC is not available or disabled", Toast.LENGTH_SHORT)
                .show()
            return
        }

        isRegisteringTag = true
        Toast.makeText(requireContext(), getString(R.string.scan_tag_to_register), Toast.LENGTH_LONG).show()
        enableForegroundDispatch()
    }

    private fun enableForegroundDispatch() {
        val activity = requireActivity()
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_MUTABLE
        )
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED))
        nfcAdapter?.enableForegroundDispatch(activity, pendingIntent, filters, null)
    }

    private fun disableForegroundDispatch() {
        try {
            nfcAdapter?.disableForegroundDispatch(requireActivity())
        } catch (e: Exception) {
            // Activity might not be in foreground
        }
    }

    fun handleTagDiscovered(tag: Tag) {
        if (isRegisteringTag) {
            val tagId = bytesToHex(tag.id)
            registeredTagIds.add(tagId)
            updateRegisteredTagsCount()
            Toast.makeText(requireContext(), getString(R.string.tag_registered), Toast.LENGTH_SHORT).show()
            isRegisteringTag = false
            disableForegroundDispatch()
        }
    }

    private fun saveConfiguration() {
        val modeType = when {
            binding.blockAll.isChecked -> Constants.NFC_LOCK_MODE_BLOCK_ALL_EX_SELECTED
            else -> Constants.NFC_LOCK_MODE_BLOCK_SELECTED
        }

        val previousData = savedPreferencesLoader?.getNfcLockModeData()
            ?: NfcLockBlocker.NfcLockModeData()

        val newData = previousData.copy(
            modeType = modeType,
            failsafeHours = binding.failsafeHoursPicker.getValue(),
            emergencyPassword = binding.emergencyPassword.text.toString(),
            requireTagValidation = binding.requireTagValidation.isChecked,
            registeredTagIds = registeredTagIds
        )

        savedPreferencesLoader?.saveNfcLockModeData(newData)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed()
    }

    override fun onResume() {
        super.onResume()
        if (isRegisteringTag) {
            enableForegroundDispatch()
        }
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
