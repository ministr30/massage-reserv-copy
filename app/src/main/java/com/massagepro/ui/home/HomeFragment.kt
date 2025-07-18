package com.massagepro.ui.home

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentStatus
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import com.massagepro.databinding.FragmentHomeBinding
import com.massagepro.ui.appointments.AppointmentsViewModel
import com.massagepro.ui.appointments.AppointmentsViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val appointmentsViewModel: AppointmentsViewModel by viewModels {
        AppointmentsViewModelFactory(
            requireActivity().application,
            AppointmentRepository(),
            ClientRepository(),
            ServiceRepository()
        )
    }
    private lateinit var timeSlotAdapter: TimeSlotAdapter
    private var selectedDate: Calendar = Calendar.getInstance()

    private val currencyFormat = DecimalFormat("#,##0")

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

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
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
        setupHideFreeSlotsSwitch()
        updateUIForSelectedDate()
    }

    private fun setupRecyclerView() {
        timeSlotAdapter = TimeSlotAdapter(
            onBookClick = { timeSlot ->
                val action =
                    HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                        appointmentId = -1L,
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

        timeSlotAdapter.submitList(emptyList())
        binding.textViewNoAppointmentsMessage.visibility = View.GONE
        binding.recyclerViewTimeSlots.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                val startOfDay = Calendar.getInstance().apply {
                    time = selectedDate.time
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endOfDay = Calendar.getInstance().apply {
                    time = selectedDate.time
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                appointmentsViewModel.getAppointmentsForDayFlow(startOfDay.timeInMillis, endOfDay.timeInMillis)
                    .combine(appointmentsViewModel.hideFreeSlots) { appointmentsForDay, hideFreeSlots ->
                        val allTimeSlots = appointmentsViewModel.generateTimeSlots(selectedDate, appointmentsForDay)

                        val slotsWithoutContinuations = allTimeSlots.filter { it.shouldDisplay || !it.isBooked }

                        val displayableTimeSlots = if (hideFreeSlots) {
                            slotsWithoutContinuations.filter { it.isBooked }
                        } else {
                            slotsWithoutContinuations
                        }
                        Triple(displayableTimeSlots, hideFreeSlots, appointmentsForDay)
                    }
                    .collectLatest { (timeSlotsToDisplay, hideFreeSlots, allAppointmentsForToday) ->
                        timeSlotAdapter.submitList(timeSlotsToDisplay)

                        if (timeSlotsToDisplay.isEmpty() && hideFreeSlots) {
                            binding.recyclerViewTimeSlots.visibility = View.GONE
                            binding.textViewNoAppointmentsMessage.visibility = View.VISIBLE
                        } else {
                            binding.recyclerViewTimeSlots.visibility = View.VISIBLE
                            binding.textViewNoAppointmentsMessage.visibility = View.GONE
                        }

                        val activeAppointmentsCount = allAppointmentsForToday.count {
                            it.appointment.status != AppointmentStatus.CANCELED.statusValue &&
                                    it.appointment.status != AppointmentStatus.MISSED.statusValue
                        }
                        binding.textViewAppointmentsCount.text =
                            getString(R.string.appointments_count_prefix, activeAppointmentsCount)

                        val completedRevenue = allAppointmentsForToday.filter {
                            it.appointment.status == AppointmentStatus.COMPLETED.statusValue
                        }.sumOf { it.appointment.servicePrice }
                        binding.textViewTotalRevenue.text =
                            getString(R.string.total_revenue_prefix, currencyFormat.format(completedRevenue))
                    }
            }
        }
    }

    private fun setupHideFreeSlotsSwitch() {
        val switch = binding.switchHideEmptySlots
        switch.setOnCheckedChangeListener { _, isChecked ->
            appointmentsViewModel.setHideFreeSlots(isChecked)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appointmentsViewModel.hideFreeSlots.collectLatest { hide ->
                    switch.isChecked = hide
                }
            }
        }
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
                    0 -> {
                        val action =
                            HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                                appointmentId = appointment.id,
                                selectedStartTime = appointment.dateTime
                            )
                        findNavController().navigate(action)
                    }

                    1 -> {
                        val action =
                            HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                                appointmentId = appointment.id,
                                selectedStartTime = appointment.dateTime
                            )
                        findNavController().navigate(action)
                    }

                    2 -> {
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(
                                appointment.id,
                                AppointmentStatus.COMPLETED.statusValue
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    3 -> { // "Отметить как отмененное"
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus( // Убедитесь, что здесь нет лишних слов
                                appointment.id, // Первый аргумент - ID записи
                                AppointmentStatus.CANCELED.statusValue // Второй аргумент - новый статус
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    4 -> {
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(
                                appointment.id,
                                AppointmentStatus.MISSED.statusValue
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    5 -> {
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