package cl.lpa.agenda

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Copias visibles en la tablet (Documentos/Agenda, Documentos/Actas). Si el archivo ya existe se actualiza, no se duplica. */
object MediaBackup {
    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val PDF = "application/pdf"

    fun copy(context: Context, file: File, relPath: String, mime: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val rel = relPath.trimEnd('/') + "/"

                val existing: Uri? = findUri(context, file.name, rel)
                if (existing != null) {
                    val ok = runCatching {
                        resolver.openOutputStream(existing, "wt")?.use { os ->
                            file.inputStream().use { it.copyTo(os) }
                        }
                        true
                    }.getOrDefault(false)
                    if (ok) return@runCatching
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath.trimEnd('/'))
                }
                val uri = resolver.insert(collection, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
                }
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                    relPath.substringAfter("Documents/", relPath)
                )
                dir.mkdirs()
                file.copyTo(File(dir, file.name), overwrite = true)
            }
        }
    }

    fun delete(context: Context, name: String, relPath: String) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val uri = findUri(context, name, relPath.trimEnd('/') + "/")
                if (uri != null) context.contentResolver.delete(uri, null, null)
            } else {
                File(
                    File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), relPath.substringAfter("Documents/", relPath)),
                    name
                ).delete()
            }
        }
    }

    private fun findUri(context: Context, name: String, rel: String): Uri? {
        val collection = MediaStore.Files.getContentUri("external")
        return context.contentResolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, rel), null
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
    }
}
