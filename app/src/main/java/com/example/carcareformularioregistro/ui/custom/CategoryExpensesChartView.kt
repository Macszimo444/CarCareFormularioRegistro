package com.example.carcareformularioregistro.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.CategoryTotal
import com.example.carcareformularioregistro.data.Expense
import java.text.NumberFormat
import java.util.Locale

/** Stacked labels and a full-width bar remain readable on narrow screens and larger font sizes. */
class CategoryExpensesChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var items: List<CategoryTotal> = emptyList()
    private var totalAmount = 0.0
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(14f)
        color = ContextCompat.getColor(context, R.color.carcare_text_primary)
    }
    private val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = ContextCompat.getColor(context, R.color.carcare_text_secondary)
    }
    private val rowHeight get() = textPaint.fontSpacing + subTextPaint.fontSpacing + dp(28f)
    private val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
    private val categoryColors = mapOf(
        Expense.CAT_MANTENIMIENTO to R.color.carcare_electric_blue,
        Expense.CAT_COMBUSTIBLE to R.color.carcare_success,
        Expense.CAT_REPARACIONES to R.color.carcare_warning,
        Expense.CAT_LLANTAS to R.color.carcare_electric_blue,
        Expense.CAT_OTROS to R.color.carcare_text_secondary
    )

    fun setData(data: List<CategoryTotal>) {
        items = data.filter { it.total.isFinite() && it.total > 0 }
        totalAmount = items.sumOf { it.total }
        contentDescription = if (items.isEmpty()) context.getString(R.string.empty_gastos)
            else items.joinToString(". ") { "${it.category}: ${currency.format(it.total)}" }
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentHeight = if (items.isEmpty()) subTextPaint.fontSpacing + dp(16f) else rowHeight * items.size
        setMeasuredDimension(resolveSize(dp(280f).toInt() + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(contentHeight.toInt() + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = paddingLeft.toFloat()
        val availableWidth = (width - paddingLeft - paddingRight).coerceAtLeast(0).toFloat()
        if (availableWidth == 0f) return
        if (items.isEmpty() || totalAmount <= 0.0) {
            canvas.drawText(TextUtils.ellipsize(context.getString(R.string.empty_gastos), subTextPaint,
                availableWidth, TextUtils.TruncateAt.END).toString(), left,
                paddingTop - subTextPaint.fontMetrics.top + dp(4f), subTextPaint)
            return
        }
        var rowTop = paddingTop.toFloat()
        for (item in items) {
            val titleBaseline = rowTop - textPaint.fontMetrics.top
            canvas.drawText(TextUtils.ellipsize(item.category, textPaint, availableWidth,
                TextUtils.TruncateAt.END).toString(), left, titleBaseline, textPaint)
            val percentage = (item.total / totalAmount).coerceIn(0.0, 1.0)
            val amount = "${currency.format(item.total)} · ${String.format(Locale.forLanguageTag("es-MX"), "%.0f", percentage * 100)}%"
            val amountBaseline = rowTop + textPaint.fontSpacing + dp(2f) - subTextPaint.fontMetrics.top
            canvas.drawText(TextUtils.ellipsize(amount, subTextPaint, availableWidth,
                TextUtils.TruncateAt.END).toString(), left, amountBaseline, subTextPaint)
            val barTop = rowTop + textPaint.fontSpacing + subTextPaint.fontSpacing + dp(8f)
            barPaint.color = ContextCompat.getColor(context, R.color.carcare_field)
            canvas.drawRoundRect(RectF(left, barTop, left + availableWidth, barTop + dp(6f)), dp(3f), dp(3f), barPaint)
            barPaint.color = ContextCompat.getColor(context, categoryColors[item.category] ?: R.color.carcare_electric_blue)
            val barWidth = (availableWidth * percentage.toFloat()).coerceAtLeast(dp(3f)).coerceAtMost(availableWidth)
            canvas.drawRoundRect(RectF(left, barTop, left + barWidth, barTop + dp(6f)), dp(3f), dp(3f), barPaint)
            rowTop += rowHeight
        }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private fun sp(value: Float) = value * resources.displayMetrics.scaledDensity
}
