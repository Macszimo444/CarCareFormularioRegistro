package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.ExpenseAdapter
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.FragmentGastosBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class GastosFragment : Fragment() {

    private var _binding: FragmentGastosBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ExpenseAdapter
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGastosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ExpenseAdapter()
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = adapter
    }

    private fun setupListeners() {
        val openAddDialog = {
            AddExpenseDialogFragment().show(parentFragmentManager, "AddExpenseDialog")
        }

        binding.btnAddExpense.setOnClickListener { openAddDialog() }
        binding.fabAddExpense.setOnClickListener { openAddDialog() }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.expenseDao().getAllExpensesFlow().collectLatest { expenses ->
                adapter.updateData(expenses)

                val stats = StatisticsCalculator.expenses(expenses)
                val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
                binding.tvMonthlyTotal.text = currency.format(stats.monthlyTotal)
                binding.tvYearlyTotal.text = currency.format(stats.yearlyTotal)
                binding.tvMonthlyAverage.text = currency.format(stats.monthlyAverage)
                val comparison = stats.comparisonPercent
                binding.tvComparison.text = if (comparison == null) {
                    getString(R.string.stats_no_comparison)
                } else getString(R.string.stats_month_comparison, comparison)
                binding.tvComparison.setTextColor(ContextCompat.getColor(requireContext(), when {
                    comparison == null || comparison == 0.0 -> R.color.carcare_text_secondary
                    comparison > 0.0 -> R.color.carcare_danger
                    else -> R.color.carcare_success
                }))

                if (expenses.isEmpty()) {
                    binding.containerEmptyGastos.visibility = View.VISIBLE
                    binding.rvExpenses.visibility = View.GONE
                } else {
                    binding.containerEmptyGastos.visibility = View.GONE
                    binding.rvExpenses.visibility = View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.expenseDao().getExpensesByCategoryFlow().collectLatest { categoryTotals ->
                binding.chartCategoryExpenses.setData(categoryTotals)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
