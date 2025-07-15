package com.massagepro.ui.clients

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.massagepro.data.model.Client
import com.massagepro.databinding.ItemClientBinding

class ClientAdapter(
    private val onClientClick: (Client) -> Unit,
    private val onEditClick: (Client) -> Unit,
    private val onDeleteClick: (Client) -> Unit
) : ListAdapter<Client, ClientAdapter.ClientViewHolder>(ClientDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClientViewHolder {
        val binding = ItemClientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ClientViewHolder(private val binding: ItemClientBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(client: Client) {
            binding.textViewClientName.text = client.name
            binding.textViewClientPhone.text = client.phone
            binding.root.setOnClickListener { onClientClick(client) }
            binding.imageButtonEditClient.setOnClickListener { onEditClick(client) }
            binding.imageButtonDeleteClient.setOnClickListener { onDeleteClick(client) }
        }
    }

    private class ClientDiffCallback : DiffUtil.ItemCallback<Client>() {
        override fun areItemsTheSame(oldItem: Client, newItem: Client): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Client, newItem: Client): Boolean =
            oldItem == newItem
    }
}