package com.massagepro.data.repository

import com.massagepro.data.network.SupabaseClient
import com.massagepro.data.model.Appointment
import com.massagepro.data.model.AppointmentWithClientAndService
import com.massagepro.data.model.Client
import com.massagepro.data.model.Service
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*


@Serializable
data class AppointmentResponse(
    val id: Long,
    val clientId: Long,
    val serviceId: Long,
    val dateTime: String,
    val serviceDuration: Int,
    val servicePrice: Int,
    val status: String,
    val notes: String? = null,
    val client: Client,       // содержит name, phone, notes
    val service: Service      // содержит category, duration, basePrice
) {
    fun toAppointment() = Appointment(
        id = id,
        clientId = clientId,
        serviceId = serviceId,
        dateTime = parseDateTime(dateTime),
        serviceDuration = serviceDuration,
        servicePrice = servicePrice,
        status = status,
        notes = notes
    )

    fun toAppointmentWithClientAndService() = AppointmentWithClientAndService(
        appointment = toAppointment(),
        clientName = client.name,
        serviceName = service.category,   // ← используем category вместо name
        serviceCategory = service.category,
        serviceDuration = service.duration,
        serviceBasePrice = service.basePrice
    )

    private fun parseDateTime(dateStr: String): Long =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            .parse(dateStr)?.time ?: 0L
}

class AppointmentRepository {

    private val postgrest = SupabaseClient.client.postgrest["Appointments"]

    fun getAppointmentsWithClientAndService(status: String): Flow<List<AppointmentWithClientAndService>> = flow {
        val result = postgrest.select(Columns.raw("*, client:clientId(*), service:serviceId(*)")) {
            filter { eq("status", status) }
        }.decodeList<AppointmentResponse>().map { it.toAppointmentWithClientAndService() }
        emit(result)
    }

    fun getAppointmentsForDayIncludingAllStatuses(startOfDayMillis: Long, endOfDayMillis: Long): Flow<List<AppointmentWithClientAndService>> = flow {
        val startStr = formatDateTime(startOfDayMillis)
        val endStr = formatDateTime(endOfDayMillis)
        val result = postgrest.select(Columns.raw("*, client:clientId(*), service:serviceId(*)")) {
            filter {
                gte("dateTime", startStr)
                lte("dateTime", endStr)
            }
        }.decodeList<AppointmentResponse>().map { it.toAppointmentWithClientAndService() }
        emit(result)
    }

    fun getAllAppointments(status: String): Flow<List<Appointment>> = flow {
        emit(postgrest.select { filter { eq("status", status) } }.decodeList<Appointment>())
    }

    suspend fun insertAppointment(appointment: Appointment) {
        postgrest.insert(appointment)   // ← передаём как есть
    }

    suspend fun updateAppointment(appointment: Appointment) {
        postgrest.update({
            set("clientId", appointment.clientId)
            set("serviceId", appointment.serviceId)
            set("dateTime", appointment.dateTime)
            set("serviceDuration", appointment.serviceDuration)
            set("servicePrice", appointment.servicePrice)
            set("status", appointment.status)
            set("notes", appointment.notes)
        }) {
            filter { eq("id", appointment.id) }
        }
    }

    suspend fun deleteAppointment(appointment: Appointment) {
        postgrest.delete { filter { eq("id", appointment.id) } }
    }

    suspend fun getAppointmentById(id: Long): Appointment? =
        postgrest.select { filter { eq("id", id) } }.decodeSingleOrNull()

    suspend fun getConflictingAppointments(newStart: Long, newEnd: Long, excludeAppointmentId: Long): List<Appointment> {
        val startStr = formatDateTime(newStart)
        val endStr = formatDateTime(newEnd)
        return postgrest.select {
            filter {
                or {
                    and { gte("dateTime", startStr); lt("dateTime", endStr) }
                    and {
                        lt("dateTime", startStr)
                        gt("dateTime + make_interval(0,0,0,0,0,0,serviceDuration*60)", startStr)
                    }
                }
                neq("id", excludeAppointmentId)
            }
        }.decodeList<Appointment>()
    }

    fun getAppointmentsForDay(startOfDayMillis: Long, endOfDayMillis: Long, status: String): Flow<List<AppointmentWithClientAndService>> = flow {
        val startStr = formatDateTime(startOfDayMillis)
        val endStr = formatDateTime(endOfDayMillis)
        val result = postgrest.select(Columns.raw("*, client:clientId(*), service:serviceId(*)")) {
            filter {
                gte("dateTime", startStr)
                lte("dateTime", endStr)
                eq("status", status)
            }
        }.decodeList<AppointmentResponse>().map { it.toAppointmentWithClientAndService() }
        emit(result)
    }

    suspend fun getAppointmentsByStatus(status: String): List<Appointment> =
        postgrest.select { filter { eq("status", status) } }.decodeList()

    suspend fun getAllAppointmentsList(): List<Appointment> =
        postgrest.select().decodeList()

    private fun formatDateTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            .format(Date(timestamp))
}