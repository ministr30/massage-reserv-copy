package com.massagepro.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.databinding.ItemTimeSlotBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil


class TimeSlotAdapter(
    private val onBookClick: (TimeSlot) -> Unit,
    private val showAppointmentActionsDialog: (Appointment) -> Unit
) : ListAdapter<TimeSlot, TimeSlotAdapter.TimeSlotViewHolder>(TimeSlotDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeSlotViewHolder {
        val binding = ItemTimeSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimeSlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimeSlotViewHolder, position: Int) {
        val timeSlot = getItem(position)
        holder.bind(timeSlot, onBookClick, showAppointmentActionsDialog)
    }

    class TimeSlotViewHolder(private val binding: ItemTimeSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot, onBookClick: (TimeSlot) -> Unit, showAppointmentActionsDialog: (Appointment) -> Unit) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale("uk", "UA"))

            // --- Базовая настройка слота ---
            binding.textViewTimeSlot.text = timeFormat.format(timeSlot.startTime.time)
            val currentTime = Calendar.getInstance()
            val isPastSlot = timeSlot.startTime.before(currentTime)
            val baseSlotHeightPx = binding.root.context.resources.getDimensionPixelSize(R.dimen.base_time_slot_height)
            val verticalMarginPx = binding.root.context.resources.getDimensionPixelSize(R.dimen.time_slot_margin_vertical)

            binding.buttonBook.visibility = View.GONE
            binding.bookedLayout.visibility = View.GONE
            binding.root.setOnClickListener(null)

            val layoutParams = binding.cardViewTimeSlot.layoutParams
            layoutParams.height = baseSlotHeightPx
            binding.cardViewTimeSlot.layoutParams = layoutParams

            // --- Логика для разных состояний слота ---
            if (timeSlot.isBooked) {
                if (timeSlot.shouldDisplay && timeSlot.bookedAppointment != null) {
                    val appointment = timeSlot.bookedAppointment
                    binding.bookedLayout.visibility = View.VISIBLE

                    // Отображение деталей записи
                    binding.textViewClientName.text = timeSlot.client?.name ?: binding.root.context.getString(R.string.unknown_client)
                    binding.textViewServiceName.text = timeSlot.service?.category ?: binding.root.context.getString(R.string.unknown_service)
                    binding.textViewAppointmentStatus.text = binding.root.context.getString(R.string.appointment_status_prefix, appointment.status)
                    binding.textViewServicePrice.text = binding.root.context.getString(R.string.service_price_prefix, appointment.servicePrice.toString())

                    // Стилизация занятого слота
                    binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.lavender_booked_slot))
                    binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                    binding.textViewClientName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                    binding.textViewServiceName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                    binding.textViewAppointmentStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                    binding.textViewServicePrice.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))

                    // Расчет высоты слота
                    val durationMinutes = appointment.serviceDuration
                    val numberOfSlots = ceil(durationMinutes / 30.0).toInt().coerceAtLeast(1)
                    layoutParams.height = (baseSlotHeightPx * numberOfSlots) + (verticalMarginPx * (numberOfSlots - 1))
                    binding.cardViewTimeSlot.layoutParams = layoutParams

                    // Точное время начала и конца из самой записи
                    val appointmentStartTime = Calendar.getInstance().apply { timeInMillis = appointment.dateTime }
                    val appointmentEndTime = Calendar.getInstance().apply { timeInMillis = appointment.dateTime + TimeUnit.MINUTES.toMillis(durationMinutes.toLong()) }

                    val startTimeString = timeFormat.format(appointmentStartTime.time)
                    val endTimeString = timeFormat.format(appointmentEndTime.time)

                    binding.textViewTimeSlot.text = binding.root.context.getString(
                        R.string.time_range_format,
                        startTimeString,
                        endTimeString
                    )

                    binding.root.setOnClickListener { showAppointmentActionsDialog(appointment) }

                } else {
                    // Слот занят, но это не начальный слот (продолжение)
                    binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.lavender_booked_slot))
                    binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                }
            } else if (isPastSlot) {
                // Прошедший свободный слот
                binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.colorPastSlot))
                binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.grey_text_disabled))
            } else {
                // Будущий свободный слот
                binding.buttonBook.visibility = View.VISIBLE
                binding.buttonBook.setOnClickListener { onBookClick(timeSlot) }
                binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.colorFreeSlot))
                binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.black))
            }
        }
    }

    private class TimeSlotDiffCallback : DiffUtil.ItemCallback<TimeSlot>() {
        override fun areItemsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            return oldItem.startTime.timeInMillis == newItem.startTime.timeInMillis &&
                    oldItem.bookedAppointment?.id == newItem.bookedAppointment?.id
        }

        override fun areContentsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            return oldItem == newItem
        }
    }
}