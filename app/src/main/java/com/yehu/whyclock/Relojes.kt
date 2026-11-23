package com.yehu.whyclock

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/* La hora que se dibuja: con movimiento suave se actualiza en cada cuadro; por pasos, una vez por segundo */
@Composable
fun rememberAhora(): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Ajustes.suave) {
        while (true) {
            if (Ajustes.suave) androidx.compose.runtime.withFrameNanos { ahora = System.currentTimeMillis() }
            else {
                ahora = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000 - ahora % 1000)
            }
        }
    }
    return ahora
}

private val JOST = FontFamily(Font(R.font.jost_300, FontWeight.Light))

/* "domingo, 22 de noviembre", arriba al centro, como en el original */
@Composable
fun FechaArriba(ahora: Long, c: Colores, modifier: Modifier = Modifier) {
    val texto = remember(ahora / 60000) { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "MX")).format(ahora) }
    Text(
        texto, color = c.fecha, fontFamily = JOST, fontWeight = FontWeight.Light, fontSize = 20.sp, letterSpacing = 0.04.em,
        modifier = modifier.statusBarsPadding().padding(top = 14.dp)
    )
}

/* Dentro del lienzo, el dibujo del original mide 200 × 200; se ajusta para que el lado chico llene la pantalla */
private class Escala(val e: Float, val ox: Float, val oy: Float, val vbY: Float, val vbH: Float) {
    fun x(v: Float) = ox + v * e
    fun y(v: Float) = oy + v * e
}

private fun DrawScope.escala(): Escala {
    val e = min(size.width, size.height) / 200f
    val ox = size.width / 2 - 100 * e
    val oy = size.height / 2 - 100 * e
    val vbH = size.height / e
    return Escala(e, ox, oy, 100 - vbH / 2, vbH)
}

private fun pincel(tf: android.graphics.Typeface, tam: Float, color: Color, alin: Paint.Align) = Paint().apply {
    isAntiAlias = true; typeface = tf; textSize = tam; this.color = color.toArgb(); textAlign = alin
}

/* ——— Pantalla 1: el reloj analógico de rayos ——— */
@Composable
fun RelojAnalogico(ahora: Long) {
    val c = Ajustes.colores()
    val ctx = LocalContext.current
    val tf = Letras.digitos(ctx)
    Box(Modifier.fillMaxSize().background(c.fondo)) {
        Canvas(Modifier.fillMaxSize()) {
            val s = escala()
            val cal = Calendar.getInstance().apply { timeInMillis = ahora }
            val seg = cal.get(Calendar.SECOND) + (if (Ajustes.suave) cal.get(Calendar.MILLISECOND) / 1000f else 0f)
            val min = cal.get(Calendar.MINUTE) + seg / 60f
            val hora = (cal.get(Calendar.HOUR) + min / 60f)
            val centro = Offset(s.x(100f), s.y(100f))
            /* Los rayos tenues, largos para cubrir toda la pantalla */
            for (k in 0 until 12) {
                val a = (k * 30) * PI / 180
                drawLine(c.rayo, centro, Offset(s.x((100 + sin(a) * 1400).toFloat()), s.y((100 - cos(a) * 1400).toFloat())), 0.6f * s.e)
            }
            /* Las marcas de las 12, las 3 y las 9 */
            val g = 2f * s.e
            drawLine(c.tinta, Offset(s.x(100f), s.y(65f)), Offset(s.x(100f), s.y(83f)), g, StrokeCap.Round)
            drawLine(c.tinta, Offset(s.x(117f), s.y(100f)), Offset(s.x(135f), s.y(100f)), g, StrokeCap.Round)
            drawLine(c.tinta, Offset(s.x(65f), s.y(100f)), Offset(s.x(83f), s.y(100f)), g, StrokeCap.Round)
            /* El 6, con la letra elegida */
            drawIntoCanvas { it.nativeCanvas.drawText("6", s.x(100f), s.y(144f), pincel(tf, 25f * s.e, c.tinta, Paint.Align.CENTER).apply { isFakeBoldText = Ajustes.letra == "original" }) }
            fun manecilla(grados: Float, largo: Float, atras: Float, ancho: Float, color: Color) {
                val r = grados * PI.toFloat() / 180f
                val d = Offset(sin(r), -cos(r))
                drawLine(color, centro - d * (atras * s.e), centro + d * (largo * s.e), ancho * s.e, StrokeCap.Round)
            }
            manecilla(hora * 30f, 42f, 0f, 3.2f, c.tinta)
            manecilla(min * 6f, 62f, 0f, 2.4f, c.tinta)
            manecilla(seg * 6f, 64f, 21f, 1.6f, c.acento)
            drawCircle(c.acento, 5f * s.e, centro)
        }
        FechaArriba(ahora, c, Modifier.align(Alignment.TopCenter))
    }
}

