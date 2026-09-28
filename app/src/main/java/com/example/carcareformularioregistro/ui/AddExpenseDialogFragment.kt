package com.example.carcareformularioregistro.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.DialogAddExpenseBinding
import com.example.carcareformularioregistro.utils.FormValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Both creation and editing keep the vehicle captured when the form was opened. */
class AddExpenseDialogFragment : DialogFragment() {
    private var _binding: DialogAddExpenseBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private val vehicles by lazy { VehicleRepository.getInstance(requireContext().applicationContext) }
    private var currentExpense: Expense? = null
    private var capturedVehicleId: Int? = null
    private var saving = false
    private var loaded = false
    private var detailsVisible = false
    private val expenseId get() = arguments?.getInt(ARG_EXPENSE_ID, 0)?.takeIf { it > 0 }
    private val categories = listOf(Expense.CAT_COMBUSTIBLE, Expense.CAT_MANTENIMIENTO,
        Expense.CAT_REPARACIONES, Expense.CAT_LLANTAS, Expense.CAT_OTROS)
    private val displayDate = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddExpenseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.setText(if (expenseId == null) R.string.expense_add_title else R.string.expense_edit_title)
        binding.actCategory.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, categories))
        capturedVehicleId = savedInstanceState?.getInt(STATE_VEHICLE, 0)?.takeIf { it > 0 }
        showDetails(savedInstanceState?.getBoolean(STATE_DETAILS) ?: false)
        binding.btnDetails.setOnClickListener { showDetails(!detailsVisible) }
        binding.etDate.setOnClickListener { chooseDate() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveExpense() }
        loadExpense(savedInstanceState == null)
    }

    private fun loadExpense(fillFields: Boolean) {
        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                currentExpense = expenseId?.let { db.expenseDao().getById(it) }
                if (expenseId != null && currentExpense == null) {
                    Toast.makeText(requireContext(), R.string.expense_missing, Toast.LENGTH_LONG).show()
                    dismiss()
                    return@launch
                }
                capturedVehicleId = currentExpense?.vehicleId ?: capturedVehicleId ?: vehicles.getSelectedVehicle()?.id
                val vehicle = capturedVehicleId?.let { db.vehicleDao().getById(it) }
                if (vehicle == null) {
                    Toast.makeText(requireContext(), R.string.form_vehicle_required, Toast.LENGTH_LONG).show()
                    dismiss()
                    return@launch
                }
                binding.tvVehicle.text = getString(R.string.vehicle_current_context, vehicle.displayName)
                if (fillFields) {
                    val item = currentExpense
                    binding.actCategory.setText(item?.category ?: categories.first(), false)
                    binding.etConcept.setText(item?.concept.orEmpty())
                    binding.etAmount.setText(item?.amount?.let { java.math.BigDecimal.valueOf(it).toPlainString() }.orEmpty())
                    binding.etDate.setText((item?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: LocalDate.now()).format(displayDate))
                    binding.etDescription.setText(item?.description.orEmpty())
                    if (!item?.description.isNullOrBlank()) showDetails(true)
                }
                loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.form_load_error, Toast.LENGTH_LONG).show() }
                dismiss()
            } finally {
                setSaving(false)
            }
        }
    }

    private fun chooseDate() {
        if (saving) return
        val initial = FormValidation.date(binding.etDate.text.toString())?.let(LocalDate::parse) ?: LocalDate.now()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            _binding?.etDate?.setText(LocalDate.of(year, month + 1, day).format(displayDate))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun saveExpense() {
        if (saving || !loaded) return
        listOf(binding.tilCategory, binding.tilConcept, binding.tilAmount, binding.tilDate).forEach { it.error = null }
        val category = binding.actCategory.text?.toString()?.trim().orEmpty()
        val concept = binding.etConcept.text?.toString()?.trim().orEmpty()
        val amount = FormValidation.amount(binding.etAmount.text.toString())
        val date = FormValidation.date(binding.etDate.text.toString())
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        when {
            concept.isBlank() -> { binding.tilConcept.error = getString(R.string.form_required); return }
            amount == null || amount <= 0 || amount > 99_999_999.99 -> { binding.tilAmount.error = getString(R.string.expense_amount_invalid); return }
            date == null -> { binding.tilDate.error = getString(R.string.form_invalid_date); return }
            LocalDate.parse(date).isAfter(LocalDate.now()) -> { binding.tilDate.error = getString(R.string.expense_date_future); return }
            category !in categories -> { binding.tilCategory.error = getString(R.string.form_invalid_option); return }
        }
        val vehicleId = capturedVehicleId ?: return
        val expense = Expense(id = currentExpense?.id ?: 0, vehicleId = vehicleId, category = category,
            concept = concept, amount = amount, date = date, description = description)
        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                db.withTransaction {
                    checkNotNull(db.vehicleDao().getById(vehicleId))
                    if (expense.id == 0) db.expenseDao().insert(expense)
                    else {
                        checkNotNull(db.expenseDao().getById(expense.id))
                        db.expenseDao().update(expense)
                    }
                }
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

    private fun showDetails(show: Boolean) {
        detailsVisible = show
        binding.containerDetails.isVisible = show
        binding.btnDetails.setText(if (show) R.string.ve_hide_details else R.string.ve_show_details)
    }

    private fun setSaving(value: Boolean) {
        saving = value
        isCancelable = !value
        _binding?.let {
            it.btnSave.isEnabled = !value
            it.btnCancel.isEnabled = !value
            it.btnDetails.isEnabled = !value
            listOf(it.actCategory, it.etConcept, it.etAmount, it.etDate, it.etDescription).forEach { field -> field.isEnabled = !value }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        capturedVehicleId?.let { outState.putInt(STATE_VEHICLE, it) }
        outState.putBoolean(STATE_DETAILS, detailsVisible)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_EXPENSE_ID = "expense_id"
        private const val STATE_VEHICLE = "expense_vehicle_id"
        private const val STATE_DETAILS = "expense_details"
        fun newInstance(expenseId: Int? = null) = AddExpenseDialogFragment().apply {
            arguments = Bundle().apply { expenseId?.let { putInt(ARG_EXPENSE_ID, it) } }
        }
    }
}
