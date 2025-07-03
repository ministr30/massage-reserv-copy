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
import com.massagepro.App
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.databinding.FragmentHomeBinding
import com.massagepro.ui.appointments.AppointmentsViewModel
import com.massagepro.ui.appointments.AppointmentsViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val appointmentsViewModel: AppointmentsViewModel by viewModels {
        val application = requireActivity().application as App
        val database = application.database
        val clientRepository = com.massagepro.data.repository.ClientRepository(database.clientDao())
        val serviceRepository = com.massagepro.data.repository.ServiceRepository(database.serviceDao())
        AppointmentsViewModelFactory(
            application, // Pass application context
            com.massagepro.data.repository.AppointmentRepository(database.appointmentDao(), serviceRepository),
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
        updateUIForSelectedDate() // <--- Вызов здесь
    }

    private fun setupRecyclerView() {
        timeSlotAdapter = TimeSlotAdapter(
            onBookClick = { timeSlot ->
                val action =
                    HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
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

        // --- ИСПРАВЛЕНИЕ МЕРЦАНИЯ: Очищаем список и скрываем сообщение сразу ---
        timeSlotAdapter.submitList(emptyList()) // Немедленно очищаем RecyclerView
        binding.textViewNoAppointmentsMessage.visibility = View.GONE // Скрываем сообщение
        binding.recyclerViewTimeSlots.visibility = View.VISIBLE // Убеждаемся, что RecyclerView виден, пока не придут новые данные
        // --- КОНЕЦ ИСПРАВЛЕНИЯ ---

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                appointmentsViewModel.getDisplayableTimeSlots(selectedDate)
                    .combine(appointmentsViewModel.hideFreeSlots) { timeSlots, hideFreeSlots ->
                        Pair(timeSlots, hideFreeSlots)
                    }
                    .collectLatest { (timeSlots, hideFreeSlots) ->
                        timeSlotAdapter.submitList(timeSlots)

                        if (timeSlots.isEmpty() && hideFreeSlots) {
                            binding.recyclerViewTimeSlots.visibility = View.GONE
                            binding.textViewNoAppointmentsMessage.visibility = View.VISIBLE
                        } else {
                            binding.recyclerViewTimeSlots.visibility = View.VISIBLE
                            binding.textViewNoAppointmentsMessage.visibility = View.GONE
                        }

                        val cal = selectedDate.clone() as Calendar
                        val startOfDayMillis = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                        val endOfDayMillis = cal.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis

                        appointmentsViewModel.getAppointmentsForDayLiveData(startOfDayMillis, endOfDayMillis).observe(viewLifecycleOwner) { appointmentsForDay ->
                            val totalRevenue = appointmentsForDay.sumOf { it.appointment.servicePrice }
                            binding.textViewAppointmentsCount.text =
                                getString(R.string.appointments_count_prefix, appointmentsForDay.size)
                            binding.textViewTotalRevenue.text =
                                getString(R.string.total_revenue_prefix, totalRevenue)
                        }
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
                    0 -> { // Редагувати
                        val action =
                            HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                                appointmentId = appointment.id,
                                selectedStartTime = appointment.dateTime
                            )
                        findNavController().navigate(action)
                    }

                    1 -> { // Перенести
                        val action =
                            HomeFragmentDirections.actionNavigationHomeToAddEditAppointmentFragment(
                                appointmentId = appointment.id,
                                selectedStartTime = appointment.dateTime
                            )
                        findNavController().navigate(action)
                    }

                    2 -> { // Позначити як завершену
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(
                                appointment.id,
                                getString(R.string.appointment_status_completed)
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    3 -> { // Позначити як скасовану
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(
                                appointment.id,
                                getString(R.string.appointment_status_cancelled)
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    4 -> { // Позначити як неявку
                        lifecycleScope.launch {
                            appointmentsViewModel.updateAppointmentStatus(
                                appointment.id,
                                getString(R.string.appointment_status_no_show)
                            )
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.appointment_status_updated_toast),
                                Toast.LENGTH_SHORT
                            ).show()
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
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Метод onResume() полностью удален, так как он стал избыточным.

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
