package com.massagepro.ui.appointments

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.databinding.ItemAppointmentBinding
import java.text.SimpleDateFormat
import java.util.*
import com.massagepro.R

class AppointmentAdapter(
    private val onAppointmentClick: (AppointmentWithClientAndService) -> Unit
) : ListAdapter<AppointmentWithClientAndService, AppointmentAdapter.AppointmentViewHolder>(
    AppointmentDiffCallback()
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppointmentViewHolder {
        val binding = ItemAppointmentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppointmentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppointmentViewHolder, position: Int) {
        holder.bind(getItem(position), onAppointmentClick)
    }

    class AppointmentViewHolder(private val binding: ItemAppointmentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppointmentWithClientAndService, onClick: (AppointmentWithClientAndService) -> Unit) {
            val ctx = binding.root.context
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            binding.textViewClientName.text = ctx.getString(R.string.client_prefix, item.clientName)
            binding.textViewServiceName.text = ctx.getString(R.string.service_prefix, item.serviceName)
            binding.textViewDateTime.text = sdf.format(Date(item.appointment.dateTime))
            val cost = ctx.getString(R.string.cost_prefix, "%.2f".format(item.appointment.servicePrice.toFloat()))
            val status = ctx.getString(R.string.status_prefix, item.appointment.status)
            binding.textViewAppointmentCostStatus.text = "$cost ($status)"

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private class AppointmentDiffCallback : DiffUtil.ItemCallback<AppointmentWithClientAndService>() {
        override fun areItemsTheSame(
            old: AppointmentWithClientAndService,
            new: AppointmentWithClientAndService
        ): Boolean = old.appointment.id == new.appointment.id

        override fun areContentsTheSame(
            old: AppointmentWithClientAndService,
            new: AppointmentWithClientAndService
        ): Boolean = old == new
    }
}