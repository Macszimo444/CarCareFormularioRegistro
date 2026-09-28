package com.example.carcareformularioregistro.data

import android.content.Context
import androidx.room.withTransaction
import com.example.carcareformularioregistro.utils.FormValidation
import com.example.carcareformularioregistro.utils.ReceiptStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Versioned, portable data rather than a live SQLite file or temporary content-provider URIs. */
class BackupArchive(private val context: Context, private val db: AppDatabase = AppDatabase.getInstance(context)) {
    data class Snapshot(val users: List<User>, val vehicles: List<Vehicle>, val services: List<Maintenance>,
        val expenses: List<Expense>, val reminders: List<Reminder>)
    data class Preview(val file: File, val fingerprint: String, val data: Snapshot) {
        val summary get() = "${data.vehicles.size} vehículos · ${data.services.size} mantenimientos\n" +
            "${data.expenses.size} gastos · ${data.reminders.size} recordatorios\n" +
            "${data.services.count { it.receipt != null }} comprobantes"
    }
    data class Restored(val vehicleIds: List<Int>, val reminderIds: List<Int>)

    suspend fun export(output: OutputStream) {
        val data = db.withTransaction { Snapshot(db.userDao().obtenerTodos(), db.vehicleDao().getAllVehicles(),
            db.maintenanceDao().getAllMaintenances(), db.expenseDao().getAllExpenses(), db.reminderDao().getAllReminders()) }
        val manifest = encode(data).toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MAX_MANIFEST) { "Demasiados registros para un solo respaldo" }
        validate(data)
        val receipts = ReceiptStore(context)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("carcare.json")); zip.write(manifest); zip.closeEntry()
            var total = manifest.size.toLong()
            data.services.mapNotNull { it.receipt }.distinct().forEach { name ->
                val file = receipts.file(name)
                require(file.isFile) { "Falta un comprobante. Revisa los adjuntos antes de exportar" }
                require(file.length() <= ReceiptStore.MAX_BYTES)
                total += file.length(); require(total <= MAX_ARCHIVE) { "El respaldo supera 128 MB" }
                zip.putNextEntry(ZipEntry("receipts/$name"))
                file.inputStream().use { ReceiptStore.copyLimited(it, zip, ReceiptStore.MAX_BYTES) }
                zip.closeEntry()
            }
        }
    }

    /** Copies a bounded archive, validates all references and entries before any database write. */
    fun inspect(input: InputStream): Preview {
        val file = File.createTempFile("carcare-import-", ".zip", context.cacheDir)
        try {
            file.outputStream().use { ReceiptStore.copyLimited(input, it, MAX_ARCHIVE) }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val bytes = ByteArray(8192)
                while (true) { val n = stream.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
            }
            val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
            val data = ZipFile(file).use { zip ->
                val names = mutableSetOf<String>()
                var total = 0L
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(names.add(entry.name) && names.size <= MAX_RECORDS + 1) { "Archivo duplicado o demasiados adjuntos" }
                    require(!entry.isDirectory && (entry.name == "carcare.json" ||
                        (entry.name.startsWith("receipts/") && ReceiptStore.NAME.matches(entry.name.removePrefix("receipts/")))))
                    require(entry.size in 0..ReceiptStore.MAX_BYTES)
                    total += entry.size; require(total <= MAX_ARCHIVE)
                }
                val entry = requireNotNull(zip.getEntry("carcare.json")) { "No es un respaldo de CarCare" }
                val bytes = java.io.ByteArrayOutputStream()
                zip.getInputStream(entry).use { ReceiptStore.copyLimited(it, bytes, MAX_MANIFEST.toLong()) }
                val result = decode(JSONObject(bytes.toString("UTF-8")))
                validate(result)
                require(names == result.services.mapNotNull { it.receipt }.map { "receipts/$it" }.toSet() + "carcare.json") {
                    "Faltan comprobantes o hay archivos inesperados"
                }
                result
            }
            return Preview(file, fingerprint, data)
        } catch (error: Throwable) { file.delete(); throw error }
    }

    suspend fun alreadyImported(preview: Preview) = db.importedBackupDao().count(preview.fingerprint) > 0

    /** Merge with remapped IDs. Cancellation cannot split the committed rows from their files. */
    suspend fun restore(preview: Preview): Restored = withContext(NonCancellable) {
        require(!alreadyImported(preview)) { "Este archivo ya fue restaurado" }
        val files = ReceiptStore(context)
        val copied = mutableMapOf<String, String>()
        var committed = false
        try {
            var total = 0L
            ZipFile(preview.file).use { zip ->
                preview.data.services.mapNotNull { it.receipt }.distinct().forEach { name ->
                    val stored = zip.getInputStream(requireNotNull(zip.getEntry("receipts/$name"))).use(files::import)
                    copied[name] = stored
                    total += files.file(stored).length()
                    require(total <= MAX_ARCHIVE)
                }
            }
            val result = db.withTransaction {
                require(db.importedBackupDao().count(preview.fingerprint) == 0) { "Este archivo ya fue restaurado" }
                // Keep the existing local profile; recover all backed-up profiles on a fresh installation.
                if (db.userDao().getPrimaryUser() == null) preview.data.users.sortedBy { it.id }.forEach {
                    db.userDao().insertar(it.copy(id = 0))
                }
                val hasVehicles = db.vehicleDao().getVehicleCount() > 0
                val primaryId = preview.data.vehicles.firstOrNull { it.isPrimary }?.id ?: preview.data.vehicles.firstOrNull()?.id
                val vehicles = preview.data.vehicles.associate { old ->
                    old.id to db.vehicleDao().insertVehicle(old.copy(id = 0, photoUri = null,
                        isPrimary = !hasVehicles && old.id == primaryId)).toInt()
                }
                val services = preview.data.services.associate { old ->
                    old.id to db.maintenanceDao().insert(old.copy(id = 0, vehicleId = vehicles.getValue(old.vehicleId),
                        receipt = old.receipt?.let(copied::getValue))).toInt()
                }
                preview.data.expenses.forEach { old ->
                    db.expenseDao().insert(old.copy(id = 0, vehicleId = vehicles.getValue(old.vehicleId)))
                }
                val reminders = preview.data.reminders.map { old ->
                    db.reminderDao().insert(old.copy(id = 0, vehicleId = vehicles.getValue(old.vehicleId),
                        maintenanceId = old.maintenanceId?.let(services::getValue))).toInt()
                }
                db.importedBackupDao().insert(ImportedBackup(preview.fingerprint, System.currentTimeMillis()))
                Restored(vehicles.values.toList(), reminders)
            }
            committed = true
            result
        } finally { if (!committed) copied.values.forEach(files::delete) }
    }

    private fun encode(s: Snapshot) = JSONObject().put("format", "CarCare").put("version", 1)
        .put("users", array(s.users) { JSONObject().put("id", it.id).put("nombre", it.nombre).put("apellidos", it.apellidos)
            .put("direccion", it.direccion).put("telefono", it.telefono).put("email", it.email) })
        .put("vehicles", array(s.vehicles) { JSONObject().put("id", it.id).put("name", it.name).put("brand", it.brand)
            .put("model", it.model).put("year", it.year).put("mileage", it.mileage).put("plates", it.plates).put("isPrimary", it.isPrimary) })
        .put("services", array(s.services) { JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("type", it.type)
            .put("date", it.date).put("mileage", it.mileage).put("cost", it.cost).put("workshop", it.workshop)
            .put("nextDate", it.nextDate).put("nextMileage", it.nextMileage).put("description", it.description)
            .put("status", it.status).put("receipt", it.receipt ?: JSONObject.NULL) })
        .put("expenses", array(s.expenses) { JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("category", it.category)
            .put("concept", it.concept).put("amount", it.amount).put("date", it.date).put("description", it.description) })
        .put("reminders", array(s.reminders) { JSONObject().put("id", it.id).put("vehicleId", it.vehicleId).put("title", it.title)
            .put("description", it.description).put("dueDate", it.dueDate).put("dueMileage", it.dueMileage)
            .put("priority", it.priority).put("enabled", it.enabled).put("maintenanceId", it.maintenanceId ?: JSONObject.NULL) })

    private fun decode(o: JSONObject): Snapshot {
        require(o.getString("format") == "CarCare" && o.integer("version") == 1) { "Versión de respaldo no compatible" }
        return Snapshot(
            rows(o, "users") { User(it.integer("id"), it.text("nombre"), it.text("apellidos"), it.text("direccion"), it.text("telefono"), it.text("email")) },
            rows(o, "vehicles") { Vehicle(id = it.integer("id"), name = it.text("name"), brand = it.text("brand"), model = it.text("model"),
                year = it.integer("year"), mileage = it.integer("mileage"), plates = it.text("plates"), isPrimary = it.getBoolean("isPrimary")) },
            rows(o, "services") { Maintenance(id = it.integer("id"), vehicleId = it.integer("vehicleId"), type = it.text("type"), date = it.text("date"),
                mileage = it.integer("mileage"), cost = it.getDouble("cost"), workshop = it.text("workshop"), nextDate = it.text("nextDate"),
                nextMileage = it.integer("nextMileage"), description = it.text("description"), status = it.text("status"),
                receipt = if (it.isNull("receipt")) null else it.text("receipt")) },
            rows(o, "expenses") { Expense(it.integer("id"), it.integer("vehicleId"), it.text("category"), it.text("concept"),
                it.getDouble("amount"), it.text("date"), it.text("description")) },
            rows(o, "reminders") { Reminder(it.integer("id"), it.integer("vehicleId"), it.text("title"), it.text("description"),
                it.text("dueDate"), it.integer("dueMileage"), it.text("priority"), it.getBoolean("enabled"),
                if (it.isNull("maintenanceId")) null else it.integer("maintenanceId")) }
        )
    }

    private fun validate(s: Snapshot) {
        require(s.users.size + s.vehicles.size + s.services.size + s.expenses.size + s.reminders.size <= MAX_RECORDS)
        listOf(s.users.map { it.id }, s.vehicles.map { it.id }, s.services.map { it.id }, s.expenses.map { it.id }, s.reminders.map { it.id }).forEach {
            require(it.all { id -> id > 0 } && it.size == it.toSet().size) { "Identificadores inválidos" }
        }
        val cars = s.vehicles.map { it.id }.toSet()
        val services = s.services.associateBy { it.id }
        s.vehicles.forEach { require(it.mileage in 0..9_999_999 && it.year in 0..2100) }
        s.services.forEach {
            require(it.vehicleId in cars && it.mileage in 0..9_999_999 && it.nextMileage in 0..9_999_999)
            require(it.cost.isFinite() && it.cost in 0.0..99_999_999.99 && FormValidation.date(it.date) != null)
            require(it.nextDate.isEmpty() || FormValidation.date(it.nextDate) != null)
            require(it.status in listOf(Maintenance.STATUS_PROXIMO, Maintenance.STATUS_PENDIENTE, Maintenance.STATUS_REALIZADO))
            require(it.receipt == null || ReceiptStore.NAME.matches(it.receipt))
        }
        s.expenses.forEach { require(it.vehicleId in cars && it.amount.isFinite() && it.amount in 0.0..99_999_999.99 && FormValidation.date(it.date) != null) }
        val linked = s.reminders.mapNotNull { it.maintenanceId }
        require(linked.size == linked.toSet().size) { "Hay recordatorios vinculados duplicados" }
        s.reminders.forEach {
            require(it.vehicleId in cars && it.dueMileage in 0..9_999_999)
            require(it.dueDate.isEmpty() || FormValidation.date(it.dueDate) != null)
            require(it.maintenanceId == null || services[it.maintenanceId]?.vehicleId == it.vehicleId) { "Referencia de mantenimiento inválida" }
        }
    }
    private fun JSONObject.integer(key: String): Int {
        val value = get(key)
        require(value is Int || value is Long)
        val number = (value as Number).toLong(); require(number in 0..Int.MAX_VALUE.toLong())
        return number.toInt()
    }
    private fun JSONObject.text(key: String): String = (get(key) as String).also { require(it.length <= 10000) }
    private fun <T> array(list: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { list.forEach { put(encode(it)) } }
    private fun <T> rows(o: JSONObject, key: String, decode: (JSONObject) -> T): List<T> {
        val a = o.getJSONArray(key); require(a.length() <= MAX_RECORDS)
        return List(a.length()) { decode(a.getJSONObject(it)) }
    }
    companion object {
        const val MAX_ARCHIVE = 128L * 1024 * 1024
        private const val MAX_MANIFEST = 8 * 1024 * 1024
        private const val MAX_RECORDS = 20000
    }
}
