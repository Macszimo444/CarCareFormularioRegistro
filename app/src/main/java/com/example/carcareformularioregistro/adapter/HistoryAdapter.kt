package com.example.carcareformularioregistro.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.databinding.ItemHistoryBinding

class HistoryAdapter(
    private var items: List<Maintenance> = emptyList(),
    private val onClick: (Maintenance) -> Unit = {}
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    fun updateData(newItems: List<Maintenance>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Maintenance) {
            binding.root.setOnClickListener { onClick(item) }
            binding.root.isFocusable = true
            binding.root.contentDescription = "Ver ${item.type}, ${item.date}" + if (item.receipt != null) ", con comprobante" else ""
            binding.tvServiceTitle.text = item.type + if (item.receipt != null) " · Comprobante" else ""
            binding.tvDateMileage.text = "${item.date} • ${item.mileage} km"
            binding.tvWorkshop.text = item.workshop.ifBlank { "Taller no registrado" }
            binding.tvCost.text = String.format("$%.2f", item.cost)
        }
    }
}
