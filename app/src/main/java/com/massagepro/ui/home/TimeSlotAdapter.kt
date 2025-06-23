package com.massagepro.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.databinding.ItemTimeSlotBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.view.View
import com.massagepro.R
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import android.util.TypedValue // ДОБАВИТЬ ЭТОТ ИМПОРТ
import android.content.res.Resources // ДОБАВИТЬ ЭТОТ ИМПОРТ

// Data class for a time slot
data class TimeSlot(
    val startTime: Calendar,
    val endTime: Calendar,
    var isBooked: Boolean = false,
    var appointment: Appointment? = null, // Store the actual appointment if booked
    var client: Client? = null, // Store client details for display
    var service: Service? = null // Store service details for display
)

class TimeSlotAdapter(
    private val onBookClick: (TimeSlot) -> Unit,
    private val onBookedSlotClick: (Appointment) -> Unit // Callback for clicking booked slots
) : RecyclerView.Adapter<TimeSlotAdapter.TimeSlotViewHolder>() {

    private val timeSlots = mutableListOf<TimeSlot>()

    fun submitList(list: List<TimeSlot>) {
        timeSlots.clear()
        timeSlots.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeSlotViewHolder {
        val binding = ItemTimeSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimeSlotViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimeSlotViewHolder, position: Int) {
        val timeSlot = timeSlots[position]
        holder.bind(timeSlot)
    }

    override fun getItemCount(): Int = timeSlots.size

    inner class TimeSlotViewHolder(private val binding: ItemTimeSlotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(timeSlot: TimeSlot) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.textViewTimeSlot.text = "${timeFormat.format(timeSlot.startTime.time)} - ${timeFormat.format(timeSlot.endTime.time)}"

            if (timeSlot.isBooked) {
                binding.buttonBookSlot.visibility = View.GONE
                binding.textViewAppointmentInfo.visibility = View.VISIBLE

                val clientName = timeSlot.client?.name ?: binding.root.context.getString(R.string.unknown_client_service)
                val serviceName = timeSlot.service?.name ?: binding.root.context.getString(R.string.unknown_client_service)
                val totalCost = timeSlot.appointment?.totalCost?.let { "%.2f".format(it) } ?: "N/A"

                val infoText = binding.root.context.getString(
                    R.string.occupied_slot_info_full,
                    clientName,
                    serviceName,
                    totalCost
                )
                binding.textViewAppointmentInfo.text = infoText

                binding.root.setBackgroundColor(binding.root.context.resources.getColor(R.color.booked_slot_background_color, null))

                // Set click listener for booked slot
                binding.root.setOnClickListener {
                    timeSlot.appointment?.let { appointment ->
                        onBookedSlotClick(appointment)
                    }
                }

            } else {
                binding.buttonBookSlot.visibility = View.VISIBLE
                binding.textViewAppointmentInfo.visibility = View.GONE

                // ДОБАВИТЬ ЭТИ СТРОКИ:
                val typedValue = TypedValue()
                val theme = binding.root.context.theme
                // Получаем значение colorSurface из текущей темы
                theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
                binding.root.setBackgroundColor(typedValue.data)

                binding.buttonBookSlot.setOnClickListener { onBookClick(timeSlot) }
                binding.root.setOnClickListener(null) // Clear listener for unbooked slots
            }
        }
    }
}
