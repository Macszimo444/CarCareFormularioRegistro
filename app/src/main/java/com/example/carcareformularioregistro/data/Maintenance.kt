package com.example.carcareformularioregistro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenances")
data class Maintenance(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vehicleId: Int,
    val type: String,
    val date: String,
    val mileage: Int,
    val cost: Double,
    val workshop: String,
    val nextDate: String,
    val nextMileage: Int,
    val description: String = "",
    val status: String = STATUS_PROXIMO // "Próximo", "Pendiente", "Realizado"
) {
    companion object {
        const val STATUS_PROXIMO = "Próximo"
        const val STATUS_PENDIENTE = "Pendiente"
        const val STATUS_REALIZADO = "Realizado"
    }
}
