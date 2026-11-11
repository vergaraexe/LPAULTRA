package cl.lpa.agenda

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Dibuja una pagina de la agenda (encabezado, logo, cuadricula y trazos) como imagen para el Word. */
object PageRenderer {
    private const val S = 2f           // pixeles por dp
    private const val HEADER_DP = 90f

    fun render(
        ctx: Context, date: LocalDate, strokes: List<InkStroke>, wDp: Float, hDp: Float,
        images: List<PagePhoto> = emptyList()
    ): Bitmap {
        val w = if (wDp > 50f) wDp else 800f
        val h = if (hDp > 50f) hDp else 1000f
        val bmp = Bitmap.createBitmap((w * S).toInt(), ((h + HEADER_DP) * S).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.parseColor("#FFFEFA"))

        // Encabezado: dia y fecha a la izquierda, logo a la derecha
        val dia = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
            .replaceFirstChar { it.uppercase() }
        val fecha = "%02d/%02d/%d".format(date.dayOfMonth, date.monthValue, date.year)
        val titulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AColor.parseColor("#1E2A5A"); textSize = 32f * S; typeface = Typeface.DEFAULT_BOLD
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AColor.parseColor("#555555"); textSize = 16f * S }
        c.drawText(dia, 20f * S, 44f * S, titulo)
        c.drawText(fecha, 20f * S, 70f * S, sub)

        val logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_lpa)
        if (logo != null) {
            val th = 70f * S
            val tw = logo.width * th / logo.height
            val rect = RectF(w * S - 20f * S - tw, 10f * S, w * S - 20f * S, 10f * S + th)
            c.drawBitmap(logo, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        val line = Paint().apply { color = AColor.parseColor("#1E2A5A"); strokeWidth = 2f * S }
        c.drawLine(0f, (HEADER_DP - 2f) * S, w * S, (HEADER_DP - 2f) * S, line)

        // Cuadricula
        c.save()
        c.translate(0f, HEADER_DP * S)
        val grid = Paint().apply { color = AColor.parseColor("#BFD4EA"); strokeWidth = S }
        val step = 26f * S
        var x = 0f
        while (x <= w * S) { c.drawLine(x, 0f, x, h * S, grid); x += step }
        var y = 0f
        while (y <= h * S) { c.drawLine(0f, y, w * S, y, grid); y += step }

        // Fotos pegadas en miniatura
        if (images.isNotEmpty()) {
            val store = DayStore(ctx)
            val frame = Paint().apply { color = AColor.WHITE; style = Paint.Style.FILL }
            val border = Paint().apply { color = AColor.parseColor("#BBBBBB"); style = Paint.Style.STROKE; strokeWidth = S }
            val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            images.forEach { im ->
                val b = ImageUtil.load(store.photoFile(date, im.file), 1000) ?: return@forEach
                val r = RectF(im.x * S, im.y * S, (im.x + im.w) * S, (im.y + im.h) * S)
                val f = RectF(r.left - 4f * S, r.top - 4f * S, r.right + 4f * S, r.bottom + 4f * S)
                c.drawRect(f, frame)
                c.drawRect(f, border)
                val src = Rect(
                    (im.cl * b.width).toInt(), (im.ct * b.height).toInt(),
                    (im.cr * b.width).toInt().coerceAtLeast(1), (im.cb * b.height).toInt().coerceAtLeast(1)
                )
                c.drawBitmap(b, src, r, bp)
                b.recycle()
            }
        }

        // Destacador debajo, lapiz encima
        strokes.filter { it.highlighter }.forEach { drawStroke(c, it) }
        strokes.filter { !it.highlighter }.forEach { drawStroke(c, it) }
        c.restore()
        return bmp
    }

    private fun drawStroke(c: Canvas, st: InkStroke) {
        if (st.points.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.color.toArgb()
            strokeWidth = st.width * S
            strokeCap = if (st.highlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (st.dashed && !st.highlighter) {
                val w = (st.width * S).coerceAtLeast(1f)
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(w, w * 3f), 0f)
            }
        }
        if (st.points.size == 1) {
            paint.style = Paint.Style.FILL
            c.drawCircle(st.points[0].x * S, st.points[0].y * S, st.width * S / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        val path = Path()
        path.moveTo(st.points[0].x * S, st.points[0].y * S)
        for (i in 1 until st.points.size) {
            val p0 = st.points[i - 1]
            val p1 = st.points[i]
            path.quadTo(p0.x * S, p0.y * S, (p0.x + p1.x) / 2f * S, (p0.y + p1.y) / 2f * S)
        }
        path.lineTo(st.points.last().x * S, st.points.last().y * S)
        c.drawPath(path, paint)
    }
}