/* ——— El reloj digital con la regla de minutos que sube con el tiempo ——— */
private fun DrawScope.dibujarDigital(ahora: Long, c: Colores, tf: android.graphics.Typeface, compacto: Boolean) {
    val s = escala()
    val cal = Calendar.getInstance().apply { timeInMillis = ahora }
    val h24 = cal.get(Calendar.HOUR_OF_DAY)
    val h12 = (h24 % 12).let { if (it == 0) 12 else it }
    val m = cal.get(Calendar.MINUTE)
    val seg = cal.get(Calendar.SECOND) + (if (Ajustes.suave) cal.get(Calendar.MILLISECOND) / 1000f else 0f)
    val minutoReal = m + seg / 60f
    val PASO = 8f
    /* La regla: una marca por minuto, mediana cada 5 y larga cada 15; se desvanece arriba y abajo */
    val arriba = if (compacto) 14f else s.vbY + 28f
    val abajo = if (compacto) 186f else s.vbY + s.vbH - 28f
    val fundido = if (compacto) 22f else 44f
    val mitad = max(100 - s.vbY, s.vbY + s.vbH - 100)
    val n = ceil(mitad / PASO).toInt() + 2
    val m0 = m
    for (i in -n..n) {
        val mv = m0 + i
        val y = 100f + (mv - minutoReal) * PASO
        val alfa = (((y - arriba) / fundido).coerceIn(0f, 1f) * ((abajo - y) / fundido).coerceIn(0f, 1f))
        if (alfa <= 0f) continue
        val (largo, ancho, color) = when {
            ((mv % 15) + 15) % 15 == 0 -> Triple(26f, 2.3f, c.dMarca)
            ((mv % 5) + 5) % 5 == 0 -> Triple(18f, 2f, c.dMarca)
            else -> Triple(8f, 1.1f, c.dMarcaTenue)
        }
        drawLine(color.copy(alpha = color.alpha * alfa), Offset(s.x(127f - largo), s.y(y)), Offset(s.x(127f), s.y(y)), ancho * s.e, StrokeCap.Round)
    }
    /* La línea roja y la flechita que marca el minuto */
    drawLine(c.dLinea, Offset(if (compacto) s.x(0f) else 0f, s.y(116.5f)), Offset(if (compacto) s.x(200f) else size.width, s.y(116.5f)), 0.8f * s.e)
    drawPath(Path().apply { moveTo(s.x(129.2f), s.y(100f)); lineTo(s.x(134.6f), s.y(95.2f)); lineTo(s.x(134.6f), s.y(104.8f)); close() }, c.dFlecha)
    /* La hora grande, los minutos al lado de la flecha, y PM / AM */
    val tamHora = if (compacto) 46f else 40f
    val tamMin = if (compacto) 23f else 20f
    drawIntoCanvas { lienzo ->
        val nc = lienzo.nativeCanvas
        nc.drawText("%02d".format(h12), s.x(78.3f), s.y(112f), pincel(tf, tamHora * s.e, c.dTinta, Paint.Align.RIGHT))
        nc.drawText("%02d".format(m), s.x(138f), s.y(101.5f + (tamMin - 13.6f) * 0.35f), pincel(tf, tamMin * s.e, c.dTinta, Paint.Align.LEFT))
        val pm = h24 >= 12
        nc.drawText("PM", s.x(197f), s.y(112f), pincel(tf, 9f * s.e, if (pm) c.dTinta else c.dApagado, Paint.Align.RIGHT))
        nc.drawText("AM", s.x(197f), s.y(129.5f), pincel(tf, 9f * s.e, if (!pm) c.dTinta else c.dApagado, Paint.Align.RIGHT))
    }
}

