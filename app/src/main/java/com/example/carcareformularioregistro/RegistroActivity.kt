package com.example.carcareformularioregistro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.User
import com.example.carcareformularioregistro.databinding.ActivityRegistroBinding
import com.example.carcareformularioregistro.utils.LocalSession
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The original master registration form, now connected to the local CarCare application. */
class RegistroActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegistroBinding
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private var existingUser: User? = null
    private var profileLoaded = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegistroBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            insets
        }
        binding.btnGuardar.setOnClickListener {
            if (profileLoaded) guardarUsuario() else loadProfile(savedInstanceState != null)
        }
        binding.btnContinuarPerfil.setOnClickListener { openApplication() }
        loadProfile(savedInstanceState != null)
    }

    private fun loadProfile(restoringForm: Boolean) {
        if (loading) return
        loading = true
        setBusy(true)
        lifecycleScope.launch {
            try {
                existingUser = database.userDao().getPrimaryUser()
                profileLoaded = true
                if (existingUser != null && !LocalSession.isClosed(this@RegistroActivity)) {
                    openApplication()
                    return@launch
                }
                existingUser?.let { user ->
                    if (!restoringForm) {
                        binding.etNombre.setText(user.nombre)
                        binding.etApellidos.setText(user.apellidos)
                        binding.etDireccion.setText(user.direccion)
                        binding.etTelefono.setText(user.telefono)
                    }
                }
                binding.btnContinuarPerfil.isVisible = existingUser != null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.error_cargar_perfil, Snackbar.LENGTH_LONG).show()
            } finally {
                loading = false
                setBusy(false)
            }
        }
    }

    private fun guardarUsuario() {
        val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
        val apellidos = binding.etApellidos.text?.toString()?.trim().orEmpty()
        val direccion = binding.etDireccion.text?.toString()?.trim().orEmpty()
        val telefono = binding.etTelefono.text?.toString()?.trim().orEmpty()
        val fields = listOf(
            binding.tilNombre to nombre,
            binding.tilApellidos to apellidos,
            binding.tilDireccion to direccion,
            binding.tilTelefono to telefono,
        )
        fields.forEach { (layout, _) -> layout.error = null }
        val missing = fields.filter { (_, value) -> value.isBlank() }
        if (missing.isNotEmpty()) {
            missing.forEach { (layout, _) -> layout.error = getString(R.string.campo_obligatorio) }
            missing.first().first.editText?.requestFocus()
            return
        }
        ocultarTeclado()
        setBusy(true)
        lifecycleScope.launch {
            try {
                val user = existingUser?.copy(
                    nombre = nombre, apellidos = apellidos, direccion = direccion, telefono = telefono,
                ) ?: User(nombre = nombre, apellidos = apellidos, direccion = direccion, telefono = telefono)
                if (existingUser == null) database.userDao().insertar(user)
                else database.userDao().actualizar(user)
                openApplication()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.error_guardar_usuario, Snackbar.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnGuardar.isEnabled = !busy
        binding.btnContinuarPerfil.isEnabled = !busy
        listOf(binding.etNombre, binding.etApellidos, binding.etDireccion, binding.etTelefono)
            .forEach { it.isEnabled = !busy }
        binding.btnGuardar.setText(when {
            busy -> R.string.guardando_usuario
            !profileLoaded -> R.string.reintentar_cargar_perfil
            existingUser != null -> R.string.actualizar_perfil_continuar
            else -> R.string.guardar_registro
        })
    }

    private fun openApplication() {
        ocultarTeclado()
        LocalSession.resume(this)
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun ocultarTeclado() {
        currentFocus?.let { view ->
            val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
