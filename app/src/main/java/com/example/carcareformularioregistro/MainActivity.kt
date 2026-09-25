package com.example.carcareformularioregistro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.ActivityMainBinding
import com.example.carcareformularioregistro.ui.GastosFragment
import com.example.carcareformularioregistro.ui.InicioFragment
import com.example.carcareformularioregistro.ui.MantenimientoFragment
import com.example.carcareformularioregistro.ui.PerfilFragment
import com.example.carcareformularioregistro.utils.LocalSession
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            // The root already reserves the system bars; prevent Material navigation from adding them twice.
            WindowInsetsCompat.Builder(insets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .build()
        }
        enterWithLocalProfile(savedInstanceState == null)
    }

    private fun enterWithLocalProfile(showHome: Boolean) {
        binding.bottomNavigation.isEnabled = false
        lifecycleScope.launch {
            try {
                val user = AppDatabase.getInstance(applicationContext).userDao().getPrimaryUser()
                if (user == null || LocalSession.isClosed(this@MainActivity)) {
                    startActivity(Intent(this@MainActivity, RegistroActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                    return@launch
                }
                setupBottomNavigation()
                binding.bottomNavigation.isEnabled = true
                if (showHome) replaceFragment(InicioFragment())
                NotificationHelper.createNotificationChannel(this@MainActivity)
                requestNotificationPermission()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.error_cargar_perfil, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.reintentar_cargar_perfil) { enterWithLocalProfile(showHome) }
                    .show()
            }
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val fragment = when (item.itemId) {
                R.id.nav_inicio -> InicioFragment()
                R.id.nav_mantenimiento -> MantenimientoFragment()
                R.id.nav_gastos -> GastosFragment()
                R.id.nav_perfil -> PerfilFragment()
                else -> return@setOnItemSelectedListener false
            }
            replaceFragment(fragment)
            true
        }
        binding.bottomNavigation.setOnItemReselectedListener { hideKeyboard() }
    }

    private fun replaceFragment(fragment: Fragment) {
        hideKeyboard()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}
