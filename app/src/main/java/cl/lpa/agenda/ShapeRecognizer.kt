package cl.lpa.agenda

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reconoce el trazo (puntos en dp) y lo convierte en una forma perfecta:
 * linea recta, circulo, elipse, rectangulo/cuadrado, triangulo u otro cuadrilatero.
 * Devuelve null si el trazo no se parece a ninguna forma (se deja como esta).
 */
object ShapeRecognizer {

    fun recognize(raw: List<Offset>, linesOnly: Boolean = false): List<Offset>? {
        if (raw.size < 8) return null
        val pts = resample(raw, 64) ?: return null

        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        val diag = hypot(w, h)
        if (diag < 24f) return null

        val len = pathLength(pts)
        val first = pts.first()
        val last = pts.last()
        val gap = dist(first, last)

        // 1) Linea recta
        if (gap > 30f && gap > 0.8f * len) {
            val maxDev = pts.maxOf { distToLine(it, first, last) }
            if (maxDev <= max(5f, 0.08f * gap)) return line(first, last)
        }
        if (linesOnly) return null

        // 2) Formas cerradas
        if (len < 60f || gap > 0.22f * len) return null

        // 2a) Circulo / elipse
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val a = w / 2f
        val b = h / 2f
        if (a > 8f && b > 8f) {
            var sum = 0f
            var worst = 0f
            for (p in pts) {
                val e = sqrt(((p.x - cx) / a) * ((p.x - cx) / a) + ((p.y - cy) / b) * ((p.y - cy) / b))
                val err = abs(e - 1f)
                sum += err
                if (err > worst) worst = err
            }
            if (sum / pts.size < 0.09f && worst < 0.28f) {
                return if (abs(a - b) / max(a, b) < 0.18f) {
                    val r = (a + b) / 2f
                    ellipse(cx, cy, r, r)
                } else {
                    ellipse(cx, cy, a, b)
                }
            }
        }

        // 2b) Poligonos: triangulo, rectangulo / cuadrilatero
        val simplified = rdp(pts, 0.07f * diag).toMutableList()
        if (simplified.size > 3 && dist(simplified.last(), simplified.first()) < 0.2f * diag) {
            simplified.removeAt(simplified.lastIndex)
        }
        val v = cleanPolygon(simplified, diag)
        return when (v.size) {
            3 -> polygon(v)
            4 -> {
                if (allEdgesNearAxis(v)) {
                    val x0 = v.minOf { it.x }
                    val x1 = v.maxOf { it.x }
                    val y0 = v.minOf { it.y }
                    val y1 = v.maxOf { it.y }
                    polygon(listOf(Offset(x0, y0), Offset(x1, y0), Offset(x1, y1), Offset(x0, y1)))
                } else {
                    polygon(v)
                }
            }
            else -> null
        }
    }

    // ---------- formas de salida (con puntos densos para que las esquinas queden nitidas) ----------
    private fun line(a: Offset, b: Offset): List<Offset> {
        val dx = abs(b.x - a.x)
        val dy = abs(b.y - a.y)
        val deg = Math.toDegrees(atan2(dy, dx).toDouble())
        var p = a
        var q = b
        if (deg < 6.0) { val y = (a.y + b.y) / 2f; p = Offset(a.x, y); q = Offset(b.x, y) }
        else if (deg > 84.0) { val x = (a.x + b.x) / 2f; p = Offset(x, a.y); q = Offset(x, b.y) }
        return densify(listOf(p, q))
    }

    private fun ellipse(cx: Float, cy: Float, a: Float, b: Float): List<Offset> =
        List(97) { k ->
            val t = 2.0 * PI * k / 96.0
            Offset(cx + a * cos(t).toFloat(), cy + b * sin(t).toFloat())
        }

    private fun polygon(v: List<Offset>): List<Offset> = densify(v + v.first())

    private fun densify(poly: List<Offset>, step: Float = 4f): List<Offset> {
        val out = ArrayList<Offset>()
        for (i in 0 until poly.size - 1) {
            val p = poly[i]
            val q = poly[i + 1]
            val n = max(1, (dist(p, q) / step).toInt())
            for (k in 0 until n) {
                out.add(Offset(p.x + (q.x - p.x) * k / n, p.y + (q.y - p.y) * k / n))
            }
        }
        out.add(poly.last())
        return out
    }

