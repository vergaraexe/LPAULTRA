package cl.lpa.agenda

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class FileShortcut(val slot: Int, val label: String, val file: File, val originalName: String)

/**
 * Dos "archivos rapidos" configurables en Ajustes (por ejemplo, el horario de los profesores en Excel).
 * El archivo se COPIA al almacenamiento de la app, asi siempre esta disponible, incluso sin internet
 * o si el original cambia de lugar. Para actualizarlo, se vuelve a cargar en Ajustes.
 */
object FileShortcuts {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("file_shortcuts", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "shortcuts").apply { mkdirs() }

    fun get(ctx: Context, slot: Int): FileShortcut? {
        val p = prefs(ctx)
        val name = p.getString("file$slot", null) ?: return null
        val f = File(dir(ctx), name)
        if (!f.exists()) return null
        return FileShortcut(
            slot,
            p.getString("label$slot", null) ?: "Archivo $slot",
            f,
            p.getString("orig$slot", null) ?: name
        )
    }

    fun setLabel(ctx: Context, slot: Int, label: String) {
        prefs(ctx).edit().putString("label$slot", label.trim().ifBlank { "Archivo $slot" }).apply()
    }

    fun remove(ctx: Context, slot: Int) {
        dir(ctx).listFiles { f -> f.name.startsWith("slot$slot.") }?.forEach { it.delete() }
        prefs(ctx).edit().remove("file$slot").remove("label$slot").remove("orig$slot").apply()
    }

    fun importFile(ctx: Context, slot: Int, uri: Uri): FileShortcut {
        val resolver = ctx.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "archivo"
        var ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) {
            ext = when (resolver.getType(uri)) {
                "application/pdf" -> "pdf"
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                "text/csv" -> "csv"
                "text/plain" -> "txt"
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                else -> "bin"
            }
        }
        remove(ctx, slot)
        val dest = File(dir(ctx), "slot$slot.$ext")
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("No se pudo leer el archivo elegido.")
        input.use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }

        val label = displayName.substringBeforeLast('.').take(12).ifBlank { "Archivo $slot" }
        prefs(ctx).edit()
            .putString("file$slot", dest.name)
            .putString("orig$slot", displayName)
            .putString("label$slot", label)
            .apply()
        return FileShortcut(slot, label, dest, displayName)
    }
}
