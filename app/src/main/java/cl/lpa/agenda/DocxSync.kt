package cl.lpa.agenda

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * Mantiene UN solo Word por dia, siempre actualizado.
 * Cada cambio (escribir, borrar, foto, pagina) programa una actualizacion unos segundos despues,
 * agrupando varios cambios seguidos en una sola. Siempre se reescribe el MISMO archivo AAAA-MM-DD.docx.
 */
object DocxSync {
    private const val DELAY_MS = 4000L
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val pending = HashMap<String, Runnable>()

    fun request(context: Context, date: LocalDate) {
        val app = context.applicationContext
        val key = date.toString()
        synchronized(pending) {
            pending.remove(key)?.let { handler.removeCallbacks(it) }
            val r = Runnable {
                synchronized(pending) { pending.remove(key) }
                executor.execute { runCatching { DocxExporter.sync(app, date) } }
            }
            pending[key] = r
            handler.postDelayed(r, DELAY_MS)
        }
    }

    /** Ejecuta ya las actualizaciones pendientes (al salir de la app). */
    fun flush(context: Context) {
        val app = context.applicationContext
        val keys: List<String> = synchronized(pending) {
            val k = pending.keys.toList()
            k.forEach { key -> pending.remove(key)?.let { handler.removeCallbacks(it) } }
            k
        }
        keys.forEach { k ->
            runCatching { LocalDate.parse(k) }.getOrNull()?.let { d ->
                executor.execute { runCatching { DocxExporter.sync(app, d) } }
            }
        }
    }
}
