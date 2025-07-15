package com.massagepro.ui.clients

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.R
import com.massagepro.data.model.Client
import com.massagepro.data.repository.ClientRepository
import com.massagepro.databinding.FragmentClientsBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ClientsFragment : Fragment() {

    private var _binding: FragmentClientsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClientsViewModel by viewModels {
        ClientsViewModelFactory(ClientRepository())
    }

    private lateinit var clientAdapter: ClientAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeUiState()
    }

    private fun setupRecyclerView() {
        clientAdapter = ClientAdapter(
            onClientClick = { client ->
                val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(client.id!!)
                findNavController().navigate(action)
            },
            onEditClick = { client ->
                val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(client.id!!)
                findNavController().navigate(action)
            },
            onDeleteClick = { client ->
                showDeleteConfirmationDialog(client)
            }
        )
        binding.recyclerViewClients.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = clientAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchViewClients.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                viewModel.setSearchQuery(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupFab() {
        binding.fabAddClient.setOnClickListener {
            val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(-1L)
            findNavController().navigate(action)
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { uiState ->
                    handleUiState(uiState)
                }
            }
        }
    }

    private fun handleUiState(uiState: ClientUiState) {
        when (uiState) {
            is ClientUiState.Loading -> {
                binding.progressBar.isVisible = true
                binding.recyclerViewClients.isVisible = false
                binding.errorTextView.isVisible = false
            }

            is ClientUiState.Success -> {
                binding.progressBar.isVisible = false
                binding.recyclerViewClients.isVisible = true
                binding.errorTextView.isVisible = uiState.clients.isEmpty()

                if (uiState.clients.isEmpty()) {
                    binding.errorTextView.text = "Нет клиентов"
                } else {
                    binding.errorTextView.text = ""
                }

                clientAdapter.submitList(uiState.clients)
            }

            is ClientUiState.Error -> {
                binding.progressBar.isVisible = false
                binding.recyclerViewClients.isVisible = false
                binding.errorTextView.isVisible = true
                binding.errorTextView.text = uiState.message
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload() // принудительно обновляем данные при возврате к фрагменту
    }

    private fun showDeleteConfirmationDialog(client: Client) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_client_confirmation_title))
            .setMessage(getString(R.string.delete_client_confirmation_message, client.name))
            .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                lifecycleScope.launch {
                    val success = viewModel.deleteClient(client)
                    if (success) {
                        Toast.makeText(requireContext(), getString(R.string.client_deleted_toast, client.name), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Ошибка при удалении клиента", Toast.LENGTH_SHORT).show()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_no), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
