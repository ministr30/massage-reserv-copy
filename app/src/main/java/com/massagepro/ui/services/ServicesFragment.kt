package com.massagepro.ui.services

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.databinding.FragmentServicesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ServicesFragment : Fragment() {

    private var _binding: FragmentServicesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ServicesViewModel by viewModels {
        ServicesViewModelFactory(ServiceRepository())
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
        setupSearchField()
        setupFab()
        observeServices()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshServices() // Обновим при возврате на экран
    }

    private fun setupRecyclerView() {
        serviceAdapter = ServiceAdapter(
            onServiceClick = { service ->
                val action = ServicesFragmentDirections
                    .actionNavigationServicesToAddEditServiceFragment(service.id ?: -1L)
                findNavController().navigate(action)
            },
            onEditClick = { service ->
                val action = ServicesFragmentDirections
                    .actionNavigationServicesToAddEditServiceFragment(service.id ?: -1L)
                findNavController().navigate(action)
            },
            onDeleteClick = { service ->
                lifecycleScope.launch {
                    val success = viewModel.deleteService(service)
                    if (!success) {
                        Toast.makeText(requireContext(), "Не вдалося видалити послугу", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        binding.recyclerViewServices.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = serviceAdapter
        }
    }

    private fun setupSearchField() {
        binding.editTextSearchServices.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFab() {
        binding.fabAddService.setOnClickListener {
            val action = ServicesFragmentDirections
                .actionNavigationServicesToAddEditServiceFragment(-1L)
            findNavController().navigate(action)
        }
    }

    private fun observeServices() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    when (state) {
                        is ServiceUiState.Loading -> {
                            // TODO: Показать лоадер
                        }
                        is ServiceUiState.Success -> {
                            serviceAdapter.submitList(state.services)
                        }
                        is ServiceUiState.Error -> {
                            Toast.makeText(requireContext(), "Помилка: ${state.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
