package cl.lpa.agenda

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlin.math.floor
import kotlin.math.roundToLong

// ---------- Datos leidos de un archivo ----------
sealed class ViewerData {
    class Table(val sheets: List<Pair<String, List<List<String>>>>) : ViewerData()
    class Lines(val lines: List<String>) : ViewerData()
    class Unsupported(val ext: String) : ViewerData()
    class Fail(val msg: String) : ViewerData()
}

/** Lectores sencillos de Excel (.xlsx), Word (.docx), CSV y texto, sin librerias externas. */
object OfficeReader {
    private const val MAX_ROWS = 400
    private const val MAX_COLS = 40

    fun load(file: File, ext: String): ViewerData = when (ext) {
        "xlsx", "xlsm" -> ViewerData.Table(readXlsx(file))
        "docx" -> ViewerData.Lines(readDocx(file))
        "csv" -> ViewerData.Table(listOf("Datos" to readCsv(file)))
        "txt", "md", "log", "json", "xml" -> ViewerData.Lines(file.readText().take(400_000).lines())
        else -> ViewerData.Unsupported(ext)
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply { setInput(ByteArrayInputStream(bytes), "UTF-8") }

    private fun tag(p: XmlPullParser): String = (p.name ?: "").substringAfter(':')

    private fun readZip(file: File, accept: (String) -> Boolean): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (accept(e.name)) out[e.name] = zin.readBytes()
                e = zin.nextEntry
            }
        }
        return out
    }

    // ----- CSV -----
    private fun readCsv(file: File): List<List<String>> {
        val lines = file.readText().lines().filter { it.isNotBlank() }.take(MAX_ROWS)
        val first = lines.firstOrNull() ?: return emptyList()
        val sep = if (first.count { it == ';' } > first.count { it == ',' }) ';' else ','
        return lines.map { l -> l.split(sep).take(MAX_COLS).map { it.trim().trim('"') } }
    }

    // ----- DOCX -----
    private fun readDocx(file: File): List<String> {
        val files = readZip(file) { it == "word/document.xml" }
        val bytes = files["word/document.xml"] ?: throw IllegalStateException("El Word no tiene contenido legible.")
        val p = parser(bytes)
        val lines = ArrayList<String>()
        val cur = StringBuilder()
        val cell = StringBuilder()
        val rowCells = ArrayList<String>()
        var tbl = 0
        var inRun = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (tag(p)) {
                    "p" -> cur.setLength(0)
                    "r" -> inRun = true
                    "t" -> cur.append(p.nextText())
                    "tab" -> if (inRun) cur.append("    ")
                    "br" -> if (inRun) cur.append(" ")
                    "tbl" -> tbl++
                    "tr" -> rowCells.clear()
                    "tc" -> cell.setLength(0)
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (tag(p)) {
                    "r" -> inRun = false
                    "p" -> if (tbl == 0) {
                        lines.add(cur.toString())
                    } else {
                        if (cell.isNotEmpty()) cell.append(" ")
                        cell.append(cur)
                    }
                    "tc" -> rowCells.add(cell.toString().trim())
                    "tr" -> lines.add("▏ " + rowCells.joinToString("  │  "))
                    "tbl" -> { tbl = maxOf(0, tbl - 1); lines.add("") }
                }
            }
            ev = p.next()
        }
        return lines
    }

    // ----- XLSX -----
    private fun sharedStrings(b: ByteArray?): List<String> {
        if (b == null) return emptyList()
        val p = parser(b)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inPh = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (tag(p)) {
                    "si" -> sb.setLength(0)
                    "rPh" -> inPh = true
                    "t" -> { val t = p.nextText(); if (!inPh) sb.append(t) }
                }
            } else if (ev == XmlPullParser.END_TAG) {
                when (tag(p)) {
                    "si" -> out.add(sb.toString())
                    "rPh" -> inPh = false
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun looksLikeDate(code: String): Boolean {
        val c = code.lowercase().replace(Regex("\"[^\"]*\""), "").replace(Regex("\\[[^\\]]*\\]"), "")
        return Regex("[ymdh]").containsMatchIn(c)
    }

    /** Indices de estilo (atributo s de la celda) que corresponden a fechas u horas. */
    private fun dateStyles(b: ByteArray?): Set<Int> {
        if (b == null) return emptySet()
        val p = parser(b)
        val customDate = HashSet<Int>()
        val xfs = ArrayList<Int>()
        var inXfs = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (tag(p)) {
                    "numFmt" -> {
                        val id = p.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                        val code = p.getAttributeValue(null, "formatCode") ?: ""
                        if (id != null && looksLikeDate(code)) customDate.add(id)
                    }
                    "cellXfs" -> inXfs = true
                    "xf" -> if (inXfs) xfs.add(p.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0)
                }
            } else if (ev == XmlPullParser.END_TAG && tag(p) == "cellXfs") {
                inXfs = false
            }
            ev = p.next()
        }
        val out = HashSet<Int>()
        xfs.forEachIndexed { i, f -> if (f in 14..22 || f in 45..47 || f in customDate) out.add(i) }
        return out
    }

    private fun serialToText(v: String): String {
        val d = v.toDoubleOrNull() ?: return v
        val days = floor(d).toLong()
        val minutes = ((d - days) * 1440.0).roundToLong()
        val date = if (days > 0) LocalDate.of(1899, 12, 30).plusDays(days) else null
        val time = if (minutes > 0) "%02d:%02d".format(minutes / 60, minutes % 60) else null
        return listOfNotNull(
            date?.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) },
            time
        ).joinToString(" ").ifEmpty { v }
    }

    private fun cleanNumber(v: String): String {
        if (!v.contains('.') && !v.contains('E')) return v
        return runCatching { BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString() }.getOrDefault(v)
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break
        }
        return n - 1
    }

    private fun readXlsx(file: File): List<Pair<String, List<List<String>>>> {
        val files = readZip(file) {
            it == "xl/workbook.xml" || it == "xl/_rels/workbook.xml.rels" || it == "xl/sharedStrings.xml" ||
                it == "xl/styles.xml" || it.startsWith("xl/worksheets/")
        }
        val shared = sharedStrings(files["xl/sharedStrings.xml"])
        val dates = dateStyles(files["xl/styles.xml"])

        // relaciones: id -> archivo
        val rels = HashMap<String, String>()
        files["xl/_rels/workbook.xml.rels"]?.let { b ->
            val p = parser(b)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && tag(p) == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        rels[id] = if (target.startsWith("/")) target.drop(1) else "xl/$target"
                    }
                }
                ev = p.next()
            }
        }
        // hojas: nombre + archivo
        val sheetRefs = ArrayList<Pair<String, String>>()
        files["xl/workbook.xml"]?.let { b ->
            val p = parser(b)
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && tag(p) == "sheet") {
                    val name = p.getAttributeValue(null, "name") ?: "Hoja"
                    val rid = p.getAttributeValue(null, "r:id") ?: p.getAttributeValue(null, "id")
                    val path = rid?.let { rels[it] }
                    if (path != null) sheetRefs.add(name to path)
                }
                ev = p.next()
            }
        }
        if (sheetRefs.isEmpty()) throw IllegalStateException("No se encontraron hojas en el Excel.")

        return sheetRefs.mapNotNull { (name, path) ->
            val bytes = files[path] ?: return@mapNotNull null
            name to readSheet(bytes, shared, dates)
        }
    }

    private fun readSheet(bytes: ByteArray, shared: List<String>, dates: Set<Int>): List<List<String>> {
        val p = parser(bytes)
        val grid = HashMap<Int, HashMap<Int, String>>()
        var row = -1
        var col = 0
        var type = ""
        var style = -1
        var v = ""
        val inline = StringBuilder()
        var maxRow = -1
        var maxCol = -1
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (tag(p)) {
                    "row" -> {
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull()
                        row = if (r != null) r - 1 else row + 1
                        col = -1
                    }
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r")
                        col = if (ref != null) colIndex(ref) else col + 1
                        type = p.getAttributeValue(null, "t") ?: ""
                        style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        v = ""
                        inline.setLength(0)
                    }
                    "v" -> v = p.nextText()
                    "t" -> inline.append(p.nextText())
                }
            } else if (ev == XmlPullParser.END_TAG && tag(p) == "c") {
                val text = when (type) {
                    "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> inline.toString()
                    "str", "e" -> v
                    "b" -> if (v == "1") "VERDADERO" else "FALSO"
                    else -> if (v.isEmpty()) "" else if (style in dates) serialToText(v) else cleanNumber(v)
                }
                if (text.isNotEmpty() && row in 0 until MAX_ROWS && col in 0 until MAX_COLS) {
                    grid.getOrPut(row) { HashMap() }[col] = text
                    if (row > maxRow) maxRow = row
                    if (col > maxCol) maxCol = col
                }
            }
            ev = p.next()
        }
        return List(maxRow + 1) { r -> List(maxCol + 1) { c -> grid[r]?.get(c) ?: "" } }
    }
}

