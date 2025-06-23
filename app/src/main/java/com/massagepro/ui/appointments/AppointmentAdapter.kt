
package com.massagepro.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import com.massagepro.databinding.ItemAppointmentBinding
import java.text.SimpleDateFormat
import java.util.Locale

data class AppointmentDisplayItem(
    val appointment: Appointment,
    val client: Client?,
    val service: Service?
)

class AppointmentAdapter(private val onAppointmentClick: (AppointmentDisplayItem) -> Unit, private val onEditClick: (AppointmentDisplayItem) -> Unit, private val onDeleteClick: (AppointmentDisplayItem) -> Unit) :
    ListAdapter<AppointmentDisplayItem, AppointmentAdapter.AppointmentViewHolder>(AppointmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class AppointmentViewHolder(private val binding: ItemAppointmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppointmentDisplayItem) {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val startTimeFormatted = dateFormat.format(item.appointment.startTime)
            val endTimeFormatted = dateFormat.format(item.appointment.endTime)

            binding.textViewAppointmentDateTime.text = "$startTimeFormatted - $endTimeFormatted"
            binding.textViewAppointmentClient.text = "Клиент: ${item.client?.name ?: "Неизвестно"} (${item.client?.phone ?: ""})"
            binding.textViewAppointmentService.text = "Услуга: ${item.service?.name ?: "Неизвестно"} (${item.service?.duration ?: 0} мин)"
            binding.textViewAppointmentCostStatus.text = "Стоимость: %.2f грн (%s)".format(item.appointment.totalCost, item.appointment.status)

            binding.root.setOnClickListener { onAppointmentClick(item) }
            binding.imageButtonEditAppointment.setOnClickListener { onEditClick(item) }
            binding.imageButtonDeleteAppointment.setOnClickListener { onDeleteClick(item) }
        }
    }

    private class AppointmentDiffCallback : DiffUtil.ItemCallback<AppointmentDisplayItem>() {
        override fun areItemsTheSame(oldItem: AppointmentDisplayItem, newItem: AppointmentDisplayItem): Boolean {
            return oldItem.appointment.id == newItem.appointment.id
        }

        override fun areContentsTheSame(oldItem: AppointmentDisplayItem, newItem: AppointmentDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}


