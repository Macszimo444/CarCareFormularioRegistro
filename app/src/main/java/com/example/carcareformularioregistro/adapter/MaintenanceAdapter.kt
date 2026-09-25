package com.example.carcareformularioregistro.adapter

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.ItemMaintenanceBinding

class MaintenanceAdapter(
    private var items: List<Maintenance> = emptyList(),
    private val onItemClick: (Maintenance) -> Unit = {},
    private val onDeleteClick: ((Maintenance) -> Unit)? = null
) : RecyclerView.Adapter<MaintenanceAdapter.ViewHolder>() {

    fun updateData(newItems: List<Maintenance>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMaintenanceBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemMaintenanceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Maintenance) {
            binding.tvTitle.text = item.type
            binding.tvSubtitle.text = "${item.date} • ${item.mileage} km"
            binding.tvWorkshop.text = item.workshop.ifBlank { "Taller no especificado" }
            binding.tvCost.text = String.format("$%.2f", item.cost)

            val context = binding.root.context

            val (statusText, colorRes) = when (item.status) {
                Maintenance.STATUS_PENDIENTE -> "Pendiente" to R.color.carcare_warning
                Maintenance.STATUS_REALIZADO -> "Realizado" to R.color.carcare_success
                else -> "Próximo" to R.color.carcare_electric_blue
            }

            binding.tvStatus.text = statusText
            binding.tvStatus.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, colorRes)
            )

            binding.btnDelete.setOnClickListener {
                onDeleteClick?.invoke(item)
            }

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
