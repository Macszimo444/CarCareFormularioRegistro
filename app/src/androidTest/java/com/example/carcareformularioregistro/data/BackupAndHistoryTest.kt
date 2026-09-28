package com.example.carcareformularioregistro.data

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carcareformularioregistro.utils.HistoryPdf
import com.example.carcareformularioregistro.utils.ReceiptStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dedicated databases and owned receipt files only. Does not alter the app's production records. */
@RunWith(AndroidJUnit4::class)
class BackupAndHistoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val store = ReceiptStore(context)
    private val owned = mutableSetOf<String>()
    private val previews = mutableListOf<BackupArchive.Preview>()

    @After fun cleanup() = runBlocking {
        listOf(source, target).forEach { db ->
            db.maintenanceDao().getAllMaintenances().mapNotNull { it.receipt }.forEach { owned.add(it) }
            db.close()
        }
        owned.forEach(store::delete)
        previews.forEach { it.file.delete() }
    }
    private fun receipt(): String {
        val bytes = ByteArrayOutputStream()
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle()
        return store.import(ByteArrayInputStream(bytes.toByteArray())).also(owned::add)
    }
    private suspend fun seed(db: AppDatabase, withReceipt: Boolean = true): Pair<Int, Int> {
        db.userDao().insertar(User(nombre = "Ana", apellidos = "López"))
        val car = db.vehicleDao().insertVehicle(Vehicle(name = "Familiar", brand = "Nissan", model = "Versa",
            year = 2020, mileage = 120000, plates = "PRUEBA", isPrimary = true)).toInt()
        val service = db.maintenanceDao().insert(Maintenance(vehicleId = car, type = "Cambio de aceite", date = "2026-01-01",
            mileage = 100000, cost = 900.0, workshop = "Taller López", nextDate = "2090-01-01", nextMileage = 130000,
            status = Maintenance.STATUS_REALIZADO, receipt = if (withReceipt) receipt() else null)).toInt()
        db.expenseDao().insert(Expense(vehicleId = car, category = Expense.CAT_COMBUSTIBLE, concept = "Gasolina", amount = 600.0, date = "2026-01-02"))
        db.reminderDao().insert(Reminder(vehicleId = car, title = "Revisión de aceite", dueDate = "2090-01-01", dueMileage = 130000,
            maintenanceId = service, enabled = false))
        return car to service
    }

    @Test fun backupMergesRemappedRecordsAndReceiptBytesWithoutReplacingProfileOrDuplicatingSameArchive() = runBlocking {
        seed(source)
        val existing = seed(target, false)
        val originalUser = requireNotNull(target.userDao().getPrimaryUser())
        target.userDao().actualizar(originalUser.copy(nombre = "Perfil existente"))
        val bytes = ByteArrayOutputStream()
        BackupArchive(context, source).export(bytes)
        val importer = BackupArchive(context, target)
        val preview = importer.inspect(ByteArrayInputStream(bytes.toByteArray())).also(previews::add)
        val restored = importer.restore(preview)
        assertEquals(2, target.vehicleDao().getVehicleCount())
        assertEquals("Perfil existente", target.userDao().getPrimaryUser()?.nombre)
        assertEquals(existing.first, target.vehicleDao().getPrimaryVehicle()?.id)
        val car = restored.vehicleIds.single()
        assertNotEquals(existing.first, car)
        val item = target.maintenanceDao().getAllMaintenances().single { it.vehicleId == car }
        val reminder = target.reminderDao().getById(restored.reminderIds.single())!!
        assertEquals(item.id, reminder.maintenanceId)
        assertEquals(car, reminder.vehicleId)
        assertEquals(1, target.expenseDao().getAllExpenses().count { it.vehicleId == car })
        val sourceReceipt = source.maintenanceDao().getAllMaintenances().single().receipt!!
        assertNotEquals(sourceReceipt, item.receipt)
        assertArrayEquals(store.file(sourceReceipt).readBytes(), store.file(item.receipt!!).readBytes())
        assertTrue(importer.alreadyImported(preview))
        try { importer.restore(preview); fail("Duplicate import accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(2, target.vehicleDao().getVehicleCount())
    }

    @Test fun damagedArchiveIsRejectedWithoutDatabaseWrites() = runBlocking {
        seed(source, false)
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("../carcare.json")); zip.write("{}".toByteArray()); zip.closeEntry()
        }
        try { BackupArchive(context, target).inspect(ByteArrayInputStream(bytes.toByteArray())); fail("Unsafe entry accepted") }
        catch (_: Exception) { }
        try { store.import(ByteArrayInputStream("%PDF-not-a-real-pdf".toByteArray())); fail("Fake PDF accepted") }
        catch (_: Exception) { }
        assertEquals(0, target.vehicleDao().getVehicleCount())
        assertNull(target.userDao().getPrimaryUser())
        try { store.import(ByteArrayInputStream("not an image".toByteArray())); fail("Invalid receipt accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun completionConsumesReminderOnceAndTransactionFailurePreservesHistoricalPlan() = runBlocking {
        val (car, service) = seed(source, false)
        val reminder = source.reminderDao().getAllReminders().single()
        try {
            source.withTransaction {
                ReviewCompletion.consume(source, reminder.id, car)
                error("Simulated failed service save")
            }
        } catch (_: IllegalStateException) { }
        assertNotNull(source.reminderDao().getById(reminder.id))
        assertEquals(130000, source.maintenanceDao().getById(service)?.nextMileage)
        source.withTransaction { ReviewCompletion.consume(source, reminder.id, car) }
        assertNull(source.reminderDao().getById(reminder.id))
        assertEquals(0, source.maintenanceDao().getById(service)?.nextMileage)
        assertEquals(100000, source.maintenanceDao().getById(service)?.mileage)
        try { source.withTransaction { ReviewCompletion.consume(source, reminder.id, car) }; fail("Completed twice") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun nativePdfWrapsLongNotesAcrossPagesAndCanBeRendered() = runBlocking {
        val (carId, serviceId) = seed(source, false)
        val car = source.vehicleDao().getById(carId)!!
        val example = source.maintenanceDao().getById(serviceId)!!
        val items = (1..18).map { example.copy(id = it, type = "Servicio $it · Cambio de aceite y revisión general",
            description = "Se sustituyó el filtro y se revisaron las observaciones del taller. ".repeat(8)) }
        val output = File(context.getExternalFilesDir(null), "qa/carcare-history.pdf").apply { parentFile!!.mkdirs() }
        output.outputStream().use { HistoryPdf.write(car, items, it) }
        // The actual exporter output is also accepted as a portable PDF receipt.
        val pdfReceipt = output.inputStream().use(store::import).also(owned::add)
        assertEquals("pdf", store.file(pdfReceipt).extension)
        val descriptor = ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            assertTrue(renderer.pageCount > 1)
            for (index in listOf(0, renderer.pageCount - 1)) {
                val page = renderer.openPage(index)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                File(output.parentFile, "history-page-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle(); page.close()
            }
        } finally { renderer.close() }
    }
}
