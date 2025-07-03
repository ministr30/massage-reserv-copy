package com.massagepro.ui.appointments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Service
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.databinding.FragmentAddEditAppointmentBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import androidx.lifecycle.asFlow

class AddEditAppointmentFragment : Fragment() {

    private var _binding: FragmentAddEditAppointmentBinding? = null
    private val binding get() = _binding!!

    // ПРАВИЛЬНО
    private val viewModel: AppointmentsViewModel by viewModels {
        val application = requireActivity().application as App
        val database = application.database
        val clientRepository = ClientRepository(database.clientDao())
        val serviceRepository = ServiceRepository(database.serviceDao())
        AppointmentsViewModelFactory(
            application, // <--- ДОБАВЛЕН ПЕРВЫЙ ПАРАМЕТР
            AppointmentRepository(database.appointmentDao(), serviceRepository),
            clientRepository,
            serviceRepository
        )
    }


    private val args: AddEditAppointmentFragmentArgs by navArgs()

    private var selectedDateMillis: Long = 0L
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    private var selectedClientId: Int? = null
    private var selectedService: Service? = null
    private var selectedNotes: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditAppointmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialTimeMillis = args.selectedStartTime
        if (initialTimeMillis != -1L) {
            val calendar = Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
            selectedDateMillis = initialTimeMillis
            selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
            selectedMinute = calendar.get(Calendar.MINUTE)
        } else {
            val now = Calendar.getInstance()
            selectedDateMillis = now.timeInMillis
            selectedHour = now.get(Calendar.HOUR_OF_DAY)
            selectedMinute = now.get(Calendar.MINUTE)
        }

        setupClientSpinner()
        setupServiceSpinner()
        setupDateTimePickers()
        binding.buttonSaveAppointment.setOnClickListener { saveAppointment() }

        val appointmentId = args.appointmentId
        if (appointmentId != -1) {
            lifecycleScope.launch {
                viewModel.getAppointmentById(appointmentId)?.let { appointment ->
                    selectedClientId = appointment.clientId
                    selectedService = viewModel.getServiceById(appointment.serviceId)
                    selectedNotes = appointment.notes

                    binding.editTextDuration.setText(appointment.serviceDuration.toString())
                    binding.editTextPrice.setText(appointment.servicePrice.toString())
                    binding.editTextNotes.setText(appointment.notes)

                    val calendar = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
                    selectedDateMillis = appointment.dateTime
                    selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = calendar.get(Calendar.MINUTE)

                    updateDateAndTimeUI()

                    viewModel.allClients.asFlow().first().find { client -> client.id == appointment.clientId }?.let { client ->
                        (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setText(client.name, false)
                    }
                    viewModel.allServices.asFlow().first().find { service -> service.id == appointment.serviceId }?.let { service ->
                        (binding.autoCompleteTextService as? AutoCompleteTextView)?.setText(service.category, false) // Используем category
                    }
                }
            }
        } else {
            updateDateAndTimeUI()
        }
    }

    private fun setupClientSpinner() {
        viewModel.allClients.observe(viewLifecycleOwner) { clients ->
            val clientNames = clients.map { it.name }.toTypedArray()
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, clientNames)
            (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setAdapter(adapter)

            (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setOnItemClickListener { parent, _, position, _ ->
                val selectedClientName = parent.getItemAtPosition(position).toString()
                selectedClientId = clients.find { it.name == selectedClientName }?.id
            }
        }
    }

    private fun setupServiceSpinner() {
        viewModel.allServices.observe(viewLifecycleOwner) { services ->
            val serviceNames = services.map { it.category }.toTypedArray() // Используем category
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, serviceNames)
            (binding.autoCompleteTextService as? AutoCompleteTextView)?.setAdapter(adapter)

            (binding.autoCompleteTextService as? AutoCompleteTextView)?.setOnItemClickListener { parent, _, position, _ ->
                val selectedServiceName = parent.getItemAtPosition(position).toString()
                val service = services.find { it.category == selectedServiceName } // Используем category
                selectedService = service

                binding.editTextDuration.setText(service?.duration?.toString() ?: "")
                binding.editTextPrice.setText(service?.basePrice?.toString() ?: "")
            }
        }
    }

    private fun setupDateTimePickers() {
        binding.textInputLayoutDate.setEndIconOnClickListener {
            showDatePicker()
        }
        binding.editTextDate.setOnClickListener {
            showDatePicker()
        }

        binding.textInputLayoutTime.setEndIconOnClickListener {
            showTimePicker()
        }
        binding.editTextTime.setOnClickListener {
            showTimePicker()
        }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .setSelection(selectedDateMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            selectedDateMillis = selection
            updateDateAndTimeUI()
        }
        datePicker.show(parentFragmentManager, "date_picker")
    }

    private fun showTimePicker() {
        val timePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText(getString(R.string.select_time))
            .build()

        timePicker.addOnPositiveButtonClickListener {
            selectedHour = timePicker.hour
            selectedMinute = timePicker.minute
            updateDateAndTimeUI()
        }
        timePicker.show(parentFragmentManager, "time_picker")
    }

    private fun updateDateAndTimeUI() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        binding.editTextDate.setText(dateFormat.format(calendar.time))

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.editTextTime.setText(timeFormat.format(calendar.time))
    }

    private fun saveAppointment() {
        val notes = binding.editTextNotes.text.toString().trim()
        val durationString = binding.editTextDuration.text.toString().trim()
        val priceString = binding.editTextPrice.text.toString().trim()

        if (selectedClientId == null || selectedService == null || durationString.isEmpty() || priceString.isEmpty() || selectedDateMillis == 0L) {
            Toast.makeText(requireContext(), getString(R.string.appointment_fields_empty_error), Toast.LENGTH_SHORT).show()
            return
        }

        val duration = durationString.toIntOrNull()
        val price = priceString.toIntOrNull()

        if (duration == null || price == null || duration <= 0 || price < 0) {
            Toast.makeText(requireContext(), getString(R.string.appointment_invalid_numeric_error), Toast.LENGTH_SHORT).show()
            return
        }

        val combinedDateTime = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val appointment = if (args.appointmentId == -1) {
            Appointment(
                clientId = selectedClientId!!,
                serviceId = selectedService!!.id,
                serviceName = selectedService!!.category, // Используем category
                serviceDuration = duration,
                servicePrice = price,
                dateTime = combinedDateTime,
                notes = notes
            )
        } else {
            Appointment(
                id = args.appointmentId,
                clientId = selectedClientId!!,
                serviceId = selectedService!!.id,
                serviceName = selectedService!!.category, // Используем category
                serviceDuration = duration,
                servicePrice = price,
                dateTime = combinedDateTime,
                notes = notes
            )
        }

        lifecycleScope.launch {
            val conflictingAppointments = viewModel.getConflictingAppointments(
                combinedDateTime,
                combinedDateTime + duration * 60 * 1000,
                args.appointmentId
            )
            if (conflictingAppointments.isNotEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.conflicting_appointment_error), Toast.LENGTH_LONG).show()
                return@launch
            }

            if (args.appointmentId == -1) {
                viewModel.insertAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_added_toast), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_updated_toast), Toast.LENGTH_SHORT).show()
            }
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}