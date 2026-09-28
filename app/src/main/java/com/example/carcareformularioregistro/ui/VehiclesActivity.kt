package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.VehicleAdapter
import com.example.carcareformularioregistro.adapter.VehicleRow
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.ActivityVehiclesBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VehiclesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVehiclesBinding
    private lateinit var adapter: VehicleAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val vehicles by lazy { VehicleRepository.getInstance(applicationContext) }
    private var mutationInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVehiclesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.fabAddVehicle.setOnClickListener { openEditor(null) }
        adapter = VehicleAdapter(onSelect = { vehicle ->
            vehicles.selectVehicle(vehicle.id)
            Toast.makeText(this, getString(R.string.vehicle_selected_message, vehicle.displayName), Toast.LENGTH_SHORT).show()
        }, onOptions = ::showOptions)
        binding.rvVehicles.layoutManager = LinearLayoutManager(this)
        binding.rvVehicles.adapter = adapter
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(db.vehicleDao().getAllVehiclesFlow(), vehicles.selectedVehicleFlow) { all, selected ->
                    all.map { VehicleRow(it, it.id == selected?.id) }
                }.collect { rows ->
                    adapter.submitList(rows)
                    binding.containerEmpty.isVisible = rows.isEmpty()
                }
            }
        }
    }

    private fun openEditor(id: Int?) {
        if (mutationInProgress || supportFragmentManager.findFragmentByTag(EDITOR_TAG) != null) return
        EditVehicleDialogFragment.newInstance(id).show(supportFragmentManager, EDITOR_TAG)
    }

    private fun showOptions(anchor: View, vehicle: Vehicle) {
        if (mutationInProgress) return
        PopupMenu(this, anchor).apply {
            menu.add(0, ACTION_EDIT, 0, R.string.ve_edit)
            if (!vehicle.isPrimary) menu.add(0, ACTION_PRIMARY, 1, R.string.vehicle_set_primary)
            menu.add(0, ACTION_DELETE, 2, R.string.ve_delete)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_EDIT -> openEditor(vehicle.id)
                    ACTION_PRIMARY -> runMutation { vehicles.setPrimary(vehicle.id) }
                    ACTION_DELETE -> confirmDelete(vehicle)
                }
                true
            }
        }.show()
    }

    private fun confirmDelete(vehicle: Vehicle) {
        if (mutationInProgress) return
        mutationInProgress = true
        lifecycleScope.launch {
            try {
                val counts = vehicles.counts(vehicle.id)
                if (isFinishing || isDestroyed) return@launch
                MaterialAlertDialogBuilder(this@VehiclesActivity)
                    .setTitle(getString(R.string.vehicle_delete_title, vehicle.displayName))
                    .setMessage(getString(R.string.vehicle_delete_message, counts.maintenances, counts.expenses, counts.reminders))
                    .setNegativeButton(R.string.cancelar, null)
                    .setPositiveButton(R.string.vehicle_delete_action) { _, _ ->
                        runMutation {
                            vehicles.deleteVehicleAndRecords(vehicle.id)
                            Toast.makeText(this@VehiclesActivity, R.string.vehicle_removed, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@VehiclesActivity, R.string.vehicle_action_error, Toast.LENGTH_LONG).show()
            } finally {
                mutationInProgress = false
            }
        }
    }

    private fun runMutation(action: suspend () -> Unit) {
        if (mutationInProgress) return
        mutationInProgress = true
        binding.fabAddVehicle.isEnabled = false
        lifecycleScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@VehiclesActivity, R.string.vehicle_action_error, Toast.LENGTH_LONG).show()
            } finally {
                mutationInProgress = false
                binding.fabAddVehicle.isEnabled = true
            }
        }
    }

    companion object {
        private const val EDITOR_TAG = "EditVehicleDialog"
        private const val ACTION_EDIT = 1
        private const val ACTION_PRIMARY = 2
        private const val ACTION_DELETE = 3
    }
}
