package com.example.carcareformularioregistro.ui

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.BackupArchive
import com.example.carcareformularioregistro.databinding.ActivityBackupBinding
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReceiptStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    data class State(val busy: Boolean = false, val message: String = "", val preview: BackupArchive.Preview? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val archive = BackupArchive(app)
    private val context get() = getApplication<Application>()

    fun export(uri: Uri) = run {
        val temp = File.createTempFile("carcare-export-", ".zip", context.cacheDir)
        try {
            temp.outputStream().use { archive.export(it) }
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { output ->
                temp.inputStream().use { ReceiptStore.copyLimited(it, output, BackupArchive.MAX_ARCHIVE) }
            }
            State(message = context.getString(R.string.backup_done))
        } finally { temp.delete() }
    }
    fun inspect(uri: Uri) = run {
        mutable.value.preview?.file?.delete()
        val preview = requireNotNull(context.contentResolver.openInputStream(uri)).use(archive::inspect)
        if (archive.alreadyImported(preview)) {
            preview.file.delete()
            State(message = "Este archivo ya fue restaurado. No se añadieron copias.")
        } else State(preview = preview)
    }
    fun cancelPreview() {
        if (mutable.value.busy) return
        mutable.value.preview?.file?.delete()
        mutable.value = State()
    }
    fun restore() {
        val preview = mutable.value.preview ?: return
        run {
            try {
                val restored = archive.restore(preview)
                // The database is committed. Notification setup is best effort, never a reason to import twice.
                var alarmError = false
                restored.reminderIds.forEach { id ->
                    try {
                        val reminder = com.example.carcareformularioregistro.data.AppDatabase.getInstance(context).reminderDao().getById(id)
                        if (reminder != null && !NotificationHelper.scheduleReminder(context, reminder)) alarmError = true
                    } catch (_: Exception) { alarmError = true }
                }
                State(message = context.getString(R.string.backup_restored) +
                    if (alarmError) " No se pudieron programar todos los avisos; revisa Recordatorios." else "")
            } finally { preview.file.delete() }
        }
    }
    private fun run(action: suspend () -> State) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = "Procesando…")
        viewModelScope.launch {
            try { mutable.value = withContext(Dispatchers.IO) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.value.preview?.file?.delete()
                mutable.value = State(message = context.getString(R.string.backup_error))
            }
        }
    }
    override fun onCleared() {
        // A restore owns its temporary archive until its transaction finishes.
        if (!mutable.value.busy) mutable.value.preview?.file?.delete()
        super.onCleared()
    }
}

class BackupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBackupBinding
    private lateinit var model: BackupViewModel
    private var confirmation: AlertDialog? = null
    private val create = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(model::export)
    }
    private val open = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::inspect) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        model = ViewModelProvider(this)[BackupViewModel::class.java]
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.btnBack.setOnClickListener { if (!model.state.value.busy) finish() }
        binding.btnExport.setOnClickListener { create.launch("CarCare-${LocalDate.now()}.zip") }
        binding.btnImport.setOnClickListener { open.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!model.state.value.busy) finish() }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.btnExport.isEnabled = !state.busy && state.preview == null
                    binding.btnImport.isEnabled = !state.busy && state.preview == null
                    binding.btnBack.isEnabled = !state.busy
                    binding.tvStatus.text = state.message
                    if (!state.busy && state.preview != null && confirmation?.isShowing != true) {
                        confirmation = AlertDialog.Builder(this@BackupActivity).setTitle("Restaurar estos datos")
                            .setMessage(state.preview.summary + "\n\n" + getString(R.string.backup_merge))
                            .setPositiveButton("Restaurar") { _, _ -> model.restore() }
                            .setNegativeButton(R.string.cancelar) { _, _ -> model.cancelPreview() }
                            .setOnCancelListener { model.cancelPreview() }.show()
                    }
                }
            }
        }
    }
    override fun onDestroy() { confirmation?.dismiss(); super.onDestroy() }
}
