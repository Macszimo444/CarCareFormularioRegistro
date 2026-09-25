package com.example.carcareformularioregistro.ui

import android.os.Bundle
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
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.ActivityHistorialBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HistorialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistorialBinding
    private lateinit var adapter: HistoryAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

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
