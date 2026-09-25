package com.example.carcareformularioregistro.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.ItemMaintenanceBinding
import java.text.NumberFormat

class MaintenanceAdapter(
    items: List<Maintenance> = emptyList(),
    private val onItemClick: (Maintenance) -> Unit = {},
    private val onDeleteClick: ((Maintenance) -> Unit)? = null
) : ListAdapter<Maintenance, MaintenanceAdapter.ViewHolder>(DiffCallback) {
    init { submitList(items) }

    fun updateData(newItems: List<Maintenance>) { submitList(newItems) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemMaintenanceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) { holder.bind(getItem(position)) }

    inner class ViewHolder(private val binding: ItemMaintenanceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Maintenance) {
            val context = binding.root.context
            binding.tvTitle.text = item.type
            binding.tvSubtitle.text = context.getString(
                R.string.maintenance_date_mileage, item.date, NumberFormat.getIntegerInstance().format(item.mileage)
            )
            binding.tvWorkshop.text = item.workshop.ifBlank { context.getString(R.string.maintenance_no_workshop) }
            binding.tvCost.text = NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("es-MX")).format(item.cost)
            val color = when (item.status) {
                Maintenance.STATUS_PENDIENTE -> R.color.carcare_warning
                Maintenance.STATUS_REALIZADO -> R.color.carcare_success
                else -> R.color.carcare_electric_blue
            }
            binding.tvStatus.text = item.status
            binding.tvStatus.setTextColor(ContextCompat.getColor(context, color))
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, when (item.status) {
                Maintenance.STATUS_PENDIENTE -> R.color.carcare_warning_15
                Maintenance.STATUS_REALIZADO -> R.color.carcare_success_15
                else -> R.color.carcare_soft_blue
            }))
            binding.btnDelete.isVisible = onDeleteClick != null
            binding.btnDelete.contentDescription = context.getString(R.string.maintenance_delete_named, item.type)
            binding.btnDelete.setOnClickListener { onDeleteClick?.invoke(item) }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Maintenance>() {
        override fun areItemsTheSame(oldItem: Maintenance, newItem: Maintenance) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Maintenance, newItem: Maintenance) = oldItem == newItem
    }
}
