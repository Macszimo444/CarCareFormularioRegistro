package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.databinding.DialogAddExpenseBinding
import com.example.carcareformularioregistro.utils.FormValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddExpenseDialogFragment : DialogFragment() {
    private var _binding: DialogAddExpenseBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private var saving = false
    private val categories = listOf(Expense.CAT_COMBUSTIBLE, Expense.CAT_MANTENIMIENTO,
        Expense.CAT_REPARACIONES, Expense.CAT_LLANTAS, Expense.CAT_OTROS)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.actCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories))
        if (savedInstanceState == null) binding.etDate.setText(LocalDate.now().toString())
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveExpense() }
    }

    private fun saveExpense() {
        if (saving) return
        listOf(binding.tilCategory, binding.tilConcept, binding.tilAmount, binding.tilDate).forEach { it.error = null }
        val category = binding.actCategory.text?.toString()?.trim().orEmpty()
        val concept = binding.etConcept.text?.toString()?.trim().orEmpty()
        val amount = FormValidation.amount(binding.etAmount.text.toString())
        val date = FormValidation.date(binding.etDate.text.toString())
        when {
            concept.isBlank() -> { binding.tilConcept.error = getString(R.string.form_required); return }
            amount == null || amount <= 0 -> { binding.tilAmount.error = getString(R.string.form_invalid_positive_amount); return }
            date == null -> { binding.tilDate.error = getString(R.string.form_invalid_date); return }
            category !in categories -> { binding.tilCategory.error = getString(R.string.form_invalid_option); return }
        }
        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val vehicle = db.vehicleDao().getPrimaryVehicle()
                if (vehicle == null) {
                    Toast.makeText(requireContext(), R.string.form_vehicle_required, Toast.LENGTH_LONG).show()
                    if (parentFragmentManager.findFragmentByTag("EditVehicleDialog") == null) {
                        EditVehicleDialogFragment().show(parentFragmentManager, "EditVehicleDialog")
                    }
                    return@launch
                }
                db.expenseDao().insert(Expense(vehicleId = vehicle.id, category = category,
                    concept = concept, amount = amount, date = date))
                Toast.makeText(requireContext(), R.string.expense_saved, Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.form_save_error, Toast.LENGTH_LONG).show() }
            } finally {
                setSaving(false)
            }
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        isCancelable = !value
        _binding?.let { it.btnSave.isEnabled = !value; it.btnCancel.isEnabled = !value }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
