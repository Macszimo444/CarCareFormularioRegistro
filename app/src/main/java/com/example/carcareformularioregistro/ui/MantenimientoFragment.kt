package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.MaintenanceAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.FragmentMantenimientoBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MantenimientoFragment : Fragment() {

    private var _binding: FragmentMantenimientoBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: MaintenanceAdapter
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    private var currentFilter = 0 // 0: Todos, 1: Próximos, 2: Realizados

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMantenimientoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = MaintenanceAdapter(
            onItemClick = { maintenance ->
                val intent = Intent(requireContext(), AddMaintenanceActivity::class.java).apply {
                    putExtra("maintenance_id", maintenance.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { maintenance ->
                confirmDeleteMaintenance(maintenance)
            }
        )
        binding.rvMaintenances.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMaintenances.adapter = adapter
    }

    private fun confirmDeleteMaintenance(maintenance: Maintenance) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Mantenimiento")
            .setMessage("¿Deseas borrar '${maintenance.type}' del registro?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.maintenanceDao().delete(maintenance)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupListeners() {
        binding.fabAddMaintenance.setOnClickListener {
            startActivity(Intent(requireContext(), AddMaintenanceActivity::class.java))
        }

        binding.btnEmptyAdd.setOnClickListener {
            startActivity(Intent(requireContext(), AddMaintenanceActivity::class.java))
        }

        binding.btnViewHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistorialActivity::class.java))
        }

        binding.tabFilters.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentFilter = tab?.position ?: 0
                observeData()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val flow = when (currentFilter) {
                1 -> db.maintenanceDao().getMaintenancesByStatusFlow(Maintenance.STATUS_PROXIMO)
                2 -> db.maintenanceDao().getMaintenancesByStatusFlow(Maintenance.STATUS_REALIZADO)
                else -> db.maintenanceDao().getAllMaintenancesFlow()
            }

            flow.collectLatest { list ->
                adapter.updateData(list)
                binding.tvMonitoredSubtitle.text = "${list.size} servicios monitoreados"

                if (list.isEmpty()) {
                    binding.containerEmpty.visibility = View.VISIBLE
                    binding.rvMaintenances.visibility = View.GONE
                } else {
                    binding.containerEmpty.visibility = View.GONE
                    binding.rvMaintenances.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
