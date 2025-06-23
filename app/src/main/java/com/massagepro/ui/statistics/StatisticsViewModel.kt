package com.massagepro.ui.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.massagepro.data.dao.AppointmentDao
import com.massagepro.data.dao.ClientDao
import com.massagepro.data.dao.ServiceDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class StatisticsViewModel(private val appointmentDao: AppointmentDao, private val clientDao: ClientDao, private val serviceDao: ServiceDao) : ViewModel() {

    private val _totalAppointments = MutableLiveData<Int>()
    val totalAppointments: LiveData<Int> = _totalAppointments

    private val _totalRevenue = MutableLiveData<Double>()
    val totalRevenue: LiveData<Double> = _totalRevenue

    private val _mostPopularService = MutableLiveData<String>()
    val mostPopularService: LiveData<String> = _mostPopularService

    private val _mostActiveClient = MutableLiveData<String>()
    val mostActiveClient: LiveData<String> = _mostActiveClient

    fun generateStatistics(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            val appointments = appointmentDao.getAppointmentsForDateRange(startDate, endDate).first()

            _totalAppointments.value = appointments.size
            _totalRevenue.value = appointments.sumOf { it.totalCost }

            // Most popular service
            val serviceCounts = appointments.groupBy { it.serviceId }.mapValues { it.value.size }
            val mostPopularServiceId = serviceCounts.maxByOrNull { it.value }?.key
            _mostPopularService.value = mostPopularServiceId?.let { serviceDao.getServiceById(it)?.name } ?: "Нет данных"

            // Most active client
            val clientCounts = appointments.groupBy { it.clientId }.mapValues { it.value.size }
            val mostActiveClientId = clientCounts.maxByOrNull { it.value }?.key
            _mostActiveClient.value = mostActiveClientId?.let { clientDao.getClientById(it)?.name } ?: "Нет данных"
        }
    }
}

class StatisticsViewModelFactory(private val appointmentDao: AppointmentDao, private val clientDao: ClientDao, private val serviceDao: ServiceDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(appointmentDao, clientDao, serviceDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


