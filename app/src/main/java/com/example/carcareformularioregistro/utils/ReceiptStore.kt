package com.example.carcareformularioregistro.utils

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import com.example.carcareformularioregistro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Private copies remain readable after the source document is moved or its permission expires. */
class ReceiptStore(private val context: Context) {
    private val directory get() = File(context.filesDir, "receipts").apply { mkdirs() }
    fun file(name: String): File {
        require(NAME.matches(name)) { "Nombre de comprobante inválido" }
        return File(directory, name)
    }
    fun import(uri: Uri): String = requireNotNull(context.contentResolver.openInputStream(uri)).use(::import)
    fun import(input: InputStream): String {
        val temp = File.createTempFile("receipt-", ".tmp", directory)
        try {
            temp.outputStream().use { copyLimited(input, it, MAX_BYTES) }
            val header = ByteArray(5)
            temp.inputStream().use { it.read(header) }
            val extension = if (header.contentEquals("%PDF-".toByteArray())) {
                val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val renderer = PdfRenderer(descriptor)
                    try { require(renderer.pageCount > 0) { "PDF sin páginas" } }
                    finally { renderer.close() }
                } finally { descriptor.close() }
                "pdf"
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(temp.path, options)
                require(options.outWidth > 0 && options.outHeight > 0) { "Elige una imagen o un PDF válido" }
                when (options.outMimeType) {
                    "image/jpeg" -> "jpg"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> throw IllegalArgumentException("Usa JPG, PNG, WebP o PDF")
                }
            }
            val name = "${UUID.randomUUID()}.$extension"
            check(temp.renameTo(file(name))) { "No se pudo guardar el comprobante" }
            return name
        } finally { temp.delete() }
    }
    fun delete(name: String?) { if (name != null && NAME.matches(name)) file(name).delete() }
    fun discardDraft(name: String?) {
        if (name == null) return
        cleanupScope.launch {
            val db = AppDatabase.getInstance(context.applicationContext)
            // Serialize cleanup behind any pending service commit; never remove a referenced receipt.
            try { db.withTransaction { if (db.maintenanceDao().countReceiptReferences(name) == 0) delete(name) } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { android.util.Log.w("CarCare", "Could not discard unused receipt", error) }
        }
    }
    fun clear() { directory.listFiles()?.forEach { it.delete() } }
    fun open(name: String) {
        val file = file(name)
        require(file.isFile) { "No se encontró el comprobante" }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = when (file.extension) { "pdf" -> "application/pdf"; "jpg" -> "image/jpeg"; else -> "image/${file.extension}" }
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    companion object {
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val MAX_BYTES = 10L * 1024 * 1024
        val NAME = Regex("[a-f0-9-]{36}\\.(jpg|png|webp|pdf)")
        fun copyLimited(input: InputStream, output: OutputStream, limit: Long): Long {
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                require(total <= limit) { "El archivo supera el tamaño permitido" }
                output.write(buffer, 0, n)
            }
            return total
        }
    }
}
