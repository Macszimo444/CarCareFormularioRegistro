package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class, Vehicle::class, Maintenance::class, Expense::class, Reminder::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "carcare.db"

        /** Preserves every profile registered in master and creates the tables introduced by angel. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Recreate only the profile table so email has exactly the same schema as angel v2.
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS usuarios_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nombre TEXT NOT NULL, apellidos TEXT NOT NULL,
                        direccion TEXT NOT NULL, telefono TEXT NOT NULL, email TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO usuarios_v2 (id, nombre, apellidos, direccion, telefono, email)
                    SELECT id, nombre, apellidos, direccion, telefono, '' FROM usuarios
                """.trimIndent())
                db.execSQL("DROP TABLE usuarios")
                db.execSQL("ALTER TABLE usuarios_v2 RENAME TO usuarios")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS vehicles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL, brand TEXT NOT NULL, model TEXT NOT NULL,
                        year INTEGER NOT NULL, mileage INTEGER NOT NULL, plates TEXT NOT NULL,
                        photoUri TEXT
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS maintenances (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL,
                        mileage INTEGER NOT NULL, cost REAL NOT NULL, workshop TEXT NOT NULL,
                        nextDate TEXT NOT NULL, nextMileage INTEGER NOT NULL,
                        description TEXT NOT NULL, status TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL, category TEXT NOT NULL, concept TEXT NOT NULL,
                        amount REAL NOT NULL, date TEXT NOT NULL, description TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL,
                        dueDate TEXT NOT NULL, dueMileage INTEGER NOT NULL,
                        priority TEXT NOT NULL, enabled INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }
    }
}
