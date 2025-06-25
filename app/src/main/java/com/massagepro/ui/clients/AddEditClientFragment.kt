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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Client
import com.massagepro.databinding.FragmentAddEditClientBinding
import kotlinx.coroutines.launch

class AddEditClientFragment : Fragment() {

    private var _binding: FragmentAddEditClientBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClientsViewModel by viewModels { ClientsViewModelFactory((requireActivity().application as App).database.clientDao()) }
    private val args: AddEditClientFragmentArgs by navArgs()

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                launchContactPicker()
            } else {
                Toast.makeText(requireContext(), "Разрешение на чтение контактов отклонено.", Toast.LENGTH_SHORT).show()
            }
        }

        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contactUri: Uri? = result.data?.data
                contactUri?.let { uri ->
                    retrieveContactDetails(uri)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditClientBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Применяем отступы к корневому представлению
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Устанавливаем padding, сохраняя оригинальные боковые и нижний отступы, заданные в XML
            v.setPadding(v.paddingLeft, systemBars.top + v.paddingTop, v.paddingRight, v.paddingBottom)
            insets
        }

        val clientId = args.clientId
        if (clientId != -1) {
            lifecycleScope.launch {
                viewModel.getClientById(clientId)?.let { client ->
                    binding.editTextClientName.setText(client.name)
                    binding.editTextClientPhone.setText(client.phone)
                    binding.editTextClientNotes.setText(client.notes)
                }
            }
        }

        binding.buttonSaveClient.setOnClickListener { saveClient() }
        binding.buttonImportContact.setOnClickListener { startContactImport() }
    }

    private fun saveClient() {
        val name = binding.editTextClientName.text.toString().trim()
        val phone = binding.editTextClientPhone.text.toString().trim()
        val notes = binding.editTextClientNotes.text.toString().trim()

        if (name.isEmpty()) {
            binding.textInputClientName.error = getString(R.string.client_name_empty_error)
            return
        } else {
            binding.textInputClientName.error = null
        }

        if (phone.isNotEmpty() && !isValidPhoneNumber(phone)) {
            binding.textInputClientPhone.error = getString(R.string.phone_format_error_flexible)
            return
        } else {
            binding.textInputClientPhone.error = null
        }

        val normalizedPhone = if (phone.isNotEmpty()) normalizePhoneNumber(phone) else null

        val client = if (args.clientId == -1) {
            Client(name = name, phone = normalizedPhone, notes = notes.ifEmpty { null })
        } else {
            Client(id = args.clientId, name = name, phone = normalizedPhone, notes = notes.ifEmpty { null })
        }

        lifecycleScope.launch {
            if (args.clientId == -1) {
                viewModel.insertClient(client)
                Toast.makeText(requireContext(), getString(R.string.client_added_toast), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateClient(client)
                Toast.makeText(requireContext(), getString(R.string.client_updated_toast), Toast.LENGTH_SHORT).show()
            }
            findNavController().popBackStack()
        }
    }

    private fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanedNumber = phoneNumber.replace("[\\s\\-()]".toRegex(), "")
        val regex = "^(\\+38)?0\\d{9}$".toRegex()
        return cleanedNumber.matches(regex)
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        var cleanedNumber = phoneNumber.replace("[^\\d+]".toRegex(), "")
        if (cleanedNumber.startsWith("0") && !cleanedNumber.startsWith("+38")) {
            cleanedNumber = "+38$cleanedNumber"
        } else if (cleanedNumber.startsWith("38") && !cleanedNumber.startsWith("+")) {
            cleanedNumber = "+$cleanedNumber"
        }
        return cleanedNumber
    }

    private fun startContactImport() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchContactPicker()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Разрешение на контакты")
                    .setMessage("Для импорта клиентов из вашей телефонной книги приложению требуется доступ к контактам.")
                    .setPositiveButton("Предоставить") { _, _ ->
                        requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)

                if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    showSettingsDialog()
                }
            }
        }
    }

    private fun launchContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    // ИСПРАВЛЕННАЯ ФУНКЦИЯ retrieveContactDetails
    private fun retrieveContactDetails(contactUri: Uri) {
        var contactId: String? = null

        // Шаг 1: Получаем ID контакта из выбранного URI
        requireContext().contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (idIndex != -1) {
                    contactId = cursor.getString(idIndex)
                }
            }
        }

        contactId?.let { id ->
            // Шаг 2: Используем ID контакта для запроса имени и номера телефона
            val phoneProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
            val selectionArgs = arrayOf(id)

            requireContext().contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, // Запрашиваем из таблицы телефонов
                phoneProjection,
                selection,
                selectionArgs,
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    val nameIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    if (nameIndex != -1 && numberIndex != -1) {
                        val name = phoneCursor.getString(nameIndex)
                        val phoneNumber = phoneCursor.getString(numberIndex)

                        binding.editTextClientName.setText(name)
                        binding.editTextClientPhone.setText(phoneNumber)
                    } else {
                        Toast.makeText(requireContext(), "Не удалось получить имя или номер телефона контакта.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "У выбранного контакта нет номера телефона.", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(requireContext(), "Ошибка при запросе данных телефона контакта.", Toast.LENGTH_LONG).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), "Не удалось получить ID контакта.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Требуется разрешение")
            .setMessage("Для импорта контактов требуется разрешение на доступ к контактам. Пожалуйста, предоставьте его в настройках приложения.")
            .setPositiveButton("Перейти в настройки") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", requireActivity().packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}