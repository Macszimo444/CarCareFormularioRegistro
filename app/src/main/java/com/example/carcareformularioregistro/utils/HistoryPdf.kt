package com.example.carcareformularioregistro.utils

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.Vehicle
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** Native PDF with wrapping and page breaks; includes only this vehicle's completed services. */
object HistoryPdf {
    fun write(vehicle: Vehicle, records: List<Maintenance>, output: OutputStream) {
        val items = records.filter { it.vehicleId == vehicle.id && it.status == Maintenance.STATUS_REALIZADO }
            .sortedWith(compareByDescending<Maintenance> { it.date }.thenByDescending { it.id })
        val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
        val number = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-MX"))
        val document = PdfDocument()
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            var page: PdfDocument.Page? = null
            var y = 0f
            var pageNumber = 0
            fun newPage() {
                page?.let { document.finishPage(it) }
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.color = Color.rgb(10, 100, 205); paint.textSize = 18f
                page!!.canvas.drawText("CarCare | Historial de mantenimiento", 40f, 42f, paint)
                paint.typeface = Typeface.DEFAULT; paint.color = Color.DKGRAY; paint.textSize = 9f
                page!!.canvas.drawText("Generado: ${LocalDate.now()}   |   Página $pageNumber", 40f, 813f, paint)
                y = 70f
            }
            fun text(value: String, bold: Boolean = false, size: Float = 11f) {
                for (paragraph in value.replace('\r', ' ').split('\n')) {
                    var remaining = paragraph
                    do {
                        if (page == null || y > 780) newPage()
                        paint.textSize = size; paint.color = Color.rgb(30, 33, 38)
                        paint.typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
                        var count = paint.breakText(remaining, true, 515f, null)
                        if (count < remaining.length && count > 0) {
                            val space = remaining.lastIndexOf(' ', count - 1)
                            if (space > 0) count = space
                        }
                        if (remaining.isNotEmpty()) count = count.coerceAtLeast(1)
                        page!!.canvas.drawText(remaining.take(count), 40f, y, paint)
                        y += size + 5f
                        remaining = remaining.drop(count).trimStart()
                    } while (remaining.isNotEmpty())
                }
            }
            text(vehicle.displayName, bold = true, size = 15f)
            text("${vehicle.brand} ${vehicle.model} ${vehicle.year} | Lectura registrada: ${number.format(vehicle.mileage)} km")
            text("${items.size} servicios realizados | Total: ${money.format(items.sumOf { it.cost })}", bold = true)
            text("Información registrada por el usuario. No certifica el estado mecánico del vehículo.", size = 9f)
            y += 16
            if (items.isEmpty()) text("No hay mantenimientos realizados para este vehículo.")
            items.forEach { service ->
                if (y > 695) newPage()
                text(service.type, bold = true, size = 13f)
                text("${service.date} | ${number.format(service.mileage)} km | ${money.format(service.cost)}")
                text("Taller: ${service.workshop.ifBlank { "No registrado" }}")
                if (service.description.isNotBlank()) text("Notas: ${service.description}")
                if (service.receipt != null) text("Comprobante disponible en CarCare (no incluido en este PDF).", size = 9f)
                y += 16
            }
            page?.let { document.finishPage(it) }
            document.writeTo(output)
        } finally { document.close() }
    }
}
