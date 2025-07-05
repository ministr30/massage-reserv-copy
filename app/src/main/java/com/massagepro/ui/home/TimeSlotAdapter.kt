package com.massagepro.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.R
import com.massagepro.databinding.ItemTimeSlotBinding
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.core.content.ContextCompat
import com.massagepro.data.model.Appointment
import java.util.Calendar

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

            binding.textViewTimeSlot.text = timeFormat.format(timeSlot.startTime.time)

            val currentTime = Calendar.getInstance()
            currentTime.set(Calendar.SECOND, 0)
            currentTime.set(Calendar.MILLISECOND, 0)

            val isPastSlot = timeSlot.startTime.before(currentTime)

            val baseSlotHeightPx = binding.root.context.resources.getDimensionPixelSize(R.dimen.base_time_slot_height)
            val verticalMarginPx = binding.root.context.resources.getDimensionPixelSize(R.dimen.time_slot_margin_vertical)

            binding.buttonBook.visibility = View.GONE
            binding.bookedLayout.visibility = View.GONE
            binding.root.setOnClickListener(null)

            val layoutParams = binding.cardViewTimeSlot.layoutParams
            layoutParams.height = baseSlotHeightPx
            binding.cardViewTimeSlot.layoutParams = layoutParams

            if (timeSlot.isBooked) {
                if (timeSlot.shouldDisplay) {
                    binding.bookedLayout.visibility = View.VISIBLE

                    val appointment = timeSlot.bookedAppointment
                    if (appointment != null) {
                        val clientName = timeSlot.client?.name ?: binding.root.context.getString(R.string.unknown_client)
                        val serviceName = timeSlot.service?.category ?: binding.root.context.getString(R.string.unknown_service)
                        val statusText = binding.root.context.getString(R.string.appointment_status_prefix, appointment.status)
                        val servicePrice = appointment.servicePrice.toString()

                        binding.textViewClientName.text = clientName
                        binding.textViewServiceName.text = serviceName
                        binding.textViewAppointmentStatus.text = statusText
                        binding.textViewServicePrice.text = binding.root.context.getString(R.string.service_price_prefix, servicePrice)

                        binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.lavender_booked_slot))
                        binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                        binding.textViewClientName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                        binding.textViewServiceName.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                        binding.textViewAppointmentStatus.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                        binding.textViewServicePrice.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))

                        val durationMinutes = appointment.serviceDuration
                        val numberOfSlots = (durationMinutes / 30.0).let { if (it > 0) Math.ceil(it).toInt() else 1 }
                        val desiredHeight = baseSlotHeightPx * numberOfSlots + (numberOfSlots - 1) * verticalMarginPx
                        layoutParams.height = desiredHeight
                        binding.cardViewTimeSlot.layoutParams = layoutParams

                        val displayedEndTime = timeSlot.appointmentEndTime ?: Calendar.getInstance().apply {
                            timeInMillis = appointment.dateTime + (durationMinutes * 60 * 1000)
                        }
                        binding.textViewTimeSlot.text = "${timeFormat.format(timeSlot.startTime.time)} - ${timeFormat.format(displayedEndTime.time)}"

                        binding.root.setOnClickListener { showAppointmentActionsDialog(appointment) }
                    } else {
                        // Если appointment == null, показываем занятый слот без деталей
                        binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.lavender_booked_slot))
                        binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                    }
                } else {
                    binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.lavender_booked_slot))
                    binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.white))
                }
            } else if (isPastSlot) {
                binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.colorPastSlot))
                binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.grey_text_disabled))
            } else {
                binding.buttonBook.visibility = View.VISIBLE
                binding.buttonBook.setOnClickListener { onBookClick(timeSlot) }

                binding.cardViewTimeSlot.setCardBackgroundColor(ContextCompat.getColor(binding.root.context, R.color.colorFreeSlot))
                binding.textViewTimeSlot.setTextColor(ContextCompat.getColor(binding.root.context, R.color.black))
            }
        }
    }

    private class TimeSlotDiffCallback : DiffUtil.ItemCallback<TimeSlot>() {
        override fun areItemsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            if (oldItem.isBooked && newItem.isBooked) {
                return oldItem.startTime.timeInMillis == newItem.startTime.timeInMillis &&
                        oldItem.bookedAppointment?.id == newItem.bookedAppointment?.id
            }
            if (!oldItem.isBooked && !newItem.isBooked) {
                return oldItem.startTime.timeInMillis == newItem.startTime.timeInMillis
            }
            return false
        }

        override fun areContentsTheSame(oldItem: TimeSlot, newItem: TimeSlot): Boolean {
            return oldItem == newItem
        }
    }
}
