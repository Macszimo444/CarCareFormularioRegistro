package com.example.carcareformularioregistro.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpensesFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpenses(): List<Expense>

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalExpensesFlow(): Flow<Double?>

    @Query("SELECT category, SUM(amount) as total FROM expenses GROUP BY category")
    fun getExpensesByCategoryFlow(): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM expenses WHERE vehicleId = :vehicleId ORDER BY date DESC, id DESC")
    fun getForVehicleFlow(vehicleId: Int): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Int): Expense?

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun countForVehicle(vehicleId: Int): Int

    @Query("DELETE FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun deleteForVehicle(vehicleId: Int)
}

data class CategoryTotal(
    val category: String,
    val total: Double
)
