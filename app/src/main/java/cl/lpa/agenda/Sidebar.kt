package cl.lpa.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Barra lateral ----------
@Composable
fun Sidebar(
    tool: Tool, penColor: Color, hlColor: Color, penWidth: Float, photoCount: Int,
    shapesOn: Boolean, dashedOn: Boolean,
    module: String, onModule: (String) -> Unit,
    onTool: (Tool) -> Unit, onPenColor: (Color) -> Unit, onPenWidth: (Float) -> Unit, onHlColor: (Color) -> Unit,
    onUndo: () -> Unit, onClear: () -> Unit,
    onPhoto: () -> Unit, onEvidence: () -> Unit, onSettings: () -> Unit,
    onShapes: () -> Unit, onDashed: () -> Unit,
    calcOpen: Boolean, onCalc: () -> Unit,
    shortcutLabels: List<String?>, openShortcuts: Set<Int>, onShortcut: (Int) -> Unit
) {
    Column(
        Modifier
            .width(98.dp).fillMaxHeight().background(navy)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (module == "acta") ToolButton("📅", "Agenda", false) { onModule("agenda") }
        else ToolButton("📋", "Acta", false) { onModule("acta") }

        ToolButton("✏️", "Lápiz", tool == Tool.PEN) { onTool(Tool.PEN) }
        ColorGrid(penColors, penColor, tool == Tool.PEN, onPenColor)
        Text("Grosor", fontSize = 11.sp, color = Color.White)
        penWidths.forEach { w -> WidthButton(w, w == penWidth && tool == Tool.PEN, penColor) { onPenWidth(w) } }
        ToolButton("┈┈", "Punteado", dashedOn, onDashed)

        Spacer(Modifier.height(4.dp))
        ToolButton("🖍️", "Destacar", tool == Tool.HIGHLIGHTER) { onTool(Tool.HIGHLIGHTER) }
        ColorGrid(highlightColors, hlColor, tool == Tool.HIGHLIGHTER, onHlColor)

        Spacer(Modifier.height(4.dp))
        ToolButton("🧽", "Goma", tool == Tool.ERASER) { onTool(Tool.ERASER) }
        ToolButton("↩️", "Deshacer", false, onUndo)
        ToolButton("🗑️", "Limpiar", false, onClear)
        ToolButton("📐", "Formas", shapesOn, onShapes)

        Spacer(Modifier.height(4.dp))
        ToolButton("🧮", "Calculadora", calcOpen, onCalc)
        shortcutLabels.forEachIndexed { i, label ->
            ToolButton("📄", label ?: "Archivo ${i + 1}", (i + 1) in openShortcuts) { onShortcut(i + 1) }
        }

        Spacer(Modifier.height(4.dp))
        if (module != "acta") {
            ToolButton("📷", "Foto", false, onPhoto)
            ToolButton("🖼️", "Fotos ($photoCount)", false, onEvidence)
        }
        ToolButton("⚙️", "Ajustes", false, onSettings)
    }
}

@Composable
private fun ColorGrid(colors: List<Color>, selected: Color, active: Boolean, onPick: (Color) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { c -> ColorDot(c, c == selected && active) { onPick(c) } }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x44FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WidthButton(width: Float, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .width(56.dp).height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0x55FFFFFF) else Color(0x22FFFFFF))
            .border(if (selected) 2.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Muestra del trazo con el grosor real; si el lapiz es oscuro se dibuja en blanco para que se vea
        Box(
            Modifier
                .width(38.dp).height(width.dp)
                .clip(RoundedCornerShape(50))
                .background(if (color == penColors[0]) Color.White else color)
        )
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x88FFFFFF), CircleShape)
            .clickable(onClick = onClick)
    )
}
