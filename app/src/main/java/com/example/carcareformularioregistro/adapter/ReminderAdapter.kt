package com.example.carcareformularioregistro.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.ItemReminderBinding
import com.example.carcareformularioregistro.utils.FormValidation
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ReminderAdapter(
    private var items: List<Reminder> = emptyList(),
    private val onToggle: (Reminder, Boolean) -> Unit = { _, _ -> },
    private val onDelete: ((Reminder) -> Unit)? = null,
    private val onEdit: ((Reminder) -> Unit)? = null,
    private val onComplete: ((Reminder) -> Unit)? = null
) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {
    private var vehicleMileage = 0
    fun updateData(newItems: List<Reminder>, mileage: Int = vehicleMileage) {
        items = newItems
        vehicleMileage = mileage
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemReminderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class ViewHolder(private val binding: ItemReminderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Reminder) {
            val context = binding.root.context
            val format = NumberFormat.getIntegerInstance()
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvDescription.isVisible = item.description.isNotBlank()
            binding.tvLinked.isVisible = item.maintenanceId != null
            val date = FormValidation.date(item.dueDate)?.let(LocalDate::parse)
            val targets = buildList {
                date?.let { add(context.getString(R.string.reminder_date_target, it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))) }
                if (item.dueMileage > 0) add(context.getString(R.string.reminder_mileage_target, format.format(item.dueMileage)))
            }
            binding.tvDueDate.text = targets.joinToString("\n").ifBlank { context.getString(R.string.reminder_no_target) }
            val progress = buildList {
                date?.let {
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), it)
                    add(when {
                        days < 0 -> "Fecha alcanzada; revisa si ya atendiste este pendiente."
                        days == 0L -> "Revisión prevista para hoy."
                        days == 1L -> "Falta 1 día."
                        else -> "Faltan $days días."
                    })
                }
                if (item.dueMileage > 0) add(if (vehicleMileage >= item.dueMileage)
                    context.getString(R.string.reminder_reached)
                    else context.getString(R.string.reminder_remaining, format.format(item.dueMileage - vehicleMileage)))
            }
            binding.tvProgress.text = progress.joinToString("\n")
            binding.tvProgress.isVisible = progress.isNotEmpty()
            val color = when (item.priority) {
                Reminder.PRIORITY_ALTA -> R.color.carcare_danger
                Reminder.PRIORITY_BAJA -> R.color.carcare_electric_blue
                else -> R.color.carcare_warning
            }
            binding.tvPriorityBadge.text = "Prioridad ${item.priority.lowercase()}"
            binding.tvPriorityBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.carcare_field))
            binding.tvPriorityBadge.setTextColor(ContextCompat.getColor(context, color))
            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }
            binding.btnCompleteReminder.setOnClickListener { onComplete?.invoke(item) }
            binding.btnCompleteReminder.contentDescription = "Marcar ${item.title} como realizada"
            binding.btnDeleteReminder.setOnClickListener { onDelete?.invoke(item) }
            binding.btnEditReminder.setOnClickListener { onEdit?.invoke(item) }
            binding.btnEditReminder.contentDescription = "Editar ${item.title}"
            binding.btnDeleteReminder.contentDescription = "Eliminar ${item.title}"
        }
    }
}
