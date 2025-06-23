package com.massagepro.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.massagepro.App
import com.massagepro.data.model.Service
import com.massagepro.databinding.FragmentAddEditServiceBinding
import kotlinx.coroutines.launch
import com.massagepro.R

class AddEditServiceFragment : Fragment() {

    private var _binding: FragmentAddEditServiceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ServicesViewModel by viewModels { ServicesViewModelFactory((requireActivity().application as App).database.serviceDao()) }
    private val args: AddEditServiceFragmentArgs by navArgs()

    private val predefinedCategories = listOf(
        "Классический",
        "Антицеллюлитный",
        "Спортивный",
        "Расслабляющий",
        "Лечебный",
        "Аппаратный",
        "Детский",
        "Массаж лица",
        "Другое"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCategorySpinner()

        val serviceId = args.serviceId
        if (serviceId != -1) {
            lifecycleScope.launch {
                viewModel.getServiceById(serviceId)?.let { service ->
                    binding.editTextServiceName.setText(service.name)
                    binding.editTextServicePrice.setText(service.basePrice.toString())
                    binding.editTextServiceDuration.setText(service.duration.toString())

                    val category = service.category
                    if (category != null && predefinedCategories.contains(category)) {
                        binding.autoCompleteServiceCategory.setText(category, false)
                        binding.textInputCustomCategory.visibility = View.GONE
                    } else if (category != null) {
                        binding.autoCompleteServiceCategory.setText("Другое", false)
                        binding.textInputCustomCategory.visibility = View.VISIBLE
                        binding.editTextCustomCategory.setText(category)
                    } else {
                        // Если категория null, устанавливаем пустую строку для AutoCompleteTextView
                        binding.autoCompleteServiceCategory.setText("", false)
                        binding.textInputCustomCategory.visibility = View.GONE
                    }
                }
            }
        }

        binding.buttonSaveService.setOnClickListener { saveService() }
    }

    private fun setupCategorySpinner() {
        val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, predefinedCategories)
        binding.autoCompleteServiceCategory.setAdapter(adapter)

        binding.autoCompleteServiceCategory.setOnItemClickListener { parent, view, position, id ->
            val selectedCategory = parent.getItemAtPosition(position).toString()
            if (selectedCategory == "Другое") {
                binding.textInputCustomCategory.visibility = View.VISIBLE
                binding.editTextCustomCategory.setText("")
                binding.editTextCustomCategory.requestFocus()
            } else {
                binding.textInputCustomCategory.visibility = View.GONE
                binding.editTextCustomCategory.setText("")
            }
        }
    }

    private fun saveService() {
        val name = binding.editTextServiceName.text.toString().trim()
        val priceString = binding.editTextServicePrice.text.toString().trim()
        val durationString = binding.editTextServiceDuration.text.toString().trim()

        var category = binding.autoCompleteServiceCategory.text.toString().trim()

        if (category == "Другое") {
            category = binding.editTextCustomCategory.text.toString().trim()
            if (category.isEmpty()) {
                binding.textInputCustomCategory.error = getString(R.string.service_category_empty_error)
                return
            } else {
                binding.textInputCustomCategory.error = null
            }
        }

        if (name.isEmpty()) {
            binding.textInputServiceName.error = getString(R.string.service_name_empty_error)
            return
        } else {
            binding.textInputServiceName.error = null
        }
        if (priceString.isEmpty()) {
            binding.textInputServicePrice.error = getString(R.string.service_price_empty_error)
            return
        } else {
            binding.textInputServicePrice.error = null
        }
        if (durationString.isEmpty()) {
            binding.textInputServiceDuration.error = getString(R.string.service_duration_empty_error)
            return
        } else {
            binding.textInputServiceDuration.error = null
        }

        val price = priceString.toDoubleOrNull()
        val duration = durationString.toIntOrNull()

        if (price == null || duration == null) {
            Toast.makeText(requireContext(), "Некоректні дані для ціни або тривалості", Toast.LENGTH_SHORT).show()
            return
        }

        val finalCategory = category.ifEmpty { null }

        val service = if (args.serviceId == -1) {
            Service(name = name, basePrice = price, duration = duration, category = finalCategory)
        } else {
            Service(id = args.serviceId, name = name, basePrice = price, duration = duration, category = finalCategory)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
