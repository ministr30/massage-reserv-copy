package com.massagepro.ui.appointments

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.databinding.FragmentAddEditAppointmentBinding
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class AddEditAppointmentFragment : Fragment( ) {

    private var _binding: FragmentAddEditAppointmentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppointmentViewModel by viewModels { AppointmentViewModelFactory((requireActivity().application as App).database.appointmentDao(), (requireActivity().application as App).database.clientDao(), (requireActivity().application as App).database.serviceDao()) }
    private val args: AddEditAppointmentFragmentArgs by navArgs()

    private var selectedClient: Client? = null
    private var selectedService: Service? = null
    private var selectedCalendar: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditAppointmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация времени из аргументов, если оно передано
        val initialTimeMillis = args.selectedStartTime
        if (initialTimeMillis != -1L) {
            selectedCalendar.timeInMillis = initialTimeMillis
        }

        setupClientDropdown()
        setupServiceDropdown()
        setupDateTimePickers()
        setupSaveButton()
        setupAddNewClientButton()

        val appointmentId = args.appointmentId
        if (appointmentId != -1) {
            // Редактирование существующей записи
            lifecycleScope.launch {
                viewModel.getAppointmentById(appointmentId)?.let { appointment ->
                    // Предзаполнение данных для редактирования
                    selectedCalendar.time = appointment.startTime
                    updateDateTimeDisplay()
                    calculateTotalCost()

                    // Выбор клиента и услуги в выпадающих списках
                    viewModel.allClients.observe(viewLifecycleOwner) { clients ->
                        clients.find { it.id == appointment.clientId }?.let { client ->
                            selectedClient = client
                            binding.autoCompleteClient.setText(client.name, false) // false, чтобы не вызывать слушатель текста
                        }
                    }
                    viewModel.allServices.observe(viewLifecycleOwner) { services ->
                        services.find { it.id == appointment.serviceId }?.let { service ->
                            selectedService = service
                            binding.autoCompleteService.setText(service.name, false) // false, чтобы не вызывать слушатель текста
                        }
                    }
                }
            }
        } else {
            // Если это новая запись, убедимся, что отображается актуальное время
            updateDateTimeDisplay()
            calculateTotalCost()
        }
    }

    private fun setupClientDropdown() {
        viewModel.allClients.observe(viewLifecycleOwner) { clients ->
            val clientNames = clients.map { it.name }
            val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, clientNames) // Используем простой макет для элементов списка
            binding.autoCompleteClient.setAdapter(adapter)

            binding.autoCompleteClient.setOnItemClickListener { parent, _, position, _ ->
                val selectedName = parent.getItemAtPosition(position).toString()
                selectedClient = clients.find { it.name == selectedName }
            }
        }
    }

    private fun setupServiceDropdown() {
        viewModel.allServices.observe(viewLifecycleOwner) { services ->
            val serviceNames = services.map { it.name }
            val adapter = ArrayAdapter(requireContext(), R.layout.list_item_dropdown, serviceNames) // Используем простой макет для элементов списка
            binding.autoCompleteService.setAdapter(adapter)

            binding.autoCompleteService.setOnItemClickListener { parent, _, position, _ ->
                val selectedName = parent.getItemAtPosition(position).toString()
                selectedService = services.find { it.name == selectedName }
                calculateTotalCost() // Пересчитываем стоимость при выборе услуги
            }
        }
    }

    private fun setupDateTimePickers() {
        binding.buttonSelectDate.setOnClickListener { showDatePicker() }
        binding.buttonSelectTime.setOnClickListener { showTimePicker() }
        updateDateTimeDisplay()
    }

    private fun showDatePicker() {
        val year = selectedCalendar.get(Calendar.YEAR)
        val month = selectedCalendar.get(Calendar.MONTH)
        val day = selectedCalendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
            updateDateTimeDisplay()
            calculateTotalCost()
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val hour = selectedCalendar.get(Calendar.HOUR_OF_DAY)
        val minute = selectedCalendar.get(Calendar.MINUTE)

        TimePickerDialog(requireContext(), { _, selectedHour, selectedMinute ->
            selectedCalendar.set(Calendar.HOUR_OF_DAY, selectedHour)
            selectedCalendar.set(Calendar.MINUTE, selectedMinute)
            updateDateTimeDisplay()
            calculateTotalCost()
        }, hour, minute, true).show()
    }

    private fun updateDateTimeDisplay() {
        // Разделяем форматирование даты и времени
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val dateString = dateFormat.format(selectedCalendar.time)
        val timeString = timeFormat.format(selectedCalendar.time)

        // Передаем два аргумента в строковый ресурс
        binding.textViewSelectedDateTime.text = getString(R.string.selected_date_time_format, dateString, timeString)
    }

    private fun calculateTotalCost() {
        val basePrice = selectedService?.basePrice ?: 0.0
        var totalCost = basePrice

        // Add Sunday surcharge
        if (selectedCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            totalCost += 100.0
            binding.textViewTotalCost.text = getString(R.string.total_cost_sunday_format, totalCost)
        } else {
            binding.textViewTotalCost.text = getString(R.string.total_cost_format, totalCost)
        }
    }

    private fun setupSaveButton() {
        binding.buttonSaveAppointment.setOnClickListener { saveAppointment() }
    }

    private fun saveAppointment() {
        val clientId = selectedClient?.id
        val serviceId = selectedService?.id
        val startTime = selectedCalendar.time

        if (clientId == null || serviceId == null) {
            Toast.makeText(requireContext(), getString(R.string.select_client_service_error), Toast.LENGTH_SHORT).show()
            return
        }

        // Validate past time
        if (startTime.before(Date())) {
            Toast.makeText(requireContext(), getString(R.string.past_appointment_error), Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate end time based on service duration
        val endTimeCalendar = Calendar.getInstance().apply { time = startTime }
        endTimeCalendar.add(Calendar.MINUTE, selectedService?.duration ?: 0)
        val endTime = endTimeCalendar.time

        // Check for conflicts
        lifecycleScope.launch {
            val conflictingAppointments = viewModel.getConflictingAppointments(startTime, endTime, args.appointmentId)
            if (conflictingAppointments.isNotEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.conflicting_appointment_error), Toast.LENGTH_LONG).show()
                return@launch
            }

            val totalCost = if (selectedCalendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                (selectedService?.basePrice ?: 0.0) + 100.0
            } else {
                selectedService?.basePrice ?: 0.0
            }

            val appointment = if (args.appointmentId == -1) {
                Appointment(clientId = clientId, serviceId = serviceId, startTime = startTime, endTime = endTime, totalCost = totalCost)
            } else {
                Appointment(id = args.appointmentId, clientId = clientId, serviceId = serviceId, startTime = startTime, endTime = endTime, totalCost = totalCost)
            }

            if (args.appointmentId == -1) {
                viewModel.insertAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_created_toast), Toast.LENGTH_SHORT).show()
            } else {
                viewModel.updateAppointment(appointment)
                Toast.makeText(requireContext(), getString(R.string.appointment_updated_toast), Toast.LENGTH_SHORT).show()
            }
            findNavController().popBackStack()
        }
    }

    private fun setupAddNewClientButton() {
        binding.buttonAddNewClient.setOnClickListener {
            val action = AddEditAppointmentFragmentDirections.actionAddEditAppointmentFragmentToAddEditClientFragment(-1)
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
