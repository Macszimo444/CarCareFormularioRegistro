package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.RegistroActivity
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.FragmentPerfilBinding
import com.example.carcareformularioregistro.utils.LocalSession
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerfilFragment : Fragment() {
    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext()) }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(requireContext(), RegistroActivity::class.java).putExtra(RegistroActivity.EXTRA_EDIT_PROFILE, true))
        }
        binding.btnVehicles.setOnClickListener { startActivity(Intent(requireContext(), VehiclesActivity::class.java)) }
        binding.optReminders.setOnClickListener { startActivity(Intent(requireContext(), RecordatoriosActivity::class.java)) }
        binding.optHistory.setOnClickListener { startActivity(Intent(requireContext(), HistorialActivity::class.java)) }
        binding.optAssistant.setOnClickListener { guidance(GuidanceActivity.MODE_ASSISTANT) }
        binding.optGuide.setOnClickListener { guidance(GuidanceActivity.MODE_GUIDE) }
        binding.optHelp.setOnClickListener { guidance(GuidanceActivity.MODE_HELP) }
        binding.optPrivacy.setOnClickListener { guidance(GuidanceActivity.MODE_PRIVACY) }
        binding.btnLogout.setOnClickListener { leaveProfile() }
        binding.btnDeleteData.setOnClickListener { confirmDeleteData() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    db.userDao().getPrimaryUserFlow().catch { showLoadError() }.collect { user ->
                        user?.let {
                            binding.tvUserName.text = "${it.nombre} ${it.apellidos}".trim()
                            binding.tvUserContact.text = it.telefono.ifBlank { getString(R.string.perfil_local_contacto) }
                            binding.tvUserInitials.text = "${it.nombre.firstOrNull() ?: ""}${it.apellidos.firstOrNull() ?: ""}".uppercase()
                        }
                    }
                }
                launch {
                    VehicleRepository.getInstance(requireContext()).selectedVehicleFlow.catch { showLoadError() }.collect {
                        binding.tvProfileVehicleName.text = it?.displayName ?: getString(R.string.sin_vehiculo_perfil)
                    }
                }
            }
        }
    }

    private fun guidance(mode: String) = startActivity(Intent(requireContext(), GuidanceActivity::class.java)
        .putExtra(GuidanceActivity.EXTRA_MODE, mode))

    private fun leaveProfile() {
        LocalSession.close(requireContext())
        startActivity(Intent(requireContext(), RegistroActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        requireActivity().finish()
    }

    private fun showLoadError() {
        _binding?.let { Snackbar.make(it.root, R.string.core_load_error, Snackbar.LENGTH_LONG).show() }
    }

    private fun confirmDeleteData() {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.profile_delete_title)
            .setMessage(R.string.profile_delete_message).setNegativeButton(R.string.core_cancel, null)
            .setPositiveButton(R.string.profile_delete_confirm) { _, _ -> deleteData() }.show()
    }

    private fun deleteData() {
        binding.btnDeleteData.isEnabled = false
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val reminders = db.reminderDao().getAllReminders()
                withContext(Dispatchers.IO) { db.clearAllTables() }
                reminders.forEach { NotificationHelper.cancelReminderAlarm(context, it.id) }
                context.getSharedPreferences("reminder_preferences", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                VehicleRepository.getInstance(context).selectVehicle(0)
                leaveProfile()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                _binding?.let {
                    it.btnDeleteData.isEnabled = true
                    Snackbar.make(it.root, R.string.profile_delete_error, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
