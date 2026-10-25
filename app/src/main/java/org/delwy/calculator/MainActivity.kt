package org.delwy.calculator

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.*
import androidx.compose.ui.res.stringResource

val Inter = FontFamily(Font(R.font.inter_extrabold, FontWeight.ExtraBold))
const val ERR = "Ошибка"


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideBars()
        setContent { App() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val pal = remember { buildPal(ctx) }
    val tm = rememberTextMeasurer()
    val d = LocalDensity.current
    val fit = remember(tm, d) { Fit(tm, d) }
    CompositionLocalProvider(LocalPal provides pal, LocalFit provides fit) { Shell() }
}


private val OutQuad = Easing { 1f - (1f - it) * (1f - it) }
private val OutCubic = Easing { val q = 1f - it; 1f - q * q * q }
private val InCubic = Easing { it * it * it }
private val InOutCubic = Easing {
    if (it < 0.5f) 4f * it * it * it else { val q = -2f * it + 2f; 1f - q * q * q / 2f }
}
private val OutExpo = Easing { if (it >= 1f) 1f else 1f - 2f.pow(-10f * it) }
private val OutBack = Easing { val q = it - 1f; q * q * (2.70158f * q + 1.70158f) + 1f }


fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t
)

class Pal(
    val surface: Color, val sheet: Color, val surfCont: Color, val btnEng: Color, val btnNum: Color,
    val onSurface: Color, val onSurfaceVar: Color, val primary: Color, val onPrimary: Color,
    val primaryCont: Color, val onPrimaryCont: Color, val secCont: Color, val onSecCont: Color,
    val terCont: Color, val onTerCont: Color
) {
    val error = Color(0xFFF2B8B5)
    val dim = onSurfaceVar.copy(alpha = 0.72f)
}

val LocalPal = staticCompositionLocalOf<Pal> { error("Pal") }

private val STEPS = listOf(
    100 to "0", 99 to "10", 95 to "50", 90 to "100", 80 to "200", 70 to "300", 60 to "400",
    50 to "500", 40 to "600", 30 to "700", 20 to "800", 10 to "900", 0 to "1000"
)

fun buildPal(ctx: Context): Pal {
    val monet: Map<String, Map<Int, Color>>? =
        if (Build.VERSION.SDK_INT >= 31) runCatching {
            listOf("accent1", "accent2", "accent3", "neutral1", "neutral2").associateWith { key ->
                STEPS.associate { (t, n) ->
                    val id = ctx.resources.getIdentifier("system_${key}_$n", "color", "android")
                    t to Color(ctx.getColor(id))
                }
            }
        }.getOrNull() else null

    fun c(fallback: Long, key: String, tone: Int): Color {
        val pl = monet?.get(key) ?: return Color(fallback)
        val ts = pl.keys.sorted()
        if (tone <= ts.first()) return pl.getValue(ts.first())
        for (i in 0 until ts.size - 1) {
            val lo = ts[i]
            val hi = ts[i + 1]
            if (tone in lo..hi) {
                return mix(pl.getValue(lo), pl.getValue(hi), (tone - lo) / (hi - lo).toFloat())
            }
        }
        return pl.getValue(ts.last())
    }

    return Pal(
        surface = c(0xFF141218, "neutral1", 6),
        sheet = c(0xFF1D1B20, "neutral1", 10),
        surfCont = c(0xFF211F26, "neutral1", 12),
        btnEng = c(0xFF2B2930, "neutral1", 17),
        btnNum = c(0xFF36343B, "neutral1", 22),
        onSurface = c(0xFFE6E0E9, "neutral1", 90),
        onSurfaceVar = c(0xFFCAC4D0, "neutral2", 80),
        primary = c(0xFFD0BCFF, "accent1", 80),
        onPrimary = c(0xFF381E72, "accent1", 20),
        primaryCont = c(0xFF4F378B, "accent1", 30),
        onPrimaryCont = c(0xFFEADDFF, "accent1", 90),
        secCont = c(0xFF4A4458, "accent2", 30),
        onSecCont = c(0xFFE8DEF8, "accent2", 90),
        terCont = c(0xFF633B48, "accent3", 30),
        onTerCont = c(0xFFFFD8E4, "accent3", 90)
    )
}

// ═════════════════════════ Подгонка шрифта ═════════════════════════

private fun Density.fs(v: Float): TextUnit = v.dp.toSp()

class Fit(private val tm: TextMeasurer, private val d: Density) {
    private val cache = HashMap<String, Float>()

    private fun width(text: String): Float {
        cache[text]?.let { return it }
        if (cache.size > 300) cache.clear()
        val r = tm.measure(
            text,
            TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = d.fs(20f)),
            softWrap = false, maxLines = 1
        )
        val w = r.size.width / d.density
        cache[text] = w
        return w
    }

    fun font(text: String, mx: Float, mn: Float, avail: Float): Float {
        val w = max(width(text.ifEmpty { " " }), 1f)
        return max(mn, min(mx, 20f * (avail * 0.97f) / w))
    }
}

val LocalFit = staticCompositionLocalOf<Fit> { error("Fit") }

