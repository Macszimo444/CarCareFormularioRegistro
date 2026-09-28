package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.utils.HistoryPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.HistoryAdapter
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.MaintenanceRepository
import com.example.carcareformularioregistro.data.Maintenance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.catch
import com.google.android.material.snackbar.Snackbar
import com.example.carcareformularioregistro.databinding.ActivityHistorialBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var adapter: HistoryAdapter
    private var currentVehicle: Vehicle? = null
    private var exportVehicleId = 0
    private var exporting = false
    private val exportPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && exportVehicleId > 0) {
            val id = exportVehicleId
            exporting = true
            binding.btnExportPdf.isEnabled = false
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(applicationContext)
                        val snapshot = db.withTransaction {
                            requireNotNull(db.vehicleDao().getById(id)) to
                                db.maintenanceDao().getAllMaintenances().filter { it.vehicleId == id }
                        }
                        requireNotNull(contentResolver.openOutputStream(uri, "wt")).use {
                            HistoryPdf.write(snapshot.first, snapshot.second, it)
                        }
                    }
                    Snackbar.make(binding.root, R.string.pdf_saved, Snackbar.LENGTH_LONG).show()
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { Snackbar.make(binding.root, "No se pudo guardar el PDF. Revisa el espacio disponible.", Snackbar.LENGTH_LONG).show() }
                finally { exporting = false; binding.btnExportPdf.isEnabled = currentVehicle != null }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val initialPadding = intArrayOf(
            binding.root.paddingLeft, binding.root.paddingTop,
            binding.root.paddingRight, binding.root.paddingBottom
        )
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPadding[0] + bars.left, initialPadding[1] + bars.top,
                initialPadding[2] + bars.right, initialPadding[3] + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        exportVehicleId = savedInstanceState?.getInt("export_vehicle_id") ?: 0
        binding.btnExportPdf.setOnClickListener {
            currentVehicle?.let {
                exportVehicleId = it.id
                exportPdf.launch("CarCare-historial-${it.id}.pdf")
            }
        }
        setupRecyclerView()
        binding.btnBack.setOnClickListener {
            finish()
        }

        observeData()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("export_vehicle_id", exportVehicleId)
        super.onSaveInstanceState(outState)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(onClick = {
            startActivity(Intent(this, AddMaintenanceActivity::class.java).putExtra("maintenance_id", it.id))
        })
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
            MaintenanceRepository(applicationContext).observeMaintenances()
                .catch { Snackbar.make(binding.root, R.string.core_load_error, Snackbar.LENGTH_LONG).show() }
                .collectLatest { records ->
                currentVehicle = records.vehicle
                binding.btnExportPdf.isEnabled = records.vehicle != null && !exporting
                val list = records.items.filter { it.status == Maintenance.STATUS_REALIZADO }
                binding.tvHistoryContext.text = listOfNotNull(records.vehicle?.displayName,
                    getString(R.string.history_context_help)).joinToString("\n")
                adapter.updateData(list)
                val stats = StatisticsCalculator.history(list)
                binding.tvTotalServices.text = resources.getQuantityString(
                    R.plurals.stats_history_services, stats.services, stats.services
                )
                binding.tvTotalSpent.text = NumberFormat
                    .getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(stats.spent)

                if (list.isEmpty()) {
                    binding.containerEmptyHistorial.visibility = View.VISIBLE
                    binding.rvHistory.visibility = View.GONE
                } else {
                    binding.containerEmptyHistorial.visibility = View.GONE
                    binding.rvHistory.visibility = View.VISIBLE
                }
            }
            }
        }
    }
}
