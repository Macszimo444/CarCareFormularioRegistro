package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.withTransaction
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReminderSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

data class VehicleMaintenances(val vehicle: Vehicle?, val items: List<Maintenance>)

class MaintenanceRepository(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.getInstance(app)
    private val vehicles = VehicleRepository.getInstance(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMaintenances() = vehicles.selectedVehicleFlow.flatMapLatest { vehicle ->
        if (vehicle == null) flowOf(VehicleMaintenances(null, emptyList()))
        else db.maintenanceDao().getForVehicleFlow(vehicle.id)
            .map { VehicleMaintenances(vehicle, it) }
            .onStart { emit(VehicleMaintenances(vehicle, emptyList())) }
    }

    suspend fun delete(maintenance: Maintenance) {
        val reminder = db.withTransaction {
            val linked = db.reminderDao().getForMaintenance(maintenance.id)
            if (linked != null) db.reminderDao().deleteById(linked.id)
            db.maintenanceDao().deleteById(maintenance.id)
            linked
        }
        maintenance.receipt?.let {
            if (db.maintenanceDao().countReceiptReferences(it) == 0)
                com.example.carcareformularioregistro.utils.ReceiptStore(app).delete(it)
        }
        reminder?.let {
            NotificationHelper.cancelReminderAlarm(app, it.id)
            ReminderSettings(app).forget(it.id)
        }
    }
}
