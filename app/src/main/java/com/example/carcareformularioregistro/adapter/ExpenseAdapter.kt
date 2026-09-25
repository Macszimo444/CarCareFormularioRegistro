package com.example.carcareformularioregistro.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.databinding.ItemExpenseBinding

class ExpenseAdapter(
    private var items: List<Expense> = emptyList(),
    private val onItemClick: (Expense) -> Unit = {}
) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {

    fun updateData(newItems: List<Expense>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExpenseBinding.inflate(
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

    inner class ViewHolder(private val binding: ItemExpenseBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Expense) {
            binding.tvConcept.text = item.concept
            binding.tvSubtext.text = "${item.category} • ${item.date}"
            binding.tvAmount.text = String.format("-$%.2f", item.amount)

            val context = binding.root.context
            val iconRes = when (item.category) {
                Expense.CAT_COMBUSTIBLE -> R.drawable.ic_gas
                Expense.CAT_MANTENIMIENTO -> R.drawable.ic_wrench
                Expense.CAT_LLANTAS -> R.drawable.ic_tire
                else -> R.drawable.ic_gastos
            }
            binding.ivIcon.setImageResource(iconRes)
            binding.ivIcon.setColorFilter(
                ContextCompat.getColor(context, R.color.carcare_electric_blue)
            )

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
