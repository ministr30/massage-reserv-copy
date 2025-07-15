package com.massagepro.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.massagepro.R
import com.massagepro.data.model.Service
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.launch

class AddEditServiceFragment : Fragment() {

    private lateinit var editTextServicePrice: EditText
    private lateinit var editTextServiceDuration: EditText
    private lateinit var autoCompleteServiceCategory: AutoCompleteTextView
    private lateinit var buttonSaveService: Button

    private val predefinedCategories = listOf(
        "Масаж обличчя",
        "Масаж спини",
        "Загальний",
        "Антицелюлітний",
        "РФ ноги",
        "РФ живіт",
        "Кавітація ноги",
        "Кавітація живіт",
        "Обгортання",
        "Ендосфера"
    )

    private val viewModel: ServicesViewModel by viewModels {
        ServicesViewModelFactory(ServiceRepository())
    }

    private val args: AddEditServiceFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_edit_service, container, false)
        editTextServicePrice = view.findViewById(R.id.edit_text_service_price)
        editTextServiceDuration = view.findViewById(R.id.edit_text_service_duration)
        autoCompleteServiceCategory = view.findViewById(R.id.autoCompleteServiceCategory)
        buttonSaveService = view.findViewById(R.id.button_save_service)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            predefinedCategories
        )
        autoCompleteServiceCategory.setAdapter(adapter)

        if (args.serviceId != -1L) {
            loadExistingService(args.serviceId)
        }

        buttonSaveService.setOnClickListener {
            lifecycleScope.launch { saveService() }
        }
    }

    private fun loadExistingService(serviceId: Long) {
        lifecycleScope.launch {
            val service = viewModel.getServiceById(serviceId)
            service?.let {
                editTextServicePrice.setText(it.basePrice.toString())
                editTextServiceDuration.setText(it.duration.toString())
                autoCompleteServiceCategory.setText(it.category, false)
            }
        }
    }

    private suspend fun saveService() {
        val priceString = editTextServicePrice.text.toString().trim()
        val durationString = editTextServiceDuration.text.toString().trim()
        val category = autoCompleteServiceCategory.text.toString().trim()

        if (priceString.isEmpty() || durationString.isEmpty() || category.isEmpty()) {
            showToast(getString(R.string.service_fields_empty_error))
            return
        }

        val price = priceString.toIntOrNull()
        val duration = durationString.toIntOrNull()

        if (price == null || duration == null || price < 0 || duration <= 0) {
            showToast(getString(R.string.service_invalid_numeric_error))
            return
        }

        val service = if (args.serviceId == -1L) {
            Service(category = category, duration = duration, basePrice = price)
        } else {
            Service(id = args.serviceId, category = category, duration = duration, basePrice = price)
        }

        val success = if (args.serviceId == -1L) {
            viewModel.insertService(service)
        } else {
            viewModel.updateService(service)
        }

        if (success) {
            val msg = if (args.serviceId == -1L) R.string.service_added_toast else R.string.service_updated_toast
            showToast(getString(msg))
        } else {
            showToast(getString(R.string.service_failed_toast))
        }

        // Назад только после завершения и обновления
        findNavController().popBackStack()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
