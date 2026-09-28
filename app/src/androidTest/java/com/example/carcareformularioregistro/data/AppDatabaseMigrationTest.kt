package com.example.carcareformularioregistro.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Runs against a separate test database; never deletes the user's carcare.db. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "carcare-migration-test-${UUID.randomUUID()}.db"
    private var database: AppDatabase? = null

    @Before
    fun prepare() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context, AppDatabase::class.java, databaseName,
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
        .build().also { database = it }

    /** Independent fixture for the released v2 schema; never builds v2 via the migration under test. */
    private fun createVersion2Database(seed: (SQLiteDatabase) -> Unit) {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("""
                CREATE TABLE usuarios (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    nombre TEXT NOT NULL, apellidos TEXT NOT NULL, direccion TEXT NOT NULL,
                    telefono TEXT NOT NULL, email TEXT NOT NULL)
            """.trimIndent())
            old.execSQL("""
                CREATE TABLE vehicles (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL, brand TEXT NOT NULL, model TEXT NOT NULL,
                    year INTEGER NOT NULL, mileage INTEGER NOT NULL, plates TEXT NOT NULL, photoUri TEXT)
            """.trimIndent())
            old.execSQL("""
                CREATE TABLE maintenances (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL,
                    mileage INTEGER NOT NULL, cost REAL NOT NULL, workshop TEXT NOT NULL,
                    nextDate TEXT NOT NULL, nextMileage INTEGER NOT NULL,
                    description TEXT NOT NULL, status TEXT NOT NULL)
            """.trimIndent())
            old.execSQL("""
                CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL, category TEXT NOT NULL, concept TEXT NOT NULL,
                    amount REAL NOT NULL, date TEXT NOT NULL, description TEXT NOT NULL)
            """.trimIndent())
            old.execSQL("""
                CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    vehicleId INTEGER NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL,
                    dueDate TEXT NOT NULL, dueMileage INTEGER NOT NULL,
                    priority TEXT NOT NULL, enabled INTEGER NOT NULL)
            """.trimIndent())
            seed(old)
            old.version = 2
        }
    }

    @Test
    fun upgradeFromMasterPreservesRegisteredProfilesAndCreatesOtherTables() = runBlocking {
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { oldDatabase ->
            oldDatabase.execSQL("""
                CREATE TABLE usuarios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    nombre TEXT NOT NULL, apellidos TEXT NOT NULL,
                    direccion TEXT NOT NULL, telefono TEXT NOT NULL
                )
            """.trimIndent())
            oldDatabase.execSQL(
                "INSERT INTO usuarios VALUES (7, 'María', 'López', 'Calle Uno 20', '5551234567')",
            )
            oldDatabase.execSQL(
                "INSERT INTO usuarios VALUES (8, 'José', 'Pérez', 'Calle Dos 30', '5557654321')",
            )
            oldDatabase.version = 1
        }
        val migrated = openDatabase()
        assertEquals(5, migrated.openHelper.readableDatabase.version)
        assertEquals(2, migrated.userDao().obtenerTodos().size)
        assertEquals("María", migrated.userDao().obtenerPorId(7)?.nombre)
        assertEquals("Calle Uno 20", migrated.userDao().obtenerPorId(7)?.direccion)
        assertEquals("5557654321", migrated.userDao().getPrimaryUser()?.telefono)
        assertEquals("", migrated.userDao().getPrimaryUser()?.email)
        assertEquals(0, migrated.vehicleDao().getVehicleCount())
        assertTrue(migrated.maintenanceDao().getAllMaintenances().isEmpty())
        migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM expenses").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM reminders").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        val nextId = migrated.userDao().insertar(User(
            nombre = "Ana", apellidos = "García", direccion = "Calle Tres", telefono = "5551111111",
        ))
        assertTrue(nextId > 8)
    }

    @Test
    fun upgradeFromVersion2RetainsEveryFieldAndUsesExistingNewestVehicleAsPrimary() = runBlocking {
        createVersion2Database { old ->
            old.execSQL("INSERT INTO usuarios VALUES (21, 'Elena', 'Ruiz', 'Calle 7', '5551234567', 'elena@example.com')")
            old.execSQL("INSERT INTO vehicles VALUES (3, 'Auto familiar', 'Nissan', 'Versa', 2020, 85000, 'ABC-123', 'content://fixture/car')")
            old.execSQL("INSERT INTO vehicles VALUES (8, 'Trabajo', 'Toyota', 'Yaris', 2022, 30000, '', NULL)")
            old.execSQL("INSERT INTO maintenances VALUES (11, 3, 'Cambio de aceite', '2026-09-15', 85000, 899.5, 'Taller López', '2027-03-15', 95000, 'Filtro nuevo', 'Realizado')")
            old.execSQL("INSERT INTO expenses VALUES (13, 8, 'Combustible', 'Carga completa', 750.25, '2026-09-20', 'Ticket guardado')")
            old.execSQL("INSERT INTO reminders VALUES (15, 3, 'Revisión', 'Consultar manual', '2027-03-15', 95000, 'Alta', 0)")
            // A newer orphan must not replace the primary chosen from the cars the user already had.
            old.execSQL("INSERT INTO reminders VALUES (16, 99, 'Registro sin coche', 'Dato antiguo', '2027-04-01', 45000, 'Media', 1)")
        }
        val migrated = openDatabase()
        assertEquals(5, migrated.openHelper.readableDatabase.version)
        assertEquals(User(21, "Elena", "Ruiz", "Calle 7", "5551234567", "elena@example.com"),
            migrated.userDao().obtenerPorId(21))
        assertEquals(Vehicle(3, "Auto familiar", "Nissan", "Versa", 2020, 85000, "ABC-123",
            "content://fixture/car", false), migrated.vehicleDao().getById(3))
        assertEquals(Vehicle(8, "Trabajo", "Toyota", "Yaris", 2022, 30000, "", null, true),
            migrated.vehicleDao().getById(8))
        assertEquals(Maintenance(11, 3, "Cambio de aceite", "2026-09-15", 85000, 899.5,
            "Taller López", "2027-03-15", 95000, "Filtro nuevo", "Realizado"),
            migrated.maintenanceDao().getById(11))
        assertEquals(Expense(13, 8, "Combustible", "Carga completa", 750.25,
            "2026-09-20", "Ticket guardado"), migrated.expenseDao().getById(13))
        assertEquals(Reminder(15, 3, "Revisión", "Consultar manual", "2027-03-15",
            95000, "Alta", false, null), migrated.reminderDao().getById(15))
        assertNull(migrated.reminderDao().getById(16)?.maintenanceId)
        assertEquals("Vehículo recuperado", migrated.vehicleDao().getById(99)?.name)
        assertEquals(8, migrated.vehicleDao().getPrimaryVehicle()?.id)
        assertEquals(1, migrated.vehicleDao().getAllVehicles().count { it.isPrimary })

        val linked = requireNotNull(migrated.reminderDao().getById(15)).copy(maintenanceId = 11)
        migrated.reminderDao().update(linked)
        migrated.close()
        val reopened = openDatabase()
        assertEquals(linked, reopened.reminderDao().getForMaintenance(11))
        assertNull(reopened.reminderDao().getById(16)?.maintenanceId)
        assertEquals(8, reopened.vehicleDao().getPrimaryVehicle()?.id)
    }

    @Test
    fun version2OrphansFromAllThreeTablesGetDistinctVehiclesWithoutLosingRecords() = runBlocking {
        createVersion2Database { old ->
            old.execSQL("INSERT INTO maintenances VALUES (31, 19, 'Frenos', '2026-09-10', 80000, 2000, '', '', 0, 'Anterior', 'Realizado')")
            old.execSQL("INSERT INTO expenses VALUES (32, 37, 'Otros', 'Lavado', 150, '2026-09-11', '')")
            old.execSQL("INSERT INTO expenses VALUES (34, 19, 'Combustible', 'Gasolina', 800, '2026-09-12', '')")
            old.execSQL("INSERT INTO reminders VALUES (33, 52, 'Revisar', '', '2027-01-01', 0, 'Baja', 1)")
        }
        val migrated = openDatabase()
        val recovered = migrated.vehicleDao().getAllVehicles()
        assertEquals(setOf(19, 37, 52), recovered.map { it.id }.toSet())
        assertEquals(1, recovered.count { it.isPrimary })
        assertEquals(19, migrated.vehicleDao().getPrimaryVehicle()?.id)
        assertTrue(recovered.all { it.name == "Vehículo recuperado" })
        assertEquals(19, migrated.maintenanceDao().getById(31)?.vehicleId)
        assertEquals(37, migrated.expenseDao().getById(32)?.vehicleId)
        assertEquals(19, migrated.expenseDao().getById(34)?.vehicleId)
        assertEquals(52, migrated.reminderDao().getById(33)?.vehicleId)
        assertNull(migrated.reminderDao().getById(33)?.maintenanceId)
        assertEquals(1, migrated.maintenanceDao().getAllMaintenances().size)
        assertEquals(2, migrated.expenseDao().getAllExpenses().size)
        assertEquals(1, migrated.reminderDao().getAllReminders().size)
        val nextVehicle = migrated.vehicleDao().insertVehicle(Vehicle(
            name = "Nuevo", brand = "Honda", model = "City", year = 2024, mileage = 100, plates = ""
        ))
        assertTrue(nextVehicle > 52)
    }

    @Test
    fun upgradeFromVersion3AddsReceiptsWithoutChangingExistingServiceOrReminder() = runBlocking {
        createVersion2Database { old ->
            old.execSQL("INSERT INTO vehicles VALUES (1, 'Auto', 'Toyota', 'Yaris', 2020, 50000, '', NULL)")
            old.execSQL("INSERT INTO maintenances VALUES (5, 1, 'Aceite', '2026-01-01', 40000, 900, 'Taller', '2027-01-01', 60000, 'Nota', 'Realizado')")
            old.execSQL("INSERT INTO reminders VALUES (8, 1, 'Revisión', '', '2027-01-01', 60000, 'Media', 1)")
        }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("ALTER TABLE vehicles ADD COLUMN isPrimary INTEGER NOT NULL DEFAULT 0")
            old.execSQL("UPDATE vehicles SET isPrimary = 1")
            old.execSQL("ALTER TABLE reminders ADD COLUMN maintenanceId INTEGER")
            old.execSQL("UPDATE reminders SET maintenanceId = 5")
            old.version = 3
        }
        val migrated = openDatabase()
        assertEquals(5, migrated.openHelper.readableDatabase.version)
        assertEquals(50000, migrated.vehicleDao().getById(1)?.mileage)
        assertEquals("Nota", migrated.maintenanceDao().getById(5)?.description)
        assertNull(migrated.maintenanceDao().getById(5)?.receipt)
        assertEquals(5, migrated.reminderDao().getById(8)?.maintenanceId)
        assertEquals(0, migrated.importedBackupDao().count("new-file"))
    }

    @Test
    fun version4RemindersBecomeAllDayWithoutLosingTargetsOrLinks() = runBlocking {
        createVersion2Database { old ->
            old.execSQL("INSERT INTO vehicles VALUES (1, 'Auto', 'Toyota', 'Yaris', 2020, 50000, '', NULL)")
            old.execSQL("INSERT INTO maintenances VALUES (5, 1, 'Aceite', '2026-01-01', 40000, 900, 'Taller', '2027-01-01', 60000, 'Nota', 'Realizado')")
            old.execSQL("INSERT INTO reminders VALUES (8, 1, 'Revisión', '', '2027-01-01', 60000, 'Media', 1)")
        }
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("ALTER TABLE vehicles ADD COLUMN isPrimary INTEGER NOT NULL DEFAULT 0")
            old.execSQL("ALTER TABLE reminders ADD COLUMN maintenanceId INTEGER")
            old.execSQL("UPDATE reminders SET maintenanceId = 5")
            old.execSQL("ALTER TABLE maintenances ADD COLUMN receipt TEXT")
            old.execSQL("CREATE TABLE imported_backups (fingerprint TEXT NOT NULL PRIMARY KEY, importedAt INTEGER NOT NULL)")
            old.version = 4
        }
        val migrated = openDatabase()
        val reminder = requireNotNull(migrated.reminderDao().getById(8))
        assertEquals(5, migrated.openHelper.readableDatabase.version)
        assertEquals(5, reminder.maintenanceId)
        assertEquals(60000, reminder.dueMileage)
        assertNull(reminder.dueTime)
        migrated.reminderDao().update(reminder.copy(dueTime = "10:30"))
        migrated.close()
        assertEquals("10:30", openDatabase().reminderDao().getById(8)?.dueTime)
    }

    @Test
    fun newInstallStartsEmptyAndLocalProfileAndMaintenanceSurviveReopening() = runBlocking {
        val fresh = openDatabase()
        assertEquals(5, fresh.openHelper.readableDatabase.version)
        assertNull(fresh.userDao().getPrimaryUser())
        assertEquals(0, fresh.vehicleDao().getVehicleCount())
        assertTrue(fresh.maintenanceDao().getAllMaintenances().isEmpty())
        val profileId = fresh.userDao().insertar(User(
            nombre = "Laura", apellidos = "Gómez", direccion = "Av. México 5", telefono = "5550001122",
        )).toInt()
        val vehicleId = fresh.vehicleDao().insertVehicle(Vehicle(
            name = "Mi auto", brand = "Nissan", model = "Versa", year = 2020,
            mileage = 120000, plates = "ABC-123",
        )).toInt()
        val maintenanceId = fresh.maintenanceDao().insert(Maintenance(
            vehicleId = vehicleId, type = "Cambio de aceite", date = "2026-09-15",
            mileage = 120000, cost = 900.0, workshop = "Taller López", nextDate = "2027-03-15",
            nextMileage = 130000, description = "Filtro nuevo", status = Maintenance.STATUS_REALIZADO,
        )).toInt()
        val profile = requireNotNull(fresh.userDao().obtenerPorId(profileId))
        fresh.userDao().actualizar(profile.copy(telefono = "5552223344"))
        fresh.close()

        val reopened = openDatabase()
        assertEquals(1, reopened.userDao().obtenerTodos().size)
        assertEquals(profileId, reopened.userDao().getPrimaryUser()?.id)
        assertEquals("5552223344", reopened.userDao().getPrimaryUser()?.telefono)
        assertEquals("Cambio de aceite", reopened.maintenanceDao().getById(maintenanceId)?.type)
        assertEquals("Taller López", reopened.maintenanceDao().getById(maintenanceId)?.workshop)
        assertEquals(vehicleId, reopened.maintenanceDao().getById(maintenanceId)?.vehicleId)
    }
}
