package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.HistoryAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.ActivityHistorialBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var adapter: HistoryAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistorialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        binding.btnBack.setOnClickListener {
            finish()
        }

        observeData()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter()
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.maintenanceDao().getCompletedMaintenancesFlow().collectLatest { list ->
                adapter.updateData(list)
                binding.tvTotalServices.text = "${list.size} servicios"

                val spent = list.sumOf { it.cost }
                binding.tvTotalSpent.text = String.format("$%.2f", spent)

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
