package com.example.carcareformularioregistro.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.databinding.ItemReminderBinding

class ReminderAdapter(
    private var items: List<Reminder> = emptyList(),
    private val onToggle: (Reminder, Boolean) -> Unit = { _, _ -> },
    private val onDelete: ((Reminder) -> Unit)? = null
) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

    fun updateData(newItems: List<Reminder>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Reminder) {
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description.ifBlank { "Sin descripción" }
            binding.tvDueDate.text = "Vence: ${item.dueDate} • ${item.dueMileage} km"

            val context = binding.root.context

            val (prioText, prioColor) = when (item.priority) {
                Reminder.PRIORITY_ALTA -> "Alta" to R.color.carcare_danger
                Reminder.PRIORITY_BAJA -> "Baja" to R.color.carcare_electric_blue
                else -> "Media" to R.color.carcare_warning
            }

            binding.tvPriorityBadge.text = prioText
            binding.tvPriorityBadge.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, prioColor)
            )

            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                onToggle(item, isChecked)
            }

            binding.btnDeleteReminder.setOnClickListener {
                onDelete?.invoke(item)
            }
        }
    }
}
