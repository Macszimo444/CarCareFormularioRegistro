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
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.databinding.ItemExpenseBinding
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseAdapter(
    items: List<Expense> = emptyList(),
    private val onItemClick: (Expense) -> Unit = {},
    private val onOptions: (View, Expense) -> Unit = { _, _ -> }
) : ListAdapter<Expense, ExpenseAdapter.ViewHolder>(object : DiffUtil.ItemCallback<Expense>() {
    override fun areItemsTheSame(oldItem: Expense, newItem: Expense) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Expense, newItem: Expense) = oldItem == newItem
}) {
    init { submitList(items) }

    fun updateData(newItems: List<Expense>) = submitList(newItems)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Expense) {
            val context = binding.root.context
            binding.tvConcept.text = item.concept
            val date = runCatching { LocalDate.parse(item.date).format(DateTimeFormatter.ofPattern("dd/MM/uuuu")) }
                .getOrDefault(item.date)
            binding.tvSubtext.text = "${item.category} · $date"
            binding.tvAmount.text = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(item.amount)
            binding.tvNotes.text = item.description
            binding.tvNotes.isVisible = item.description.isNotBlank()
            val iconRes = when (item.category) {
                Expense.CAT_COMBUSTIBLE -> R.drawable.ic_gas
                Expense.CAT_MANTENIMIENTO -> R.drawable.ic_wrench
                Expense.CAT_LLANTAS -> R.drawable.ic_tire
                else -> R.drawable.ic_gastos
            }
            binding.ivIcon.setImageResource(iconRes)
            binding.ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.carcare_electric_blue))
            binding.root.setOnClickListener { onItemClick(item) }
            binding.btnOptions.contentDescription = context.getString(R.string.expense_options, item.concept)
            binding.btnOptions.setOnClickListener { onOptions(it, item) }
        }
    }
}
