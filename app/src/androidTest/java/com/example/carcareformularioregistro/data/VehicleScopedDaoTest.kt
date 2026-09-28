package com.example.carcareformularioregistro.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses a dedicated on-disk fixture, never the singleton or the user's database/preferences. */
@RunWith(AndroidJUnit4::class)
class VehicleScopedDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "carcare-vehicle-scope-test-${UUID.randomUUID()}.db"
    private lateinit var db: AppDatabase

    @Before fun prepare() {
        db = openDatabase()
    }

    @After fun cleanUp() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()

    private suspend fun addVehicle(name: String): Int = db.vehicleDao().insertVehicle(Vehicle(
        name = name, brand = "Nissan", model = "Versa", year = 2020, mileage = 85000, plates = ""
    )).toInt()

    private fun maintenance(vehicle: Int, date: String = "2026-09-15") = Maintenance(
        vehicleId = vehicle, type = "Aceite", date = date, mileage = 85000,
        cost = 900.0, workshop = "Taller", nextDate = "2027-03-15", nextMileage = 95000,
        status = Maintenance.STATUS_REALIZADO
    )

    private fun expense(vehicle: Int, date: String = "2026-09-15") = Expense(
        vehicleId = vehicle, category = Expense.CAT_COMBUSTIBLE,
        concept = "Carga", amount = 600.0, date = date
    )

    private fun reminder(vehicle: Int, date: String = "2027-03-15", maintenance: Int? = null) = Reminder(
        vehicleId = vehicle, title = "Revisión", dueDate = date, maintenanceId = maintenance
    )

    @Test fun eachVehicleFlowReturnsOnlyItsOwnRecordsInDateOrder() = runBlocking {
        val first = addVehicle("Familiar")
        val second = addVehicle("Trabajo")
        val olderMaintenance = db.maintenanceDao().insert(maintenance(first, "2026-01-01")).toInt()
        db.maintenanceDao().insert(maintenance(second, "2026-12-01"))
        val newerMaintenance = db.maintenanceDao().insert(maintenance(first, "2026-09-01")).toInt()
        val olderExpense = db.expenseDao().insert(expense(first, "2026-01-01")).toInt()
        db.expenseDao().insert(expense(second, "2026-12-01"))
        val newerExpense = db.expenseDao().insert(expense(first, "2026-09-01")).toInt()
        val laterReminder = db.reminderDao().insert(reminder(first, "2027-09-01")).toInt()
        db.reminderDao().insert(reminder(second, "2027-01-01"))
        val soonerReminder = db.reminderDao().insert(reminder(first, "2027-03-01")).toInt()

        withTimeout(5_000) {
            assertEquals(listOf(newerMaintenance, olderMaintenance),
                db.maintenanceDao().getForVehicleFlow(first).first().map { it.id })
            assertEquals(listOf(newerExpense, olderExpense),
                db.expenseDao().getForVehicleFlow(first).first().map { it.id })
            assertEquals(listOf(soonerReminder, laterReminder),
                db.reminderDao().getForVehicleFlow(first).first().map { it.id })
            assertEquals(listOf(second), db.maintenanceDao().getForVehicleFlow(second).first().map { it.vehicleId })
            assertEquals(listOf(second), db.expenseDao().getForVehicleFlow(second).first().map { it.vehicleId })
            assertEquals(listOf(second), db.reminderDao().getForVehicleFlow(second).first().map { it.vehicleId })
            assertTrue(db.maintenanceDao().getForVehicleFlow(9999).first().isEmpty())
            assertTrue(db.expenseDao().getForVehicleFlow(9999).first().isEmpty())
            assertTrue(db.reminderDao().getForVehicleFlow(9999).first().isEmpty())
        }
    }

    @Test fun deletingScopedRecordsLeavesOtherVehicleRecordsIntact() = runBlocking {
        val first = addVehicle("Familiar")
        val second = addVehicle("Trabajo")
        db.maintenanceDao().insert(maintenance(first))
        val survivingMaintenance = db.maintenanceDao().insert(maintenance(second)).toInt()
        db.expenseDao().insert(expense(first))
        val survivingExpense = db.expenseDao().insert(expense(second)).toInt()
        db.reminderDao().insert(reminder(first))
        val survivingReminder = db.reminderDao().insert(reminder(second)).toInt()
        assertEquals(1, db.maintenanceDao().countForVehicle(first))
        assertEquals(1, db.expenseDao().countForVehicle(first))
        assertEquals(1, db.reminderDao().countForVehicle(first))

        db.reminderDao().deleteForVehicle(first)
        db.maintenanceDao().deleteForVehicle(first)
        db.expenseDao().deleteForVehicle(first)

        assertEquals(0, db.maintenanceDao().countForVehicle(first))
        assertEquals(0, db.expenseDao().countForVehicle(first))
        assertEquals(0, db.reminderDao().countForVehicle(first))
        assertEquals(second, db.maintenanceDao().getById(survivingMaintenance)?.vehicleId)
        assertEquals(second, db.expenseDao().getById(survivingExpense)?.vehicleId)
        assertEquals(second, db.reminderDao().getById(survivingReminder)?.vehicleId)
        assertEquals(2, db.vehicleDao().getVehicleCount())
    }

    @Test fun primarySwitchIsExclusiveAndPersistsWithoutChangingRecordOwnership() = runBlocking {
        val first = addVehicle("Familiar")
        val second = addVehicle("Trabajo")
        val maintenanceId = db.maintenanceDao().insert(maintenance(first)).toInt()
        db.vehicleDao().markPrimary(first)
        assertEquals(first, db.vehicleDao().getPrimaryVehicle()?.id)
        db.vehicleDao().markPrimary(second)
        assertEquals(listOf(second), db.vehicleDao().getAllVehicles().filter { it.isPrimary }.map { it.id })
        withTimeout(5_000) {
            assertEquals(second, db.vehicleDao().getPrimaryVehicleFlow().first()?.id)
        }
        db.close()
        db = openDatabase()
        assertEquals(second, db.vehicleDao().getPrimaryVehicle()?.id)
        assertEquals(first, db.maintenanceDao().getById(maintenanceId)?.vehicleId)
    }

    @Test fun linkedAndStandaloneRemindersRetainDistinctAssociationAfterReopen() = runBlocking {
        val vehicle = addVehicle("Familiar")
        val serviceId = db.maintenanceDao().insert(maintenance(vehicle)).toInt()
        val linkedId = db.reminderDao().insert(reminder(vehicle, maintenance = serviceId)).toInt()
        val standaloneId = db.reminderDao().insert(reminder(vehicle)).toInt()
        val changed = requireNotNull(db.reminderDao().getById(linkedId)).copy(title = "Aceite", enabled = false)
        db.reminderDao().update(changed)
        db.close()
        db = openDatabase()
        assertEquals(changed, db.reminderDao().getForMaintenance(serviceId))
        assertNull(db.reminderDao().getById(standaloneId)?.maintenanceId)
        db.reminderDao().deleteById(linkedId)
        assertNull(db.reminderDao().getForMaintenance(serviceId))
        assertEquals(standaloneId, db.reminderDao().getForVehicle(vehicle).single().id)
        assertEquals(serviceId, db.maintenanceDao().getById(serviceId)?.id)
    }
}