/* ——— Pantalla 2: el reloj digital con regla ——— */
@Composable
fun RelojDigital(ahora: Long) {
    val c = Ajustes.colores()
    val tf = Letras.digitos(LocalContext.current)
    Box(Modifier.fillMaxSize().background(c.fondo)) {
        Canvas(Modifier.fillMaxSize()) { dibujarDigital(ahora, c, tf, compacto = false) }
        FechaArriba(ahora, c, Modifier.align(Alignment.TopCenter))
    }
}

/* ——— Pantalla 3: el digital y, al lado, el calendario del mes (en vertical, uno arriba del otro) ——— */
@Composable
fun RelojDual(ahora: Long) {
    val c = Ajustes.colores()
    val ctx = LocalContext.current
    val tf = Letras.digitos(ctx)
    BoxWithConstraints(Modifier.fillMaxSize().background(if (Ajustes.oscuro) Color(0xFF111111) else c.fondo)) {
        val horizontal = maxWidth > maxHeight
        val reloj: @Composable (Modifier) -> Unit = { m -> Canvas(m) { dibujarDigital(ahora, c, tf, compacto = true) } }
        val calendario: @Composable (Modifier) -> Unit = { m -> Box(m, contentAlignment = Alignment.Center) { CalendarioMes(ahora, c) } }
        if (horizontal) Row(Modifier.fillMaxSize()) {
            reloj(Modifier.weight(1f).fillMaxHeight())
            calendario(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp))
        } else Column(Modifier.fillMaxSize()) {
            reloj(Modifier.weight(1f).fillMaxWidth())
            calendario(Modifier.weight(1f).fillMaxWidth().padding(24.dp))
        }
    }
}

/* El calendario del mes: letras de los días, el mes en la letra elegida, y hoy en un círculo rojo */
@Composable
fun CalendarioMes(ahora: Long, c: Colores) {
    val ctx = LocalContext.current
    val familia = remember(Ajustes.letra) {
        FontFamily(Font(when (Ajustes.letra) { "concert" -> R.font.concert_one; "oswald" -> R.font.oswald_500; else -> R.font.bruno_ace }))
    }
    val cal = Calendar.getInstance().apply { timeInMillis = ahora }
    val hoy = cal.get(Calendar.DAY_OF_MONTH)
    val primero = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val inicio = (primero.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val dias = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val anterior = (primero.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
    val celdas = ceil((inicio + dias) / 7f).toInt() * 7
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val lado = min(maxWidth.value / 7f, 56f)
        val tam = (lado * 0.42f).sp
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                    Box(Modifier.weight(1f).aspectRatio(1.3f), contentAlignment = Alignment.Center) {
                        Text(it, color = c.calendario.copy(alpha = 0.75f), fontFamily = familia, fontSize = tam * 0.72f)
                    }
                }
            }
            for (fila in 0 until celdas / 7) Row {
                for (col in 0 until 7) {
                    val k = fila * 7 + col
                    val (num, tenue) = when {
                        k < inicio -> (anterior - inicio + k + 1) to true
                        k >= inicio + dias -> (k - inicio - dias + 1) to true
                        else -> (k - inicio + 1) to false
                    }
                    val esHoy = !tenue && num == hoy
                    Box(Modifier.weight(1f).aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                        if (esHoy) Box(
                            Modifier.fillMaxHeight(0.92f).aspectRatio(1f).clip(CircleShape)
                                .border(2.dp, c.hoy, CircleShape)
                        )
                        Text(
                            "$num", fontFamily = familia, fontSize = tam,
                            color = if (esHoy) c.hoy else c.calendario.copy(alpha = if (tenue) 0.35f else 1f)
                        )
                    }
                }
            }
        }
    }
}

/* El reloj digital con su regla, para otras pantallas (versión compacta) */
fun DrawScope.dibujarDigitalPublico(ahora: Long, c: Colores, tf: android.graphics.Typeface) = dibujarDigital(ahora, c, tf, compacto = true)
