package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import java.time.LocalDate

class AddMaintenanceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddMaintenanceBinding
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private var editingId = 0
    private var vehicleId = 0
    private var busy = false
    private var loaded = false
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
        editingId = intent.getIntExtra("maintenance_id", 0)
        vehicleId = savedInstanceState?.getInt("vehicle_id") ?: 0
        binding.actStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses))
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSaveMaintenance.setOnClickListener { saveMaintenance() }
        binding.btnDeleteMaintenance.setOnClickListener { confirmDelete() }
        binding.btnToggleDetails.setOnClickListener { showDetails(!binding.containerDetails.isVisible) }
        binding.btnTogglePlan.setOnClickListener { showPlan(!binding.containerPlan.isVisible) }
        showDetails(savedInstanceState?.getBoolean("details_open") == true)
        showPlan(savedInstanceState?.getBoolean("plan_open") == true)
        loadData(savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("vehicle_id", vehicleId)
        outState.putBoolean("details_open", binding.containerDetails.isVisible)
        outState.putBoolean("plan_open", binding.containerPlan.isVisible)
        super.onSaveInstanceState(outState)
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
        binding.checkCreateReminder.isEnabled = !value
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
                val item = if (editingId > 0) db.maintenanceDao().getById(editingId) else null
                if (editingId > 0 && item == null) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_not_found, Toast.LENGTH_LONG).show()
                    finish(); return@launch
                }
                val vehicle = when {
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
                if (fillFields) {
                    binding.etType.setText(item?.type.orEmpty())
                    binding.actStatus.setText(item?.status ?: Maintenance.STATUS_REALIZADO, false)
                    binding.etDate.setText(item?.date ?: LocalDate.now().toString())
                    binding.etMileage.setText((item?.mileage ?: vehicle.mileage).toString())
                    binding.etCost.setText(item?.cost?.toString() ?: "0")
                    binding.etWorkshop.setText(item?.workshop.orEmpty())
                    binding.etDescription.setText(item?.description.orEmpty())
                    val linked = item?.let { db.reminderDao().getForMaintenance(it.id) }
                    // v2 used the service date/mileage as defaults for an omitted next review.
                    // Keep those empty until the user explicitly plans a new target; persist on save.
                    val legacyDefault = item != null && linked == null && item.nextDate == item.date && item.nextMileage == item.mileage
                    val nextDate = if (legacyDefault) "" else item?.nextDate.orEmpty()
                    val nextMileage = if (legacyDefault) 0 else item?.nextMileage ?: 0
                    binding.etNextDate.setText(nextDate)
                    binding.etNextMileage.setText(nextMileage.takeIf { it > 0 }?.toString().orEmpty())
                    binding.checkCreateReminder.isChecked = linked != null
                    showDetails(!item?.workshop.isNullOrBlank() || !item?.description.isNullOrBlank())
                    showPlan(nextDate.isNotEmpty() || nextMileage > 0 || linked != null)
                }
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
                Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_deleted, Toast.LENGTH_SHORT).show()
                finish()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Snackbar.make(binding.root, R.string.form_delete_error, Snackbar.LENGTH_LONG).show() }
            finally { setBusy(false) }
        }
    }

    private fun invalid(layout: TextInputLayout, message: Int) {
        if (layout == binding.tilNextDate || layout == binding.tilNextMileage) showPlan(true)
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
        val nextDateText = binding.etNextDate.text?.toString()?.trim().orEmpty()
        val nextMileageText = binding.etNextMileage.text?.toString()?.trim().orEmpty()
        val nextDate = if (nextDateText.isBlank()) "" else FormValidation.date(nextDateText)
        val nextMileage = if (nextMileageText.isBlank()) 0 else FormValidation.mileage(nextMileageText)
        val linkReminder = binding.checkCreateReminder.isChecked
        when {
            type.isBlank() -> { invalid(binding.tilType, R.string.form_required); return }
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
            linkReminder && nextDate.isEmpty() && nextMileage == 0 -> {
                invalid(binding.tilNextDate, R.string.service_target_required); return
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
                val savedReminder = db.withTransaction {
                    check(db.vehicleDao().getById(vehicleId) != null)
                    if (editingId > 0) check(db.maintenanceDao().getById(editingId) != null)
                    val item = Maintenance(id = editingId, vehicleId = vehicleId, type = type, date = date,
                        mileage = mileage, cost = cost, workshop = workshop, nextDate = nextDate,
                        nextMileage = nextMileage, description = description, status = status)
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
                deletedReminder?.let {
                    NotificationHelper.cancelReminderAlarm(applicationContext, it.id)
                    runCatching { ReminderSettings(applicationContext).forget(it.id) }
                }
                val scheduled = savedReminder?.let { NotificationHelper.scheduleReminder(applicationContext, it) } ?: true
                Toast.makeText(this@AddMaintenanceActivity,
                    if (scheduled) R.string.maintenance_saved else R.string.reminder_alarm_error, Toast.LENGTH_LONG).show()
                finish()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Snackbar.make(binding.root, R.string.form_save_error, Snackbar.LENGTH_LONG).show() }
            finally { setBusy(false) }
        }
    }
}
