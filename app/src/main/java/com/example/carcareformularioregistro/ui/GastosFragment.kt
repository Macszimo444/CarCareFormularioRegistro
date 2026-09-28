package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.ExpenseAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.CategoryTotal
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.FragmentGastosBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class GastosFragment : Fragment() {
    private var _binding: FragmentGastosBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ExpenseAdapter
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private val vehicles by lazy { VehicleRepository.getInstance(requireContext().applicationContext) }
    private var deleting = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGastosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ExpenseAdapter(onItemClick = { openEditor(it.id) }, onOptions = ::showOptions)
        binding.rvExpenses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExpenses.adapter = adapter
        binding.fabAddExpense.setOnClickListener { openEditor(null) }
        observeData()
    }

    private fun openEditor(id: Int?) {
        if (parentFragmentManager.findFragmentByTag(EDITOR_TAG) == null) {
            AddExpenseDialogFragment.newInstance(id).show(parentFragmentManager, EDITOR_TAG)
        }
    }

    private fun showOptions(anchor: View, expense: Expense) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.ve_edit)
            menu.add(0, 2, 1, R.string.ve_delete)
            setOnMenuItemClickListener {
                if (it.itemId == 1) openEditor(expense.id) else confirmDelete(expense)
                true
            }
        }.show()
    }

    private fun confirmDelete(expense: Expense) {
        if (deleting) return
        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.expense_delete_title)
            .setMessage(getString(R.string.expense_delete_message, expense.concept, currency.format(expense.amount)))
            .setNegativeButton(R.string.cancelar, null)
            .setPositiveButton(R.string.ve_delete) { _, _ ->
                if (deleting) return@setPositiveButton
                deleting = true
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // The captured expense id is unchanged even if the selected vehicle changes.
                        db.expenseDao().deleteById(expense.id)
                        context?.let { Toast.makeText(it, R.string.expense_deleted, Toast.LENGTH_SHORT).show() }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        context?.let { Toast.makeText(it, R.string.vehicle_action_error, Toast.LENGTH_LONG).show() }
                    } finally {
                        deleting = false
                    }
                }
            }.show()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vehicles.selectedVehicleFlow.flatMapLatest { vehicle ->
                    if (vehicle == null) flowOf(null to emptyList<Expense>())
                    else db.expenseDao().getForVehicleFlow(vehicle.id)
                        .onStart { emit(emptyList()) }.map { vehicle to it }
                }.catch { error ->
                    if (error is CancellationException) throw error
                    _binding?.let { form ->
                        adapter.updateData(emptyList())
                        form.containerStats.isVisible = false
                        form.containerListHeader.isVisible = false
                        form.rvExpenses.isVisible = false
                        form.containerEmptyGastos.isVisible = true
                        form.tvEmptyGastos.setText(R.string.form_load_error)
                        form.fabAddExpense.isEnabled = false
                    }
                }.collect { (vehicle, expenses) ->
                    binding.tvVehicle.text = vehicle?.let { getString(R.string.vehicle_current_context, it.displayName) }
                        ?: getString(R.string.vehicle_not_selected)
                    binding.fabAddExpense.isEnabled = vehicle != null
                    binding.tvEmptyGastos.setText(if (vehicle == null) R.string.expense_no_vehicle_help else R.string.expense_empty_help)
                    adapter.updateData(expenses)
                    val stats = StatisticsCalculator.expenses(expenses)
                    val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
                    binding.tvMonthlyTotal.text = currency.format(stats.monthlyTotal)
                    binding.tvYearlyTotal.text = currency.format(stats.yearlyTotal)
                    binding.tvMonthlyAverage.text = currency.format(stats.monthlyAverage)
                    val comparison = stats.comparisonPercent
                    binding.tvComparison.text = if (comparison == null) getString(R.string.stats_no_comparison)
                        else getString(R.string.stats_month_comparison, comparison)
                    binding.tvComparison.setTextColor(ContextCompat.getColor(requireContext(), when {
                        comparison == null || comparison == 0.0 -> R.color.carcare_text_secondary
                        comparison > 0.0 -> R.color.carcare_danger
                        else -> R.color.carcare_success
                    }))
                    binding.containerStats.isVisible = expenses.isNotEmpty()
                    binding.containerListHeader.isVisible = expenses.isNotEmpty()
                    binding.containerEmptyGastos.isVisible = expenses.isEmpty()
                    binding.rvExpenses.isVisible = expenses.isNotEmpty()
                    binding.chartCategoryExpenses.setData(expenses.groupBy { it.category }
                        .map { (category, values) -> CategoryTotal(category, values.sumOf { it.amount }) }
                        .sortedByDescending { it.total })
                }
            }
        }
    }

    override fun onDestroyView() {
        binding.rvExpenses.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object { private const val EDITOR_TAG = "AddExpenseDialog" }
}
