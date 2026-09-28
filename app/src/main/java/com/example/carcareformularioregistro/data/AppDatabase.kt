package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [User::class, Vehicle::class, Maintenance::class, Expense::class, Reminder::class, ImportedBackup::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun importedBackupDao(): ImportedBackupDao
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

        /** Preserve existing records; the previous default (newest car) becomes primary. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vehicles ADD COLUMN isPrimary INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE vehicles SET isPrimary = 1 WHERE id = (SELECT MAX(id) FROM vehicles)")
                db.execSQL("ALTER TABLE reminders ADD COLUMN maintenanceId INTEGER")
                // Older versions could save records with vehicleId=1 before adding a vehicle.
                // Recover those associations instead of hiding or deleting the user's records.
                db.execSQL("""
                    INSERT INTO vehicles (id, name, brand, model, year, mileage, plates, photoUri, isPrimary)
                    SELECT DISTINCT linked.vehicleId, 'Vehículo recuperado', '', '', 0, 0, '', NULL, 0
                    FROM (
                        SELECT vehicleId FROM maintenances UNION SELECT vehicleId FROM expenses
                        UNION SELECT vehicleId FROM reminders
                    ) AS linked LEFT JOIN vehicles v ON v.id = linked.vehicleId
                    WHERE v.id IS NULL
                """.trimIndent())
                db.execSQL("""
                    UPDATE vehicles SET isPrimary = 1 WHERE id = (SELECT MIN(id) FROM vehicles)
                    AND NOT EXISTS (SELECT 1 FROM vehicles WHERE isPrimary = 1)
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE maintenances ADD COLUMN receipt TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS imported_backups (fingerprint TEXT NOT NULL PRIMARY KEY, importedAt INTEGER NOT NULL)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN dueTime TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }
    }
}
