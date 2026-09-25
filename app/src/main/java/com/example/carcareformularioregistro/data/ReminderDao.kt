package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAllRemindersFlow(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Reminder?

    @Query("SELECT * FROM reminders WHERE enabled = 1 ORDER BY dueDate ASC")
    suspend fun getActiveReminders(): List<Reminder>

    @Query("SELECT COUNT(*) FROM reminders WHERE enabled = 1")
    fun getActiveRemindersCountFlow(): Flow<Int>
}
