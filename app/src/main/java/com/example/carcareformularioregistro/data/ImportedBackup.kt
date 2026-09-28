package com.example.carcareformularioregistro.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "imported_backups")
data class ImportedBackup(@PrimaryKey val fingerprint: String, val importedAt: Long)

@Dao
interface ImportedBackupDao {
    @Query("SELECT COUNT(*) FROM imported_backups WHERE fingerprint = :fingerprint")
    suspend fun count(fingerprint: String): Int
    @Insert
    suspend fun insert(record: ImportedBackup)
}
