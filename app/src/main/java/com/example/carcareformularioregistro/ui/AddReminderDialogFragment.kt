package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.app.TimePickerDialog
import com.example.carcareformularioregistro.utils.ReminderSchedule
import java.time.LocalTime
import java.time.format.DateTimeFormatter
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
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.DialogAddReminderBinding
import com.example.carcareformularioregistro.utils.FormDatePicker
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
    private var loaded = false
    private var targetMode = ReviewTargetMode.DATE
    private var selectedTime = LocalTime.of(9, 0)
    private var vehicleId = 0
    private var original: Reminder? = null
    private val editingId get() = arguments?.getInt("reminder_id", 0) ?: 0
    private val priorities = listOf(Reminder.PRIORITY_ALTA, Reminder.PRIORITY_MEDIA, Reminder.PRIORITY_BAJA)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        FormDatePicker.attach(binding.tilDueDate)
        selectedTime = ReminderSchedule.time(savedInstanceState?.getString("chosen_time")) ?: LocalTime.of(9, 0)
        binding.switchAllDay.isChecked = savedInstanceState?.getBoolean("all_day") ?: true
        binding.switchAllDay.setOnCheckedChangeListener { _, _ -> renderSchedule() }
        binding.btnDueTime.setOnClickListener {
            TimePickerDialog(requireContext(), { _, hour, minute ->
                selectedTime = LocalTime.of(hour, minute)
                renderSchedule()
            }, selectedTime.hour, selectedTime.minute, true).apply {
                setTitle(R.string.reminder_pick_time_title)
            }.show()
        }
        vehicleId = savedInstanceState?.getInt("vehicle_id") ?: 0
        binding.actPriority.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, priorities))
        binding.tvTitle.setText(if (editingId > 0) R.string.reminder_edit_title else R.string.reminder_new_title)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveReminder() }
        binding.btnTargetHelp.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(targetMode.label).setMessage(targetMode.help)
                .setPositiveButton("Entendido", null).show()
        }
        binding.actTargetMode.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line,
            ReviewTargetMode.entries.map { getString(it.label) }))
        binding.actTargetMode.setOnItemClickListener { _, _, position, _ -> showTargetMode(ReviewTargetMode.entries[position]) }
        showTargetMode(ReviewTargetMode.restore(savedInstanceState?.getString("target_mode")))
        setSaving(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val item = if (editingId > 0) db.reminderDao().getById(editingId) else null
                if (editingId > 0 && item == null) {
                    Toast.makeText(requireContext(), R.string.reminder_missing, Toast.LENGTH_LONG).show()
                    dismiss(); return@launch
                }
                original = item
                val vehicle = when {
                    item != null -> db.vehicleDao().getById(item.vehicleId)
                    vehicleId > 0 -> db.vehicleDao().getById(vehicleId)
                    else -> VehicleRepository.getInstance(requireContext()).getSelectedVehicle()
                }
                if (vehicle == null) {
                    Toast.makeText(requireContext(), R.string.service_vehicle_missing, Toast.LENGTH_LONG).show()
                    dismiss(); return@launch
                }
                vehicleId = vehicle.id
                binding.tvVehicleContext.text = getString(R.string.service_vehicle_context, vehicle.displayName)
                binding.tvLinkedHelp.isVisible = item?.maintenanceId != null
                if (savedInstanceState == null) {
                    binding.etTitle.setText(item?.title.orEmpty())
                    binding.etDescription.setText(item?.description.orEmpty())
                    binding.etDueDate.setText(item?.dueDate ?: LocalDate.now().toString())
                    binding.etDueMileage.setText(item?.dueMileage?.takeIf { it > 0 }?.toString().orEmpty())
                    binding.actPriority.setText(item?.priority ?: Reminder.PRIORITY_MEDIA, false)
                    selectedTime = ReminderSchedule.time(item?.dueTime) ?: LocalTime.of(9, 0)
                    binding.switchAllDay.isChecked = item?.dueTime == null
                    showTargetMode(ReviewTargetMode.fromTargets(item?.dueDate.orEmpty(), item?.dueMileage ?: 0))
                }
                loaded = true
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.form_load_error, Toast.LENGTH_LONG).show() }
                dismiss()
            } finally { setSaving(false) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("chosen_time", selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
        outState.putBoolean("all_day", binding.switchAllDay.isChecked)
        outState.putString("target_mode", targetMode.name)
        outState.putInt("vehicle_id", vehicleId)
        super.onSaveInstanceState(outState)
    }

    private fun renderSchedule() {
        binding.containerSchedule.isVisible = targetMode.usesDate
        binding.btnDueTime.isVisible = !binding.switchAllDay.isChecked
        binding.btnDueTime.text = getString(R.string.reminder_pick_time,
            selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
        binding.tvScheduleHelp.setText(if (binding.switchAllDay.isChecked) R.string.reminder_all_day_help else R.string.reminder_timed_help)
    }

    private fun showTargetMode(mode: ReviewTargetMode) {
        targetMode = mode
        binding.actTargetMode.setText(getString(mode.label), false)
        binding.tilDueDate.isVisible = mode.usesDate
        binding.tilDueMileage.isVisible = mode.usesMileage
        binding.tvTargetHelp.setText(mode.shortHelp)
        binding.tilDueDate.error = null
        binding.tilDueMileage.error = null
        renderSchedule()
    }

    private fun saveReminder() {
        if (saving || !loaded) return
        listOf(binding.tilTitle, binding.tilDueDate, binding.tilDueMileage, binding.tilPriority).forEach { it.error = null }
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val dateText = if (targetMode.usesDate) binding.etDueDate.text?.toString()?.trim().orEmpty() else ""
        val mileageText = if (targetMode.usesMileage) binding.etDueMileage.text?.toString()?.trim().orEmpty() else ""
        val dueDate = if (dateText.isBlank()) "" else FormValidation.date(dateText)
        val dueMileage = if (mileageText.isBlank()) 0 else FormValidation.mileage(mileageText)
        val priority = binding.actPriority.text?.toString()?.trim().orEmpty()
        when {
            title.isBlank() -> { binding.tilTitle.error = getString(R.string.form_required); return }
            dueDate == null -> { binding.tilDueDate.error = getString(R.string.form_invalid_date); return }
            dueDate.isNotEmpty() && LocalDate.parse(dueDate).year !in 1900..2100 -> {
                binding.tilDueDate.error = getString(R.string.service_date_range); return
            }
            dueMileage == null || dueMileage > 9_999_999 || (mileageText.isNotEmpty() && dueMileage == 0) -> {
                binding.tilDueMileage.error = getString(R.string.form_invalid_mileage); return
            }
            targetMode.usesDate && dueDate.isEmpty() -> { binding.tilDueDate.error = getString(R.string.form_required); return }
            targetMode.usesMileage && dueMileage == 0 -> { binding.tilDueMileage.error = getString(R.string.form_required); return }
            priority !in priorities -> { binding.tilPriority.error = getString(R.string.form_invalid_option); return }
        }
        setSaving(true)
        val app = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Validate the linked plan against its service before touching either row.
                val maintenance = original?.maintenanceId?.let { db.maintenanceDao().getById(it) }
                if (maintenance != null) {
                    if (dueDate.isNotEmpty() && dueDate <= maintenance.date) {
                        binding.tilDueDate.error = getString(R.string.service_next_date_invalid); return@launch
                    }
                    if (dueMileage > 0 && dueMileage <= maintenance.mileage) {
                        binding.tilDueMileage.error = getString(R.string.service_next_mileage_invalid); return@launch
                    }
                }
                val reminder = db.withTransaction {
                    check(db.vehicleDao().getById(vehicleId) != null)
                    val current = if (editingId > 0) db.reminderDao().getById(editingId) else null
                    check(editingId == 0 || current != null)
                    val linked = current?.maintenanceId?.let { db.maintenanceDao().getById(it) }
                    if (linked != null) {
                        require(dueDate.isEmpty() || dueDate > linked.date)
                        require(dueMileage == 0 || dueMileage > linked.mileage)
                        db.maintenanceDao().update(linked.copy(nextDate = dueDate, nextMileage = dueMileage))
                    }
                    val item = Reminder(id = editingId, vehicleId = vehicleId, title = title, description = description,
                        dueDate = dueDate, dueMileage = dueMileage, priority = priority,
                        enabled = current?.enabled ?: true, maintenanceId = linked?.id,
                        dueTime = if (targetMode.usesDate && !binding.switchAllDay.isChecked)
                            selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")) else null)
                    if (editingId > 0) { db.reminderDao().update(item); item }
                    else item.copy(id = db.reminderDao().insert(item).toInt())
                }
                val scheduled = NotificationHelper.scheduleReminder(app, reminder)
                Toast.makeText(app, if (scheduled) R.string.reminder_saved_updated else R.string.reminder_alarm_error,
                    Toast.LENGTH_LONG).show()
                dismiss()
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { Toast.makeText(app, R.string.form_save_error, Toast.LENGTH_LONG).show() }
            finally { setSaving(false) }
        }
    }

    private fun setSaving(value: Boolean) {
        saving = value
        isCancelable = !value
        _binding?.let {
            it.switchAllDay.isEnabled = !value
            it.btnDueTime.isEnabled = !value
            it.btnSave.isEnabled = !value
            it.btnCancel.isEnabled = !value
            listOf(it.etTitle, it.etDescription, it.etDueDate, it.etDueMileage, it.actPriority, it.actTargetMode).forEach { field -> field.isEnabled = !value }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(),
                (resources.displayMetrics.heightPixels * 0.88).toInt())
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    companion object {
        fun edit(reminderId: Int) = AddReminderDialogFragment().apply {
            arguments = Bundle().apply { putInt("reminder_id", reminderId) }
        }
    }
}
