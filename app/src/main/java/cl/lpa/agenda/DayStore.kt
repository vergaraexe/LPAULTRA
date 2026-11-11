package cl.lpa.agenda

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

data class Photo(val file: String, val description: String, val time: Long)
/** Foto pegada en miniatura sobre la hoja (posicion y tamano en dp). */
data class PagePhoto(
    val file: String, val x: Float, val y: Float, val w: Float, val h: Float,
    // recorte como fraccion de la foto original (0..1): izquierda, arriba, derecha, abajo
    val cl: Float = 0f, val ct: Float = 0f, val cr: Float = 1f, val cb: Float = 1f
)
data class PageData(
    val strokes: List<InkStroke>,
    val w: Float,
    val h: Float,
    val images: List<PagePhoto> = emptyList()
)

/**
 * Cada dia tiene su carpeta:
 *   files/agenda/2026-11-07/page_1.json, page_2.json ...
 *   files/agenda/2026-11-07/photos/IMG_xxx.jpg  +  photos.json (descripciones)
 * Los puntos se guardan en dp, asi la pagina se ve igual en cualquier orientacion.
 */
class DayStore(context: Context) {
    private val app = context.applicationContext
    private val root = File(context.filesDir, "agenda").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("agenda_state", Context.MODE_PRIVATE)

    fun dayDir(d: LocalDate) = File(root, d.toString())
    private fun pageFile(d: LocalDate, page: Int) = File(dayDir(d), "page_$page.json")

    // ---------- paginas ----------
    fun pageCount(d: LocalDate): Int {
        val n = dayDir(d).listFiles { f -> f.name.matches(Regex("page_\\d+\\.json")) }?.size ?: 0
        return maxOf(1, n)
    }

    fun addPage(d: LocalDate): Int {
        val next = pageCount(d) + 1
        if (!pageFile(d, 1).exists()) save(d, 1, emptyList())
        save(d, next, emptyList())
        return next
    }

    fun load(d: LocalDate, page: Int): List<InkStroke> = loadPage(d, page).strokes

    fun loadPage(d: LocalDate, page: Int): PageData {
        val f = pageFile(d, page)
        if (!f.exists()) return PageData(emptyList(), 0f, 0f)
        return try {
            val root = JSONObject(f.readText())
            val arr = root.getJSONArray("strokes")
            val strokes = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val p = o.getJSONArray("p")
                InkStroke(
                    points = List(p.length() / 2) { k ->
                        Offset(p.getDouble(2 * k).toFloat(), p.getDouble(2 * k + 1).toFloat())
                    },
                    color = Color(o.getInt("c")),
                    width = o.getDouble("w").toFloat(),
                    highlighter = o.getBoolean("h"),
                    dashed = o.optBoolean("d", false)
                )
            }
            val imgArr = root.optJSONArray("images")
            val images = if (imgArr == null) emptyList() else List(imgArr.length()) { i ->
                val o = imgArr.getJSONObject(i)
                PagePhoto(
                    o.getString("f"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("w").toFloat(), o.getDouble("h").toFloat(),
                    o.optDouble("cl", 0.0).toFloat(), o.optDouble("ct", 0.0).toFloat(),
                    o.optDouble("cr", 1.0).toFloat(), o.optDouble("cb", 1.0).toFloat()
                )
            }
            PageData(strokes, root.optDouble("w", 0.0).toFloat(), root.optDouble("h", 0.0).toFloat(), images)
        } catch (e: Exception) {
            PageData(emptyList(), 0f, 0f)
        }
    }

