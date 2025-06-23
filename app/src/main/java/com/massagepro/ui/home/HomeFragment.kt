package com.massagepro.ui.home

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.databinding.FragmentHomeBinding
import com.massagepro.ui.appointments.AppointmentViewModel
import com.massagepro.ui.appointments.AppointmentViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.async
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val appointmentViewModel: AppointmentViewModel by viewModels { AppointmentViewModelFactory((requireActivity().application as App).database.appointmentDao(), (requireActivity().application as App).database.clientDao(), (requireActivity().application as App).database.serviceDao()) }
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

        // Применяем отступы к корневому представлению фрагмента
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

                binding.headerLayout.setPadding(
                binding.headerLayout.paddingLeft,
                binding.headerLayout.paddingTop + systemBars.top,
                binding.headerLayout.paddingRight,
                binding.headerLayout.paddingBottom
            )
            insets // Потребляем инсеты, чтобы они не распространялись дальше на другие представления в этом фрагменте
        }

        setupRecyclerView()
        setupDateNavigation()
        updateUIForSelectedDate()
    }

    private fun setupRecyclerView() {
        timeSlotAdapter = TimeSlotAdapter(
            onBookClick = { timeSlot ->
                // Handle booking a time slot (for unbooked slots)
                val action = HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                    appointmentId = -1, // For a new appointment, ID is -1
                    selectedStartTime = timeSlot.startTime.timeInMillis // Pass the selected time
                )
                findNavController().navigate(action)
            },
            onBookedSlotClick = { appointment ->
                // Handle clicking on a booked slot (for editing/deleting)
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
            val startOfDay = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            val endOfDay = Calendar.getInstance().apply { time = selectedDate.time; set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.time

            val appointmentsForDay = appointmentViewModel.getAppointmentsForDateRange(startOfDay, endOfDay).first()
            val totalRevenue = appointmentsForDay.sumOf { it.totalCost }

            binding.textViewAppointmentsCount.text = "${getString(R.string.appointments_count_prefix)} ${appointmentsForDay.size}"
            binding.textViewTotalRevenue.text = "${getString(R.string.total_revenue_prefix)} ${"%.2f".format(totalRevenue)} грн"

            val timeSlots = generateTimeSlots(appointmentsForDay)
            timeSlotAdapter.submitList(timeSlots)
        }
    }

    private suspend fun generateTimeSlots(appointments: List<Appointment>): List<TimeSlot> {
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

            for (appointment in appointments) {
                // Check for overlap between time slot and appointment
                if (slotStartTime.time.before(appointment.endTime) && slotEndTime.time.after(appointment.startTime)) {
                    isBooked = true
                    bookedAppointment = appointment
                    // Fetch client and service details asynchronously
                    val clientDeferred = lifecycleScope.async { appointmentViewModel.getClientById(appointment.clientId) }
                    val serviceDeferred = lifecycleScope.async { appointmentViewModel.getServiceById(appointment.serviceId) }
                    client = clientDeferred.await()
                    service = serviceDeferred.await()
                    break // Found an overlapping appointment, no need to check others for this slot
                }
            }
            slots.add(TimeSlot(slotStartTime, slotEndTime, isBooked, bookedAppointment, client, service))
        }
        return slots
    }

    private fun showAppointmentActionsDialog(appointment: Appointment) {
        val options = arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.action_dialog_title))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // Edit
                        val action = HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                            appointmentId = appointment.id,
                            selectedStartTime = appointment.startTime.time
                        )
                        findNavController().navigate(action)
                    }
                    1 -> { // Delete
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
                    appointmentViewModel.deleteAppointment(appointment)
                    updateUIForSelectedDate() // Refresh the list after deletion
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
        // Refresh data when returning to the fragment (e.g., after adding/editing an appointment)
        updateUIForSelectedDate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
