package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.databinding.DialogEditVehicleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditVehicleDialogFragment : DialogFragment() {

    private var _binding: DialogEditVehicleBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private var currentVehicle: Vehicle? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadVehicle()

        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveVehicle() }
    }

    private fun loadVehicle() {
        lifecycleScope.launch {
            currentVehicle = withContext(Dispatchers.IO) {
                db.vehicleDao().getPrimaryVehicle()
            }
            currentVehicle?.let {
                binding.etName.setText(it.name)
                binding.etBrand.setText(it.brand)
                binding.etModel.setText(it.model)
                binding.etYear.setText(it.year.toString())
                binding.etMileage.setText(it.mileage.toString())
                binding.etPlates.setText(it.plates)
            }
        }
    }

    private fun saveVehicle() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val brand = binding.etBrand.text?.toString()?.trim().orEmpty()
        val model = binding.etModel.text?.toString()?.trim().orEmpty()
        val yearStr = binding.etYear.text?.toString()?.trim().orEmpty()
        val mileageStr = binding.etMileage.text?.toString()?.trim().orEmpty()
        val plates = binding.etPlates.text?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            binding.tilName.error = "Ingresa un nombre para tu vehículo"
            return
        }

        val year = yearStr.toIntOrNull() ?: 2020
        val mileage = mileageStr.toIntOrNull() ?: 0

        lifecycleScope.launch {
            val vehicleToSave = Vehicle(
                id = currentVehicle?.id ?: 0,
                name = name,
                brand = brand.ifBlank { "Ford" },
                model = model.ifBlank { "F-150" },
                year = year,
                mileage = mileage,
                plates = plates.ifBlank { "ABC-123" }
            )

            withContext(Dispatchers.IO) {
                if (currentVehicle != null) {
                    db.vehicleDao().updateVehicle(vehicleToSave)
                } else {
                    db.vehicleDao().insertVehicle(vehicleToSave)
                }
            }

            Toast.makeText(requireContext(), "Vehículo guardado correctamente", Toast.LENGTH_SHORT).show()
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
