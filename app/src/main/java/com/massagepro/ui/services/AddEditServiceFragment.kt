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

class AddEditServiceFragment : Fragment() {

    private lateinit var editTextServicePrice: EditText
    private lateinit var editTextServiceDuration: EditText
    private lateinit var autoCompleteServiceCategory: AutoCompleteTextView
    // УДАЛЕНО: private lateinit var editTextCustomCategory: EditText
    // УДАЛЕНО: private lateinit var textInputCustomCategory: View
    // УДАЛЕНО: private lateinit var switchServiceActive: SwitchMaterial
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
        editTextServicePrice = view.findViewById(R.id.edit_text_service_price)
        editTextServiceDuration = view.findViewById(R.id.edit_text_service_duration)
        autoCompleteServiceCategory = view.findViewById(R.id.autoCompleteServiceCategory)
        // УДАЛЕНО: editTextCustomCategory = view.findViewById(R.id.edit_text_custom_category)
        // УДАЛЕНО: textInputCustomCategory = view.findViewById(R.id.text_input_custom_category)
        // УДАЛЕНО: switchServiceActive = view.findViewById(R.id.switch_service_active)
        buttonSaveService = view.findViewById(R.id.button_save_service)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, predefinedCategories)
        autoCompleteServiceCategory.setAdapter(adapter)

        // УДАЛЕНО: Логика onItemClickListener, связанная с "Інше" и textInputCustomCategory, была удалена ранее.

        val serviceId = args.serviceId
        if (serviceId != -1) {
            lifecycleScope.launch {
                viewModel.getServiceById(serviceId)?.let { service ->
                    editTextServicePrice.setText(service.basePrice.toString())
                    editTextServiceDuration.setText(service.duration.toString())
                    // УДАЛЕНО: switchServiceActive.isChecked = service.isActive
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
            // УДАЛЕНО: switchServiceActive.isChecked = true
        }

        buttonSaveService.setOnClickListener { saveService() }
    }

    private fun saveService() {
        val priceString = editTextServicePrice.text.toString().trim()
        val durationString = editTextServiceDuration.text.toString().trim()
        val isActive = true // ИЗМЕНЕНО: Всегда считаем услугу активной, так как переключатель удален

        // Больше не проверяем на "Інше", так как этой категории нет
        val category = autoCompleteServiceCategory.text.toString().trim()

        when {
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

        // Используем категорию в качестве имени или другое значение по умолчанию
        // val name = category // Поле name удалено из Service.kt

        val service = if (args.serviceId == -1) {
            Service(duration = duration, basePrice = price, category = category, isActive = isActive)
        } else {
            Service(id = args.serviceId, duration = duration, basePrice = price, category = category, isActive = isActive)
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