private fun ts(color: Color, size: TextUnit, align: TextAlign = TextAlign.Unspecified) =
    TextStyle(color = color, fontSize = size, fontFamily = Inter, fontWeight = FontWeight.ExtraBold, textAlign = align)


private val TOKEN_RE = Regex(
    """(\d+\.?\d*(?:E[+\-−]?\d+)?|\.\d+(?:E[+\-−]?\d+)?)|(asin|acos|atan|sin|cos|tan|ln|log|√)|(π|e)|(\S)"""
)

private class Tok(val k: Char, val s: String = "", val v: Double = 0.0)

private fun gamma(x: Double): Double {
    if (x < 0.5) return PI / (sin(PI * x) * gamma(1 - x))
    val c = doubleArrayOf(
        0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
        -176.61502916214059, 12.507343278686905, -0.13857109526572012,
        9.9843695780195716e-6, 1.5056327351493116e-7
    )
    val xx = x - 1
    var a = c[0]
    val t = xx + 7.5
    for (i in 1 until 9) a += c[i] / (xx + i)
    return sqrt(2 * PI) * t.pow(xx + 0.5) * exp(-t) * a
}

private fun factorial(v: Double): Double {
    if (v < 0) throw ArithmeticException()
    val rv = Math.rint(v)
    if (abs(v - rv) < 1e-9) {
        if (rv > 170) throw ArithmeticException()
        var r = 1.0
        for (k in 2..rv.toInt()) r *= k
        return r
    }
    if (v > 170) throw ArithmeticException()
    return gamma(v + 1)
}

private class Ev(private val t: List<Tok>, private val deg: Boolean) {
    private var pos = 0

    private fun isOp(vararg ops: String) = pos < t.size && t[pos].k == 'o' && t[pos].s in ops

    fun run(): Double {
        val v = expr()
        if (pos != t.size) throw IllegalStateException()
        return v
    }

    private fun expr(): Double {
        var v = term().first
        while (isOp("+", "-")) {
            val op = t[pos++].s
            val pr = term()
            var r = pr.first
            if (pr.second) r = v * r
            v = if (op == "+") v + r else v - r
        }
        return v
    }

    private fun term(): Pair<Double, Boolean> {
        val first = unary()
        var v = first.first
        var pct = first.second
        while (true) {
            if (isOp("×", "÷")) {
                val op = t[pos++].s
                val r = unary().first
                if (op == "×") v *= r else {
                    if (r == 0.0) throw ArithmeticException()
                    v /= r
                }
                pct = false
            } else if ((pos < t.size && (t[pos].k == 'n' || t[pos].k == 'f')) || isOp("(")) {
                v *= unary().first
                pct = false
            } else break
        }
        return v to pct
    }

    private fun unary(): Pair<Double, Boolean> {
        if (isOp("-")) { pos++; return (-unary().first) to false }
        if (isOp("+")) { pos++; return unary() }
        return power()
    }

    private fun power(): Pair<Double, Boolean> {
        val pr = postfix()
        if (isOp("^")) {
            pos++
            val e = unary().first
            val r = pr.first.pow(e)
            if (r.isNaN() || r.isInfinite()) throw ArithmeticException()
            return r to false
        }
        return pr
    }

    private fun postfix(): Pair<Double, Boolean> {
        var v = primary()
        var pct = false
        while (true) {
            if (isOp("!")) { pos++; v = factorial(v); pct = false }
            else if (isOp("%")) { pos++; v /= 100.0; pct = true }
            else break
        }
        return v to pct
    }

    private fun primary(): Double {
        if (pos >= t.size) throw IllegalStateException()
        val tk = t[pos++]
        when (tk.k) {
            'n' -> return tk.v
            'f' -> {
                val a: Double
                if (isOp("(")) {
                    pos++
                    a = expr()
                    if (isOp(")")) pos++
                } else a = postfix().first
                return func(tk.s, a)
            }
            'o' -> if (tk.s == "(") {
                val a = expr()
                if (isOp(")")) pos++
                return a
            }
        }
        throw IllegalStateException()
    }

    private fun func(n: String, x: Double): Double {
        when (n) {
            "sin", "cos", "tan" -> {
                val a = if (deg) Math.toRadians(x) else x
                val r = when (n) {
                    "sin" -> sin(a)
                    "cos" -> cos(a)
                    else -> {
                        if (abs(cos(a)) < 1e-12) throw ArithmeticException()
                        tan(a)
                    }
                }
                return if (abs(r) < 1e-12) 0.0 else r
            }
            "asin", "acos", "atan" -> {
                if ((n == "asin" || n == "acos") && abs(x) > 1) throw ArithmeticException()
                val r = when (n) { "asin" -> asin(x); "acos" -> acos(x); else -> atan(x) }
                return if (deg) Math.toDegrees(r) else r
            }
            "ln" -> { if (x <= 0) throw ArithmeticException(); return ln(x) }
            "log" -> { if (x <= 0) throw ArithmeticException(); return log10(x) }
            "√" -> { if (x < 0) throw ArithmeticException(); return sqrt(x) }
        }
        throw IllegalStateException()
    }
}