    fun save(
        d: LocalDate, page: Int, strokes: List<InkStroke>,
        w: Float = 0f, h: Float = 0f, images: List<PagePhoto> = emptyList()
    ) {
        val arr = JSONArray()
        strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(it.x.toDouble()); pts.put(it.y.toDouble()) }
            arr.put(
                JSONObject()
                    .put("c", s.color.toArgb())
                    .put("w", s.width.toDouble())
                    .put("h", s.highlighter)
                    .put("d", s.dashed)
                    .put("p", pts)
            )
        }
        val imgs = JSONArray()
        images.forEach {
            imgs.put(
                JSONObject().put("f", it.file).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                    .put("w", it.w.toDouble()).put("h", it.h.toDouble())
                    .put("cl", it.cl.toDouble()).put("ct", it.ct.toDouble())
                    .put("cr", it.cr.toDouble()).put("cb", it.cb.toDouble())
            )
        }
        val dir = dayDir(d).apply { mkdirs() }
        val tmp = File(dir, "page_$page.tmp")
        tmp.writeText(
            JSONObject().put("w", w.toDouble()).put("h", h.toDouble())
                .put("strokes", arr).put("images", imgs).toString()
        )
        pageFile(d, page).delete()
        tmp.renameTo(pageFile(d, page))
        markDirty(d)
    }

    // ---------- fotos ----------
    private fun photosJson(d: LocalDate) = File(dayDir(d), "photos.json")
    fun photosDir(d: LocalDate) = File(dayDir(d), "photos").apply { mkdirs() }
    fun photoFile(d: LocalDate, name: String) = File(photosDir(d), name)
    fun newPhotoFile(d: LocalDate) = File(photosDir(d), "IMG_${System.currentTimeMillis()}.jpg")

    fun loadPhotos(d: LocalDate): List<Photo> {
        val f = photosJson(d)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Photo(o.getString("file"), o.optString("desc", ""), o.optLong("time", 0L))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writePhotos(d: LocalDate, list: List<Photo>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("file", it.file).put("desc", it.description).put("time", it.time))
        }
        dayDir(d).mkdirs()
        photosJson(d).writeText(arr.toString())
        markDirty(d)
    }

    fun addPhoto(d: LocalDate, fileName: String, description: String) {
        writePhotos(d, loadPhotos(d) + Photo(fileName, description, System.currentTimeMillis()))
    }

    fun deletePhoto(d: LocalDate, photo: Photo) {
        photoFile(d, photo.file).delete()
        // quitar tambien las miniaturas pegadas en las hojas
        for (p in 1..pageCount(d)) {
            val pd = loadPage(d, p)
            if (pd.images.any { it.file == photo.file }) {
                save(d, p, pd.strokes, pd.w, pd.h, pd.images.filter { it.file != photo.file })
            }
        }
        writePhotos(d, loadPhotos(d).filter { it.file != photo.file })
    }

    // ---------- contenido y respaldo ----------
    fun hasContent(d: LocalDate): Boolean {
        if (!dayDir(d).exists()) return false
        if (loadPhotos(d).isNotEmpty()) return true
        return (1..pageCount(d)).any { load(d, it).isNotEmpty() }
    }

    fun daysWithContent(): List<LocalDate> =
        root.listFiles()
            ?.mapNotNull { runCatching { LocalDate.parse(it.name) }.getOrNull() }
            ?.filter { hasContent(it) }
            ?.sorted() ?: emptyList()

    private fun markDirty(d: LocalDate) {
        val s = (prefs.getStringSet("dirty", emptySet()) ?: emptySet()).toMutableSet()
        s.add(d.toString())
        prefs.edit().putStringSet("dirty", s).apply()
        DocxSync.request(app, d)   // Word del dia actualizado automaticamente (mismo archivo)
    }

    fun unmarkDirty(d: LocalDate) {
        val s = (prefs.getStringSet("dirty", emptySet()) ?: emptySet()).toMutableSet()
        if (s.remove(d.toString())) prefs.edit().putStringSet("dirty", s).apply()
    }

    fun takeDirty(): List<LocalDate> {
        val s = (prefs.getStringSet("dirty", emptySet()) ?: emptySet()).toSet()
        prefs.edit().remove("dirty").apply()
        return s.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
}
