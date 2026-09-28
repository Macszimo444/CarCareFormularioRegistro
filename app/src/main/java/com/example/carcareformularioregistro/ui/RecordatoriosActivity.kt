package com.example.carcareformularioregistro.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.ReminderAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.ActivityRecordatoriosBinding
import com.example.carcareformularioregistro.databinding.DialogReminderSettingsBinding
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReminderSettings
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class RecordatoriosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRecordatoriosBinding
    private lateinit var adapter: ReminderAdapter
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val vehicles by lazy { VehicleRepository.getInstance(applicationContext) }
    private val settings by lazy { ReminderSettings(applicationContext) }
    private var reminders: List<Reminder> = emptyList()
    private var mileage = 0
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Snackbar.make(binding.root, R.string.reminders_permission_hint, Snackbar.LENGTH_LONG)
            .setAction("Ajustes") { openSystemNotificationSettings() }.show()
        updatePermissionHint()
        reschedule()
    }

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
        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddReminder.setOnClickListener {
            if (supportFragmentManager.findFragmentByTag("AddReminderDialog") == null)
                AddReminderDialogFragment().show(supportFragmentManager, "AddReminderDialog")
        }
        binding.btnNotificationSettings.setOnClickListener { showSettings() }
        binding.tvPermissionHint.setOnClickListener { requestNotificationAccess() }
        lifecycleScope.launch {
            val fromNotification = intent.getIntExtra("vehicle_id", 0)
            if (savedInstanceState == null && fromNotification > 0 && db.vehicleDao().getById(fromNotification) != null)
                vehicles.selectVehicle(fromNotification)
            observeData()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionHint()
        reschedule()
    }

    private fun updatePermissionHint() {
        binding.tvPermissionHint.isVisible = settings.enabled && !NotificationHelper.notificationsAllowed(this)
    }

    private fun requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else openSystemNotificationSettings()
    }

    private fun openSystemNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun reschedule() {
        lifecycleScope.launch {
            try { NotificationHelper.rescheduleAll(applicationContext) }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { Snackbar.make(binding.root, R.string.reminder_alarm_error, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun showSettings() {
        val panel = DialogReminderSettingsBinding.inflate(layoutInflater)
        panel.switchNotifications.isChecked = settings.enabled
        panel.switchNotifications.setOnCheckedChangeListener { _, checked ->
            settings.enabled = checked
            if (checked && !NotificationHelper.notificationsAllowed(this)) requestNotificationAccess()
            updatePermissionHint()
            reschedule()
        }
        panel.btnSystemNotifications.setOnClickListener {
            openSystemNotificationSettings()
        }
        AlertDialog.Builder(this).setTitle("Configurar avisos").setView(panel.root)
            .setPositiveButton("Listo", null).show()
    }

    private fun setupRecyclerView() {
        adapter = ReminderAdapter(
            onToggle = { reminder, enabled ->
                lifecycleScope.launch {
                    try {
                        // Re-read to preserve edits if a notification or another screen changed the row.
                        val current = db.reminderDao().getById(reminder.id) ?: return@launch
                        val updated = current.copy(enabled = enabled)
                        db.reminderDao().update(updated)
                        if (!NotificationHelper.scheduleReminder(applicationContext, updated))
                            Snackbar.make(binding.root, R.string.reminder_alarm_error, Snackbar.LENGTH_LONG).show()
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) {
                        adapter.updateData(reminders, mileage)
                        Snackbar.make(binding.root, R.string.form_save_error, Snackbar.LENGTH_LONG).show()
                    }
                }
            },
            onComplete = { reminder ->
                startActivity(Intent(this, AddMaintenanceActivity::class.java).putExtra("complete_reminder_id", reminder.id))
            },
            onDelete = { confirmDelete(it) },
            onEdit = { reminder ->
                if (supportFragmentManager.findFragmentByTag("AddReminderDialog") == null)
                    AddReminderDialogFragment.edit(reminder.id).show(supportFragmentManager, "AddReminderDialog")
            }
        )
        binding.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.rvReminders.adapter = adapter
    }

    private fun confirmDelete(reminder: Reminder) {
        AlertDialog.Builder(this).setTitle(R.string.reminder_delete_title)
            .setMessage(getString(R.string.reminder_delete_message, reminder.title))
            .setPositiveButton(R.string.reminder_delete_action) { _, _ ->
                lifecycleScope.launch {
                    try {
                        db.reminderDao().delete(reminder)
                        NotificationHelper.cancelReminderAlarm(applicationContext, reminder.id)
                        settings.forget(reminder.id)
                    } catch (error: CancellationException) { throw error }
                    catch (_: Exception) { Snackbar.make(binding.root, R.string.form_delete_error, Snackbar.LENGTH_LONG).show() }
                }
            }.setNegativeButton(R.string.cancelar, null).show()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeData() {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            vehicles.selectedVehicleFlow.flatMapLatest { vehicle ->
                mileage = vehicle?.mileage ?: 0
                binding.tvVehicleContext.text = vehicle?.let { getString(R.string.service_vehicle_context, it.displayName) }
                    ?: getString(R.string.service_vehicle_missing)
                binding.btnAddReminder.isEnabled = vehicle != null
                (vehicle?.let { db.reminderDao().getForVehicleFlow(it.id) } ?: flowOf(emptyList()))
                    .onStart { emit(emptyList()) }
            }.catch { error ->
                if (error is CancellationException) throw error
                binding.btnAddReminder.isEnabled = false
                Snackbar.make(binding.root, R.string.form_load_error, Snackbar.LENGTH_LONG).show()
                emit(emptyList())
            }.collectLatest { list ->
                val targetId = intent.getIntExtra("open_reminder_id", 0)
                val target = list.firstOrNull { it.id == targetId }
                if (target != null) {
                    intent.removeExtra("open_reminder_id")
                    AlertDialog.Builder(this@RecordatoriosActivity).setTitle(target.title)
                        .setMessage(listOf(target.description, target.dueDate,
                            if (target.dueDate.isNotBlank()) com.example.carcareformularioregistro.utils.ReminderSchedule.label(target) else "",
                            target.dueMileage.takeIf { it > 0 }?.let { "$it km" }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n"))
                        .setPositiveButton(R.string.complete_review) { _, _ ->
                            startActivity(Intent(this@RecordatoriosActivity, AddMaintenanceActivity::class.java)
                                .putExtra("complete_reminder_id", target.id))
                        }.setNeutralButton(R.string.reminder_edit_action) { _, _ ->
                            AddReminderDialogFragment.edit(target.id).show(supportFragmentManager, "AddReminderDialog")
                        }.setNegativeButton(R.string.cancelar, null).show()
                }
                reminders = list
                adapter.updateData(list, mileage)
                binding.containerEmptyRecordatorios.isVisible = list.isEmpty()
                binding.rvReminders.isVisible = list.isNotEmpty()
            }
        }
    }
}
