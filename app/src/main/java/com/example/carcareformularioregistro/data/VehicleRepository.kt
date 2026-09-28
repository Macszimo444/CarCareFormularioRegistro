package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.withTransaction
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.util.Log

data class VehicleRecordCounts(val maintenances: Int, val expenses: Int, val reminders: Int)

/** Primary belongs to a car; selected is the last vehicle workspace in this installation. */
class VehicleRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.getInstance(app)
    private val preferences = app.getSharedPreferences("carcare_vehicle_selection", Context.MODE_PRIVATE)
    private val selectedId = MutableStateFlow(preferences.getInt("selected_id", 0))
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val selectedVehicleFlow: Flow<Vehicle?> = combine(db.vehicleDao().getAllVehiclesFlow(), selectedId) { cars, id ->
        resolve(cars, id)
    }.distinctUntilChanged()

    private fun resolve(cars: List<Vehicle>, id: Int): Vehicle? =
        cars.firstOrNull { it.id == id } ?: cars.firstOrNull { it.isPrimary } ?: cars.firstOrNull()

    suspend fun getSelectedVehicle(): Vehicle? = resolve(db.vehicleDao().getAllVehicles(), selectedId.value)

    fun selectVehicle(id: Int) {
        preferences.edit().putInt("selected_id", id).apply()
        selectedId.value = id
    }

    suspend fun saveVehicle(vehicle: Vehicle, makePrimary: Boolean = vehicle.isPrimary): Int {
        var previousMileage: Int? = null
        val id = db.withTransaction {
            val old = if (vehicle.id == 0) null else db.vehicleDao().getById(vehicle.id)
            require(vehicle.id == 0 || old != null) { "El vehículo ya no existe" }
            previousMileage = old?.mileage
            val primary = makePrimary || old?.isPrimary == true || db.vehicleDao().getVehicleCount() == 0
            val saved = vehicle.copy(isPrimary = primary)
            val result = if (old == null) db.vehicleDao().insertVehicle(saved).toInt() else {
                db.vehicleDao().updateVehicle(saved)
                saved.id
            }
            if (primary) db.vehicleDao().markPrimary(result)
            result
        }
        if (vehicle.id == 0) selectVehicle(id)
        if (previousMileage != vehicle.mileage) notificationScope.launch {
            try { NotificationHelper.rescheduleVehicle(app, id) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.w("CarCare", "Saved vehicle; could not refresh its alerts", error) }
        }
        return id
    }

    suspend fun setPrimary(id: Int) = db.withTransaction {
        requireNotNull(db.vehicleDao().getById(id)) { "El vehículo ya no existe" }
        db.vehicleDao().markPrimary(id)
    }

    suspend fun counts(id: Int) = db.withTransaction {
        VehicleRecordCounts(db.maintenanceDao().countForVehicle(id),
            db.expenseDao().countForVehicle(id), db.reminderDao().countForVehicle(id))
    }

    suspend fun deleteVehicleAndRecords(id: Int) {
        val reminderIds = db.withTransaction {
            val reminders = db.reminderDao().getForVehicle(id).map { it.id }
            db.reminderDao().deleteForVehicle(id)
            db.maintenanceDao().deleteForVehicle(id)
            db.expenseDao().deleteForVehicle(id)
            db.vehicleDao().deleteById(id)
            val cars = db.vehicleDao().getAllVehicles()
            if (cars.isNotEmpty() && cars.none { it.isPrimary }) db.vehicleDao().markPrimary(cars.first().id)
            reminders
        }
        reminderIds.forEach {
            NotificationHelper.cancelReminderAlarm(app, it)
            ReminderSettings(app).forget(it)
        }
        if (selectedId.value == id) selectVehicle(db.vehicleDao().getPrimaryVehicle()?.id ?: 0)
    }

    companion object {
        @Volatile private var instance: VehicleRepository? = null
        fun getInstance(context: Context): VehicleRepository = instance ?: synchronized(this) {
            instance ?: VehicleRepository(context).also { instance = it }
        }
    }
}
