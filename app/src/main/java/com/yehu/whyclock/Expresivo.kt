package com.yehu.whyclock

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* Formas expresivas (Material 3 Expressive) con los colores de siempre */

/* Galleta: círculo con ondas suaves en el borde (como las de Android 16). giro en grados */
class Galleta(private val puntas: Int = 9, private val hondo: Float = 0.12f, private val giro: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(size.width, size.height) / 2
        val c = Offset(size.width / 2, size.height / 2)
        val g = giro * PI / 180
        val p = Path()
        for (i in 0..360) {
            val t = i * PI / 180
            val rr = r * (1 - hondo + hondo * (0.5 + 0.5 * cos(puntas * (t - g)))).toFloat()
            val x = c.x + rr * cos(t).toFloat(); val y = c.y + rr * sin(t).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

/* Botón expresivo: al presionarlo se hunde y su forma cambia (de píldora a cuadrito redondeado, o al revés) */
@Composable
fun BotonExpresivo(
    fondo: Color,
    modifier: Modifier = Modifier,
    redondo: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contenido: @Composable BoxScope.() -> Unit
) {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val resorte = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    val esquina by animateFloatAsState(if (redondo) (if (presionado) 24f else 50f) else (if (presionado) 50f else 28f), resorte, label = "esquina")
    val escala by animateFloatAsState(if (presionado) 0.94f else 1f, resorte, label = "escala")
    Box(
        modifier.graphicsLayer { scaleX = escala; scaleY = escala }
            .clip(RoundedCornerShape(esquina.toInt().coerceIn(0, 50)))
            .background(fondo)
            .clickable(interactionSource = fuente, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center, content = contenido
    )
}

/* Anillo expresivo: el progreso es una línea ondulada que se mueve (fase) y la pista es lisa, con un huequito entre las dos.
   amplitud 0 = liso (en pausa). frac de 0 a 1 */
fun DrawScope.anilloExpresivo(frac: Float, fase: Float, amplitud: Float, pista: Color, progreso: Color, grosor: Float, ondas: Int = 22) {
    val r = min(size.width, size.height) / 2 - grosor * 1.6f
    val c = Offset(size.width / 2, size.height / 2)
    val hueco = (grosor * 1.8f) / (2 * PI * r).toFloat()
    val a = amplitud * grosor * 0.42f
    val f = frac.coerceIn(0f, 1f)
    /* Pista: lo que falta */
    if (f < 1f - hueco * 2) {
        val inicio = f + hueco; val fin = 1f - hueco
        if (fin > inicio) {
            val p = Path()
            val n = ((fin - inicio) * 360).toInt().coerceAtLeast(2)
            for (i in 0..n) {
                val t = (inicio + (fin - inicio) * i / n) * 2 * PI - PI / 2
                val x = c.x + r * cos(t).toFloat(); val y = c.y + r * sin(t).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, pista, style = Stroke(grosor, cap = StrokeCap.Round))
        }
    }
    /* Progreso ondulado */
    if (f > 0.002f) {
        val p = Path()
        val n = (f * 720).toInt().coerceAtLeast(2)
        for (i in 0..n) {
            val u = f * i / n
            val t = u * 2 * PI - PI / 2
            val rr = r + a * sin(ondas * u * 2 * PI + fase).toFloat()
            val x = c.x + rr * cos(t).toFloat(); val y = c.y + rr * sin(t).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, progreso, style = Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
