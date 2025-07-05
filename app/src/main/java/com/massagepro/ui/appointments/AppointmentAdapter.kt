package com.massagepro.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.databinding.ItemAppointmentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.massagepro.R

class AppointmentAdapter(private val onAppointmentClick: (AppointmentWithClientAndService) -> Unit) :
    ListAdapter<AppointmentWithClientAndService, AppointmentAdapter.AppointmentViewHolder>(AppointmentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        val appointment = getItem(position)
        holder.bind(appointment, onAppointmentClick)
    }

    class AppointmentViewHolder(private val binding: ItemAppointmentBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appointmentWithDetails: AppointmentWithClientAndService, onAppointmentClick: (AppointmentWithClientAndService) -> Unit) {
            val clientNameText = binding.root.context.getString(R.string.client_prefix, appointmentWithDetails.clientName)
            // ИСПРАВЛЕНО: serviceName теперь через appointment
            val serviceNameText = binding.root.context.getString(R.string.service_prefix, appointmentWithDetails.appointment.serviceName)

            binding.textViewClientName.text = clientNameText
            binding.textViewServiceName.text = serviceNameText

            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            // ИСПРАВЛЕНО: dateTime теперь через appointment
            binding.textViewDateTime.text = dateFormat.format(Date(appointmentWithDetails.appointment.dateTime))

            // ИСПРАВЛЕНО: servicePrice и status теперь через appointment
            val costText = binding.root.context.getString(R.string.cost_prefix, "%.2f".format(appointmentWithDetails.appointment.servicePrice))
            val statusText = binding.root.context.getString(R.string.status_prefix, appointmentWithDetails.appointment.status)
            binding.textViewAppointmentCostStatus.text = "$costText ($statusText)"


            binding.root.setOnClickListener { onAppointmentClick(appointmentWithDetails) }
        }
    }

    private class AppointmentDiffCallback : DiffUtil.ItemCallback<AppointmentWithClientAndService>() {
        override fun areItemsTheSame(oldItem: AppointmentWithClientAndService, newItem: AppointmentWithClientAndService): Boolean {
            return oldItem.appointment.id == newItem.appointment.id
        }

        override fun areContentsTheSame(oldItem: AppointmentWithClientAndService, newItem: AppointmentWithClientAndService): Boolean {
            return oldItem == newItem
        }
    }
}
