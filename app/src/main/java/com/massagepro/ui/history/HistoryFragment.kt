package com.massagepro.ui.history

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.data.model.Appointment
import com.massagepro.databinding.FragmentHistoryBinding
import com.massagepro.ui.appointments.AppointmentAdapter
import com.massagepro.ui.appointments.AppointmentDisplayItem
import com.massagepro.ui.appointments.AppointmentViewModel
import com.massagepro.ui.appointments.AppointmentViewModelFactory
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppointmentViewModel by viewModels { AppointmentViewModelFactory((requireActivity().application as App).database.appointmentDao(), (requireActivity().application as App).database.clientDao(), (requireActivity().application as App).database.serviceDao()) }
    private lateinit var appointmentAdapter: AppointmentAdapter

    private var startDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    private var endDate: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
    private var selectedClientFilterId: Int? = null
    private var selectedStatusFilter: String = "Все"

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

        setupRecyclerView()
        setupDatePickers()
        setupClientFilter()
        setupStatusFilter()
        observeAppointments()
    }

    private fun setupRecyclerView() {
        appointmentAdapter = AppointmentAdapter(
            onAppointmentClick = { item ->
                // TODO: Navigate to appointment details if needed
            },
            onEditClick = { item ->
                val action = HistoryFragmentDirections.actionNavigationHistoryToAddEditAppointmentFragment(item.appointment.id)
                findNavController().navigate(action)
            },
            onDeleteClick = { item ->
                showDeleteConfirmationDialog(item.appointment)
            }
        )
        binding.recyclerViewHistoryAppointments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = appointmentAdapter
        }
    }

    private fun setupDatePickers() {
        updateDateDisplay()

        binding.buttonSelectStartDate.setOnClickListener { showDatePicker(true) }
        binding.buttonSelectEndDate.setOnClickListener { showDatePicker(false) }
    }

    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = if (isStartDate) startDate else endDate
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            if (isStartDate) {
                startDate.set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
            } else {
                endDate.set(selectedYear, selectedMonth, selectedDay, 23, 59, 59)
            }
            updateDateDisplay()
            observeAppointments()
        }, year, month, day).show()
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        binding.buttonSelectStartDate.text = dateFormat.format(startDate.time)
        binding.buttonSelectEndDate.text = dateFormat.format(endDate.time)
    }

    private fun setupClientFilter() {
        viewModel.allClients.observe(viewLifecycleOwner) {
            val clientNames = mutableListOf("Все клиенты")
            clientNames.addAll(it.map { client -> client.name })
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, clientNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerFilterClient.adapter = adapter

            binding.spinnerFilterClient.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                    selectedClientFilterId = if (position == 0) null else it[position - 1].id
                    observeAppointments()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            })
        }
    }

    private fun setupStatusFilter() {
        binding.spinnerFilterStatus.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedStatusFilter = parent.getItemAtPosition(position).toString()
                observeAppointments()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })
    }

    private fun observeAppointments() {
        lifecycleScope.launch {
            viewModel.getFilteredAppointments(
                startDate.time,
                endDate.time,
                selectedClientFilterId,
                selectedStatusFilter
            ).collect { appointments ->
                val displayItems = mutableListOf<AppointmentDisplayItem>()
                for (appointment in appointments) {
                    val client = viewModel.getClientById(appointment.clientId)
                    val service = viewModel.getServiceById(appointment.serviceId)
                    displayItems.add(AppointmentDisplayItem(appointment, client, service))
                }
                appointmentAdapter.submitList(displayItems)
            }
        }
    }

    private fun showDeleteConfirmationDialog(appointment: Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Удалить запись")
            .setMessage("Вы уверены, что хотите удалить эту запись?")
            .setPositiveButton("Удалить") {
                dialog, _ ->
                lifecycleScope.launch {
                    viewModel.deleteAppointment(appointment)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}


