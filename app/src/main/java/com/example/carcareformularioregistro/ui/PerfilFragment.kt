package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.FragmentPerfilBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        binding.btnEditVehicle.setOnClickListener {
            EditVehicleDialogFragment().show(parentFragmentManager, "EditVehicleDialog")
        }

        binding.optReminders.setOnClickListener {
            startActivity(Intent(requireContext(), RecordatoriosActivity::class.java))
        }

        binding.optHistory.setOnClickListener {
            startActivity(Intent(requireContext(), HistorialActivity::class.java))
        }

        binding.optHelp.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "CarCare Soporte: Contacta a soporte@carcare.app",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.optAbout.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "CarCare v1.0 • Desarrollo de Aplicaciones Móviles",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.btnLogout.setOnClickListener {
            Snackbar.make(binding.root, "Has cerrado sesión correctamente", Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.userDao().getPrimaryUserFlow().collectLatest { user ->
                user?.let {
                    binding.tvUserName.text = "${it.nombre} ${it.apellidos}"
                    binding.tvUserContact.text = "${it.email} • ${it.telefono}"

                    val initials = "${it.nombre.firstOrNull() ?: "U"}${it.apellidos.firstOrNull() ?: ""}"
                    binding.tvUserInitials.text = initials.uppercase()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.vehicleDao().getPrimaryVehicleFlow().collectLatest { vehicle ->
                vehicle?.let {
                    binding.tvProfileVehicleName.text = it.name
                    binding.tvProfileVehicleDetails.text =
                        String.format("%,d km • Placas: %s", it.mileage, it.plates)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
