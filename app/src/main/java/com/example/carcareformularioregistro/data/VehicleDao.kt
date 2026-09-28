package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle): Long

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, id DESC LIMIT 1")
    fun getPrimaryVehicleFlow(): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, id DESC LIMIT 1")
    suspend fun getPrimaryVehicle(): Vehicle?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun getVehicleCount(): Int

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, id ASC")
    fun getAllVehiclesFlow(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles ORDER BY isPrimary DESC, id ASC")
    suspend fun getAllVehicles(): List<Vehicle>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: Int): Vehicle?

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Int): Flow<Vehicle?>

    @Query("UPDATE vehicles SET isPrimary = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun markPrimary(id: Int)

    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteById(id: Int)
}