// ---------- Interfaz del visor ----------
@Composable
fun FileViewerContent(file: File) {
    val ext = file.extension.lowercase()
    when (ext) {
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> ImageViewer(file)
        "pdf" -> PdfViewer(file)
        else -> DocumentViewer(file, ext)
    }
}

@Composable
private fun ZoomBar(zoom: Float, min: Float, max: Float, onChange: (Float) -> Unit, extra: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF1EFE8)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DialogButton("−", enabled = zoom > min + 0.01f) { onChange((zoom - 0.2f).coerceAtLeast(min)) }
        Text("${(zoom * 100).toInt()}%", fontSize = 14.sp, color = navy, fontWeight = FontWeight.Medium)
        DialogButton("＋", enabled = zoom < max - 0.01f) { onChange((zoom + 0.2f).coerceAtMost(max)) }
        extra()
    }
}

@Composable
private fun DocumentViewer(file: File, ext: String) {
    val data by produceState<ViewerData?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) {
            runCatching { OfficeReader.load(file, ext) }
                .getOrElse { ViewerData.Fail(it.message ?: "No se pudo leer el archivo.") }
        }
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    Column(Modifier.fillMaxSize()) {
        when (val d = data) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Abriendo archivo…", fontSize = 16.sp, color = navy)
            }
            is ViewerData.Fail -> MessageBox("No se pudo mostrar el archivo.\n${d.msg}", file)
            is ViewerData.Unsupported -> MessageBox(
                "El formato .${d.ext} no se puede mostrar dentro de la app.\n" +
                    "Tip: guarda el archivo como .xlsx, .docx o .pdf y cárgalo de nuevo en Ajustes.",
                file
            )
            is ViewerData.Table -> {
                ZoomBar(zoom, 0.6f, 2.6f, { zoom = it })
                TableView(d.sheets, zoom)
            }
            is ViewerData.Lines -> {
                ZoomBar(zoom, 0.6f, 2.6f, { zoom = it })
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    items(d.lines) { line ->
                        Text(line.ifEmpty { " " }, fontSize = (14f * zoom).sp, color = Color(0xFF222222))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBox(text: String, file: File) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, fontSize = 15.sp, color = Color(0xFF333333))
        DialogButton("Abrir con otra app") { openWithOtherApp(ctx, file) }
    }
}

