package com.example.carcareformularioregistro.ui

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.utils.FormValidation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MileageDialogFragment : DialogFragment() {
    private lateinit var field: TextInputEditText
    private lateinit var layout: TextInputLayout
    private var vehicle: Vehicle? = null
    private var savedText: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedText = savedInstanceState?.getString("reading")
        val ctx = requireContext()
        val padding = (20 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, 0) }
        content.addView(TextView(ctx).apply { setText(R.string.odometer_help); textSize = 14f })
        layout = TextInputLayout(ctx).apply { hint = "Kilometraje actual (km)" }
        field = TextInputEditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(7))
            id = ViewGroup.generateViewId()
        }
        layout.addView(field)
        content.addView(layout)
        return MaterialAlertDialogBuilder(ctx).setTitle(R.string.update_odometer).setView(content)
            .setNegativeButton(R.string.core_cancel, null).setPositiveButton(R.string.core_save, null).create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        lifecycleScope.launch {
            try {
                vehicle = AppDatabase.getInstance(requireContext()).vehicleDao().getById(requireArguments().getInt("vehicle_id"))
                if (vehicle == null) { dismiss(); return@launch }
                field.setText(savedText ?: vehicle!!.mileage.toString())
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { validateAndSave() }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { layout.error = getString(R.string.core_load_error) }
        }
    }

    private fun validateAndSave() {
        val reading = FormValidation.mileage(field.text?.toString().orEmpty())
        val current = vehicle ?: return
        if (reading == null || reading > 9_999_999) { layout.error = getString(R.string.odometer_invalid); return }
        layout.error = null
        if (reading < current.mileage) MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.odometer_correction_title).setMessage(R.string.odometer_correction_message)
            .setNegativeButton(R.string.core_cancel, null)
            .setPositiveButton(R.string.odometer_correction_confirm) { _, _ -> save(reading) }.show()
        else save(reading)
    }

    private fun save(reading: Int) {
        val button = (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val id = requireArguments().getInt("vehicle_id")
                val latest = AppDatabase.getInstance(requireContext()).vehicleDao().getById(id)
                    ?: error("Vehículo eliminado")
                VehicleRepository.getInstance(requireContext()).saveVehicle(latest.copy(mileage = reading))
                dismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { layout.error = getString(R.string.core_save_error); button.isEnabled = true }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("reading", field.text?.toString())
        super.onSaveInstanceState(outState)
    }
    companion object {
        fun newInstance(id: Int) = MileageDialogFragment().apply { arguments = Bundle().apply { putInt("vehicle_id", id) } }
    }
}
