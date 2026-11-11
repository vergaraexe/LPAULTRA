package cl.lpa.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Ventana flotante: se mueve arrastrando la barra azul, se agranda o achica con el tirador ◢ de la esquina
 * inferior derecha y se cierra con ✕. Se dibuja encima de la hoja sin sacar al usuario del acta.
 */
@Composable
fun FloatingWindow(
    title: String,
    initialX: Float,
    initialY: Float,
    initialW: Float,
    initialH: Float,
    minW: Float = 220f,
    minH: Float = 180f,
    onClose: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var x by remember { mutableFloatStateOf(initialX) }
    var y by remember { mutableFloatStateOf(initialY) }
    var w by remember { mutableFloatStateOf(initialW) }
    var h by remember { mutableFloatStateOf(initialH) }
    val dens = LocalDensity.current.density
    val cfg = LocalConfiguration.current
    val maxX = cfg.screenWidthDp.toFloat()
    val maxY = cfg.screenHeightDp.toFloat()

    Box(
        Modifier
            .offset { IntOffset((x * dens).roundToInt(), (y * dens).roundToInt()) }
            .size(w.dp, h.dp)
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(2.dp, navy, RoundedCornerShape(10.dp))
            // evita que el lapiz "atraviese" la ventana y dibuje en la hoja de abajo
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(navy)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            x = (x + drag.x / dens).coerceIn(-w + 80f, maxX - 60f)
                            y = (y + drag.y / dens).coerceIn(0f, maxY - 48f)
                        }
                    }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✥  $title",
                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, modifier = Modifier.weight(1f)
                )
                Text(
                    "✕",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        }
        // tirador para cambiar el tamano
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(44.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        w = (w + drag.x / dens).coerceIn(minW, maxX)
                        h = (h + drag.y / dens).coerceIn(minH, maxY)
                    }
                },
            contentAlignment = Alignment.BottomEnd
        ) {
            Text("◢", color = navy, fontSize = 24.sp, modifier = Modifier.padding(6.dp))
        }
    }
}
