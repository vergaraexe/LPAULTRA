package cl.lpa.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext

/** Logica de la calculadora (suma, resta, multiplicacion, division, porcentaje). */
class CalcState {
    var display by mutableStateOf("0")
        private set
    var expr by mutableStateOf("")
        private set

    private var acc: Double? = null
    private var op: Char? = null
    private var fresh = true

    private fun cur(): Double = display.toDoubleOrNull() ?: 0.0

    fun digit(c: Char) {
        if (display == "Error") clear()
        if (fresh) {
            display = if (c == '.') "0." else c.toString()
            fresh = false
        } else {
            if (c == '.' && display.contains('.')) return
            if (display.replace("-", "").replace(".", "").length >= 14) return
            display = if (display == "0" && c != '.') c.toString() else display + c
        }
    }

    fun operator(o: Char) {
        if (display == "Error") return
        val a = acc
        val p = op
        if (a != null && p != null && !fresh) {
            val r = compute(a, cur(), p)
            if (r == null) {
                fail()
                return
            }
            acc = r
            display = format(r)
        } else {
            acc = cur()
        }
        op = o
        fresh = true
        expr = format(acc ?: 0.0) + " " + o
    }

    fun evaluate() {
        val a = acc
        val p = op
        if (a == null || p == null) return
        val b = cur()
        val r = compute(a, b, p)
        if (r == null) {
            fail()
            return
        }
        expr = format(a) + " " + p + " " + format(b) + " ="
        display = format(r)
        acc = null
        op = null
        fresh = true
    }

    fun clear() {
        display = "0"
        expr = ""
        acc = null
        op = null
        fresh = true
    }

    fun back() {
        if (fresh || display == "Error") return
        display = display.dropLast(1)
        if (display.isEmpty() || display == "-") display = "0"
    }

    fun negate() {
        if (display == "Error" || display == "0") return
        display = if (display.startsWith("-")) display.drop(1) else "-$display"
    }

    fun percent() {
        if (display == "Error") return
        val a = acc
        val p = op
        val v = cur()
        val r = if (a != null && p != null && (p == '+' || p == '−')) a * v / 100.0 else v / 100.0
        display = format(r)
        fresh = true
    }

    private fun compute(a: Double, b: Double, p: Char): Double? = when (p) {
        '+' -> a + b
        '−' -> a - b
        '×' -> a * b
        '÷' -> if (b == 0.0) null else a / b
        else -> b
    }

    private fun fail() {
        display = "Error"
        expr = ""
        acc = null
        op = null
        fresh = true
    }

    private fun format(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Error"
        val s = BigDecimal(d).round(MathContext(12)).stripTrailingZeros().toPlainString()
        return if (s == "-0") "0" else s
    }
}

private val calcRows = listOf(
    listOf("C", "⌫", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("±", "0", ".", "=")
)

private fun press(c: CalcState, k: String) {
    when (k) {
        "C" -> c.clear()
        "⌫" -> c.back()
        "%" -> c.percent()
        "÷", "×", "−", "+" -> c.operator(k[0])
        "=" -> c.evaluate()
        "±" -> c.negate()
        "." -> c.digit('.')
        else -> c.digit(k[0])
    }
}

@Composable
fun CalculatorContent() {
    val calc = remember { CalcState() }
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F5FA)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            calc.expr, fontSize = 14.sp, color = Color(0xFF666666), maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
        )
        Text(
            calc.display, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = navy, maxLines = 1,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()
        )
        calcRows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { k ->
                    CalcKey(k, Modifier.weight(1f).fillMaxHeight()) { press(calc, k) }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    val isOp = label in listOf("÷", "×", "−", "+")
    val bg = when {
        label == "=" -> Color(0xFF2E7D32)
        isOp -> navy
        label == "C" || label == "⌫" || label == "%" || label == "±" -> Color(0xFFD5D9E6)
        else -> Color.White
    }
    val fg = if (label == "=" || isOp) Color.White else navy
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}
