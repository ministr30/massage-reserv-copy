package com.massagepro.ui.services

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.R
import com.massagepro.databinding.FragmentServicesBinding
import com.massagepro.data.repository.ServiceRepository // Додано для ServiceRepository
import android.app.AlertDialog // Додано для AlertDialog
import android.widget.Toast // Додано для Toast
import com.massagepro.data.model.Service // Додано для Service
import kotlinx.coroutines.launch // Додано для корутин

class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServicesViewModel by viewModels {
        val database = (requireActivity().application as App).database
        // ВИПРАВЛЕНО: Тепер передаємо ServiceRepository до фабрики
        ServicesViewModelFactory(
            ServiceRepository(database.serviceDao())
        )
    }

    private lateinit var serviceAdapter: ServiceAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentServicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeServices()
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

    private fun setupSearchView() {
        // Логика поиска, если есть SearchView в макете fragment_services.xml
        // binding.searchViewServices.setOnQueryTextListener(...)
    }

    private fun setupFab() {
        binding.fabAddService.setOnClickListener {
            val action = ServicesFragmentDirections.actionNavigationServicesToAddEditServiceFragment(-1)
            findNavController().navigate(action)
        }
    }

    private fun observeServices() {
        viewModel.allServices.observe(viewLifecycleOwner) { services ->
            serviceAdapter.submitList(services)
        }
    }

    private fun showDeleteConfirmationDialog(service: Service) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_service_dialog_title))
            .setMessage(getString(R.string.delete_service_dialog_message, service.category)) // %1$s Используем category
            .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                lifecycleScope.launch {
                    viewModel.deleteService(service)
                    Toast.makeText(requireContext(), getString(R.string.service_deleted_toast, service.category), Toast.LENGTH_SHORT).show() // Используем category
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}