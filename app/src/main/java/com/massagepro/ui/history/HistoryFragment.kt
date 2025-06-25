package com.massagepro.ui.history

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.databinding.FragmentHistoryBinding
import com.massagepro.ui.appointments.AppointmentAdapter
import com.massagepro.ui.appointments.AppointmentDisplayItem
import com.massagepro.ui.appointments.AppointmentViewModel
import com.massagepro.ui.appointments.AppointmentViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppointmentViewModel by viewModels {
        AppointmentViewModelFactory(
            (requireActivity().application as App).database.appointmentDao(),
            (requireActivity().application as App).database.clientDao(),
            (requireActivity().application as App).database.serviceDao()
        )
    }

    private lateinit var appointmentAdapter: AppointmentAdapter

    private val startDateFlow = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    })

    private val endDateFlow = MutableStateFlow(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    })

    private val selectedClientIdFlow = MutableStateFlow<Int?>(null)
    private val selectedStatusFlow = MutableStateFlow("Все")

    private var clientList: List<Client> = emptyList()
    private var job: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, v.paddingBottom)
            insets
        }

        setupRecyclerView()
        setupDatePickers()
        observeClientList()
        setupFilterListeners()

        job?.cancel()
        job = lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                combine(
                    startDateFlow,
                    endDateFlow,
                    selectedClientIdFlow,
                    selectedStatusFlow
                ) { start, end, clientId, status ->
                    Triple(start.time, end.time, Pair(clientId, status))
                }.collectLatest { (start, end, filter) ->
                    val (clientId, status) = filter

                    // Вывод в лог реальных значений дат для проверки
                    Log.d("ФИЛЬТР", "start: $start, end: $end")

                    viewModel.getFilteredAppointments(start, end, clientId, status)
                        .collectLatest { appointments ->
                            val displayItems = appointments.map { appointment ->
                                val client = viewModel.getClientById(appointment.clientId)
                                val service = viewModel.getServiceById(appointment.serviceId)
                                AppointmentDisplayItem(appointment, client, service)
                            }
                            appointmentAdapter.submitList(displayItems)
                        }
                }
            }
        }
    }


    private fun setupRecyclerView() {
        appointmentAdapter = AppointmentAdapter(
            onAppointmentClick = {},
            onEditClick = { item ->
                val action = HistoryFragmentDirections.actionNavigationHistoryToAddEditAppointmentFragment(item.appointment.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { item -> showDeleteConfirmationDialog(item.appointment) }
        )
        binding.recyclerViewHistoryAppointments.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewHistoryAppointments.adapter = appointmentAdapter
    }

    private fun setupDatePickers() {
        updateDateDisplay()
        binding.buttonSelectStartDate.setOnClickListener { showDatePicker(true) }
        binding.buttonSelectEndDate.setOnClickListener { showDatePicker(false) }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) startDateFlow.value else endDateFlow.value
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val updated = Calendar.getInstance().apply {
                    set(year, month, day)
                    if (isStartDate) {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    } else {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                }
                if (isStartDate) startDateFlow.value = updated else endDateFlow.value = updated
                updateDateDisplay()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        binding.buttonSelectStartDate.text = dateFormat.format(startDateFlow.value.time)
        binding.buttonSelectEndDate.text = dateFormat.format(endDateFlow.value.time)
    }

    private fun observeClientList() {
        viewModel.allClients.observe(viewLifecycleOwner) { clients ->
            clientList = clients
            val names = listOf("Все клиенты") + clients.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerFilterClient.adapter = adapter
        }
    }

    private fun setupFilterListeners() {
        binding.spinnerFilterClient.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedClientIdFlow.value = if (position == 0) null else clientList[position - 1].id
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
        binding.spinnerFilterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedStatusFlow.value = parent.getItemAtPosition(position).toString()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun showDeleteConfirmationDialog(appointment: Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить запись")
            .setMessage("Вы уверены, что хотите удалить эту запись?")
            .setPositiveButton("Удалить") { dialog, _ ->
                lifecycleScope.launch { viewModel.deleteAppointment(appointment) }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        job?.cancel()
    }
}