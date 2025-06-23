
package com.massagepro.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.massagepro.data.dao.ClientDao

class ClientsViewModelFactory(private val clientDao: ClientDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClientsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ClientsViewModel(clientDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


