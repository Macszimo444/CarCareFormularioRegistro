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
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.ActivityAddMaintenanceBinding
import com.example.carcareformularioregistro.utils.FormValidation
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AddMaintenanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMaintenanceBinding
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private var editingId = 0
    private var original: Maintenance? = null
    private var busy = false
    private var loaded = false
    private val statuses = listOf(
        Maintenance.STATUS_PROXIMO, Maintenance.STATUS_PENDIENTE, Maintenance.STATUS_REALIZADO
    )

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
        editingId = intent.getIntExtra("maintenance_id", 0)
        binding.actStatus.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses))
        if (savedInstanceState == null) binding.actStatus.setText(Maintenance.STATUS_REALIZADO, false)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSaveMaintenance.setOnClickListener { saveMaintenance() }
        binding.btnDeleteMaintenance.setOnClickListener { confirmDelete() }
        if (editingId > 0) loadExistingData(savedInstanceState == null) else loaded = true
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.btnSaveMaintenance.isEnabled = !value
        binding.btnDeleteMaintenance.isEnabled = !value
        binding.btnCancel.isEnabled = !value
        binding.btnBack.isEnabled = !value
        listOf(binding.etType, binding.actStatus, binding.etDate, binding.etMileage,
            binding.etCost, binding.etWorkshop, binding.etNextDate, binding.etNextMileage,
            binding.etDescription).forEach { it.isEnabled = !value }
    }

    private fun loadExistingData(fillFields: Boolean) {
        binding.tvHeaderTitle.setText(R.string.maintenance_edit_title)
        binding.btnDeleteMaintenance.visibility = View.VISIBLE
        setBusy(true)
        lifecycleScope.launch {
            try {
                val item = db.maintenanceDao().getById(editingId)
                if (item == null) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }
                original = item
                if (fillFields) {
                    binding.etType.setText(item.type)
                    binding.actStatus.setText(item.status, false)
                    binding.etDate.setText(item.date)
                    binding.etMileage.setText(item.mileage.toString())
                    binding.etCost.setText(item.cost.toString())
                    binding.etWorkshop.setText(item.workshop)
                    binding.etNextDate.setText(item.nextDate)
                    binding.etNextMileage.setText(item.nextMileage.toString())
                    binding.etDescription.setText(item.description)
                }
                loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@AddMaintenanceActivity, R.string.form_load_error, Toast.LENGTH_LONG).show()
                finish()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun confirmDelete() {
        if (busy || !loaded || editingId <= 0) return
        AlertDialog.Builder(this)
            .setTitle(R.string.maintenance_delete_title)
            .setMessage(R.string.maintenance_delete_message)
            .setPositiveButton(R.string.maintenance_delete_confirm) { _, _ -> deleteMaintenance() }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun deleteMaintenance() {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            try {
                db.maintenanceDao().deleteById(editingId)
                Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_deleted, Toast.LENGTH_SHORT).show()
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.form_delete_error, Snackbar.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun invalid(layout: TextInputLayout, message: Int) {
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
        val nextDate = if (nextDateText.isBlank()) date else FormValidation.date(nextDateText)
        val nextMileage = if (nextMileageText.isBlank()) mileage else FormValidation.mileage(nextMileageText)
        when {
            type.isBlank() -> { invalid(binding.tilType, R.string.form_required); return }
            status !in statuses -> { invalid(binding.tilStatus, R.string.form_invalid_option); return }
            date == null -> { invalid(binding.tilDate, R.string.form_invalid_date); return }
            mileage == null -> { invalid(binding.tilMileage, R.string.form_invalid_mileage); return }
            cost == null -> { invalid(binding.tilCost, R.string.form_invalid_cost); return }
            nextDate == null -> { invalid(binding.tilNextDate, R.string.form_invalid_date); return }
            nextMileage == null -> { invalid(binding.tilNextMileage, R.string.form_invalid_mileage); return }
        }
        val workshop = binding.etWorkshop.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        currentFocus?.clearFocus()
        setBusy(true)
        lifecycleScope.launch {
            try {
                val vehicleId = original?.vehicleId ?: db.vehicleDao().getPrimaryVehicle()?.id
                if (vehicleId == null) {
                    Toast.makeText(this@AddMaintenanceActivity, R.string.form_vehicle_required, Toast.LENGTH_LONG).show()
                    if (supportFragmentManager.findFragmentByTag("EditVehicleDialog") == null) {
                        EditVehicleDialogFragment().show(supportFragmentManager, "EditVehicleDialog")
                    }
                    return@launch
                }
                val item = Maintenance(
                    id = editingId, vehicleId = vehicleId, type = type, date = date,
                    mileage = mileage, cost = cost, workshop = workshop, nextDate = nextDate,
                    nextMileage = nextMileage, description = description, status = status
                )
                if (editingId > 0) db.maintenanceDao().update(item) else db.maintenanceDao().insert(item)
                Toast.makeText(this@AddMaintenanceActivity, R.string.maintenance_saved, Toast.LENGTH_SHORT).show()
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.form_save_error, Snackbar.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }
}
