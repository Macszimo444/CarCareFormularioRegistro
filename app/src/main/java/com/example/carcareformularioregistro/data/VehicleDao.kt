package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles ORDER BY id DESC LIMIT 1")
    fun getPrimaryVehicleFlow(): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles ORDER BY id DESC LIMIT 1")
    suspend fun getPrimaryVehicle(): Vehicle?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun getVehicleCount(): Int
}
