package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.databinding.FragmentInicioBinding
import com.example.carcareformularioregistro.utils.StatisticsCalculator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class InicioFragment : Fragment() {

    private var _binding: FragmentInicioBinding? = null
    private val binding get() = _binding!!

    private var nextMaintenanceId: Int? = null
    private val db by lazy { AppDatabase.getInstance(requireContext().applicationContext) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInicioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        observeData()
    }

    private fun setupListeners() {
        binding.cardVehicle.setOnClickListener {
            EditVehicleDialogFragment().show(parentFragmentManager, "EditVehicleDialog")
        }

        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(requireContext(), RecordatoriosActivity::class.java))
        }

        binding.btnViewAllReminders.setOnClickListener {
            startActivity(Intent(requireContext(), RecordatoriosActivity::class.java))
        }

        binding.btnQuickAddMaintenance.setOnClickListener {
            startActivity(Intent(requireContext(), AddMaintenanceActivity::class.java))
        }

        binding.btnQuickAddExpense.setOnClickListener {
            AddExpenseDialogFragment().show(parentFragmentManager, "AddExpenseDialog")
        }

        binding.btnQuickAddReminder.setOnClickListener {
            AddReminderDialogFragment().show(parentFragmentManager, "AddReminderDialog")
        }

        binding.btnViewServiceDetails.setOnClickListener {
            nextMaintenanceId?.let { id ->
                startActivity(Intent(requireContext(), AddMaintenanceActivity::class.java).apply {
                    putExtra("maintenance_id", id)
                })
            }
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.vehicleDao().getPrimaryVehicleFlow().collectLatest { vehicle ->
                if (vehicle != null) {
                    binding.tvVehicleName.text = vehicle.name
                    binding.tvVehicleDetails.text =
                        String.format("%,d km • Placas: %s", vehicle.mileage, vehicle.plates)
                } else {
                    binding.tvVehicleName.text = "Agregar Vehículo"
                    binding.tvVehicleDetails.text = "Toca aquí para registrar tu auto"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.maintenanceDao().getNextMaintenanceFlow().collectLatest { nextService ->
                nextMaintenanceId = nextService?.id
                binding.btnViewServiceDetails.isEnabled = nextService != null
                if (nextService != null) {
                    binding.tvNextServiceTitle.text = nextService.type
                    binding.tvNextServiceDate.text = "Fecha recomendada: ${nextService.nextDate}"
                    binding.tvNextServiceRemaining.text =
                        "Próximo a los ${nextService.nextMileage} km (${nextService.workshop})"
                    binding.progressNextService.progress = 65
                    binding.tvStatNextServiceDays.text = "Pendiente"
                } else {
                    binding.tvNextServiceTitle.text = "Sin mantenimientos pendientes"
                    binding.tvNextServiceRemaining.text = "Tu vehículo se encuentra al día"
                    binding.tvNextServiceDate.text = "No hay fechas requeridas"
                    binding.progressNextService.progress = 100
                    binding.tvStatNextServiceDays.text = "Al día"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.reminderDao().getAllRemindersFlow().collectLatest { reminders ->
                val active = reminders.filter { it.enabled }
                binding.vNotificationBadge.visibility =
                    if (active.isNotEmpty()) View.VISIBLE else View.GONE

                if (active.isNotEmpty()) {
                    binding.tvRemindersSummaryTitle.text = "${active.size} recordatorios activos"
                    val first = active.first()
                    binding.tvLatestReminderText.text = "• ${first.title} (Vence: ${first.dueDate})"
                } else {
                    binding.tvRemindersSummaryTitle.text = "Sin recordatorios pendientes"
                    binding.tvLatestReminderText.text = "Toca '+' para agregar tu primer aviso"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.maintenanceDao().getCompletedCountFlow().collectLatest { count ->
                binding.tvStatMaintenancesCount.text = count.toString()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            db.expenseDao().getAllExpensesFlow().collectLatest { expenses ->
                val monthlyTotal = StatisticsCalculator.expenses(expenses).monthlyTotal
                binding.tvStatMonthlySpent.text = NumberFormat
                    .getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(monthlyTotal)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
