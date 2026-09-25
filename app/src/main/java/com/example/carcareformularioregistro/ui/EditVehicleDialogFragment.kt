package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.databinding.DialogEditVehicleBinding
import com.example.carcareformularioregistro.utils.FormValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate

class EditVehicleDialogFragment : DialogFragment() {
    private var _binding: DialogEditVehicleBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private var currentVehicle: Vehicle? = null
    private var busy = true
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEditVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadVehicle(savedInstanceState == null)
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveVehicle() }
    }

    private fun loadVehicle(fillFields: Boolean) {
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                currentVehicle = db.vehicleDao().getPrimaryVehicle()
                if (fillFields) currentVehicle?.let {
                    binding.etName.setText(it.name)
                    binding.etBrand.setText(it.brand)
                    binding.etModel.setText(it.model)
                    binding.etYear.setText(it.year.toString())
                    binding.etMileage.setText(it.mileage.toString())
                    binding.etPlates.setText(it.plates)
                }
                loaded = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.form_load_error, Toast.LENGTH_LONG).show() }
                dismiss()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun saveVehicle() {
        if (busy || !loaded) return
        listOf(binding.tilName, binding.tilBrand, binding.tilModel, binding.tilYear,
            binding.tilMileage, binding.tilPlates).forEach { it.error = null }
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val brand = binding.etBrand.text?.toString()?.trim().orEmpty()
        val model = binding.etModel.text?.toString()?.trim().orEmpty()
        val year = FormValidation.mileage(binding.etYear.text.toString())
        val mileage = FormValidation.mileage(binding.etMileage.text.toString())
        val plates = binding.etPlates.text?.toString()?.trim().orEmpty()
        val maxYear = LocalDate.now().year + 1
        when {
            name.isBlank() -> { binding.tilName.error = getString(R.string.form_required); return }
            brand.isBlank() -> { binding.tilBrand.error = getString(R.string.form_required); return }
            model.isBlank() -> { binding.tilModel.error = getString(R.string.form_required); return }
            year == null || year !in 1886..maxYear -> { binding.tilYear.error = getString(R.string.form_invalid_year, maxYear); return }
            mileage == null -> { binding.tilMileage.error = getString(R.string.form_invalid_mileage); return }
        }
        val vehicle = Vehicle(id = currentVehicle?.id ?: 0, name = name, brand = brand,
            model = model, year = year, mileage = mileage, plates = plates,
            photoUri = currentVehicle?.photoUri)
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (currentVehicle == null) db.vehicleDao().insertVehicle(vehicle)
                else db.vehicleDao().updateVehicle(vehicle)
                Toast.makeText(requireContext(), R.string.vehicle_saved, Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.form_save_error, Toast.LENGTH_LONG).show() }
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        isCancelable = !value
        _binding?.let { form ->
            form.btnSave.isEnabled = !value
            form.btnCancel.isEnabled = !value
            listOf(form.etName, form.etBrand, form.etModel, form.etYear, form.etMileage, form.etPlates)
                .forEach { it.isEnabled = !value }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
