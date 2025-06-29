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
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Service
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.launch
import com.google.android.material.switchmaterial.SwitchMaterial

class AddEditServiceFragment : Fragment() {

    private lateinit var editTextServiceName: EditText
    private lateinit var editTextServicePrice: EditText
    private lateinit var editTextServiceDuration: EditText
    private lateinit var autoCompleteServiceCategory: AutoCompleteTextView
    // private lateinit var editTextCustomCategory: EditText
    // private lateinit var textInputCustomCategory: View
    private lateinit var switchServiceActive: SwitchMaterial
    private lateinit var buttonSaveService: Button

    // Обновлено: Категории "Обгортання" и "Ендосфера" добавлены в список
    private val predefinedCategories = listOf(
        "Класичний",
        "Антицелюлітний",
        "Спортивний",
        "Лікувальний",
        "Апаратний",
        "Обгортання",
        "Ендосфера",
        "Дитячий",
        "Масаж обличчя",
        "Розслабляючий"
    )

    private val viewModel: ServicesViewModel by viewModels {
        val database = (requireActivity().application as App).database
        ServicesViewModelFactory(ServiceRepository(database.serviceDao()))
    }
    private val args: AddEditServiceFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_add_edit_service, container, false)
        editTextServiceName = view.findViewById(R.id.edit_text_service_name)
        editTextServicePrice = view.findViewById(R.id.edit_text_service_price)
        editTextServiceDuration = view.findViewById(R.id.edit_text_service_duration)
        autoCompleteServiceCategory = view.findViewById(R.id.autoCompleteServiceCategory)
        // editTextCustomCategory = view.findViewById(R.id.edit_text_custom_category)
        // textInputCustomCategory = view.findViewById(R.id.text_input_custom_category)
        switchServiceActive = view.findViewById(R.id.switch_service_active)
        buttonSaveService = view.findViewById(R.id.button_save_service)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, predefinedCategories)
        autoCompleteServiceCategory.setAdapter(adapter)

        // Логика onItemClickListener, связанная с "Інше" и textInputCustomCategory, была удалена ранее.

        val serviceId = args.serviceId
        if (serviceId != -1) {
            lifecycleScope.launch {
                viewModel.getServiceById(serviceId)?.let { service ->
                    editTextServiceName.setText(service.name)
                    editTextServicePrice.setText(service.basePrice.toString())
                    editTextServiceDuration.setText(service.duration.toString())
                    switchServiceActive.isChecked = service.isActive
                    // Логика для существующей услуги, если категория не из предопределенных
                    if (predefinedCategories.contains(service.category)) {
                        autoCompleteServiceCategory.setText(service.category, false)
                    } else {
                        // Если сохраненная категория не находится в предопределенных,
                        // это может быть старая "Інше" или неизвестная.
                        // Можно сбросить поле или установить значение по умолчанию.
                        autoCompleteServiceCategory.setText("", false)
                        Toast.makeText(requireContext(), getString(R.string.category_not_found_message, service.category), Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            switchServiceActive.isChecked = true
        }

        buttonSaveService.setOnClickListener { saveService() }
    }

    private fun saveService() {
        val name = editTextServiceName.text.toString().trim()
        val priceString = editTextServicePrice.text.toString().trim()
        val durationString = editTextServiceDuration.text.toString().trim()
        val isActive = switchServiceActive.isChecked

        // Больше не проверяем на "Інше", так как этой категории нет
        val category = autoCompleteServiceCategory.text.toString().trim()

        when {
            name.isEmpty() -> {
                editTextServiceName.error = getString(R.string.service_name_required_hint)
                return
            }
            priceString.isEmpty() -> {
                editTextServicePrice.error = getString(R.string.service_base_price_hint)
                return
            }
            durationString.isEmpty() -> {
                editTextServiceDuration.error = getString(R.string.service_duration_minutes_hint)
                return
            }
            category.isEmpty() -> {
                // Удалена проверка на "Інше" для ошибки категории
                autoCompleteServiceCategory.error = getString(R.string.service_category_empty_error)
                return
            }
        }

        val price = priceString.toIntOrNull()
        val duration = durationString.toIntOrNull()

        if (price == null || duration == null || duration <= 0 || price < 0) {
            Toast.makeText(requireContext(), getString(R.string.service_invalid_numeric_error), Toast.LENGTH_SHORT).show()
            return
        }

        val service = if (args.serviceId == -1) {
            Service(name = name, duration = duration, basePrice = price, category = category, isActive = isActive)
        } else {
            Service(id = args.serviceId, name = name, duration = duration, basePrice = price, category = category, isActive = isActive)
        }

        lifecycleScope.launch {
            if (args.serviceId == -1) {
                viewModel.insertService(service)
                Toast.makeText(requireContext(), getString(R.string.service_added_toast), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateService(service)
                Toast.makeText(requireContext(), getString(R.string.service_updated_toast), Toast.LENGTH_SHORT).show()
            }
            findNavController().popBackStack()
        }
    }
}
