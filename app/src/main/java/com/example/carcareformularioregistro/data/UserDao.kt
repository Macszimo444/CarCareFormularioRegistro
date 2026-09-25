package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(usuario: User): Long

    @Update
    suspend fun actualizar(usuario: User)

    @Query("SELECT * FROM usuarios ORDER BY id DESC LIMIT 1")
    fun getPrimaryUserFlow(): Flow<User?>

    @Query("SELECT * FROM usuarios ORDER BY id DESC LIMIT 1")
    suspend fun getPrimaryUser(): User?

    @Query("SELECT * FROM usuarios ORDER BY id DESC")
    suspend fun obtenerTodos(): List<User>

    @Query("SELECT * FROM usuarios WHERE id = :id LIMIT 1")
    suspend fun obtenerPorId(id: Int): User?
}