    // ---------- geometria ----------
    private fun dist(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)

    private fun pathLength(p: List<Offset>): Float {
        var s = 0f
        for (i in 1 until p.size) s += dist(p[i - 1], p[i])
        return s
    }

    private fun distToLine(p: Offset, a: Offset, b: Offset): Float {
        val l = dist(a, b)
        if (l < 1e-3f) return dist(p, a)
        return abs((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) / l
    }

    private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-6f) return dist(p, a)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        return dist(p, Offset(a.x + t * dx, a.y + t * dy))
    }

    private fun angleAt(prev: Offset, cur: Offset, next: Offset): Float {
        val ax = prev.x - cur.x
        val ay = prev.y - cur.y
        val bx = next.x - cur.x
        val by = next.y - cur.y
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la < 1e-3f || lb < 1e-3f) return 180f
        val c = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(c).toDouble()).toFloat()
    }

    private fun allEdgesNearAxis(v: List<Offset>): Boolean {
        for (i in v.indices) {
            val p = v[i]
            val q = v[(i + 1) % v.size]
            val ang = Math.toDegrees(abs(atan2((q.y - p.y).toDouble(), (q.x - p.x).toDouble()))).toFloat()
            val near = ang < 12f || ang > 168f || abs(ang - 90f) < 12f
            if (!near) return false
        }
        return true
    }

    /** Ramer-Douglas-Peucker: simplifica el trazo a sus esquinas principales. */
    private fun rdp(p: List<Offset>, eps: Float): List<Offset> {
        if (p.size < 3) return p
        var maxD = 0f
        var idx = 0
        for (i in 1 until p.size - 1) {
            val d = distToSegment(p[i], p.first(), p.last())
            if (d > maxD) { maxD = d; idx = i }
        }
        return if (maxD > eps) {
            val l = rdp(p.subList(0, idx + 1), eps)
            val r = rdp(p.subList(idx, p.size), eps)
            l.dropLast(1) + r
        } else {
            listOf(p.first(), p.last())
        }
    }

    /** Une vertices muy juntos y elimina los que estan casi en linea recta. */
    private fun cleanPolygon(input: List<Offset>, diag: Float): List<Offset> {
        val v = input.toMutableList()
        var changed = true
        while (changed && v.size > 3) {
            changed = false
            for (i in v.indices) {
                val j = (i + 1) % v.size
                if (dist(v[i], v[j]) < 0.12f * diag) {
                    v[i] = Offset((v[i].x + v[j].x) / 2f, (v[i].y + v[j].y) / 2f)
                    v.removeAt(j)
                    changed = true
                    break
                }
            }
            if (changed) continue
            for (i in v.indices) {
                val prev = v[(i - 1 + v.size) % v.size]
                val next = v[(i + 1) % v.size]
                if (angleAt(prev, v[i], next) > 150f) {
                    v.removeAt(i)
                    changed = true
                    break
                }
            }
        }
        return v
    }

    /** Remuestrea el trazo a n puntos equidistantes. */
    private fun resample(p: List<Offset>, n: Int): List<Offset>? {
        val total = pathLength(p)
        if (total < 1f) return null
        val step = total / (n - 1)
        val pts = p.toMutableList()
        val out = ArrayList<Offset>()
        out.add(pts[0])
        var acc = 0f
        var i = 1
        while (i < pts.size) {
            val d = dist(pts[i - 1], pts[i])
            if (d > 0f && acc + d >= step) {
                val t = (step - acc) / d
                val q = Offset(
                    pts[i - 1].x + t * (pts[i].x - pts[i - 1].x),
                    pts[i - 1].y + t * (pts[i].y - pts[i - 1].y)
                )
                out.add(q)
                pts.add(i, q)
                acc = 0f
            } else {
                acc += d
            }
            i++
        }
        if (out.size < n) out.add(pts.last())
        return out
    }
}
