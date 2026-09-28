package com.example.carcareformularioregistro.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import com.example.carcareformularioregistro.data.ReviewCompletion
import com.example.carcareformularioregistro.utils.ReceiptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.ActivityAddMaintenanceBinding
import com.example.carcareformularioregistro.utils.FormDatePicker
import com.example.carcareformularioregistro.utils.FormValidation
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReminderSettings
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class AddMaintenanceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddMaintenanceBinding
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private var editingId = 0
    private var vehicleId = 0
    private var busy = false
    private var loaded = false
    private val completionId get() = intent.getIntExtra("complete_reminder_id", 0)
    private var receipt: String? = null
    private var originalReceipt: String? = null
    private var receiptCommitted = false
    private val receipts by lazy { ReceiptStore(applicationContext) }
    private val pickReceipt = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            setBusy(true)
            var imported: String? = null
            try {
                val name = withContext(Dispatchers.IO) { receipts.import(uri).also { imported = it } }
                if (receipt != originalReceipt) receipts.delete(receipt)
                receipt = name
                renderReceipt()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Snackbar.make(binding.root, "Elige una imagen o PDF válido de hasta 10 MB", Snackbar.LENGTH_LONG).show() }
            finally {
                if (imported != receipt) receipts.discardDraft(imported)
                setBusy(false)
            }
        }
    }
    private var targetMode = ReviewTargetMode.DATE
    private val statuses = listOf(Maintenance.STATUS_PROXIMO, Maintenance.STATUS_PENDIENTE, Maintenance.STATUS_REALIZADO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddMaintenanceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        FormDatePicker.attach(binding.tilDate)
        FormDatePicker.attach(binding.tilNextDate)
        receipt = savedInstanceState?.getString("receipt")
        originalReceipt = savedInstanceState?.getString("original_receipt")
        binding.btnReceipt.setOnClickListener { manageReceipt() }
        editingId = intent.getIntExtra("maintenance_id", 0)
        vehicleId = savedInstanceState?.getInt("vehicle_id") ?: 0
        binding.actStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses))
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!busy) finish() }
        })
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSaveMaintenance.setOnClickListener { saveMaintenance() }
        binding.btnDeleteMaintenance.setOnClickListener { confirmDelete() }
        binding.btnToggleDetails.setOnClickListener { showDetails(!binding.containerDetails.isVisible) }
        binding.btnTogglePlan.setOnClickListener { showPlan(!binding.containerPlan.isVisible) }
        binding.btnToggleServiceMileage.setOnClickListener { showServiceMileage(!binding.tilMileage.isVisible) }
        binding.etMileage.doAfterTextChanged { updateMileageSummary() }
        binding.tvServiceMileageHelp.setText(R.string.service_mileage_short)
        binding.btnMileageHelp.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Kilometraje del servicio")
                .setMessage(if (editingId > 0) R.string.service_mileage_saved_help else R.string.service_mileage_auto_help)
                .setPositiveButton("Entendido", null).show()
        }
        showServiceMileage(savedInstanceState?.getBoolean("mileage_open") == true)
        binding.btnTargetHelp.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(targetMode.label).setMessage(targetMode.help)
                .setPositiveButton("Entendido", null).show()
        }
        binding.actTargetMode.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line,
            ReviewTargetMode.entries.map { getString(it.label) }))
        binding.actTargetMode.setOnItemClickListener { _, _, position, _ -> showTargetMode(ReviewTargetMode.entries[position]) }
        showTargetMode(ReviewTargetMode.restore(savedInstanceState?.getString("target_mode")))
        showDetails(savedInstanceState?.getBoolean("details_open") == true)
        showPlan(savedInstanceState?.getBoolean("plan_open") == true)
        loadData(savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("receipt", receipt)
        outState.putString("original_receipt", originalReceipt)
        outState.putString("target_mode", targetMode.name)
        outState.putBoolean("mileage_open", binding.tilMileage.isVisible)
        outState.putInt("vehicle_id", vehicleId)
        outState.putBoolean("details_open", binding.containerDetails.isVisible)
        outState.putBoolean("plan_open", binding.containerPlan.isVisible)
        super.onSaveInstanceState(outState)
    }

    private fun renderReceipt() {
        binding.btnReceipt.setText(if (receipt == null) R.string.receipt_add else R.string.receipt_manage)
    }

    private fun manageReceipt() {
        if (busy) return
        val name = receipt
        if (name == null) pickReceipt.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
        else AlertDialog.Builder(this).setTitle("Comprobante")
            .setItems(arrayOf("Ver comprobante", "Reemplazar", "Quitar del servicio")) { _, selected ->
                when (selected) {
                    0 -> try { receipts.open(name) } catch (_: Exception) {
                        Snackbar.make(binding.root, "No se pudo abrir. Comprueba que tengas una app para este archivo.", Snackbar.LENGTH_LONG).show()
                    }
                    1 -> pickReceipt.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
                    2 -> { if (receipt != originalReceipt) receipts.delete(receipt); receipt = null; renderReceipt() }
                }
            }.show()
    }

    override fun onDestroy() {
        if (isFinishing && !receiptCommitted && receipt != originalReceipt) receipts.discardDraft(receipt)
        super.onDestroy()
    }

    private fun showServiceMileage(open: Boolean) {
        if (!open && binding.etMileage.hasFocus()) {
            binding.etMileage.clearFocus()
            WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        }
        binding.tilMileage.isVisible = open
        binding.btnToggleServiceMileage.setText(if (open) R.string.service_mileage_close else R.string.service_mileage_change)
    }

    private fun updateMileageSummary() {
        val mileage = FormValidation.mileage(binding.etMileage.text.toString())
        binding.tvServiceMileage.text = if (mileage == null) getString(R.string.service_mileage_missing)
            else getString(R.string.service_mileage_summary, NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX")).format(mileage))
    }

    private fun showTargetMode(mode: ReviewTargetMode) {
        targetMode = mode
        binding.actTargetMode.setText(getString(mode.label), false)
        binding.tilNextDate.isVisible = mode.usesDate
        binding.tilNextMileage.isVisible = mode.usesMileage
        binding.tvTargetHelp.setText(mode.shortHelp)
        binding.tilNextDate.error = null
        binding.tilNextMileage.error = null
    }

    private fun showDetails(open: Boolean) {
        binding.containerDetails.isVisible = open
        binding.btnToggleDetails.setText(if (open) R.string.service_details_hide else R.string.service_details_show)
    }
    private fun showPlan(open: Boolean) {
        binding.containerPlan.isVisible = open
        binding.btnTogglePlan.setText(if (open) R.string.service_plan_hide else R.string.service_plan_show)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnSaveMaintenance.isEnabled = !value
        binding.btnDeleteMaintenance.isEnabled = !value
        binding.btnCancel.isEnabled = !value
        binding.btnBack.isEnabled = !value
        binding.btnReceipt.isEnabled = !value
        binding.checkCreateReminder.isEnabled = !value
        binding.actTargetMode.isEnabled = !value
        binding.btnToggleServiceMileage.isEnabled = !value
        listOf(binding.etType, binding.actStatus, binding.etDate, binding.etMileage, binding.etCost,
            binding.etWorkshop, binding.etNextDate, binding.etNextMileage, binding.etDescription).forEach { it.isEnabled = !value }
    }

    private fun loadData(fillFields: Boolean) {
        if (editingId > 0) {
            binding.tvHeaderTitle.setText(R.string.maintenance_edit_title)
            binding.btnDeleteMaintenance.visibility = View.VISIBLE
        }
        setBusy(true)
        lifecycleScope.launch {
            try {
                val source = if (completionId > 0) db.reminderDao().getById(completionId) else null
                if (completionId > 0 && source == null) {
                    Toast.makeText(this@AddMaintenanceActivity, "Esta revisión ya fue resuelta o eliminada", Toast.LENGTH_LONG).show()
                    finish(); return@launch
                }
                val previous = source?.maintenanceId?.let { db.maintenanceDao().getById(it) }
                if (source != null) editingId = previous?.takeIf { it.status != Maintenance.STATUS_REALIZADO }?.id ?: 0
                val item = if (editingId > 0) db.maintenanceDao().getById(editingId) else null
                if (editingId > 0 && item == null) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_not_found, Toast.LENGTH_LONG).show()
                    finish(); return@launch
                }
                val vehicle = when {
                    source != null -> db.vehicleDao().getById(source.vehicleId)
                    item != null -> db.vehicleDao().getById(item.vehicleId)
                    vehicleId > 0 -> db.vehicleDao().getById(vehicleId)
                    else -> VehicleRepository.getInstance(applicationContext).getSelectedVehicle()
                }
                if (vehicle == null) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.service_vehicle_missing, Toast.LENGTH_LONG).show()
                    finish(); return@launch
                }
                vehicleId = vehicle.id
                binding.tvVehicleContext.text = getString(R.string.service_vehicle_context, vehicle.displayName)
                if (source != null) {
                    binding.tvHeaderTitle.setText(R.string.complete_review_title)
                    binding.tvVehicleContext.append("\n" + getString(R.string.complete_review_intro))
                    binding.btnDeleteMaintenance.isVisible = false
                }
                if (fillFields) {
                    receipt = item?.receipt
                    originalReceipt = receipt
                    binding.etType.setText(item?.type ?: previous?.type ?: source?.title.orEmpty())
                    binding.actStatus.setText(if (source != null) Maintenance.STATUS_REALIZADO else item?.status ?: Maintenance.STATUS_REALIZADO, false)
                    binding.etDate.setText(if (source != null) LocalDate.now().toString() else item?.date ?: LocalDate.now().toString())
                    binding.etMileage.setText((if (source != null) vehicle.mileage else item?.mileage ?: vehicle.mileage).toString())
                    binding.etCost.setText(item?.cost?.toString() ?: "0")
                    binding.etWorkshop.setText(item?.workshop.orEmpty())
                    binding.etDescription.setText(item?.description.orEmpty())
                    val linked = if (source != null) null else item?.let { db.reminderDao().getForMaintenance(it.id) }
                    // v2 used the service date/mileage as defaults for an omitted next review.
                    // Keep those empty until the user explicitly plans a new target; persist on save.
                    val legacyDefault = item != null && linked == null && item.nextDate == item.date && item.nextMileage == item.mileage
                    val nextDate = if (source != null || legacyDefault) "" else item?.nextDate.orEmpty()
                    val nextMileage = if (source != null || legacyDefault) 0 else item?.nextMileage ?: 0
                    binding.etNextDate.setText(nextDate)
                    binding.etNextMileage.setText(nextMileage.takeIf { it > 0 }?.toString().orEmpty())
                    showTargetMode(ReviewTargetMode.fromTargets(nextDate, nextMileage))
                    binding.checkCreateReminder.isChecked = linked != null
                    showDetails(!item?.workshop.isNullOrBlank() || !item?.description.isNullOrBlank())
                    showPlan(nextDate.isNotEmpty() || nextMileage > 0 || linked != null)
                }
                renderReceipt()
                loaded = true
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                Toast.makeText(this@AddMaintenanceActivity, R.string.form_load_error, Toast.LENGTH_LONG).show()
                finish()
            } finally { setBusy(false) }
        }
    }

    private fun confirmDelete() {
        if (busy || !loaded || editingId <= 0) return
        AlertDialog.Builder(this).setTitle(R.string.maintenance_delete_title)
            .setMessage(R.string.service_delete_linked)
            .setPositiveButton(R.string.maintenance_delete_confirm) { _, _ -> deleteMaintenance() }
            .setNegativeButton(R.string.cancelar, null).show()
    }

    private fun deleteMaintenance() {
        setBusy(true)
        lifecycleScope.launch {
            try {
                val linked = db.withTransaction {
                    val reminder = db.reminderDao().getForMaintenance(editingId)
                    reminder?.let { db.reminderDao().delete(it) }
                    db.maintenanceDao().deleteById(editingId)
                    reminder
                }
                linked?.let {
                    NotificationHelper.cancelReminderAlarm(applicationContext, it.id)
                    runCatching { ReminderSettings(applicationContext).forget(it.id) }
                }
                originalReceipt?.let { if (db.maintenanceDao().countReceiptReferences(it) == 0) receipts.delete(it) }
                Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_deleted, Toast.LENGTH_SHORT).show()
                finish()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Snackbar.make(binding.root, R.string.form_delete_error, Snackbar.LENGTH_LONG).show() }
            finally { setBusy(false) }
        }
    }

    private fun invalid(layout: TextInputLayout, message: Int) {
        if (layout == binding.tilNextDate || layout == binding.tilNextMileage) showPlan(true)
        if (layout == binding.tilMileage) showServiceMileage(true)
        layout.error = getString(message)
        layout.editText?.requestFocus()
    }

    private fun saveMaintenance() {
        if (busy || !loaded) return
        listOf(binding.tilType, binding.tilStatus, binding.tilDate, binding.tilMileage,
            binding.tilCost, binding.tilNextDate, binding.tilNextMileage).forEach { it.error = null }
        val type = binding.etType.text?.toString()?.trim().orEmpty()
        val status = binding.actStatus.text.toString()
        val date = FormValidation.date(binding.etDate.text.toString())
        val mileage = FormValidation.mileage(binding.etMileage.text.toString())
        val cost = FormValidation.amount(binding.etCost.text.toString())
        val nextDateText = if (targetMode.usesDate) binding.etNextDate.text?.toString()?.trim().orEmpty() else ""
        val nextMileageText = if (targetMode.usesMileage) binding.etNextMileage.text?.toString()?.trim().orEmpty() else ""
        val nextDate = if (nextDateText.isBlank()) "" else FormValidation.date(nextDateText)
        val nextMileage = if (nextMileageText.isBlank()) 0 else FormValidation.mileage(nextMileageText)
        val linkReminder = binding.checkCreateReminder.isChecked
        when {
            type.isBlank() -> { invalid(binding.tilType, R.string.form_required); return }
            completionId > 0 && status != Maintenance.STATUS_REALIZADO -> { invalid(binding.tilStatus, R.string.completion_requires_done); return }
            status !in statuses -> { invalid(binding.tilStatus, R.string.form_invalid_option); return }
            date == null -> { invalid(binding.tilDate, R.string.form_invalid_date); return }
            LocalDate.parse(date).year !in 1900..2100 -> { invalid(binding.tilDate, R.string.service_date_range); return }
            status == Maintenance.STATUS_REALIZADO && LocalDate.parse(date).isAfter(LocalDate.now()) -> {
                invalid(binding.tilDate, R.string.service_done_future); return
            }
            mileage == null || mileage > 9_999_999 -> { invalid(binding.tilMileage, R.string.form_invalid_mileage); return }
            cost == null || cost > 99_999_999.99 -> { invalid(binding.tilCost, R.string.form_invalid_cost); return }
            nextDate == null -> { invalid(binding.tilNextDate, R.string.form_invalid_date); return }
            nextDate.isNotEmpty() && (LocalDate.parse(nextDate).year !in 1900..2100 || nextDate <= date) -> {
                invalid(binding.tilNextDate, R.string.service_next_date_invalid); return
            }
            nextMileage == null || nextMileage > 9_999_999 -> { invalid(binding.tilNextMileage, R.string.form_invalid_mileage); return }
            nextMileageText.isNotBlank() && nextMileage <= mileage -> {
                invalid(binding.tilNextMileage, R.string.service_next_mileage_invalid); return
            }
            (linkReminder || nextMileage > 0) && targetMode.usesDate && nextDate.isEmpty() -> {
                invalid(binding.tilNextDate, R.string.form_required); return
            }
            (linkReminder || nextDate.isNotEmpty()) && targetMode.usesMileage && nextMileage == 0 -> {
                invalid(binding.tilNextMileage, R.string.form_required); return
            }
        }
        val workshop = binding.etWorkshop.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
        setBusy(true)
        lifecycleScope.launch {
            try {
                var deletedReminder: Reminder? = null
                var completedReminder: Reminder? = null
                val savedReminder = db.withTransaction {
                    check(db.vehicleDao().getById(vehicleId) != null)
                    if (completionId > 0) completedReminder = ReviewCompletion.consume(db, completionId, vehicleId)
                    if (editingId > 0) check(db.maintenanceDao().getById(editingId) != null)
                    val item = Maintenance(id = editingId, vehicleId = vehicleId, type = type, date = date,
                        mileage = mileage, cost = cost, workshop = workshop, nextDate = nextDate,
                        nextMileage = nextMileage, description = description, status = status, receipt = receipt)
                    val id = if (editingId > 0) { db.maintenanceDao().update(item); editingId }
                        else db.maintenanceDao().insert(item).toInt()
                    val existing = db.reminderDao().getForMaintenance(id)
                    if (linkReminder) {
                        val reminder = Reminder(id = existing?.id ?: 0, vehicleId = vehicleId,
                            title = existing?.title ?: getString(R.string.service_reminder_title, type).take(120),
                            description = existing?.description ?: getString(R.string.service_reminder_notes, type),
                            dueDate = nextDate, dueMileage = nextMileage,
                            priority = existing?.priority ?: Reminder.PRIORITY_MEDIA,
                            enabled = existing?.enabled ?: true, maintenanceId = id)
                        if (existing == null) reminder.copy(id = db.reminderDao().insert(reminder).toInt())
                        else { db.reminderDao().update(reminder); reminder }
                    } else {
                        existing?.let { db.reminderDao().delete(it); deletedReminder = it }
                        null
                    }
                }
                receiptCommitted = true
                loaded = false
                if (originalReceipt != receipt) originalReceipt?.let {
                    if (db.maintenanceDao().countReceiptReferences(it) == 0) receipts.delete(it)
                }
                completedReminder?.let {
                    NotificationHelper.cancelReminderAlarm(applicationContext, it.id)
                    ReminderSettings(applicationContext).forget(it.id)
                }
                deletedReminder?.let {
                    NotificationHelper.cancelReminderAlarm(applicationContext, it.id)
                    runCatching { ReminderSettings(applicationContext).forget(it.id) }
                }
                val scheduled = savedReminder?.let { NotificationHelper.scheduleReminder(applicationContext, it) } ?: true
                Toast.makeText(this@AddMaintenanceActivity,
                    if (scheduled) R.string.maintenance_saved else R.string.reminder_alarm_error, Toast.LENGTH_LONG).show()
                finish()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (receiptCommitted) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.reminder_alarm_error, Toast.LENGTH_LONG).show()
                    finish()
                } else Snackbar.make(binding.root, R.string.form_save_error, Snackbar.LENGTH_LONG).show()
            }
            finally { setBusy(false) }
        }
    }
}
