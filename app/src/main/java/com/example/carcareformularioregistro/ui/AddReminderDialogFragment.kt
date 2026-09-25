package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.DialogAddReminderBinding
import com.example.carcareformularioregistro.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddReminderDialogFragment : DialogFragment() {

    private var _binding: DialogAddReminderBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val priorities = arrayOf(
            Reminder.PRIORITY_ALTA,
            Reminder.PRIORITY_MEDIA,
            Reminder.PRIORITY_BAJA
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, priorities)
        binding.actPriority.setAdapter(adapter)

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveReminder() }
    }

    private fun saveReminder() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val dueDate = binding.etDueDate.text?.toString()?.trim().orEmpty()
        val priority = binding.actPriority.text?.toString()?.trim().orEmpty()

        if (title.isBlank()) {
            binding.tilTitle.error = "Escribe un título para el recordatorio"
            return
        }

        lifecycleScope.launch {
            val primaryVehicle = withContext(Dispatchers.IO) {
                db.vehicleDao().getPrimaryVehicle()
            }
            val vehicleId = primaryVehicle?.id ?: 1

            val reminder = Reminder(
                vehicleId = vehicleId,
                title = title,
                description = description,
                dueDate = dueDate.ifBlank { "2026-10-30" },
                dueMileage = primaryVehicle?.mileage ?: 85000,
                priority = priority.ifBlank { Reminder.PRIORITY_MEDIA },
                enabled = true
            )

            val newId = withContext(Dispatchers.IO) {
                db.reminderDao().insert(reminder).toInt()
            }

            // Schedule local notification alarm
            NotificationHelper.scheduleReminderAlarm(
                context = requireContext().applicationContext,
                reminderId = newId,
                title = title,
                triggerAtMillis = System.currentTimeMillis() + 10_000 // Trigger in 10 seconds for demo
            )

            Toast.makeText(requireContext(), "Recordatorio creado correctamente", Toast.LENGTH_SHORT).show()
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
