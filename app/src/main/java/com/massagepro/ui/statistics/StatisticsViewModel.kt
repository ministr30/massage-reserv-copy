package com.massagepro.ui.statistics
import java.util.Calendar

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.massagepro.data.repository.AppointmentRepository
import com.massagepro.data.repository.ClientRepository
import com.massagepro.data.repository.ServiceRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.AppointmentStatus

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

    private val _appointmentsByDate = MutableLiveData<Map<String, Int>>()
    val appointmentsByDate: LiveData<Map<String, Int>> = _appointmentsByDate

    private val _revenueByCategory = MutableLiveData<Map<String, Int>>()
    val revenueByCategory: LiveData<Map<String, Int>> = _revenueByCategory

    fun generateStatistics(startDate: Date, endDate: Date, groupingInterval: GroupingInterval) {
        viewModelScope.launch {
            try {
                val allAppointments: List<Appointment>
                val appointmentsWithDetails: List<AppointmentWithClientAndService>

                val completedStatus = AppointmentStatus.COMPLETED.statusValue

                if (groupingInterval == GroupingInterval.ALL_TIME) {
                    allAppointments = appointmentRepository.getAllAppointments(completedStatus).firstOrNull() ?: emptyList()
                    appointmentsWithDetails = appointmentRepository.getAppointmentsWithClientAndService(completedStatus).firstOrNull() ?: emptyList()

                } else {
                    appointmentsWithDetails = appointmentRepository.getAppointmentsForDay(
                        startDate.time,
                        endDate.time,
                        completedStatus
                    ).firstOrNull() ?: emptyList()
                    allAppointments = appointmentsWithDetails.map { it.appointment }
                }

                _totalAppointments.value = allAppointments.size
                _totalRevenue.value = allAppointments.sumOf { it.servicePrice }
                calculateMostPopularService(allAppointments)
                calculateMostActiveClient(allAppointments)
                calculateAppointmentsByDate(appointmentsWithDetails, groupingInterval)
                calculateRevenueByCategory(appointmentsWithDetails)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun calculateMostPopularService(appointments: List<Appointment>) {
        val serviceCounts = appointments.groupBy { it.serviceId }.mapValues { it.value.size }
        val mostPopularServiceId = serviceCounts.maxByOrNull { it.value }?.key

        _mostPopularService.value = mostPopularServiceId?.let {
            serviceRepository.getServiceById(it)?.category
        } ?: "Немає даних"
    }

    private suspend fun calculateMostActiveClient(appointments: List<Appointment>) {
        val clientCounts = appointments.groupBy { it.clientId }.mapValues { it.value.size }
        val mostActiveClientId = clientCounts.maxByOrNull { it.value }?.key

        _mostActiveClient.value = mostActiveClientId?.let {
            clientRepository.getClientById(it)?.name
        } ?: "Немає даних"
    }

    private fun calculateAppointmentsByDate(appointments: List<AppointmentWithClientAndService>, groupingInterval: GroupingInterval) {
        val dailyAppointments = when (groupingInterval) {
            GroupingInterval.DAY -> appointments
                .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.appointment.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.WEEK -> appointments
                .groupBy {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.appointment.dateTime }
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.MONTH -> appointments
                .groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.appointment.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.YEAR -> appointments
                .groupBy { SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(it.appointment.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.ALL_TIME -> mapOf("За весь час" to appointments.size)
        }
        _appointmentsByDate.value = dailyAppointments
    }

    private fun calculateRevenueByCategory(appointments: List<AppointmentWithClientAndService>) {
        val revenuePerCategory = mutableMapOf<String, Int>()
        appointments.forEach { appointmentWithDetails ->
            val category = appointmentWithDetails.serviceCategory
            val price = appointmentWithDetails.appointment.servicePrice
            revenuePerCategory[category] = (revenuePerCategory[category] ?: 0) + price
        }
        _revenueByCategory.value = revenuePerCategory
    }
}