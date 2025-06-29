package com.massagepro.ui.services

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.data.model.Service
import com.massagepro.databinding.ItemServiceBinding
import com.massagepro.R
import java.text.NumberFormat
import java.util.Locale

class ServiceAdapter(
    private val onServiceClick: (Service) -> Unit,
    private val onEditClick: (Service) -> Unit,
    private val onDeleteClick: (Service) -> Unit
) : ListAdapter<Service, ServiceAdapter.ServiceViewHolder>(ServiceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServiceViewHolder {
        val binding = ItemServiceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServiceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServiceViewHolder, position: Int) {
        val service = getItem(position)
        holder.bind(service, onServiceClick, onEditClick, onDeleteClick)
    }

    inner class ServiceViewHolder(private val binding: ItemServiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(service: Service, onServiceClick: (Service) -> Unit, onEditClick: (Service) -> Unit, onDeleteClick: (Service) -> Unit) {
            binding.textViewServiceName.text = service.name

            // ЗМІНЕНО: Отображаем basePrice как Int (без копеек)
            binding.textViewServiceDetails.text =
                "${service.basePrice} грн, ${service.duration} хв"

            // Отображение статуса активности
            if (service.isActive) {
                binding.imageViewServiceStatus.setImageResource(R.drawable.ic_status_active)
                binding.imageViewServiceStatus.visibility = View.VISIBLE
            } else {
                binding.imageViewServiceStatus.setImageResource(R.drawable.ic_status_inactive)
                binding.imageViewServiceStatus.visibility = View.VISIBLE
            }

            binding.root.setOnClickListener { onServiceClick(service) }
            binding.imageButtonEditService.setOnClickListener { onEditClick(service) }
            binding.imageButtonDeleteService.setOnClickListener { onDeleteClick(service) }
        }
    }

    private class ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.duration == newItem.duration &&
                    oldItem.basePrice == newItem.basePrice &&
                    oldItem.category == newItem.category &&
                    oldItem.isActive == newItem.isActive
        }
    }
}