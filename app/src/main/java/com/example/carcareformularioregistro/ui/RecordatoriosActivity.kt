package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.ReminderAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.ActivityRecordatoriosBinding
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecordatoriosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordatoriosBinding
    private lateinit var adapter: ReminderAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private var reminders: List<Reminder> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRecordatoriosBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
        setupRecyclerView()
        setupListeners()

        observeData()
    }

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onToggle = { reminder, isEnabled ->
                lifecycleScope.launch {
                    try {
                        val updated = reminder.copy(enabled = isEnabled)
                        db.reminderDao().update(updated)
                        if (!NotificationHelper.scheduleReminder(applicationContext, updated)) {
                            Snackbar.make(binding.root, R.string.reminder_alarm_error, Snackbar.LENGTH_LONG).show()
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        adapter.updateData(reminders)
                        Snackbar.make(binding.root, R.string.form_save_error, Snackbar.LENGTH_LONG).show()
                    }
                }
            },
            onDelete = { reminder ->
                confirmDelete(reminder)
            }
        )
        binding.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.rvReminders.adapter = adapter
    }

    private fun confirmDelete(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar Recordatorio")
            .setMessage("¿Deseas eliminar '${reminder.title}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        db.reminderDao().delete(reminder)
                        NotificationHelper.cancelReminderAlarm(applicationContext, reminder.id)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Snackbar.make(binding.root, R.string.form_delete_error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        val openAddReminder = {
            AddReminderDialogFragment().show(supportFragmentManager, "AddReminderDialog")
        }

        binding.btnAddReminder.setOnClickListener { openAddReminder() }
        binding.fabAddReminder.setOnClickListener { openAddReminder() }
    }

    private fun observeData() {
        lifecycleScope.launch {
            db.reminderDao().getAllRemindersFlow().collectLatest { list ->
                reminders = list
                adapter.updateData(list)

                if (list.isEmpty()) {
                    binding.containerEmptyRecordatorios.visibility = View.VISIBLE
                    binding.rvReminders.visibility = View.GONE
                } else {
                    binding.containerEmptyRecordatorios.visibility = View.GONE
                    binding.rvReminders.visibility = View.VISIBLE
                }
            }
        }
    }
}
