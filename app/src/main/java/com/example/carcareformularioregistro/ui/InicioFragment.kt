package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.*
import com.example.carcareformularioregistro.databinding.FragmentInicioBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import com.example.carcareformularioregistro.utils.UpcomingReview
import com.example.carcareformularioregistro.utils.UpcomingReviews
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class InicioFragment : Fragment() {
    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!
    private val db by lazy { AppDatabase.getInstance(requireContext()) }
    private var vehicleId: Int? = null
    private var next: UpcomingReview? = null
    private data class Snapshot(val vehicle: Vehicle?, val maintenance: List<Maintenance> = emptyList(),
        val expenses: List<Expense> = emptyList(), val reminders: List<Reminder> = emptyList())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnVehicles.setOnClickListener { startActivity(Intent(requireContext(), VehiclesActivity::class.java)) }
        binding.btnMileage.setOnClickListener { vehicleId?.let {
            MileageDialogFragment.newInstance(it).show(parentFragmentManager, "MileageDialog")
        } }
        binding.btnReminders.setOnClickListener { openReminders() }
        binding.btnAssistant.setOnClickListener { guidance(GuidanceActivity.MODE_ASSISTANT) }
        binding.btnGuide.setOnClickListener { guidance(GuidanceActivity.MODE_GUIDE) }
        binding.btnViewServiceDetails.setOnClickListener {
            next?.maintenanceId?.let {
                startActivity(Intent(requireContext(), AddMaintenanceActivity::class.java).putExtra("maintenance_id", it))
            } ?: openReminders()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                VehicleRepository.getInstance(requireContext()).selectedVehicleFlow.flatMapLatest { vehicle ->
                    if (vehicle == null) flowOf(Snapshot(null)) else combine(
                        db.maintenanceDao().getForVehicleFlow(vehicle.id), db.expenseDao().getForVehicleFlow(vehicle.id),
                        db.reminderDao().getForVehicleFlow(vehicle.id)
                    ) { maintenance, expenses, reminders -> Snapshot(vehicle, maintenance, expenses, reminders) }
                        .onStart { emit(Snapshot(vehicle)) }
                }.catch { Snackbar.make(binding.root, R.string.core_load_error, Snackbar.LENGTH_LONG).show() }
                    .collect(::render)
            }
        }
    }

    private fun render(data: Snapshot) {
        val vehicle = data.vehicle
        vehicleId = vehicle?.id
        binding.tvVehicleName.text = vehicle?.displayName ?: getString(R.string.home_no_vehicle)
        binding.tvVehicleDetails.text = vehicle?.let {
            getString(R.string.home_mileage_details, NumberFormat.getIntegerInstance().format(it.mileage),
                if (it.isPrimary) " · Principal" else "")
        } ?: getString(R.string.home_no_vehicle_details)
        binding.btnMileage.isVisible = vehicle != null
        next = vehicle?.let { UpcomingReviews.next(data.maintenance, data.reminders, it.mileage) }
        binding.tvNextServiceTitle.text = next?.title ?: getString(R.string.home_no_actions)
        binding.tvNextServiceRemaining.text = next?.details ?: getString(R.string.home_no_actions_details)
        binding.btnViewServiceDetails.isVisible = next != null
        binding.tvMonthlySummary.text = getString(R.string.home_monthly_summary,
            data.maintenance.count { it.status == Maintenance.STATUS_REALIZADO },
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
                .format(StatisticsCalculator.expenses(data.expenses).monthlyTotal))
    }

    private fun openReminders() = startActivity(Intent(requireContext(), RecordatoriosActivity::class.java))
    private fun guidance(mode: String) = startActivity(Intent(requireContext(), GuidanceActivity::class.java)
        .putExtra(GuidanceActivity.EXTRA_MODE, mode))

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
