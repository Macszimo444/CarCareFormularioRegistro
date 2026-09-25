package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class,
        Vehicle::class,
        Maintenance::class,
        Expense::class,
        Reminder::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        private const val DATABASE_NAME = "carcare.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                populateInitialData(getInstance(context))
                            }
                        }
                    })
                    .build().also { database -> instance = database }
            }

        private suspend fun populateInitialData(db: AppDatabase) {
            // Seed initial User
            val user = User(
                nombre = "Ángel Eduardo",
                apellidos = "García",
                direccion = "Av. Universidad 100",
                telefono = "555-0192",
                email = "angel@carcare.app"
            )
            db.userDao().insertar(user)

            // Seed initial Vehicle (Ford F-150 2018)
            val vehicle = Vehicle(
                name = "Ford F-150 2018",
                brand = "Ford",
                model = "F-150",
                year = 2018,
                mileage = 85000,
                plates = "ABC-1234"
            )
            val vehicleId = db.vehicleDao().insertVehicle(vehicle).toInt()

            // Seed initial Maintenances
            db.maintenanceDao().insert(
                Maintenance(
                    vehicleId = vehicleId,
                    type = "Cambio de Aceite Sintético",
                    date = "2026-08-15",
                    mileage = 80000,
                    cost = 1250.0,
                    workshop = "Taller Ford San Fernando",
                    nextDate = "2026-11-15",
                    nextMileage = 90000,
                    description = "Filtro de aceite nuevo y aceite 5W-30.",
                    status = Maintenance.STATUS_REALIZADO
                )
            )
            db.maintenanceDao().insert(
                Maintenance(
                    vehicleId = vehicleId,
                    type = "Alineación y Balanceo",
                    date = "2026-09-01",
                    mileage = 82000,
                    cost = 850.0,
                    workshop = "Llantas Express",
                    nextDate = "2027-03-01",
                    nextMileage = 92000,
                    description = "Ajuste de suspensión y balanceo dinámico.",
                    status = Maintenance.STATUS_REALIZADO
                )
            )
            db.maintenanceDao().insert(
                Maintenance(
                    vehicleId = vehicleId,
                    type = "Revisión de Frenos",
                    date = "2026-10-10",
                    mileage = 85000,
                    cost = 1800.0,
                    workshop = "ServiFrenos del Norte",
                    nextDate = "2026-10-10",
                    nextMileage = 88000,
                    description = "Cambio preventivo de balatas delanteras.",
                    status = Maintenance.STATUS_PROXIMO
                )
            )

            // Seed initial Expenses
            db.expenseDao().insert(
                Expense(
                    vehicleId = vehicleId,
                    category = Expense.CAT_COMBUSTIBLE,
                    concept = "Gasolina Premium 50L",
                    amount = 1250.0,
                    date = "2026-09-20",
                    description = "Tanque lleno"
                )
            )
            db.expenseDao().insert(
                Expense(
                    vehicleId = vehicleId,
                    category = Expense.CAT_MANTENIMIENTO,
                    concept = "Cambio de Aceite Sintético",
                    amount = 1250.0,
                    date = "2026-08-15",
                    description = "Servicio de 80,000km"
                )
            )
            db.expenseDao().insert(
                Expense(
                    vehicleId = vehicleId,
                    category = Expense.CAT_REPARACIONES,
                    concept = "Alineación y Balanceo",
                    amount = 850.0,
                    date = "2026-09-01"
                )
            )
            db.expenseDao().insert(
                Expense(
                    vehicleId = vehicleId,
                    category = Expense.CAT_LLANTAS,
                    concept = "Calibración y Rotación",
                    amount = 350.0,
                    date = "2026-09-10"
                )
            )

            // Seed initial Reminders
            db.reminderDao().insert(
                Reminder(
                    vehicleId = vehicleId,
                    title = "Cambio de Filtro de Aire",
                    description = "Reemplazar filtro de motor para mejor rendimiento.",
                    dueDate = "2026-10-15",
                    dueMileage = 87000,
                    priority = Reminder.PRIORITY_ALTA,
                    enabled = true
                )
            )
            db.reminderDao().insert(
                Reminder(
                    vehicleId = vehicleId,
                    title = "Verificar Presión de Llantas",
                    description = "Revisar 35 PSI en las 4 ruedas.",
                    dueDate = "2026-09-30",
                    dueMileage = 86000,
                    priority = Reminder.PRIORITY_MEDIA,
                    enabled = true
                )
            )
        }
    }
}
