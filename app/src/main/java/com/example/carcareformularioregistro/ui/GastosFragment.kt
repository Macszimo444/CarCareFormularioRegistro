package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.ExpenseAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.FragmentGastosBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

                val monthlyTotal = expenses.sumOf { it.amount }
                binding.tvMonthlyTotal.text = String.format("$%.2f", monthlyTotal)
                binding.tvYearlyTotal.text = String.format("$%.2f", monthlyTotal * 1.2)
                binding.tvMonthlyAverage.text = String.format("$%.2f", monthlyTotal)
                binding.tvComparison.text = "-12% vs. mes anterior"

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
