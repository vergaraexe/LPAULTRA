package cl.lpa.agenda

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/** Recuerda cuando fue la ultima vez que se detecto el lapiz (tocando o apenas sobre la pantalla). */
object PalmState {
    @Volatile
    var lastPenMs: Long = 0L

    fun penNearby(windowMs: Long = 900L): Boolean =
        SystemClock.uptimeMillis() - lastPenMs < windowMs
}

/**
 * Proteccion contra la palma: mientras el lapiz esta cerca o se uso hace menos de ~1 segundo,
 * los toques de dedo/palma se descartan en toda la pantalla (botones, barras, etc.).
 * El lapiz siempre funciona.
 */
fun Modifier.palmGuard(guardMs: Long = 900L): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val now = SystemClock.uptimeMillis()
            for (c in event.changes) {
                if (c.type == PointerType.Stylus || c.type == PointerType.Eraser) {
                    PalmState.lastPenMs = now
                }
            }
            if (now - PalmState.lastPenMs < guardMs) {
                for (c in event.changes) {
                    if (c.type == PointerType.Touch) c.consume()
                }
            }
        }
    }
}
