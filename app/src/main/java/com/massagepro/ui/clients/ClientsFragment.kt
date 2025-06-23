package com.massagepro.ui.clients

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
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.model.Client
import com.massagepro.databinding.FragmentClientsBinding
import kotlinx.coroutines.launch
import com.massagepro.R // ДОБАВЛЕН ИМПОРТ R

class ClientsFragment : Fragment() {

    private var _binding: FragmentClientsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ClientsViewModel by viewModels { ClientsViewModelFactory((requireActivity().application as App).database.clientDao()) }
    private lateinit var clientAdapter: ClientAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentClientsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearch()
        setupFab()

        viewModel.allClients.observe(viewLifecycleOwner) {
            clientAdapter.submitList(it)
        }
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

    private fun setupSearch() {
        binding.editTextSearchClients.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isEmpty()) {
                    viewModel.allClients.observe(viewLifecycleOwner) {
                        clientAdapter.submitList(it)
                    }
                } else {
                    viewModel.searchClients("%" + query + "%").observe(viewLifecycleOwner) {
                        clientAdapter.submitList(it)
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFab() {
        binding.fabAddClient.setOnClickListener {
            val action = ClientsFragmentDirections.actionNavigationClientsToAddEditClientFragment(-1)
            findNavController().navigate(action)
        }
    }

    private fun showDeleteConfirmationDialog(client: Client) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_client_dialog_title)) // ИЗМЕНЕНО ЗДЕСЬ
            .setMessage(getString(R.string.delete_client_dialog_message, client.name)) // ИЗМЕНЕНО ЗДЕСЬ
            .setPositiveButton(getString(R.string.delete_button_text)) { // ИЗМЕНЕНО ЗДЕСЬ
                    dialog, _ ->
                lifecycleScope.launch {
                    viewModel.deleteClient(client)
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
