package com.massagepro.ui.clients

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.massagepro.R
import com.massagepro.data.model.Client
import com.massagepro.databinding.FragmentAddEditClientBinding
import com.massagepro.data.repository.ClientRepository
import kotlinx.coroutines.launch

class AddEditClientFragment : Fragment() {

    private var _binding: FragmentAddEditClientBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClientsViewModel by viewModels {
        ClientsViewModelFactory(ClientRepository())
    }
    private val args: AddEditClientFragmentArgs by navArgs()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchContactPicker()
            else Toast.makeText(requireContext(), getString(R.string.contacts_permission_denied), Toast.LENGTH_SHORT).show()
        }

        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { retrieveContactDetails(it) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEditClientBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val clientId = args.clientId
        if (clientId != -1L) {
            lifecycleScope.launch {
                viewModel.getClientById(clientId.toLong())?.let { client ->
                    binding.editTextClientName.setText(client.name)
                    binding.editTextClientPhone.setText(client.phone ?: "")
                    binding.editTextClientNotes.setText(client.notes)
                }
            }
        }

        binding.buttonSaveClient.setOnClickListener {
            saveClient()
        }
        binding.buttonImportContact.setOnClickListener { startContactImport() }
    }

    private fun saveClient() {
        lifecycleScope.launch {
            try {
                val name = binding.editTextClientName.text.toString().trim()
                val phone = binding.editTextClientPhone.text.toString().trim()
                val notes = binding.editTextClientNotes.text.toString().trim()

                if (name.isEmpty()) {
                    binding.textInputClientName.error = getString(R.string.client_name_empty_error)
                    return@launch
                }

                val normalizedPhone = normalizePhoneNumber(phone)

                val client = if (args.clientId == -1L) {
                    Client(
                        name = name,
                        phone = normalizedPhone,
                        notes = notes
                    )
                } else {
                    Client(
                        id = args.clientId.toLong(),
                        name = name,
                        phone = normalizedPhone,
                        notes = notes
                    )
                }

                val success = if (args.clientId == -1L) {
                    viewModel.insertClient(client)
                } else {
                    viewModel.updateClient(client)
                }

                if (success) {
                    val messageId = if (args.clientId == -1L) R.string.client_added_toast else R.string.client_updated_toast
                    Toast.makeText(requireContext(), getString(messageId), Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Ошибка при сохранении клиента", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isValidPhoneNumber(phone: String): Boolean =
        phone.replace("[\\s\\-()]".toRegex(), "").matches("^(\\+38)?0\\d{9}$".toRegex())

    private fun normalizePhoneNumber(phone: String): String =
        phone.replace("[^\\d+]".toRegex(), "")
            .let { if (it.startsWith("0") && !it.startsWith("+38")) "+38$it" else if (it.startsWith("38") && !it.startsWith("+")) "+$it" else it }

    private fun startContactImport() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED -> launchContactPicker()
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> showRationaleDialog()
            else -> requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.contacts_permission_title))
            .setMessage(getString(R.string.contacts_permission_message))
            .setPositiveButton(getString(R.string.contacts_permission_grant_button)) { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton(getString(R.string.cancel_button_text), null)
            .show()
    }

    private fun retrieveContactDetails(contactUri: Uri) {
        var contactId: String? = null
        requireContext().contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
            }
        }

        contactId?.let { id ->
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
            val args = arrayOf(id)

            requireContext().contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    binding.editTextClientName.setText(name)
                    binding.editTextClientPhone.setText(phone)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.contact_details_not_found), Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(requireContext(), getString(R.string.contact_no_phone_number), Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), getString(R.string.contact_id_not_found), Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_message))
            .setPositiveButton(getString(R.string.go_to_settings_button)) { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.cancel_button_text), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
