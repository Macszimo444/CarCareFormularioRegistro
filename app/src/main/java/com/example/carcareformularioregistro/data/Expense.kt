package com.example.carcareformularioregistro.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val vehicleId: Int,
    val category: String, // "Mantenimiento", "Combustible", "Reparaciones", "Llantas", "Otros"
    val concept: String,
    val amount: Double,
    val date: String,
    val description: String = ""
) {
    companion object {
        const val CAT_MANTENIMIENTO = "Mantenimiento"
        const val CAT_COMBUSTIBLE = "Combustible"
        const val CAT_REPARACIONES = "Reparaciones"
        const val CAT_LLANTAS = "Llantas"
        const val CAT_OTROS = "Otros"
    }
}
