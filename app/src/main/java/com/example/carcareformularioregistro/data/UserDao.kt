package com.example.carcareformularioregistro.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(usuario: User): Long

    @Query("SELECT * FROM usuarios ORDER BY id DESC")
    suspend fun obtenerTodos(): List<User>

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Int): User?
}
