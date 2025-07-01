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
import kotlinx.coroutines.flow.firstOrNull // ИЗМЕНЕНО: firstOrNull вместо first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.massagepro.data.model.Appointment

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

    // ИЗМЕНЕНО: Добавлен параметр groupingInterval
    fun generateStatistics(startDate: Date, endDate: Date, groupingInterval: GroupingInterval) {
        viewModelScope.launch {
            try {
                // ИЗМЕНЕНО: Логика для "За весь час" и получения списка AppointmentWithClientAndService
                val appointmentWithClientAndServiceList = if (groupingInterval == GroupingInterval.ALL_TIME) {
                    appointmentRepository.getAllAppointments().firstOrNull() ?: emptyList() // Предполагаем, что getAllAppointments() возвращает Flow<List<AppointmentWithClientAndService>>
                } else {
                    appointmentRepository.getAppointmentsForDay(
                        startDate.time,
                        endDate.time
                    ).firstOrNull() ?: emptyList() // ИЗМЕНЕНО: firstOrNull()
                }

                // ИЗМЕНЕНО: Преобразуем в список Appointment
                val appointments = appointmentWithClientAndServiceList.map { it.appointment }

                // 1. Общее количество записей
                _totalAppointments.value = appointments.size

                // 2. Общая выручка
                _totalRevenue.value = appointments.sumOf { it.servicePrice }

                // 3. Самая популярная услуга
                calculateMostPopularService(appointments)

                // 4. Самый активный клиент
                calculateMostActiveClient(appointments)

                // 5. Данные для BarChart - ИЗМЕНЕНО: Теперь с учетом groupingInterval
                calculateAppointmentsByDate(appointments, groupingInterval)

                // 6. Данные для PieChart
                calculateRevenueByCategory(appointments)

            } catch (e: Exception) {
                // Логируем ошибку
                e.printStackTrace()
            }
        }
    }

    private suspend fun calculateMostPopularService(appointments: List<Appointment>) {
        val serviceCounts = appointments.groupBy { it.serviceId }.mapValues { it.value.size }
        val mostPopularServiceId = serviceCounts.maxByOrNull { it.value }?.key

        _mostPopularService.value = mostPopularServiceId?.let {
            serviceRepository.getServiceById(it)?.name
        } ?: "Немає даних" // ИЗМЕНЕНО: "Нет данных" на "Немає даних"
    }

    private suspend fun calculateMostActiveClient(appointments: List<Appointment>) {
        val clientCounts = appointments.groupBy { it.clientId }.mapValues { it.value.size }
        val mostActiveClientId = clientCounts.maxByOrNull { it.value }?.key

        _mostActiveClient.value = mostActiveClientId?.let {
            clientRepository.getClientById(it)?.name
        } ?: "Немає даних" // ИЗМЕНЕНО: "Нет данных" на "Немає даних"
    }

    // ИЗМЕНЕНО: Добавлен параметр groupingInterval
    private fun calculateAppointmentsByDate(appointments: List<Appointment>, groupingInterval: GroupingInterval) {
        val dailyAppointments = when (groupingInterval) {
            GroupingInterval.DAY -> appointments
                .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.WEEK -> appointments
                .groupBy {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.dateTime }
                    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) // Неделя начинается с понедельника
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time) // Дата начала недели
                }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.MONTH -> appointments
                .groupBy { SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(it.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.YEAR -> appointments
                .groupBy { SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(it.dateTime)) }
                .mapValues { it.value.size }
                .toSortedMap()
            GroupingInterval.ALL_TIME -> mapOf("За весь час" to appointments.size) // Для "За весь час" одна колонка
        }
        _appointmentsByDate.value = dailyAppointments
    }

    private suspend fun calculateRevenueByCategory(appointments: List<Appointment>) {
        val revenuePerCategory = mutableMapOf<String, Int>()
        appointments.forEach { appointment ->
            val service = serviceRepository.getServiceById(appointment.serviceId)
            val category = service?.category ?: "Невідома категорія" // ИЗМЕНЕНО: "Неизвестная категория" на "Невідома категорія"
            revenuePerCategory[category] = (revenuePerCategory[category] ?: 0) + appointment.servicePrice
        }

        _revenueByCategory.value = revenuePerCategory
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
