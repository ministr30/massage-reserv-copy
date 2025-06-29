package com.massagepro.ui.clients

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
import com.massagepro.databinding.FragmentClientsBinding
import com.massagepro.data.repository.ClientRepository
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.collectLatest // ДОДАНО
import kotlinx.coroutines.launch // ДОДАНО, якщо його немає
import android.app.AlertDialog
import android.widget.Toast
import com.massagepro.data.model.Client
import androidx.lifecycle.repeatOnLifecycle // ДОДАНО
import androidx.lifecycle.Lifecycle // ДОДАНО

class ClientsFragment : Fragment() {

    private var _binding: FragmentClientsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClientsViewModel by viewModels {
        val database = (requireActivity().application as App).database
        ClientsViewModelFactory(
            ClientRepository(database.clientDao())
        )
    }

    private lateinit var clientAdapter: ClientAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()
        setupFab()
        observeClients() // Метод, який збиратиме Flow
    }

    private fun setupRecyclerView() {
        clientAdapter = ClientAdapter(
            onClientClick = { client ->
                val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(client.id)
                findNavController().navigate(action)
            },
            onEditClick = { client ->
                val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(client.id)
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
                viewModel.setSearchQuery(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })
    }

    private fun setupFab() {
        binding.fabAddClient.setOnClickListener {
            val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(-1)
            findNavController().navigate(action)
        }
    }

    private fun observeClients() {
        // Збір StateFlow замість спостереження за LiveData
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allClients.collectLatest { clients ->
                    clientAdapter.submitList(clients)
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(client: Client) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_client_confirmation_title))
            .setMessage(getString(R.string.delete_client_confirmation_message, client.name))
            .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                lifecycleScope.launch {
                    viewModel.deleteClient(client)
                    Toast.makeText(requireContext(), getString(R.string.client_deleted_toast, client.name), Toast.LENGTH_SHORT).show()
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