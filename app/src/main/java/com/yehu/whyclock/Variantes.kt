package com.yehu.whyclock

import android.content.Context
import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* Lo que cambia al tocar algunas pantallas, como en la original:
   Fecha y Música cambian su reloj entre analógico y digital, y en Grande la hora cambia de color */
object Variantes {
    var fechaDigital by mutableStateOf(false)
    var musicaDigital by mutableStateOf(false)
    var colorGrande by mutableIntStateOf(0)

    fun cargar(ctx: Context) {
        val p = ctx.getSharedPreferences("whyclock", Context.MODE_PRIVATE)
        fechaDigital = p.getBoolean("fecha_digital", false)
        musicaDigital = p.getBoolean("musica_digital", false)
        colorGrande = p.getInt("color_grande", 0).coerceIn(0, PALETA_GRANDE.size - 1)
    }

    fun guardar(ctx: Context) {
        ctx.getSharedPreferences("whyclock", Context.MODE_PRIVATE).edit()
            .putBoolean("fecha_digital", fechaDigital).putBoolean("musica_digital", musicaDigital).putInt("color_grande", colorGrande).apply()
    }
}

/* La paleta de Grande, la de la original: [claro, oscuro] para la hora (c1) y los detalles (c2) */
data class ColorGrande(val nombre: String, val c1: Pair<Color, Color>, val c2: Pair<Color, Color>)
val PALETA_GRANDE = listOf(
    ColorGrande("Rojo", Color(0xFFE0392B) to Color(0xFFFF4A3D), Color(0xFFD99A1E) to Color(0xFFF0B23A)),
    ColorGrande("Ámbar", Color(0xFFD99A1E) to Color(0xFFF0B23A), Color(0xFFE0392B) to Color(0xFFFF4A3D)),
    ColorGrande("Azul", Color(0xFF1D45F2) to Color(0xFF4D7CFF), Color(0xFFD99A1E) to Color(0xFFF0B23A)),
    ColorGrande("Coral", Color(0xFFD8694A) to Color(0xFFE8836A), Color(0xFF1D45F2) to Color(0xFF4D7CFF)),
    ColorGrande("Tinta", Color(0xFF1C1C1E) to Color(0xFFF5F2EE), Color(0xFFE0392B) to Color(0xFFFF4A3D))
)

/* La cara grande de Fecha y Música (60 marcas, 12·3·6·9 y tres manecillas), en un lienzo de 200 */
fun DrawScope.caraGrande(ahora: Long, tinta: Color, tenue: Color, acento: Color, tf: android.graphics.Typeface) {
    val e = min(size.width, size.height) / 200f
    val c = Offset(size.width / 2, size.height / 2)
    fun p(x: Float, y: Float) = Offset(c.x + (x - 100) * e, c.y + (y - 100) * e)
    for (i in 0 until 60) {
        val hora = i % 5 == 0
        rotate(i * 6f, c) { drawLine(if (hora) tinta else tenue, p(100f, 8f), p(100f, if (hora) 24f else 16f), (if (hora) 2.8f else 1.1f) * e, StrokeCap.Round) }
    }
    val pincel = Paint().apply { isAntiAlias = true; typeface = tf; textSize = 25 * e; color = tinta.toArgb(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    listOf(12 to 0.0, 3 to 90.0, 6 to 180.0, 9 to 270.0).forEach { (n, g) ->
        val a = Math.toRadians(g)
        val q = p((100 + 60 * sin(a)).toFloat(), (100 - 60 * cos(a) + 9).toFloat())
        drawContext.canvas.nativeCanvas.drawText("$n", q.x, q.y, pincel)
    }
    val ca = Calendar.getInstance().apply { timeInMillis = ahora }
    val s = ca.get(Calendar.SECOND) + ca.get(Calendar.MILLISECOND) / 1000f
    val m = ca.get(Calendar.MINUTE) + s / 60f
    val h = ca.get(Calendar.HOUR) + m / 60f
    rotate(h * 30f, c) { drawLine(tinta, p(100f, 112f), p(100f, 54f), 7 * e, StrokeCap.Round) }
    rotate(m * 6f, c) { drawLine(tinta, p(100f, 112f), p(100f, 30f), 4.6f * e, StrokeCap.Round) }
    rotate(s * 6f, c) { drawLine(acento, p(100f, 124f), p(100f, 22f), 2 * e) }
    drawCircle(tinta, 5 * e, c)
    drawCircle(acento, 2 * e, c)
}

/* Un reloj que al tocarlo cambia entre la cara grande y la regla digital, con un cambio de forma suave */
@Composable
fun RelojCambiable(ahora: Long, digital: Boolean, lado: Dp, onCambio: () -> Unit, pista: Boolean = true, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val c = Ajustes.colores()
    val tf = Letras.digitos(ctx)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedContent(digital, transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f)) togetherWith
                (fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.1f))
        }, label = "reloj", modifier = Modifier.size(lado).pulsable(hundir = 0.95f, onClick = onCambio)) { dig ->
            Canvas(Modifier.fillMaxSize().clipToBounds()) {
                if (dig) dibujarDigitalPublico(ahora, c, tf) else caraGrande(ahora, c.dTinta, c.dMarcaTenue, c.acento, tf)
            }
        }
        if (pista) Text(if (digital) "Toca el reloj para ver la hora analógica" else "Toca el reloj para ver la hora digital",
            color = c.dTinta.copy(alpha = 0.45f), fontFamily = FontFamily(Font(R.font.jost_300)), fontSize = 11.sp)
    }
}
