package com.example.carcareformularioregistro.data

/** Called inside the same Room transaction that saves the service and its new optional plan. */
object ReviewCompletion {
    suspend fun consume(db: AppDatabase, reminderId: Int, vehicleId: Int): Reminder {
        val reminder = requireNotNull(db.reminderDao().getById(reminderId)) { "La revisión ya fue resuelta o eliminada" }
        require(reminder.vehicleId == vehicleId)
        reminder.maintenanceId?.let { id ->
            val service = requireNotNull(db.maintenanceDao().getById(id))
            require(service.vehicleId == vehicleId)
            if (service.status == Maintenance.STATUS_REALIZADO) {
                // Keep the old historical service, but its next-review target is now resolved.
                db.maintenanceDao().update(service.copy(nextDate = "", nextMileage = 0))
            }
        }
        db.reminderDao().delete(reminder)
        return reminder
    }
}