fun evaluate(text: String, deg: Boolean): Double {
    val s = text.replace('−', '-')
    val toks = ArrayList<Tok>()
    for (m in TOKEN_RE.findAll(s)) {
        when {
            m.groups[1] != null -> toks += Tok('n', v = m.groupValues[1].toDouble())
            m.groups[2] != null -> toks += Tok('f', m.groupValues[2])
            m.groups[3] != null -> toks += Tok('n', v = if (m.groupValues[3] == "π") PI else E)
            else -> toks += Tok('o', m.groupValues[4])
        }
    }
    return Ev(toks, deg).run()
}

fun pyG(x: Double, p: Int): String {
    if (x == 0.0) return "0"
    val bd = BigDecimal(x).round(MathContext(p, RoundingMode.HALF_EVEN))
    val exp = bd.precision() - bd.scale() - 1
    return if (exp < -4 || exp >= p) {
        val digits = bd.unscaledValue().abs().toString().trimEnd('0').ifEmpty { "0" }
        val mant = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        (if (bd.signum() < 0) "-" else "") + mant + "e" + (if (exp < 0) "-" else "+") +
            abs(exp).toString().padStart(2, '0')
    } else bd.stripTrailingZeros().toPlainString()
}

fun fmt(x: Double): String {
    if (x.isNaN() || x.isInfinite()) throw ArithmeticException()
    if (x == 0.0) return "0"
    if (abs(x) < 1e15 && x == floor(x)) return x.toLong().toString().replace('-', '−')
    var s = pyG(x, 12)
    if ('e' in s) {
        val parts = s.split('e')
        s = parts[0] + "E" + parts[1].toInt()
    }
    return s.replace('-', '−')
}

fun groupDigits(s0: String): String {
    var s = s0
    val neg = s.startsWith("-")
    if (neg) s = s.drop(1)
    val idx = s.indexOf('.')
    val ip = if (idx < 0) s else s.substring(0, idx)
    val tail = if (idx < 0) "" else s.substring(idx)
    val grouped = ip.reversed().chunked(3).joinToString(" ").reversed()
    return (if (neg) "-" else "") + grouped + tail
}

fun plainStr(x: Double): String {
    val s = pyG(x, 10)
    return if ('e' in s) BigDecimal(s).toPlainString() else s
}

fun convText(x: Double): String {
    if (x == 0.0) return "0"
    val a = abs(x)
    if (a >= 1e15 || a < 1e-9) return fmt(x)
    return groupDigits(plainStr(x))
}


enum class Mode(val title: String) {
    CALC(R.string.mode_calc),
    WEIGHT(R.strig.mode_weight),
    TEMP(R.string.mode_gradus),
    TIME(R.string.mode_time)
}

class Meas(val abbr: String, val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

private fun unit(abbr: String, name: String, f: Double) = Meas(abbr, name, { it * f }, { it / f })

val UNITS: Map<Mode, List<Meas>> = mapOf(
    Mode.WEIGHT to listOf(
        unit("кг", "Килограммы", 1.0),
        unit("г", "Граммы", 0.001),
        unit("т", "Тонны", 1000.0),
        unit("lb", "Фунты", 0.45359237),
        unit("oz", "Унции", 0.028349523125)
    ),
    Mode.TEMP to listOf(
        Meas("°C", "Цельсий", { it }, { it }),
        Meas("°F", "Фаренгейт", { (it - 32) * 5.0 / 9.0 }, { it * 9.0 / 5.0 + 32 }),
        Meas("K", "Кельвин", { it - 273.15 }, { it + 273.15 })
    ),
    Mode.TIME to listOf(
        unit("с", "Секунды", 1.0),
        unit("мин", "Минуты", 60.0),
        unit("ч", "Часы", 3600.0),
        unit("дн", "Дни", 86400.0),
        unit("нед", "Недели", 604800.0)
    )
)

class ConvModel(val units: List<Meas>) {
    var active by mutableIntStateOf(0)
    var buf by mutableStateOf("")
    var fresh by mutableStateOf(false)
    var exact by mutableStateOf<Double?>(null)

    private fun value() = buf.toDoubleOrNull() ?: 0.0

    private fun base(): Double {
        val ex = exact
        return if (fresh && ex != null) ex else units[active].toBase(value())
    }

    private fun digit(d: Char) {
        if (fresh) { buf = ""; fresh = false }
        var b = buf
        if (b.count { it.isDigit() } >= 12) return
        b = if (b == "0" || b == "-0") {
            if (d != '0') b.dropLast(1) + d else b
        } else b + d
        buf = b
    }

    fun press(key: String) {
        when {
            key.length == 1 && key[0] in '0'..'9' -> digit(key[0])
            key == "00" -> { digit('0'); digit('0') }
            key == "." -> {
                if (fresh) { buf = ""; fresh = false }
                if ('.' in buf) return
                buf += if (buf == "" || buf == "-") "0." else "."
            }
            key == "±" -> {
                exact = null
                buf = if (buf == "") "-0" else if (buf.startsWith("-")) buf.drop(1) else "-$buf"
            }
            key == "DEL" -> {
                fresh = false
                buf = buf.dropLast(1)
                if (buf == "-") buf = ""
            }
            key == "AC" -> { buf = ""; fresh = false }
        }
    }

    fun select(idx: Int) {
        if (idx == active) return
        val b = base()
        val x = units[idx].fromBase(b)
        active = idx
        buf = if (x == 0.0) "" else plainStr(x)
        fresh = buf.isNotEmpty()
        exact = if (fresh) b else null
    }

    fun texts(): List<String> {
        val b = base()
        return units.mapIndexed { i, u ->
            val t = if (i == active) {
                if (buf.isNotEmpty()) groupDigits(buf) else "0"
            } else convText(u.fromBase(b))
            t.replace('-', '−')
        }
    }
}

private const val OPS = "+−×÷^"
private const val POSTFIX_OK = "0123456789.)πe!%"
private val FUNCS = setOf("sin", "cos", "tan", "ln", "log", "√")
private val FUNC_TOKENS = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "log(", "ln(", "√(")
private val CONTINUE = setOf("+", "−", "×", "÷", "x^y", "%", "!", "x²", "1/x", "DEL")
private val PLAIN = Regex("[\\d.]+")

