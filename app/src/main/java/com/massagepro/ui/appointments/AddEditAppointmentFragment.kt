package com.massagepro.ui.appointments

import android.app.AlertDialog
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
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentStatus
import com.massagepro.data.model.Service
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.databinding.FragmentAddEditAppointmentBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AddEditAppointmentFragment : Fragment() {

    private var _binding: FragmentAddEditAppointmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AppointmentsViewModel by viewModels {
        AppointmentsViewModelFactory(
            requireActivity().application,
            AppointmentRepository(),
            ClientRepository(),
            ServiceRepository()
        )
    }
    private val args: AddEditAppointmentFragmentArgs by navArgs()

    private var selectedDateMillis: Long = 0L
    private var selectedHour: Int = 0
    private var selectedMinute: Int = 0

    private var selectedClientId: Long? = null
    private var selectedClientName: String? = null
    private var selectedService: Service? = null
    private var selectedNotes: String? = null
    private var currentAppointmentStatus: String = AppointmentStatus.PLANNED.statusValue

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

        setupClientAutoComplete()
        setupServiceAutoComplete()
        setupDateTimePickers()
        binding.buttonSaveAppointment.setOnClickListener { validateAndStartSaveProcess() }

        val appointmentId = args.appointmentId
        if (appointmentId != -1L) {
            lifecycleScope.launch {
                viewModel.getAppointmentById(appointmentId.toLong())?.let { appointment ->
                    selectedClientId = appointment.clientId
                    selectedService = viewModel.getServiceById(appointment.serviceId)
                    selectedNotes = appointment.notes
                    currentAppointmentStatus = appointment.status

                    binding.editTextDuration.setText(appointment.serviceDuration.toString())
                    binding.editTextPrice.setText(appointment.servicePrice.toString())
                    binding.editTextNotes.setText(appointment.notes)

                    val calendar = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
                    selectedDateMillis = appointment.dateTime
                    selectedHour = calendar.get(Calendar.HOUR_OF_DAY)
                    selectedMinute = calendar.get(Calendar.MINUTE)

                    updateDateAndTimeUI()

                    viewModel.getClientById(appointment.clientId)?.let { client ->
                        selectedClientName = client.name
                        (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setText(client.name, false)
                    }
                    selectedService?.let { service ->
                        val display = "${service.category} - ${service.duration} хвилин (${service.basePrice} грн)"
                        (binding.autoCompleteTextService as? AutoCompleteTextView)?.setText(display, false)
                    }
                }
            }
        } else {
            updateDateAndTimeUI()
            currentAppointmentStatus = AppointmentStatus.PLANNED.statusValue
        }
    }

    // region === UI helpers ===
    private fun setupClientAutoComplete() {
        lifecycleScope.launch {
            viewModel.allClients.observe(viewLifecycleOwner) { clients ->
                val names = clients.map { it.name }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, names)
                (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setAdapter(adapter)
                (binding.autoCompleteTextClient as? AutoCompleteTextView)?.setOnItemClickListener { _, _, pos, _ ->
                    val client = clients[pos]
                    selectedClientId = client.id
                    selectedClientName = client.name
                }
            }
        }
    }

    private fun setupServiceAutoComplete() {
        lifecycleScope.launch {
            viewModel.allServices.observe(viewLifecycleOwner) { services ->
                val displays = services.map { "${it.category} - ${it.duration} хвилин (${it.basePrice} грн)" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, displays)
                (binding.autoCompleteTextService as? AutoCompleteTextView)?.setAdapter(adapter)
                (binding.autoCompleteTextService as? AutoCompleteTextView)?.setOnItemClickListener { _, _, pos, _ ->
                    selectedService = services[pos]
                    binding.editTextDuration.setText(selectedService?.duration?.toString() ?: "")
                    binding.editTextPrice.setText(selectedService?.basePrice?.toString() ?: "")
                }
            }
        }
    }

    private fun setupDateTimePickers() {
        binding.textInputLayoutDate.setEndIconOnClickListener { showDatePicker() }
        binding.editTextDate.setOnClickListener { showDatePicker() }
        binding.textInputLayoutTime.setEndIconOnClickListener { showTimePicker() }
        binding.editTextTime.setOnClickListener { showTimePicker() }
    }

    private fun showDatePicker() {
        MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_date))
            .setSelection(selectedDateMillis)
            .build()
            .apply {
                addOnPositiveButtonClickListener { selection ->
                    selectedDateMillis = selection
                    updateDateAndTimeUI()
                }
                show(parentFragmentManager, "date_picker")
            }
    }

    private fun showTimePicker() {
        MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(selectedHour)
            .setMinute(selectedMinute)
            .setTitleText(getString(R.string.select_time))
            .build()
            .apply {
                addOnPositiveButtonClickListener {
                    selectedHour = hour
                    selectedMinute = minute
                    updateDateAndTimeUI()
                }
                show(parentFragmentManager, "time_picker")
            }
    }

    private fun updateDateAndTimeUI() {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("uk", "UA"))
        val timeFormat = SimpleDateFormat("HH:mm", Locale("uk", "UA"))
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }

        binding.editTextDate.setText(dateFormat.format(calendar.time))
        binding.editTextTime.setText(
            timeFormat.format(
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                }.time
            )
        )
    }
    // endregion

    // region === Validation & save ===
    private fun validateAndStartSaveProcess() {
        val notes = binding.editTextNotes.text.toString().trim()
        val durationString = binding.editTextDuration.text.toString().trim()
        val priceString = binding.editTextPrice.text.toString().trim()

        if (selectedClientId == null || selectedService == null || durationString.isEmpty() || priceString.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.appointment_fields_empty_error), Toast.LENGTH_SHORT).show()
            return
        }
        val duration = durationString.toIntOrNull()
        val initialPrice = priceString.toIntOrNull()
        if (duration == null || initialPrice == null || duration <= 0 || initialPrice < 0) {
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

        lifecycleScope.launch {
            val preceding = viewModel.getPrecedingAppointment(combinedDateTime)
            if (preceding != null && (preceding.dateTime + TimeUnit.MINUTES.toMillis(preceding.serviceDuration.toLong())) == combinedDateTime) {
                showPreparationTimeDialog(combinedDateTime, duration, initialPrice, notes)
            } else {
                checkConflictsAndSave(combinedDateTime, duration, initialPrice, notes)
            }
        }
    }

    private fun showPreparationTimeDialog(originalDateTime: Long, duration: Int, price: Int, notes: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.preparation_time_title))
            .setMessage(getString(R.string.preparation_time_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                checkConflictsAndSave(originalDateTime + TimeUnit.MINUTES.toMillis(15), duration, price, notes)
            }
            .setNegativeButton(getString(R.string.dialog_no)) { _, _ ->
                checkConflictsAndSave(originalDateTime, duration, price, notes)
            }
            .setCancelable(false)
            .show()
    }

    private fun checkConflictsAndSave(dateTime: Long, duration: Int, price: Int, notes: String) {
        lifecycleScope.launch {
            val conflictEnd = dateTime + TimeUnit.MINUTES.toMillis(duration.toLong())
            val conflicting = viewModel.getConflictingAppointments(
                dateTime,
                conflictEnd,
                args.appointmentId.toLong()
            )
            if (conflicting.isNotEmpty()) {
                val searchFrom = Calendar.getInstance().apply { timeInMillis = dateTime }
                val nextSlot = viewModel.findNextAvailableSlot(duration, searchFrom)
                if (nextSlot != null) {
                    val sdf = SimpleDateFormat("dd.MM.yyyy 'о' HH:mm", Locale("uk", "UA"))
                    AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.slot_busy_title))
                        .setMessage(getString(R.string.slot_busy_message, sdf.format(nextSlot.time)))
                        .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                            selectedDateMillis = nextSlot.timeInMillis
                            selectedHour = nextSlot.get(Calendar.HOUR_OF_DAY)
                            selectedMinute = nextSlot.get(Calendar.MINUTE)
                            updateDateAndTimeUI()
                        }
                        .setNegativeButton(getString(R.string.dialog_no), null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.no_free_slots_found), Toast.LENGTH_LONG).show()
                }
                return@launch
            }
            checkSurchargeAndSave(dateTime, duration, price, notes)
        }
    }

    private fun checkSurchargeAndSave(dateTime: Long, duration: Int, price: Int, notes: String) {
        val cal = Calendar.getInstance().apply { timeInMillis = dateTime }
        if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.weekend_surcharge_title))
                .setMessage(getString(R.string.weekend_surcharge_message))
                .setPositiveButton(getString(R.string.dialog_yes)) { _, _ ->
                    saveAppointment(dateTime, duration, price + 100, notes)
                }
                .setNegativeButton(getString(R.string.dialog_no)) { _, _ ->
                    saveAppointment(dateTime, duration, price, notes)
                }
                .show()
        } else {
            saveAppointment(dateTime, duration, price, notes)
        }
    }

    private fun saveAppointment(finalDateTime: Long, duration: Int, price: Int, notes: String) {
        lifecycleScope.launch {
            val appointment = Appointment(
                id = if (args.appointmentId == -1L) 0L else args.appointmentId.toLong(),
                clientId = selectedClientId!!,
                serviceId = selectedService!!.id!!,
                serviceDuration = duration,
                servicePrice = price,
                dateTime = finalDateTime,
                notes = notes,
                status = currentAppointmentStatus
            )

            if (args.appointmentId == -1L) {
                viewModel.insertAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_added_toast), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_updated_toast), Toast.LENGTH_SHORT).show()
            }
            findNavController().popBackStack()
        }
    }
    // endregion

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}