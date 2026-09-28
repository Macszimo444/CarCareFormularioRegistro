package com.example.carcareformularioregistro.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.databinding.DialogEditVehicleBinding
import com.example.carcareformularioregistro.utils.FormValidation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** An explicit id edits that vehicle; a missing id always creates a new one. */
class EditVehicleDialogFragment : DialogFragment() {
    private var _binding: DialogEditVehicleBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }
    private val vehicles by lazy { VehicleRepository.getInstance(requireContext().applicationContext) }
    private var currentVehicle: Vehicle? = null
    private var busy = true
    private var loaded = false
    private var detailsVisible = false
    private val vehicleId get() = arguments?.getInt(ARG_VEHICLE_ID, 0)?.takeIf { it > 0 }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogEditVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvTitle.setText(if (vehicleId == null) R.string.vehicles_add else R.string.vehicles_edit)
        detailsVisible = savedInstanceState?.getBoolean(STATE_DETAILS) ?: false
        showDetails(detailsVisible)
        binding.btnDetails.setOnClickListener { showDetails(!detailsVisible) }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnSave.setOnClickListener { saveVehicle() }
        loadVehicle(savedInstanceState == null)
    }

    private fun loadVehicle(fillFields: Boolean) {
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                currentVehicle = vehicleId?.let { db.vehicleDao().getById(it) }
                if (vehicleId != null && currentVehicle == null) {
                    Toast.makeText(requireContext(), R.string.vehicle_record_missing, Toast.LENGTH_LONG).show()
                    dismiss()
                    return@launch
                }
                if (fillFields) currentVehicle?.let {
                    binding.etName.setText(it.name)
                    binding.etBrand.setText(it.brand)
                    binding.etModel.setText(it.model)
                    binding.etYear.setText(it.year.toString())
                    binding.etMileage.setText(it.mileage.toString())
                    binding.etPlates.setText(it.plates)
                    binding.checkPrimary.isChecked = it.isPrimary
                }
                // A primary vehicle stays primary until another is chosen.
                binding.checkPrimary.isEnabled = currentVehicle?.isPrimary != true
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
        val plates = binding.etPlates.text?.toString()?.trim().orEmpty().uppercase(Locale.ROOT)
        val maxYear = LocalDate.now().year + 1
        when {
            brand.isBlank() -> { binding.tilBrand.error = getString(R.string.form_required); return }
            model.isBlank() -> { binding.tilModel.error = getString(R.string.form_required); return }
            year == null || year !in 1886..maxYear -> { binding.tilYear.error = getString(R.string.form_invalid_year, maxYear); return }
            mileage == null || mileage !in 0..9_999_999 -> { binding.tilMileage.error = getString(R.string.vehicle_odometer_error); return }
        }
        val vehicle = Vehicle(id = currentVehicle?.id ?: 0, name = name, brand = brand,
            model = model, year = year, mileage = mileage, plates = plates,
            photoUri = currentVehicle?.photoUri,
            isPrimary = currentVehicle?.isPrimary == true || binding.checkPrimary.isChecked)
        val previousMileage = currentVehicle?.mileage
        if (previousMileage != null && mileage < previousMileage) {
            val number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX"))
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.vehicle_mileage_correction_title)
                .setMessage(getString(R.string.vehicle_mileage_correction_message,
                    number.format(previousMileage), number.format(mileage)))
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.vehicle_correct) { _, _ -> persistVehicle(vehicle) }
                .show()
        } else persistVehicle(vehicle)
    }

    private fun persistVehicle(vehicle: Vehicle) {
        if (busy) return
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (vehicle.id != 0 && db.vehicleDao().getById(vehicle.id) == null) {
                    Toast.makeText(requireContext(), R.string.vehicle_record_missing, Toast.LENGTH_LONG).show()
                    dismiss()
                    return@launch
                }
                val id = vehicles.saveVehicle(vehicle, makePrimary = vehicle.isPrimary)
                if (vehicle.id == 0) vehicles.selectVehicle(id)
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

    private fun showDetails(show: Boolean) {
        detailsVisible = show
        binding.containerDetails.isVisible = show
        binding.btnDetails.setText(if (show) R.string.ve_hide_details else R.string.ve_show_details)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        isCancelable = !value
        _binding?.let { form ->
            form.btnSave.isEnabled = !value
            form.btnCancel.isEnabled = !value
            form.btnDetails.isEnabled = !value
            form.checkPrimary.isEnabled = !value && currentVehicle?.isPrimary != true
            listOf(form.etName, form.etBrand, form.etModel, form.etYear, form.etMileage, form.etPlates)
                .forEach { it.isEnabled = !value }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_DETAILS, detailsVisible)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout((resources.displayMetrics.widthPixels * 0.94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_VEHICLE_ID = "vehicle_id"
        private const val STATE_DETAILS = "vehicle_details"
        fun newInstance(vehicleId: Int? = null) = EditVehicleDialogFragment().apply {
            arguments = Bundle().apply { vehicleId?.let { putInt(ARG_VEHICLE_ID, it) } }
        }
    }
}
