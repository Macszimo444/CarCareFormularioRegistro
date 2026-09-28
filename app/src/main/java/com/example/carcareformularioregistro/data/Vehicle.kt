package com.example.carcareformularioregistro.data

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val brand: String,
    val model: String,
    val year: Int,
    val mileage: Int,
    val plates: String,
    val photoUri: String? = null,
    @ColumnInfo(defaultValue = "0") val isPrimary: Boolean = false
) {
    @get:Ignore
    val displayName: String
        get() = name.ifBlank { listOf(brand, model, year.toString()).filter { it.isNotBlank() }.joinToString(" ") }
}
