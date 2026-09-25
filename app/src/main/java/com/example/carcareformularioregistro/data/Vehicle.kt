package com.example.carcareformularioregistro.data

import androidx.room.Entity
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
    val photoUri: String? = null
)
