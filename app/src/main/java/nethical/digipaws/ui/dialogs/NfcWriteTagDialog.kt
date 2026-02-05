package nethical.digipaws.ui.dialogs

import android.app.Dialog
import android.app.PendingIntent
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nethical.digipaws.R
import nethical.digipaws.databinding.DialogNfcWriteTagBinding
import nethical.digipaws.utils.SavedPreferencesLoader

class NfcWriteTagDialog(
    savedPreferencesLoader: SavedPreferencesLoader,
    private val onDismissed: () -> Unit
) : BaseDialog(savedPreferencesLoader) {

    private var _binding: DialogNfcWriteTagBinding? = null
    private val binding get() = _binding!!

    private var nfcAdapter: NfcAdapter? = null
    private var dialog: Dialog? = null
    private var pendingTag: Tag? = null

    companion object {
        const val NFC_URI = "digipaws://nfclock/toggle"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNfcWriteTagBinding.inflate(layoutInflater)

        nfcAdapter = NfcAdapter.getDefaultAdapter(requireContext())

        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) {
            binding.description.text = "NFC is not available or disabled"
            binding.progress.visibility = View.GONE
        }

        dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setNegativeButton(getString(R.string.cancel), null)
            .create()

        return dialog!!
    }

    override fun onResume() {
        super.onResume()
        enableForegroundDispatch()
    }

    override fun onPause() {
        super.onPause()
        disableForegroundDispatch()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed()
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
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )
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
        binding.status.text = getString(R.string.writing_tag)
        pendingTag = tag

        // Check if tag has existing data
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val existingMessage = ndef.ndefMessage
                ndef.close()

                if (existingMessage != null && existingMessage.records.isNotEmpty()) {
                    // Tag has existing data - prompt user
                    showOverwriteConfirmation(tag, existingMessage)
                    return
                }
            } catch (e: Exception) {
                // Could not read existing data, proceed with write
            }
        }

        // No existing data or couldn't read, proceed with write
        writeTag(tag)
    }

    private fun showOverwriteConfirmation(tag: Tag, existingMessage: NdefMessage) {
        val existingContent = try {
            existingMessage.records.firstOrNull()?.let { record ->
                when {
                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_URI) -> {
                        val payload = record.payload
                        if (payload.isNotEmpty()) {
                            val prefixCode = payload[0].toInt()
                            val uriPrefix = getUriPrefix(prefixCode)
                            uriPrefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
                        } else "Unknown"
                    }
                    record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                    record.type.contentEquals(NdefRecord.RTD_TEXT) -> {
                        val payload = record.payload
                        val languageCodeLength = payload[0].toInt() and 0x3F
                        String(payload, 1 + languageCodeLength, payload.size - 1 - languageCodeLength, Charsets.UTF_8)
                    }
                    else -> "Binary data (${existingMessage.byteArrayLength} bytes)"
                }
            } ?: "Unknown data"
        } catch (e: Exception) {
            "Could not read existing data"
        }

        binding.progress.visibility = View.GONE
        binding.status.text = "Tag contains existing data"

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Overwrite Tag?")
            .setMessage("This tag already contains data:\n\n$existingContent\n\nDo you want to overwrite it with the DigiPaws NFC Lock toggle?")
            .setPositiveButton("Overwrite") { _, _ ->
                binding.progress.visibility = View.VISIBLE
                binding.status.text = getString(R.string.writing_tag)
                writeTag(tag)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                binding.progress.visibility = View.VISIBLE
                binding.status.text = "Waiting for NFC tag..."
            }
            .show()
    }

    private fun getUriPrefix(code: Int): String {
        return when (code) {
            0x00 -> ""
            0x01 -> "http://www."
            0x02 -> "https://www."
            0x03 -> "http://"
            0x04 -> "https://"
            0x05 -> "tel:"
            0x06 -> "mailto:"
            else -> ""
        }
    }

    private fun writeTag(tag: Tag) {
        try {
            val record = NdefRecord.createUri(NFC_URI)
            val message = NdefMessage(arrayOf(record))

            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    showError("Tag is read-only and cannot be written")
                    ndef.close()
                    return
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    showError("Tag storage is too small (${ndef.maxSize} bytes available, ${message.toByteArray().size} needed)")
                    ndef.close()
                    return
                }
                ndef.writeNdefMessage(message)
                ndef.close()
                onWriteSuccess()
            } else {
                // Try to format the tag
                val ndefFormatable = NdefFormatable.get(tag)
                if (ndefFormatable != null) {
                    ndefFormatable.connect()
                    ndefFormatable.format(message)
                    ndefFormatable.close()
                    onWriteSuccess()
                } else {
                    showError("Tag does not support NDEF format")
                }
            }
        } catch (e: Exception) {
            showError("Write failed: ${e.message}")
        }
    }

    private fun showError(message: String) {
        binding.progress.visibility = View.GONE
        binding.status.text = message
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun onWriteSuccess() {
        binding.progress.visibility = View.GONE
        binding.status.text = getString(R.string.tag_written_success)
        Toast.makeText(requireContext(), getString(R.string.tag_written_success), Toast.LENGTH_SHORT).show()
        dialog?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