val MAIN_KEYS = listOf(
    "AC", "DEL", "%", "÷",
    "7", "8", "9", "×",
    "4", "5", "6", "−",
    "1", "2", "3", "+",
    "fx", "0", ".", "="
)

val ENG_KEYS = listOf(
    "deg" to "DEG", "inv" to "INV", "π" to "π", "e" to "e",
    "sin" to "sin", "cos" to "cos", "tan" to "tan", "ln" to "ln",
    "log" to "log", "√" to "√", "x²" to "x²", "x^y" to "x^y",
    "(" to "(", ")" to ")", "!" to "n!", "1/x" to "1/x"
)

class CalcState {
    var expr by mutableStateOf("")
    var result = ""
    var errExpr = ""
    var showing by mutableStateOf(false)
    var deg by mutableStateOf(true)
    var inv by mutableStateOf(false)
    var engOpen by mutableStateOf(false)
    var exprLabel by mutableStateOf("0")
    var resText by mutableStateOf("")
    var resErr by mutableStateOf(false)

    private fun sync() { exprLabel = expr.ifEmpty { "0" } }

    fun press(k: String) {
        when (k) {
            "fx" -> { engOpen = !engOpen; return }
            "deg" -> { deg = !deg; return }
            "inv" -> { inv = !inv; return }
            "=" -> { calc(); return }
            "AC" -> { expr = ""; showing = false; sync(); return }
        }
        var pending = ""
        if (showing) {
            showing = false
            val err = result == ERR
            val res = if (err) "" else result
            if (k == "DEL" && err) expr = errExpr
            else if (k in CONTINUE) expr = res
            else if (k in FUNCS) { expr = ""; pending = res }
            else expr = ""
        }
        apply(k)
        if (pending.isNotEmpty() && k in FUNCS) expr += pending
        sync()
    }

    private fun apply(k: String) {
        var e = expr
        if (e.length > 120 && k != "DEL") return
        when {
            k.length == 1 && k[0].isDigit() -> {
                val cur = e.takeLastWhile { it.isDigit() || it == '.' }
                e = if (cur == "0") e.dropLast(1) + k else e + k
            }
            k == "." -> {
                val cur = e.takeLastWhile { it.isDigit() || it == '.' }
                if ('.' in cur) return
                e += if (cur.isEmpty()) "0." else "."
            }
            k == "−" -> e = if (e.isNotEmpty() && e.last() in "+−") e.dropLast(1) + "−" else e + "−"
            k == "+" || k == "×" || k == "÷" || k == "x^y" -> {
                val op = if (k == "x^y") "^" else k
                val base = e.trimEnd { it in OPS }
                if (base.isEmpty() || base.endsWith("(")) return
                e = base + op
            }
            k == "%" || k == "!" || k == "x²" -> {
                if (e.isNotEmpty() && e.last() in POSTFIX_OK) {
                    e += when (k) { "%" -> "%"; "!" -> "!"; else -> "^2" }
                }
            }
            k == "(" -> e += "("
            k == ")" -> {
                if (e.count { it == '(' } > e.count { it == ')' } && e.isNotEmpty() && e.last() !in "$OPS(") e += ")"
            }
            k == "π" || k == "e" -> e += k
            k in FUNCS -> {
                var name = k
                if (inv && (k == "sin" || k == "cos" || k == "tan")) {
                    name = "a$k"
                    inv = false
                }
                e += "$name("
            }
            k == "1/x" -> {
                if (e.isNotEmpty() && e.last() !in "$OPS(") e = "1÷($e)"
            }
            k == "DEL" -> {
                val f = FUNC_TOKENS.firstOrNull { e.endsWith(it) }
                e = if (f != null) e.dropLast(f.length) else e.dropLast(1)
            }
        }
        expr = e
    }

