package com.massagepro.ui.home

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.databinding.FragmentHomeBinding
import com.massagepro.ui.appointments.AppointmentsViewModel
import com.massagepro.ui.appointments.AppointmentsViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.async
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import androidx.lifecycle.asFlow

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val appointmentsViewModel: AppointmentsViewModel by viewModels {
        val database = (requireActivity().application as App).database
        val clientRepository = ClientRepository(database.clientDao())
        val serviceRepository = ServiceRepository(database.serviceDao())
        AppointmentsViewModelFactory(
            AppointmentRepository(database.appointmentDao(), serviceRepository),
            clientRepository,
            serviceRepository
        )
    }
    private lateinit var timeSlotAdapter: TimeSlotAdapter

    private var selectedDate: Calendar = Calendar.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.headerLayout.setPadding(
                binding.headerLayout.paddingLeft,
                binding.headerLayout.paddingTop + systemBars.top,
                binding.headerLayout.paddingRight,
                binding.headerLayout.paddingBottom
            )
            insets
        }

        setupRecyclerView()
        setupDateNavigation()
        updateUIForSelectedDate()
    }

    private fun setupRecyclerView() {
        timeSlotAdapter = TimeSlotAdapter(
            onBookClick = { timeSlot ->
                val action = HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                    appointmentId = -1,
                    selectedStartTime = timeSlot.startTime.timeInMillis
                )
                findNavController().navigate(action)
            },
            showAppointmentActionsDialog = { appointment ->
                showAppointmentActionsDialog(appointment)
            }
        )
        binding.recyclerViewTimeSlots.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = timeSlotAdapter
        }
    }

    private fun setupDateNavigation() {
        binding.buttonPrevDay.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, -1)
            updateUIForSelectedDate()
        }
        binding.buttonNextDay.setOnClickListener {
            selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            updateUIForSelectedDate()
        }
        binding.textViewSelectedDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, selectedDay ->
            selectedDate.set(selectedYear, selectedMonth, selectedDay)
            updateUIForSelectedDate()
        }, year, month, day).show()
    }

    private fun updateUIForSelectedDate() {
        val dateFormat = SimpleDateFormat("EEEE, dd MMMM", Locale("uk", "UA"))
        binding.textViewSelectedDate.text = dateFormat.format(selectedDate.time)

        lifecycleScope.launch {
            val startOfDayMillis = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val endOfDayMillis = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

            val appointmentsForDay = appointmentsViewModel.getAppointmentsForDay(startOfDayMillis, endOfDayMillis).asFlow().first()

            val totalRevenue = appointmentsForDay.sumOf { it.appointment.servicePrice }

            binding.textViewAppointmentsCount.text = "${getString(R.string.appointments_count_prefix)} ${appointmentsForDay.size}"
            binding.textViewTotalRevenue.text = "${getString(R.string.total_revenue_prefix)} ${"%d".format(totalRevenue)} грн"

            val timeSlots = generateTimeSlots(appointmentsForDay)
            timeSlotAdapter.submitList(timeSlots)
        }
    }

    private suspend fun generateTimeSlots(appointmentsWithDetails: List<AppointmentWithClientAndService>): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()
        val calendar = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

        val endOfDay = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 21); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

        while (calendar.before(endOfDay)) {
            val slotStartTime = calendar.clone() as Calendar
            calendar.add(Calendar.MINUTE, 30) // 30-minute slots
            val slotEndTime = calendar.clone() as Calendar

            var isBooked = false
            var bookedAppointment: Appointment? = null
            var client: Client? = null
            var service: Service? = null

            for (appWithDetails in appointmentsWithDetails) {
                val appointment = appWithDetails.appointment
                val currentClient = appointmentsViewModel.getClientById(appointment.clientId)
                val currentService = appointmentsViewModel.getServiceById(appointment.serviceId)

                val appStartTime = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
                val appEndTime = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
                appEndTime.add(Calendar.MINUTE, appointment.serviceDuration)

                if (slotStartTime.time.before(appEndTime.time) && slotEndTime.time.after(appStartTime.time)) {
                    isBooked = true
                    bookedAppointment = appointment
                    client = currentClient
                    service = currentService
                    break
                }
            }
            slots.add(TimeSlot(slotStartTime, slotEndTime, isBooked, bookedAppointment, client, service))
        }
        return slots
    }

    private fun showAppointmentActionsDialog(appointment: Appointment) {
        val statusOptions = arrayOf(
            getString(R.string.action_edit),
            getString(R.string.action_reschedule),
            getString(R.string.action_mark_completed),
            getString(R.string.action_mark_cancelled),
            getString(R.string.action_mark_no_show),
            getString(R.string.action_delete)
        )

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.action_dialog_title))
            .setItems(statusOptions) { dialog, which ->
                when (which) {
                    0 -> { // Редагувати
                        val action = HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                            appointmentId = appointment.id,
                            selectedStartTime = appointment.dateTime
                        )
                        findNavController().navigate(action)
                    }
                    1 -> { // Перенести
                        val action = HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                            appointmentId = appointment.id,
                            selectedStartTime = appointment.dateTime
                        )
                        findNavController().navigate(action)
                    }
                    2 -> { // Позначити як завершену
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(appointment.id, getString(R.string.appointment_status_completed))
                            updateUIForSelectedDate()
                            Toast.makeText(requireContext(), getString(R.string.appointment_status_updated_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                    3 -> { // Позначити як скасовану
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(appointment.id, getString(R.string.appointment_status_cancelled))
                            updateUIForSelectedDate()
                            Toast.makeText(requireContext(), getString(R.string.appointment_status_updated_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> { // Позначити як неявку
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(appointment.id, getString(R.string.appointment_status_no_show))
                            updateUIForSelectedDate()
                            Toast.makeText(requireContext(), getString(R.string.appointment_status_updated_toast), Toast.LENGTH_SHORT).show()
                        }
                    }
                    5 -> { // Видалити
                        showDeleteConfirmationDialog(appointment)
                    }
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(appointment: Appointment) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_confirmation_title))
            .setMessage(getString(R.string.delete_confirmation_message))
            .setPositiveButton(getString(R.string.dialog_yes)) { dialog, _ ->
                lifecycleScope.launch {
                    appointmentsViewModel.deleteAppointment(appointment)
                    updateUIForSelectedDate()
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        updateUIForSelectedDate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}