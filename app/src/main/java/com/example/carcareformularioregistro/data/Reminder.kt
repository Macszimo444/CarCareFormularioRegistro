package com.example.carcareformularioregistro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vehicleId: Int,
    val title: String,
    val description: String = "",
    val dueDate: String,
    val dueMileage: Int = 0,
    val priority: String = PRIORITY_MEDIA, // "Alta", "Media", "Baja"
    val enabled: Boolean = true
) {
    companion object {
        const val PRIORITY_ALTA = "Alta"
        const val PRIORITY_MEDIA = "Media"
        const val PRIORITY_BAJA = "Baja"
    }
}
