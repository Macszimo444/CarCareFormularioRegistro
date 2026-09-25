package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.adapter.ReminderAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.ActivityRecordatoriosBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class RecordatoriosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordatoriosBinding
    private lateinit var adapter: ReminderAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordatoriosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        setupListeners()

        observeData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onToggle = { reminder, isEnabled ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.reminderDao().update(reminder.copy(enabled = isEnabled))
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
                lifecycleScope.launch(Dispatchers.IO) {
                    db.reminderDao().delete(reminder)
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
