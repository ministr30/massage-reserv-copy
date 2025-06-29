package com.massagepro.ui.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

class StatisticsViewModel(
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    private val _totalAppointments = MutableLiveData<Int>()
    val totalAppointments: LiveData<Int> = _totalAppointments

    private val _totalRevenue = MutableLiveData<Int>()
    val totalRevenue: LiveData<Int> = _totalRevenue

    private val _mostPopularService = MutableLiveData<String>()
    val mostPopularService: LiveData<String> = _mostPopularService

    private val _mostActiveClient = MutableLiveData<String>()
    val mostActiveClient: LiveData<String> = _mostActiveClient

    fun generateStatistics(startDate: Date, endDate: Date) {
        viewModelScope.launch {
            val appointmentsWithDetails = appointmentRepository.getAppointmentsForDay(startDate.time, endDate.time).first()

            _totalAppointments.value = appointmentsWithDetails.size

            // Приводим сумму к Int, округляя вниз (если нужны другие варианты — скажи)
            _totalRevenue.value = appointmentsWithDetails.sumOf { it.appointment.servicePrice.toInt() }

            val serviceCounts = appointmentsWithDetails.groupBy { it.appointment.serviceId }.mapValues { it.value.size }
            val mostPopularServiceId = serviceCounts.maxByOrNull { it.value }?.key
            _mostPopularService.value = mostPopularServiceId?.let { serviceRepository.getServiceById(it)?.name } ?: "Немає даних"

            val clientCounts = appointmentsWithDetails.groupBy { it.appointment.clientId }.mapValues { it.value.size }
            val mostActiveClientId = clientCounts.maxByOrNull { it.value }?.key
            _mostActiveClient.value = mostActiveClientId?.let { clientRepository.getClientById(it)?.name } ?: "Немає даних"
        }
    }
}


class StatisticsViewModelFactory(
    private val appointmentRepository: AppointmentRepository,
    private val clientRepository: ClientRepository,
    private val serviceRepository: ServiceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatisticsViewModel(appointmentRepository, clientRepository, serviceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}