    private fun calc() {
        if (showing || expr.isEmpty()) return
        val e = expr.trimEnd { it in OPS }
        if (e.isEmpty()) return
        val opened = e.count { it == '(' } - e.count { it == ')' }
        val shown = e + ")".repeat(max(opened, 0))
        if (PLAIN.matches(e)) { expr = e; sync(); return }
        val res = try { fmt(evaluate(e, deg)) } catch (ex: Exception) { ERR }
        expr = shown
        errExpr = shown
        result = res
        exprLabel = if (res == ERR) shown else "$shown ="
        resText = res
        resErr = res == ERR
        showing = true
    }
}


class Press(val scale: Animatable<Float, AnimationVector1D>, val hold: Animatable<Float, AnimationVector1D>)

@Composable
fun rememberPress(pressed: Boolean, pScale: Float, dDown: Int, dDelay: Int, dScale: Int, dHold: Int): Press {
    val pr = remember { Press(Animatable(1f), Animatable(0f)) }
    LaunchedEffect(pressed) {
        if (pressed) {
            coroutineScope {
                launch { pr.scale.animateTo(pScale, tween(dDown, easing = OutQuad)) }
                launch { pr.hold.animateTo(1f, tween(dDown, easing = OutQuad)) }
            }
        } else {
            delay(dDelay.toLong())
            coroutineScope {
                launch { pr.scale.animateTo(1f, tween(dScale, easing = OutBack)) }
                launch { pr.hold.animateTo(0f, tween(dHold, easing = OutQuad)) }
            }
        }
    }
    return pr
}

class KS(val bg: Color, val fg: Color, val ratio: Float, val bgOn: Color?, val fgOn: Color?)

fun keyStyle(p: Pal, key: String, kind: String, fr: Float?): KS {
    var bg = p.btnNum
    var fg = p.onSurface
    var ratio = 0.40f
    var bgOn: Color? = null
    var fgOn: Color? = null
    if (kind == "eng") {
        bg = p.btnEng; fg = p.primary; ratio = 0.42f
        when (key) {
            "π", "e", "(", ")" -> fg = p.onSurface
            "deg" -> { bg = p.secCont; fg = p.onSecCont }
            "inv" -> { bgOn = p.primaryCont; fgOn = p.onPrimaryCont }
        }
    } else {
        when {
            (key.length == 1 && key[0].isDigit()) || key == "." || key == "00" -> {
                bg = p.btnNum; fg = p.onSurface
            }
            key in setOf("+", "−", "×", "÷", "%", "±") -> {
                bg = p.secCont; fg = p.onSecCont; ratio = 0.44f
            }
            key == "AC" || key == "DEL" -> {
                bg = p.terCont; fg = p.onTerCont; ratio = 0.36f
            }
            key == "=" -> {
                bg = p.primary; fg = p.onPrimary; ratio = 0.48f
            }
            else -> {
                bg = p.btnEng; fg = p.primary; ratio = 0.38f
                bgOn = p.primaryCont; fgOn = p.onPrimaryCont
            }
        }
    }
    if (fr != null) ratio = fr
    return KS(bg, fg, ratio, bgOn, fgOn)
}

