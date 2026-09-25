package com.example.carcareformularioregistro.data

import android.content.Context
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

/** Runs against a separate test database; never deletes the user's carcare.db. */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "carcare-migration-test.db"
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
    ).addMigrations(AppDatabase.MIGRATION_1_2).build().also { database = it }

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
    fun newInstallStartsEmptyAndLocalProfileAndMaintenanceSurviveReopening() = runBlocking {
        val fresh = openDatabase()
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
