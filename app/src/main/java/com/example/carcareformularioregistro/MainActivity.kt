package com.example.carcareformularioregistro

import android.content.Context
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var nombreLayout: TextInputLayout
    private lateinit var apellidosLayout: TextInputLayout
    private lateinit var direccionLayout: TextInputLayout
    private lateinit var telefonoLayout: TextInputLayout

    private lateinit var nombreInput: TextInputEditText
    private lateinit var apellidosInput: TextInputEditText
    private lateinit var direccionInput: TextInputEditText
    private lateinit var telefonoInput: TextInputEditText
    private lateinit var guardarButton: MaterialButton

    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        guardarButton.setOnClickListener { guardarUsuario() }
    }

    private fun bindViews() {
        nombreLayout = findViewById(R.id.tilNombre)
        apellidosLayout = findViewById(R.id.tilApellidos)
        direccionLayout = findViewById(R.id.tilDireccion)
        telefonoLayout = findViewById(R.id.tilTelefono)

        nombreInput = findViewById(R.id.etNombre)
        apellidosInput = findViewById(R.id.etApellidos)
        direccionInput = findViewById(R.id.etDireccion)
        telefonoInput = findViewById(R.id.etTelefono)
        guardarButton = findViewById(R.id.btnGuardar)
    }

    private fun guardarUsuario() {
        val nombre = nombreInput.text?.toString()?.trim().orEmpty()
        val apellidos = apellidosInput.text?.toString()?.trim().orEmpty()
        val direccion = direccionInput.text?.toString()?.trim().orEmpty()
        val telefono = telefonoInput.text?.toString()?.trim().orEmpty()

        if (!validarCampos(nombre, apellidos, direccion, telefono)) return

        ocultarTeclado()
        mostrarEstadoGuardando(true)

        val usuario = User(
            nombre = nombre,
            apellidos = apellidos,
            direccion = direccion,
            telefono = telefono,
        )

        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    database.userDao().insertar(usuario)
                }
            }.onSuccess {
                limpiarFormulario()
                Snackbar.make(
                    findViewById(R.id.main),
                    R.string.usuario_guardado,
                    Snackbar.LENGTH_LONG,
                ).show()
            }.onFailure {
                Snackbar.make(
                    findViewById(R.id.main),
                    R.string.error_guardar_usuario,
                    Snackbar.LENGTH_LONG,
                ).show()
            }

            mostrarEstadoGuardando(false)
        }
    }

    private fun validarCampos(
        nombre: String,
        apellidos: String,
        direccion: String,
        telefono: String,
    ): Boolean {
        val campos = listOf(
            nombreLayout to nombre,
            apellidosLayout to apellidos,
            direccionLayout to direccion,
            telefonoLayout to telefono,
        )

        campos.forEach { (layout, _) -> layout.error = null }
        val camposVacios = campos.filter { (_, valor) -> valor.isBlank() }

        if (camposVacios.isNotEmpty()) {
            camposVacios.forEach { (layout, _) ->
                layout.error = getString(R.string.campo_obligatorio)
            }
            camposVacios.first().first.editText?.requestFocus()
            return false
        }

        return true
    }

    private fun mostrarEstadoGuardando(guardando: Boolean) {
        guardarButton.isEnabled = !guardando
        guardarButton.setText(
            if (guardando) R.string.guardando_usuario else R.string.guardar_registro,
        )
    }

    private fun limpiarFormulario() {
        nombreInput.text?.clear()
        apellidosInput.text?.clear()
        direccionInput.text?.clear()
        telefonoInput.text?.clear()
        nombreInput.requestFocus()
    }

    private fun ocultarTeclado() {
        currentFocus?.let { view ->
            val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
