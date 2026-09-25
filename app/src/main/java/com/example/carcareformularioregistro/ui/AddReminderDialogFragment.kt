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
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.DialogAddReminderBinding
import com.example.carcareformularioregistro.utils.FormValidation
import com.example.carcareformularioregistro.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddReminderDialogFragment : DialogFragment() {
    private var _binding: DialogAddReminderBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private var saving = false
    private val priorities = listOf(Reminder.PRIORITY_ALTA, Reminder.PRIORITY_MEDIA, Reminder.PRIORITY_BAJA)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.actPriority.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, priorities))
        if (savedInstanceState == null) binding.etDueDate.setText(LocalDate.now().toString())
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveReminder() }
    }

    private fun saveReminder() {
        if (saving) return
        listOf(binding.tilTitle, binding.tilDueDate, binding.tilPriority).forEach { it.error = null }
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val dueDate = FormValidation.date(binding.etDueDate.text.toString())
        val priority = binding.actPriority.text?.toString()?.trim().orEmpty()
        when {
            title.isBlank() -> { binding.tilTitle.error = getString(R.string.form_required); return }
            dueDate == null -> { binding.tilDueDate.error = getString(R.string.form_invalid_date); return }
            priority !in priorities -> { binding.tilPriority.error = getString(R.string.form_invalid_option); return }
        }
        setSaving(true)
        val appContext = requireContext().applicationContext
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
                val reminder = Reminder(vehicleId = vehicle.id, title = title, description = description,
                    dueDate = dueDate, dueMileage = vehicle.mileage, priority = priority, enabled = true)
                val newId = db.reminderDao().insert(reminder).toInt()
                val scheduled = NotificationHelper.scheduleReminder(appContext, reminder.copy(id = newId))
                Toast.makeText(requireContext(), if (scheduled) R.string.reminder_saved else R.string.reminder_alarm_error,
                    Toast.LENGTH_LONG).show()
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
