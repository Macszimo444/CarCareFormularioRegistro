package com.example.carcareformularioregistro.data

/** Reuses the existing Room table and one observable query. */
class MaintenanceRepository(private val dao: MaintenanceDao) {
    fun observeMaintenances() = dao.getAllMaintenancesFlow()

    suspend fun delete(maintenance: Maintenance) = dao.delete(maintenance)
}
