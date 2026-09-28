package com.example.carcareformularioregistro.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.databinding.ItemVehicleBinding
import java.text.NumberFormat
import java.util.Locale

data class VehicleRow(val vehicle: Vehicle, val selected: Boolean)

class VehicleAdapter(
    private val onSelect: (Vehicle) -> Unit,
    private val onOptions: (View, Vehicle) -> Unit
) : ListAdapter<VehicleRow, VehicleAdapter.Holder>(object : DiffUtil.ItemCallback<VehicleRow>() {
    override fun areItemsTheSame(oldItem: VehicleRow, newItem: VehicleRow) = oldItem.vehicle.id == newItem.vehicle.id
    override fun areContentsTheSame(oldItem: VehicleRow, newItem: VehicleRow) = oldItem == newItem
}) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemVehicleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemVehicleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: VehicleRow) {
            val item = row.vehicle
            val context = binding.root.context
            binding.tvName.text = item.displayName
            binding.tvPrimary.isVisible = item.isPrimary
            binding.tvSpecification.text = context.getString(R.string.vehicle_specification, item.brand, item.model, item.year,
                NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX")).format(item.mileage))
            binding.btnSelect.setText(if (row.selected) R.string.vehicle_selected else R.string.vehicle_select)
            binding.btnSelect.isEnabled = !row.selected
            binding.btnSelect.setOnClickListener { onSelect(item) }
            binding.btnOptions.contentDescription = context.getString(R.string.vehicle_options_for, item.displayName)
            binding.btnOptions.setOnClickListener { onOptions(it, item) }
            binding.root.strokeColor = ContextCompat.getColor(context,
                if (row.selected) R.color.carcare_electric_blue else R.color.carcare_border)
        }
    }
}
