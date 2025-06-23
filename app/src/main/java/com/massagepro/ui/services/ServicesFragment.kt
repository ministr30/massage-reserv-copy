package com.massagepro.ui.services

import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.data.model.Service
import com.massagepro.databinding.FragmentServicesBinding
import kotlinx.coroutines.launch
import com.massagepro.R // ДОБАВЛЕН ИМПОРТ R

class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ServicesViewModel by viewModels { ServicesViewModelFactory((requireActivity().application as App).database.serviceDao()) }
    private lateinit var serviceAdapter: ServiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupFab()

        viewModel.allServices.observe(viewLifecycleOwner) {
            serviceAdapter.submitList(it)
        }
    }

    private fun setupRecyclerView() {
        serviceAdapter = ServiceAdapter(
            onServiceClick = { service ->
                val action = ServicesFragmentDirections.actionNavigationServicesToAddEditServiceFragment(service.id)
                findNavController().navigate(action)
            },
            onEditClick = { service ->
                val action = ServicesFragmentDirections.actionNavigationServicesToAddEditServiceFragment(service.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { service ->
                showDeleteConfirmationDialog(service)
            }
        )
        binding.recyclerViewServices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = serviceAdapter
        }
    }

    private fun setupSearch() {
        binding.editTextSearchServices.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Implement search logic if needed, currently not in spec for services
                // For now, just refresh the list
                viewModel.allServices.observe(viewLifecycleOwner) {
                    serviceAdapter.submitList(it)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFab() {
        binding.fabAddService.setOnClickListener {
            val action = ServicesFragmentDirections.actionNavigationServicesToAddEditServiceFragment(-1)
            findNavController().navigate(action)
        }
    }

    private fun showDeleteConfirmationDialog(service: Service) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_service_dialog_title)) // ИЗМЕНЕНО ЗДЕСЬ
            .setMessage(getString(R.string.delete_service_dialog_message, service.name)) // ИЗМЕНЕНО ЗДЕСЬ
            .setPositiveButton(getString(R.string.delete_button_text)) { // ИЗМЕНЕНО ЗДЕСЬ
                    dialog, _ ->
                lifecycleScope.launch {
                    viewModel.deleteService(service)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ -> // ИЗМЕНЕНО ЗДЕСЬ
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
