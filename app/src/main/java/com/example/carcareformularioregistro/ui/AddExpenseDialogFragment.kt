package com.example.carcareformularioregistro.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.databinding.DialogAddExpenseBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AddExpenseDialogFragment : DialogFragment() {

    private var _binding: DialogAddExpenseBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = arrayOf(
            Expense.CAT_COMBUSTIBLE,
            Expense.CAT_MANTENIMIENTO,
            Expense.CAT_REPARACIONES,
            Expense.CAT_LLANTAS,
            Expense.CAT_OTROS
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories)
        binding.actCategory.setAdapter(adapter)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        binding.etDate.setText(today)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveExpense() }
    }

    private fun saveExpense() {
        val category = binding.actCategory.text?.toString()?.trim().orEmpty()
        val concept = binding.etConcept.text?.toString()?.trim().orEmpty()
        val amountStr = binding.etAmount.text?.toString()?.trim().orEmpty()
        val date = binding.etDate.text?.toString()?.trim().orEmpty()

        if (concept.isBlank()) {
            binding.tilConcept.error = "Ingresa el concepto del gasto"
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            binding.tilAmount.error = "Ingresa un monto válido"
            return
        }

        lifecycleScope.launch {
            val primaryVehicle = withContext(Dispatchers.IO) {
                db.vehicleDao().getPrimaryVehicle()
            }
            val vehicleId = primaryVehicle?.id ?: 1

            val expense = Expense(
                vehicleId = vehicleId,
                category = category.ifBlank { Expense.CAT_OTROS },
                concept = concept,
                amount = amount,
                date = date.ifBlank { "2026-09-24" }
            )

            withContext(Dispatchers.IO) {
                db.expenseDao().insert(expense)
            }

            Toast.makeText(requireContext(), "Gasto registrado con éxito", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
