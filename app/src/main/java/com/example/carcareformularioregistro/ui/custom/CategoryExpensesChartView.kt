package com.example.carcareformularioregistro.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.CategoryTotal
import com.example.carcareformularioregistro.data.Expense

class CategoryExpensesChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<CategoryTotal> = emptyList()
    private var totalAmount: Double = 0.0

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        color = ContextCompat.getColor(context, R.color.carcare_text_primary)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = ContextCompat.getColor(context, R.color.carcare_text_secondary)
    }

    private val categoryColors = mapOf(
        Expense.CAT_MANTENIMIENTO to R.color.carcare_electric_blue,
        Expense.CAT_COMBUSTIBLE to R.color.carcare_success,
        Expense.CAT_REPARACIONES to R.color.carcare_warning,
        Expense.CAT_LLANTAS to R.color.carcare_soft_blue,
        Expense.CAT_OTROS to R.color.carcare_text_secondary
    )

    fun setData(data: List<CategoryTotal>) {
        items = data.filter { it.total > 0 }
        totalAmount = items.sumOf { it.total }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = if (items.isEmpty()) 120 else (items.size * 70) + 60
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (items.isEmpty() || totalAmount <= 0.0) {
            canvas.drawText(
                "Sin datos de gastos registrados",
                30f,
                60f,
                subTextPaint
            )
            return
        }

        var currentY = 40f
        val maxBarWidth = (width - 240).toFloat().coerceAtLeast(100f)

        for (item in items) {
            val percentage = (item.total / totalAmount).toFloat()
            val barWidth = maxBarWidth * percentage

            val colorRes = categoryColors[item.category] ?: R.color.carcare_electric_blue
            barPaint.color = ContextCompat.getColor(context, colorRes)

            // Draw Category bar
            val rect = RectF(20f, currentY, 20f + barWidth.coerceAtLeast(16f), currentY + 32f)
            canvas.drawRoundRect(rect, 8f, 8f, barPaint)

            // Draw Category name
            canvas.drawText(
                item.category,
                30f + maxBarWidth,
                currentY + 24f,
                textPaint
            )

            // Draw Amount and percentage
            val textFormatted = String.format("$%.2f (%.0f%%)", item.total, percentage * 100)
            canvas.drawText(
                textFormatted,
                30f + maxBarWidth,
                currentY + 54f,
                subTextPaint
            )

            currentY += 70f
        }
    }
}