private fun DrawScope.drawBackspace(color: Color, m: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val iw = m * 0.46f
    val ih = m * 0.30f
    val d = ih * 0.6f
    val x0 = cx - iw / 2f
    val sw = 1.8.dp.toPx()
    val path = Path().apply {
        moveTo(x0, cy)
        lineTo(x0 + d, cy - ih / 2f)
        lineTo(cx + iw / 2f, cy - ih / 2f)
        lineTo(cx + iw / 2f, cy + ih / 2f)
        lineTo(x0 + d, cy + ih / 2f)
        close()
    }
    drawPath(path, color, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val xc = cx + d / 2f
    val s = ih * 0.2f
    drawLine(color, Offset(xc - s, cy - s), Offset(xc + s, cy + s), sw, StrokeCap.Round)
    drawLine(color, Offset(xc - s, cy + s), Offset(xc + s, cy - s), sw, StrokeCap.Round)
}

@Composable
fun CalcButton(
    text: String, bg: Color, fg: Color, ratio: Float, modifier: Modifier,
    backspace: Boolean = false, bgOn: Color? = null, fgOn: Color? = null,
    active: Boolean = false, onClick: () -> Unit
) {
    val d = LocalDensity.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pr = rememberPress(pressed, 0.92f, 100, 50, 380, 300)
    val bgC by animateColorAsState(
        if (active && bgOn != null) bgOn else bg, tween(250, easing = OutCubic), label = "bg"
    )
    val fgC by animateColorAsState(
        if (active && fgOn != null) fgOn else fg, tween(250, easing = OutCubic), label = "fg"
    )
    BoxWithConstraints(
        modifier
            .graphicsLayer { scaleX = pr.scale.value; scaleY = pr.scale.value }
            .drawBehind {
                val h = pr.hold.value
                val m = min(size.width, size.height)
                val r = CornerRadius(m * (0.34f + 0.16f * h))
                drawRoundRect(bgC, cornerRadius = r)
                drawRoundRect(fgC.copy(alpha = 0.16f * h), cornerRadius = r)
                if (backspace) drawBackspace(fgC, m)
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!backspace && text.isNotEmpty()) {
            val m = min(maxWidth.value, maxHeight.value)
            BasicText(text, style = ts(fgC, d.fs(m * ratio)), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun KeyBtn(
    key: String, label: String, kind: String, modifier: Modifier,
    fr: Float? = null, active: Boolean = false, onClick: () -> Unit
) {
    val st = keyStyle(LocalPal.current, key, kind, fr)
    CalcButton(label, st.bg, st.fg, st.ratio, modifier, key == "DEL", st.bgOn, st.fgOn, active, onClick)
}


@Composable
fun Display(s: CalcState) {
    val p = LocalPal.current
    val fit = LocalFit.current
    val d = LocalDensity.current
    val prog = remember { Animatable(if (s.showing) 1f else 0f) }
    LaunchedEffect(s.showing) {
        if (s.showing) prog.animateTo(1f, tween(500, easing = OutExpo))
        else prog.animateTo(0f, tween(300, easing = OutCubic))
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        val avail = maxWidth.value
        val pv = prog.value
        val et = s.exprLabel
        val big = fit.font(et, 56f, 26f, avail)
        val small = fit.font(et, 22f, 13f, avail)
        val rfs = fit.font(s.resText.ifEmpty { "0" }, 64f, 28f, avail)
        val resH = rfs * 1.2f

        BasicText(
            s.resText,
            Modifier.fillMaxWidth().graphicsLayer {
                translationY = (1f - pv) * 16.dp.toPx()
                alpha = ((pv - 0.25f) / 0.75f).coerceIn(0f, 1f)
            },
            style = ts(if (s.resErr) p.error else p.onSurface, d.fs(rfs), TextAlign.End)
        )
        BasicText(
            et,
            Modifier.fillMaxWidth().graphicsLayer { translationY = -(resH + 6f).dp.toPx() * pv },
            style = ts(mix(p.onSurface, p.dim, pv), d.fs(big + (small - big) * pv), TextAlign.End)
        )
    }
}

@Composable
fun UnitRow(abbr: String, name: String, value: String, selected: Boolean, index: Int, h: Float, onClick: () -> Unit) {
    val p = LocalPal.current
    val fit = LocalFit.current
    val d = LocalDensity.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pr = rememberPress(pressed, 0.97f, 80, 40, 300, 250)
    val act by animateFloatAsState(if (selected) 1f else 0f, tween(250, easing = OutCubic), label = "act")
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L + 50L * index)
        fade.animateTo(1f, tween(350, easing = OutCubic))
    }
    val main = mix(p.onSurface, p.onPrimaryCont, act)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(h.dp)
            .graphicsLayer { alpha = fade.value; scaleX = pr.scale.value; scaleY = pr.scale.value }
            .drawBehind {
                val r = CornerRadius(22.dp.toPx())
                drawRoundRect(mix(p.surfCont, p.primaryCont, act), cornerRadius = r)
                drawRoundRect(main.copy(alpha = 0.10f * pr.hold.value), cornerRadius = r)
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
    ) {
        val vw = max(maxWidth.value - 36f - 96f, 1f)
        Box(Modifier.offset(x = 18.dp, y = (h * 0.16f).dp).size(96.dp, (h * 0.42f).dp), contentAlignment = Alignment.CenterStart) {
            BasicText(abbr, style = ts(mix(p.primary, p.onPrimaryCont, act), d.fs(h * 0.32f)), maxLines = 1, softWrap = false)
        }
        Box(Modifier.offset(x = 18.dp, y = (h * 0.56f).dp).size(96.dp, (h * 0.30f).dp), contentAlignment = Alignment.TopStart) {
            BasicText(name, style = ts(mix(p.onSurfaceVar, p.onPrimaryCont, act), d.fs(h * 0.17f)), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
        Box(Modifier.offset(x = 114.dp).width(vw.dp).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            BasicText(
                value,
                style = ts(main, d.fs(fit.font(value, min(h * 0.55f, 34f), 14f, vw)), TextAlign.End),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
fun ConverterView(model: ConvModel, mode: Mode) {
    val p = LocalPal.current
    val d = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = model.units.size
        val avail = maxHeight.value - 28f - 48f - 16f
        val rowH = max(48f, min(76f, (avail - (n - 1) * 8f) / n))
        val texts = model.texts()

        Box(
            Modifier.padding(start = 24.dp, top = 28.dp).height(48.dp).width(max(maxWidth.value - 32f - 72f, 1f).dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicText(stringResource(mode.titleRes), style = ts(p.primary, d.fs(17f)), maxLines = 1)
        }
        key(model) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                model.units.forEachIndexed { i, u ->
                    UnitRow(u.abbr, u.name, texts[i], model.active == i, i, rowH) { model.select(i) }
                }
            }
        }
    }
}

@Composable
fun MainPad(s: CalcState, bh: Float) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (r in 0 until 5) {
            Row(Modifier.fillMaxWidth().height(bh.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until 4) {
                    val key = MAIN_KEYS[r * 4 + c]
                    KeyBtn(key, key, "main", Modifier.weight(1f).fillMaxHeight(), active = key == "fx" && s.engOpen) {
                        s.press(key)
                    }
                }
            }
        }
    }
}

@Composable
fun ConvPad(model: ConvModel, convMode: Mode, colW: Float) {
    val sign = if (convMode == Mode.TEMP) "±" else "00"
    val rows = listOf(
        listOf("7", "8", "9"), listOf("4", "5", "6"), listOf("1", "2", "3"), listOf(sign, "0", ".")
    )
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in rows) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (k in row) {
                        KeyBtn(k, k, "conv", Modifier.weight(1f).fillMaxHeight(), fr = 0.36f) { model.press(k) }
                    }
                }
            }
        }
        Column(Modifier.width(colW.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in listOf("DEL", "AC")) {
                KeyBtn(k, k, "conv", Modifier.fillMaxWidth().weight(1f), fr = 0.30f) { model.press(k) }
            }
        }
    }
}

