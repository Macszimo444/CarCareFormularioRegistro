package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(maintenance: Maintenance): Long

    @Update
    suspend fun update(maintenance: Maintenance)

    @Delete
    suspend fun delete(maintenance: Maintenance)

    @Query("DELETE FROM maintenances WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM maintenances ORDER BY date DESC")
    fun getAllMaintenancesFlow(): Flow<List<Maintenance>>

    @Query("SELECT * FROM maintenances ORDER BY date DESC")
    suspend fun getAllMaintenances(): List<Maintenance>

    @Query("SELECT * FROM maintenances WHERE status = :status ORDER BY date DESC")
    fun getMaintenancesByStatusFlow(status: String): Flow<List<Maintenance>>

    @Query("SELECT * FROM maintenances WHERE status = 'Realizado' ORDER BY date DESC")
    fun getCompletedMaintenancesFlow(): Flow<List<Maintenance>>

    @Query("SELECT * FROM maintenances WHERE status != 'Realizado' ORDER BY date ASC LIMIT 1")
    fun getNextMaintenanceFlow(): Flow<Maintenance?>

    @Query("SELECT * FROM maintenances WHERE status != 'Realizado' ORDER BY date ASC LIMIT 1")
    suspend fun getNextMaintenance(): Maintenance?

    @Query("SELECT COUNT(*) FROM maintenances WHERE status = 'Realizado'")
    fun getCompletedCountFlow(): Flow<Int>

    @Query("SELECT SUM(cost) FROM maintenances WHERE status = 'Realizado'")
    fun getTotalSpentFlow(): Flow<Double?>

    @Query("SELECT * FROM maintenances WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Maintenance?
}
