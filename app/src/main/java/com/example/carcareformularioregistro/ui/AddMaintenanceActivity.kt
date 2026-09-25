package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.ActivityAddMaintenanceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddMaintenanceActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddMaintenanceBinding
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    private var editingId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddMaintenanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        editingId = intent.getIntExtra("maintenance_id", 0)

        setupUI()
        if (editingId > 0) {
            loadExistingData()
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnSaveMaintenance.setOnClickListener {
            saveMaintenance()
        }

        binding.btnDeleteMaintenance.setOnClickListener {
            confirmDelete()
        }
    }

    private fun loadExistingData() {
        binding.tvHeaderTitle.text = "Editar Mantenimiento"
        binding.btnDeleteMaintenance.visibility = View.VISIBLE

        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) {
                db.maintenanceDao().getById(editingId)
            }
            item?.let {
                binding.etType.setText(it.type)
                binding.etDate.setText(it.date)
                binding.etMileage.setText(it.mileage.toString())
                binding.etCost.setText(it.cost.toString())
                binding.etWorkshop.setText(it.workshop)
                binding.etNextDate.setText(it.nextDate)
                binding.etNextMileage.setText(it.nextMileage.toString())
                binding.etDescription.setText(it.description)
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Mantenimiento")
            .setMessage("¿Estás seguro de que deseas eliminar este registro de mantenimiento?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteMaintenance()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteMaintenance() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.maintenanceDao().deleteById(editingId)
            }
            Toast.makeText(
                this@AddMaintenanceActivity,
                "Mantenimiento eliminado",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    private fun saveMaintenance() {
        val type = binding.etType.text?.toString()?.trim().orEmpty()
        val date = binding.etDate.text?.toString()?.trim().orEmpty()
        val mileageStr = binding.etMileage.text?.toString()?.trim().orEmpty()
        val costStr = binding.etCost.text?.toString()?.trim().orEmpty()
        val workshop = binding.etWorkshop.text?.toString()?.trim().orEmpty()
        val nextDate = binding.etNextDate.text?.toString()?.trim().orEmpty()
        val nextMileageStr = binding.etNextMileage.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()

        if (type.isBlank()) {
            binding.tilType.error = "El tipo es obligatorio"
            return
        }
        if (date.isBlank()) {
            binding.tilDate.error = "La fecha es obligatoria"
            return
        }

        val mileage = mileageStr.toIntOrNull() ?: 0
        val cost = costStr.toDoubleOrNull() ?: 0.0
        val nextMileage = nextMileageStr.toIntOrNull() ?: (mileage + 10000)

        lifecycleScope.launch {
            val primaryVehicle = withContext(Dispatchers.IO) {
                db.vehicleDao().getPrimaryVehicle()
            }
            val vehicleId = primaryVehicle?.id ?: 1

            val maintenance = Maintenance(
                id = editingId,
                vehicleId = vehicleId,
                type = type,
                date = date,
                mileage = mileage,
                cost = cost,
                workshop = workshop,
                nextDate = nextDate.ifBlank { date },
                nextMileage = nextMileage,
                description = description,
                status = Maintenance.STATUS_REALIZADO
            )

            withContext(Dispatchers.IO) {
                if (editingId > 0) {
                    db.maintenanceDao().update(maintenance)
                } else {
                    db.maintenanceDao().insert(maintenance)
                }
            }

            Toast.makeText(
                this@AddMaintenanceActivity,
                "Mantenimiento guardado correctamente",
                Toast.LENGTH_SHORT
            ).show()

            finish()
        }
    }
}