@Composable
fun EngPanel(
    s: CalcState, engH: Animatable<Float, AnimationVector1D>, engA: Animatable<Float, AnimationVector1D>,
    ebh: Float, gridH: Float
) {
    val alphas = remember { List(16) { Animatable(0f) } }
    LaunchedEffect(s.engOpen) {
        if (s.engOpen) {
            coroutineScope {
                alphas.forEachIndexed { i, a ->
                    launch {
                        a.snapTo(0f)
                        delay(80L + 30L * i)
                        a.animateTo(1f, tween(250, easing = OutQuad))
                    }
                }
            }
        }
    }
    val h = max(engH.value, 0f)
    Box(Modifier.fillMaxWidth().height(h.dp).clipToBounds().graphicsLayer { alpha = engA.value }) {
        if (h > 0.5f) {
            Column(
                Modifier.wrapContentHeight(Alignment.Top, unbounded = true).height(gridH.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0 until 4) {
                    Row(Modifier.fillMaxWidth().height(ebh.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0 until 4) {
                            val i = r * 4 + c
                            val key = ENG_KEYS[i].first
                            val label = when {
                                key == "deg" -> if (s.deg) "DEG" else "RAD"
                                (key == "sin" || key == "cos" || key == "tan") && s.inv -> "a$key"
                                else -> ENG_KEYS[i].second
                            }
                            KeyBtn(
                                key, label, "eng",
                                Modifier.weight(1f).fillMaxHeight().graphicsLayer { alpha = alphas[i].value },
                                active = key == "inv" && s.inv
                            ) { s.press(key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Calculator(s: CalcState, mode: Mode, convMode: Mode, model: ConvModel) {
    val p = LocalPal.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wf = maxWidth.value
        val hf = maxHeight.value
        val colW = (wf - 24f - 24f) / 4f
        val bh = min(colW * 0.84f, hf * 0.088f)
        val ebh = bh * 0.62f
        val padH = 5 * bh + 4 * 8f
        val engGridH = 4 * ebh + 3 * 8f
        val engFull = engGridH + 8f

        val engH = remember { Animatable(0f) }
        val engA = remember { Animatable(0f) }
        LaunchedEffect(s.engOpen, engFull) {
            if (s.engOpen) {
                coroutineScope {
                    launch { engH.animateTo(engFull, tween(450, easing = OutCubic)) }
                    launch { engA.animateTo(1f, tween(450, easing = OutCubic)) }
                }
            } else {
                coroutineScope {
                    launch { engH.animateTo(0f, tween(320, easing = InOutCubic)) }
                    launch { engA.animateTo(0f, tween(320, easing = InOutCubic)) }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = mode == Mode.CALC,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    fadeIn(tween(350, easing = OutCubic)) togetherWith fadeOut(tween(220, easing = OutCubic))
                },
                label = "top"
            ) { isCalc ->
                if (isCalc) Display(s) else ConverterView(model, convMode)
            }

            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(p.sheet)
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 20.dp)
            ) {
                EngPanel(s, engH, engA, ebh, engGridH)
                AnimatedContent(
                    targetState = mode == Mode.CALC,
                    modifier = Modifier.fillMaxWidth().height(padH.dp),
                    transitionSpec = {
                        fadeIn(tween(350, easing = OutCubic)) togetherWith fadeOut(tween(220, easing = OutCubic))
                    },
                    label = "pad"
                ) { isCalc ->
                    if (isCalc) MainPad(s, bh) else ConvPad(model, convMode, colW)
                }
            }
        }
    }
}

@Composable
fun MenuButton(prog: Animatable<Float, AnimationVector1D>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pr = rememberPress(pressed, 1f, 100, 0, 300, 300)
    Box(
        modifier
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .drawBehind {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val k0 = prog.value
                fun lp(a: Float, b: Float) = a + (b - a) * k0
                val w = 11.dp.toPx()
                val g = 6.5.dp.toPx()
                val s = 8.dp.toPx()
                val sw = 2.2.dp.toPx()
                drawCircle(p.onSurface.copy(alpha = 0.14f * pr.hold.value), radius = size.minDimension / 2f)
                drawLine(
                    p.onSurface,
                    Offset(lp(cx - w, cx - s), cy - lp(g, s)),
                    Offset(lp(cx + w, cx + s), cy - lp(g, -s)),
                    sw, StrokeCap.Round
                )
                drawLine(
                    p.onSurface,
                    Offset(lp(cx - w, cx - s), cy - lp(-g, -s)),
                    Offset(lp(cx + w, cx + s), cy - lp(-g, s)),
                    sw, StrokeCap.Round
                )
                val k = 1f - k0
                drawLine(
                    p.onSurface.copy(alpha = k),
                    Offset(cx - w * k, cy), Offset(cx + w * k, cy),
                    sw, StrokeCap.Round
                )
            }
    )
}

@Composable
fun MenuItem(m: Mode, selected: Boolean, index: Int, menuOpen: Boolean, onClick: () -> Unit) {
    val p = LocalPal.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val pr = rememberPress(pressed, 1f, 80, 0, 250, 250)
    val a = remember { Animatable(0f) }
    LaunchedEffect(menuOpen) {
        if (menuOpen) {
            a.snapTo(0f)
            delay(60L + 45L * index)
            a.animateTo(1f, tween(250, easing = OutQuad))
        }
    }
    val sel = if (selected) 1f else 0f
    val col = mix(p.onSurface, p.onSecCont, sel)
    Box(
        Modifier.fillMaxWidth().height(52.dp)
            .graphicsLayer { alpha = a.value }
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(p.secCont.copy(alpha = sel), cornerRadius = r)
                drawRoundRect(col.copy(alpha = 0.12f * pr.hold.value), cornerRadius = r)
            }
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(start = 20.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicText(m.title, style = ts(col, 15.sp), maxLines = 1)
    }
}

@Composable
fun MenuOverlay(
    mode: Mode, menuOpen: Boolean,
    dim: Animatable<Float, AnimationVector1D>,
    cardScale: Animatable<Float, AnimationVector1D>,
    cardAlpha: Animatable<Float, AnimationVector1D>,
    onPick: (Mode) -> Unit, onDismiss: () -> Unit
) {
    val p = LocalPal.current
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.4f * dim.value)) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        val cw = min(272f, maxWidth.value - 24f)
        Column(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 84.dp, end = 12.dp)
                .width(cw.dp).height(230.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = cardAlpha.value
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .clip(RoundedCornerShape(30.dp))
                .background(p.btnNum)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Mode.entries.forEachIndexed { i, m ->
                MenuItem(m, m == mode, i, menuOpen) { onPick(m) }
            }
        }
    }
}

@Composable
fun Shell() {
    val p = LocalPal.current
    val s = remember { CalcState() }
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.CALC) }
    var convMode by remember { mutableStateOf(Mode.WEIGHT) }
    var model by remember { mutableStateOf(ConvModel(UNITS.getValue(Mode.WEIGHT))) }
    var menuOpen by remember { mutableStateOf(false) }
    var overlayVisible by remember { mutableStateOf(false) }
    val dim = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.55f) }
    val cardAlpha = remember { Animatable(0f) }
    val btnProg = remember { Animatable(0f) }

    LaunchedEffect(menuOpen) {
        if (menuOpen) {
            overlayVisible = true
            cardScale.snapTo(0.55f)
            cardAlpha.snapTo(0f)
            coroutineScope {
                launch { dim.animateTo(1f, tween(300, easing = LinearEasing)) }
                launch { cardScale.animateTo(1f, tween(450, easing = OutBack)) }
                launch { cardAlpha.animateTo(1f, tween(200, easing = LinearEasing)) }
            }
        } else if (overlayVisible) {
            coroutineScope {
                launch { dim.animateTo(0f, tween(220, easing = LinearEasing)) }
                launch { cardScale.animateTo(0.6f, tween(220, easing = InCubic)) }
                launch { cardAlpha.animateTo(0f, tween(220, easing = InCubic)) }
            }
            overlayVisible = false
        }
    }
    LaunchedEffect(menuOpen) {
        btnProg.animateTo(if (menuOpen) 1f else 0f, tween(if (menuOpen) 300 else 250, easing = OutCubic))
    }

    fun pick(key: Mode) {
        if (key != mode) {
            mode = key
            if (key != Mode.CALC) {
                if (s.engOpen) s.engOpen = false
                convMode = key
                model = ConvModel(UNITS.getValue(key))
            }
        }
        scope.launch { delay(120); menuOpen = false }
    }

    BackHandler(enabled = menuOpen || s.engOpen || mode != Mode.CALC) {
        if (menuOpen) menuOpen = false
        else if (s.engOpen) s.engOpen = false
        else pick(Mode.CALC)
    }

    Box(Modifier.fillMaxSize().background(p.surface)) {
        Calculator(s, mode, convMode, model)
        if (overlayVisible) {
            MenuOverlay(mode, menuOpen, dim, cardScale, cardAlpha, onPick = { pick(it) }, onDismiss = { menuOpen = false })
        }
        MenuButton(
            btnProg,
            Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 12.dp).size(48.dp)
        ) { menuOpen = !menuOpen }
    }
}
