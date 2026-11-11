package cl.lpa.agenda

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.io.FileOutputStream

/** Fondo del acta: las hojas del formato original (ACTA_GENERAL.docx) como imagen. */
object ActaAssets {
    fun load(context: Context, kind: String): Bitmap? = try {
        val name = when (kind) {
            ActaStore.K_P2 -> "page2.png"
            ActaStore.K_TEMAS -> "cont_temas.png"
            ActaStore.K_ACUERDOS -> "cont_acuerdos.png"
            else -> "page1.png"
        }
        context.assets.open("acta/$name").use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }
}

/** Genera el PDF en hoja carta (612 x 792) con el formato original, lo escrito y las firmas. */
object ActaPdf {
    fun build(context: Context, pages: List<ActaSnap>, out: File) {
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, snap ->
                val strokes = snap.strokes
                val info = PdfDocument.PageInfo.Builder(612, 792, i + 1).create()
                val page = doc.startPage(info)
                val c: Canvas = page.canvas
                c.drawColor(AColor.WHITE)
                val bg = ActaAssets.load(context, snap.kind)
                if (bg != null) {
                    c.drawBitmap(
                        bg, null, RectF(0f, 0f, 612f, 792f),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    bg.recycle()
                }
                strokes.filter { it.highlighter }.forEach { drawStroke(c, it) }
                strokes.filter { !it.highlighter }.forEach { drawStroke(c, it) }
                doc.finishPage(page)
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    private fun drawStroke(c: Canvas, st: InkStroke) {
        if (st.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.color.toArgb()
            strokeWidth = st.width
            strokeCap = if (st.highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (st.dashed && !st.highlighter) {
                val w = st.width.coerceAtLeast(0.5f)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(w, w * 3f), 0f)
            }
        }
        if (st.points.size == 1) {
            paint.style = Paint.Style.FILL
            c.drawCircle(st.points[0].x, st.points[0].y, st.width / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        val path = Path()
        path.moveTo(st.points[0].x, st.points[0].y)
        for (i in 1 until st.points.size) {
            val p0 = st.points[i - 1]
            val p1 = st.points[i]
            path.quadTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        }
        path.lineTo(st.points.last().x, st.points.last().y)
        c.drawPath(path, paint)
    }
}
