package cl.lpa.agenda

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una hoja del acta. kind: p1 (hoja 1 del formato), temas / acuerdos (hojas adicionales), p2 (participantes y firmas). */
class ActaSheet(val kind: String) {
    val strokes: SnapshotStateList<InkStroke> = mutableStateListOf()
}

/** Copia inmutable de una hoja, para generar el PDF en segundo plano. */
data class ActaSnap(val kind: String, val strokes: List<InkStroke>)

/**
 * Actas: completamente separadas de la agenda.
 *   files/actas/draft.json   -> acta en edicion (borrador); se guarda sola a cada trazo
 *   files/actas/final/ (archivos PDF)  -> actas finalizadas y enviadas (para consulta)
 * Los trazos se guardan en puntos de hoja carta (612 x 792), asi se ven igual en cualquier pantalla y en el PDF.
 * El acta parte con 2 hojas (p1 y p2); se pueden insertar hojas adicionales de TEMAS TRATADOS o ACUERDOS
 * entre ellas, y las firmas siempre quedan en la ultima hoja.
 */
class ActaStore(context: Context) {
    companion object {
        const val PAGE_W = 612f      // carta en puntos
        const val PAGE_H = 792f
        const val K_P1 = "p1"
        const val K_P2 = "p2"
        const val K_TEMAS = "temas"
        const val K_ACUERDOS = "acuerdos"

        fun titleOf(kind: String) = when (kind) {
            K_TEMAS -> "Hoja adicional · Temas tratados"
            K_ACUERDOS -> "Hoja adicional · Acuerdos"
            K_P2 -> "Participantes y firmas"
            else -> "Acta de reunión"
        }

        fun blank(): List<ActaSheet> = listOf(ActaSheet(K_P1), ActaSheet(K_P2))
    }

    private val root = File(context.filesDir, "actas").apply { mkdirs() }
    private val draftFile = File(root, "draft.json")
    private val finalDir = File(root, "final").apply { mkdirs() }

    private fun readStrokes(arr: JSONArray, into: MutableList<InkStroke>) {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pts = o.getJSONArray("p")
            into.add(
                InkStroke(
                    points = List(pts.length() / 2) { k ->
                        Offset(pts.getDouble(2 * k).toFloat(), pts.getDouble(2 * k + 1).toFloat())
                    },
                    color = Color(o.getInt("c")),
                    width = o.getDouble("w").toFloat(),
                    highlighter = o.getBoolean("h"),
                    dashed = o.optBoolean("d", false)
                )
            )
        }
    }

    fun loadDraft(): List<ActaSheet> {
        if (!draftFile.exists()) return blank()
        return try {
            val root = JSONObject(draftFile.readText())
            val sheets = root.optJSONArray("sheets")
            val result = ArrayList<ActaSheet>()
            if (sheets != null) {
                for (i in 0 until sheets.length()) {
                    val o = sheets.getJSONObject(i)
                    val sh = ActaSheet(o.getString("k"))
                    readStrokes(o.getJSONArray("s"), sh.strokes)
                    result.add(sh)
                }
            } else {
                // formato anterior (v1.5): solo 2 hojas
                val pages = root.getJSONArray("pages")
                for (p in 0 until minOf(2, pages.length())) {
                    val sh = ActaSheet(if (p == 0) K_P1 else K_P2)
                    readStrokes(pages.getJSONArray(p), sh.strokes)
                    result.add(sh)
                }
            }
            if (result.size < 2 || result.first().kind != K_P1 || result.last().kind != K_P2) blank() else result
        } catch (e: Exception) {
            blank() // borrador danado: se parte en blanco
        }
    }

    fun saveDraft(sheets: List<ActaSheet>) {
        val all = JSONArray()
        sheets.forEach { sh ->
            val arr = JSONArray()
            sh.strokes.toList().forEach { s ->
                val pts = JSONArray()
                s.points.forEach { pts.put(it.x.toDouble()); pts.put(it.y.toDouble()) }
                arr.put(
                    JSONObject().put("c", s.color.toArgb()).put("w", s.width.toDouble())
                        .put("h", s.highlighter).put("d", s.dashed).put("p", pts)
                )
            }
            all.put(JSONObject().put("k", sh.kind).put("s", arr))
        }
        val tmp = File(root, "draft.tmp")
        tmp.writeText(JSONObject().put("sheets", all).toString())
        draftFile.delete()
        tmp.renameTo(draftFile)
    }

    fun clearDraft() {
        draftFile.delete()
    }

    /** Guarda el PDF finalizado en la carpeta de consulta y devuelve ese archivo. */
    fun addFinal(pdf: File): File {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
        val dest = File(finalDir, "Acta_$stamp.pdf")
        pdf.copyTo(dest, overwrite = true)
        return dest
    }

    fun listFinal(): List<File> =
        finalDir.listFiles { f -> f.extension.equals("pdf", true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
}