@Composable
private fun TableView(sheets: List<Pair<String, List<List<String>>>>, zoom: Float) {
    var sel by remember { mutableIntStateOf(0) }
    val sheet = sheets.getOrNull(sel) ?: sheets.firstOrNull()
    if (sheet == null) return
    val rows = sheet.second
    val cols = rows.maxOfOrNull { it.size } ?: 0
    val widths = remember(sheet, zoom) {
        List(cols) { c ->
            val m = rows.maxOfOrNull { r -> r.getOrNull(c)?.length ?: 0 } ?: 0
            (m.coerceIn(4, 30) * 7.5f + 18f) * zoom
        }
    }
    val hs = rememberScrollState()
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).background(Color(0xFFE6E8F0)),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sheets.forEachIndexed { i, s ->
                    Text(
                        s.first, fontSize = 14.sp,
                        color = if (i == sel) Color.White else navy,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .background(if (i == sel) navy else Color.Transparent)
                            .clickable { sel = i }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
        if (rows.isEmpty()) {
            Text("La hoja está vacía.", Modifier.padding(16.dp), fontSize = 15.sp)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(rows) { ri, r ->
                    Row(Modifier.horizontalScroll(hs).height(IntrinsicSize.Min)) {
                        for (c in 0 until cols) {
                            Box(
                                Modifier
                                    .width(widths[c].dp)
                                    .fillMaxHeight()
                                    .background(if (ri == 0) Color(0xFFDCE6F5) else Color.White)
                                    .border(0.5.dp, Color(0xFFC9CDD8))
                                    .padding(4.dp)
                            ) {
                                Text(
                                    r.getOrNull(c) ?: "",
                                    fontSize = (13f * zoom).sp,
                                    fontWeight = if (ri == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = Color(0xFF222222)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(file: File) {
    val bmp = remember(file.path) { ImageUtil.load(file, 1800)?.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF222222))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offX += pan.x
                    offY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bmp == null) {
            Text("No se pudo abrir la imagen.", color = Color.White)
        } else {
            Image(
                bitmap = bmp, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offX, translationY = offY)
            )
        }
    }
}

@Composable
private fun PdfViewer(file: File) {
    val ctx = LocalContext.current
    var zoom by remember { mutableFloatStateOf(1f) }
    val renderer = remember(file.path) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }.getOrNull()
    }
    DisposableEffect(renderer) {
        onDispose { runCatching { renderer?.close() } }
    }
    Column(Modifier.fillMaxSize()) {
        ZoomBar(zoom, 1f, 3f, { zoom = it }) {
            Spacer(Modifier.weight(1f))
            DialogButton("Abrir con otra app") { openWithOtherApp(ctx, file) }
        }
        if (renderer == null) {
            Text("No se pudo abrir el PDF.", Modifier.padding(16.dp), fontSize = 15.sp)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFD8D8D8))) {
                val baseW = maxWidth
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    LazyColumn(
                        Modifier.width(baseW * zoom),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(renderer.pageCount) { index ->
                            val page by produceState<Bitmap?>(null, index) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        synchronized(renderer) {
                                            val pg = renderer.openPage(index)
                                            try {
                                                val w = 1400
                                                val h = (w.toFloat() * pg.height / pg.width).toInt().coerceAtLeast(1)
                                                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                                b.eraseColor(android.graphics.Color.WHITE)
                                                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                b
                                            } finally {
                                                pg.close()
                                            }
                                        }
                                    }.getOrNull()
                                }
                            }
                            val pb = page
                            if (pb != null) {
                                Image(
                                    bitmap = pb.asImageBitmap(), contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Box(Modifier.fillMaxWidth().height(300.dp).background(Color.White))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Abre el archivo con otra app instalada (ultima opcion; saca de la agenda por un momento). */
fun openWithOtherApp(ctx: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "*/*"
        }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        Toast.makeText(ctx, "No hay una app instalada que pueda abrir este archivo.", Toast.LENGTH_LONG).show()
    }
}
