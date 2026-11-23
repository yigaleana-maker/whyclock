package com.yehu.whyclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private val UI = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold), Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black))

/* Las técnicas del original: Caja 4·4·4·4, Calma 4·4·6 y Dormir 4·7·8 */
private data class Fase(val n: String, val d: Int)
private data class Tecnica(val nombre: String, val patron: String, val desc: String, val fases: List<Fase>) {
    val ciclo get() = fases.sumOf { it.d }
}
private val TECNICAS = linkedMapOf(
    "caja" to Tecnica("Caja", "4·4·4·4", "Para concentrarte", listOf(Fase("Inhala", 4), Fase("Sostén", 4), Fase("Exhala", 4), Fase("Descansa", 4))),
    "calma" to Tecnica("Calma", "4·4·6", "Para soltar el estrés", listOf(Fase("Inhala", 4), Fase("Sostén", 4), Fase("Exhala", 6))),
    "dormir" to Tecnica("Dormir", "4·7·8", "Para dormir", listOf(Fase("Inhala", 4), Fase("Sostén", 7), Fase("Exhala", 8)))
)
private const val PREP = 16
private val PREP_CUES = listOf(
    "Ponte cómodo. Espalda recta, hombros sueltos.",
    "Respira hondo por la nariz… y suelta por la boca.",
    "Siente el peso de tu cuerpo… recorre tu cuerpo y suelta la tensión.",
    "Cierra los ojos si quieres… y deja que la respiración vuelva a su ritmo."
)
private val CIERRE_CUES = listOf(
    "Suelta la respiración… deja que vuelva a su ritmo natural.",
    "Vuelve a sentir tu cuerpo, el peso, los sonidos de alrededor.",
    "Observa cómo te sientes… y cuando estés listo, abre los ojos despacio."
)
private val GUIAS = listOf(
    "Respira por la nariz, suelta por la boca.",
    "Lleva el aire al abdomen, no solo al pecho.",
    "Espalda recta, hombros sueltos.",
    "Nota el principio de la inhalación y el final de la exhalación.",
    "Si tu mente se va, vuelve a la respiración.",
    "Cuando aparezca un pensamiento, nótalo y déjalo pasar.",
    "Siente cómo sube y baja el pecho.",
    "Suelta la tensión al exhalar.",
    "Imagina tus pensamientos como nubes que pasan por el cielo.",
    "No hay prisa. Solo este respiro."
)
private const val TRABAJO = 25 * 60
private const val DESCANSO = 5 * 60

private fun fmt(s: Float): String { val t = ceil(s).toInt().coerceAtLeast(0); return "${t / 60}:%02d".format(t % 60) }

/* ——— Pantalla: Respira + Enfocar, como en el original ——— */
@Composable
fun PantallaRespira() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("whyclock", Context.MODE_PRIVATE) }
    var modo by remember { mutableStateOf(prefs.getString("enf_modo", "respira") ?: "respira") }
    var durRes by remember { mutableIntStateOf(prefs.getInt("enf_dur", 180).takeIf { it in listOf(60, 180, 300) } ?: 180) }
    var tec by remember { mutableStateOf(prefs.getString("enf_tec", "caja").takeIf { it in TECNICAS } ?: "caja") }
    var faseEnf by remember { mutableStateOf("trabajo") }
    var hechas by remember { mutableIntStateOf(0) }
    fun totalSeg() = if (modo == "respira") durRes else if (faseEnf == "trabajo") TRABAJO else DESCANSO

    var corriendo by remember { mutableStateOf(false) }
    var finEn by remember { mutableLongStateOf(0L) }
    var total by remember { mutableIntStateOf(totalSeg()) }
    var restante by remember { mutableFloatStateOf(total.toFloat()) }
    var enCierre by remember { mutableStateOf(false) }
    var cierreInicio by remember { mutableLongStateOf(0L) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mensaje by remember { mutableStateOf("Toca empezar para respirar") }
    var guiaFija by remember { mutableStateOf(GUIAS[0]) }
    var contento by remember { mutableStateOf(false) }
    var ajustes by remember { mutableStateOf(false) }
    val t = TECNICAS.getValue(tec)

    fun reiniciar() {
        corriendo = false; enCierre = false
        if (modo == "enfoque") { faseEnf = "trabajo"; hechas = 0 }
        total = totalSeg(); restante = total.toFloat(); contento = false
        mensaje = if (modo == "respira") "Toca empezar para respirar" else "Toca empezar para concentrarte"
        guiaFija = GUIAS[0]
    }
    fun terminar() {
        corriendo = false; enCierre = false; contento = true
        campanita()
        if (modo == "respira") {
            mensaje = "¡Bien hecho! Respiraste " + when (durRes) { 60 -> "1 minuto"; 180 -> "3 minutos"; else -> "5 minutos" } + "."
            guiaFija = "Lleva esta calma contigo."
            total = durRes; restante = total.toFloat()
        } else if (faseEnf == "trabajo") {
            hechas = (hechas + 1) % 4; faseEnf = "descanso"
            mensaje = "Sesión completa · toca empezar para descansar"
            total = DESCANSO; restante = total.toFloat()
        } else {
            faseEnf = "trabajo"
            mensaje = "Descanso terminado · toca empezar la siguiente sesión"
            total = TRABAJO; restante = total.toFloat()
        }
    }
    fun empezar() {
        if (corriendo) {
            corriendo = false
            restante = ((finEn - System.currentTimeMillis()) / 1000f).coerceAtLeast(0f)
            mensaje = if (modo == "respira") "En pausa · toca Seguir para continuar" else if (faseEnf == "trabajo") "Toca empezar para concentrarte" else "Toca empezar para descansar"
            return
        }
        contento = false
        total = totalSeg()
        if (restante <= 0f || restante > total) restante = total.toFloat()
        finEn = System.currentTimeMillis() + (restante * 1000).toLong()
        corriendo = true
    }

    /* Cada cuadro: avanza el tiempo y la sesión */
    LaunchedEffect(corriendo) {
        while (corriendo) {
            withFrameMillis { }
            ahora = System.currentTimeMillis()
            restante = ((finEn - ahora) / 1000f).coerceAtLeast(0f)
            if (modo == "respira" && !enCierre && restante <= 0f) { enCierre = true; cierreInicio = ahora }
            if (enCierre && ahora - cierreInicio >= CIERRE_CUES.size * 4000L) terminar()
            if (modo == "enfoque" && restante <= 0f) terminar()
        }
    }
    LaunchedEffect(contento) { if (contento) { kotlinx.coroutines.delay(2500); contento = false } }

    /* Lo que dice la pantalla en este momento */
    val elap = total - restante
    var estado = mensaje
    var clave: Any = mensaje
    var guia = guiaFija
    var respiro = 0f
    var durmiendo = false
    var sesiones: String
    if (modo == "respira") {
        val ciclos = ((total - PREP) / t.ciclo.toFloat()).let { ceil(it).toInt() }.coerceAtLeast(1)
        var ciclo = 1
        if (corriendo && enCierre) {
            estado = "Cierre…"; clave = "cierre"
            guia = CIERRE_CUES[((ahora - cierreInicio) / 4000L).toInt().coerceIn(0, CIERRE_CUES.size - 1)]
        } else if (corriendo && elap < PREP) {
            val pi = (elap / 4).toInt().coerceIn(0, PREP_CUES.size - 1)
            estado = "Respira en ${ceil(PREP - elap).toInt().coerceAtLeast(1)} s"; clave = "prep"
            guia = PREP_CUES[pi]; durmiendo = pi >= 2
        } else if (corriendo) {
            val dentro = (elap - PREP) % t.ciclo
            ciclo = ((elap - PREP) / t.ciclo).toInt() + 1
            var b = 0f
            var fase = t.fases[0]; var fin = fase.d.toFloat(); var prog = 0f
            for (f in t.fases) { if (dentro < b + f.d) { fase = f; fin = b + f.d; prog = (dentro - b) / f.d; break }; b += f.d }
            respiro = when (fase.n) { "Inhala" -> prog; "Sostén" -> 1f; "Exhala" -> 1f - prog; else -> 0f }
            durmiendo = fase.n == "Sostén" || fase.n == "Descansa"
            estado = "${fase.n} · ${ceil(fin - dentro).toInt().coerceAtLeast(1)}"; clave = fase.n + ciclo
            guia = GUIAS[(ciclo - 1) % GUIAS.size]
        }
        sesiones = "Respiración $ciclo de $ciclos"
    } else {
        if (corriendo) { estado = if (faseEnf == "trabajo") "Concéntrate…" else "Descanso · respira"; clave = estado }
        durmiendo = corriendo && faseEnf == "trabajo"
        sesiones = "Sesión ${hechas + 1} de 4 · " + (0 until 4).joinToString("") { if (it < hechas) "●" else "○" }
    }
    val porc = if (total > 0) ((1f - restante / total) * 100).toInt() else 0

    val oscuro = Ajustes.oscuro
    val fondo = if (oscuro) Color(0xFF11110F) else Color(0xFFEFEEE9)
    val tinta = if (oscuro) Color(0xFFF5F2EE) else Color(0xFF1C1C1E)
    val tenue = if (oscuro) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f)
    val acento = Ajustes.colores().acento
    val dig = familiaDigitos()

    /* El gato respira (crece al inhalar) con un resorte suave, y salta de gusto al terminar */
    val tamGato by animateFloatAsState(1f + 0.10f * respiro, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow), label = "gato")
    val saltos = rememberInfiniteTransition(label = "salto")
    val salto by saltos.animateFloat(0f, 1f, infiniteRepeatable(tween(600), RepeatMode.Restart), label = "s")
    val brinco = if (contento) sin(salto * PI).toFloat() else 0f
    val zeta by saltos.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "z")
    val ojos by animateFloatAsState(if (durmiendo) 1f else 0f, tween(400), label = "ojos")
    val progreso by animateFloatAsState(if (total > 0) 1f - restante / total else 0f, tween(if (corriendo) 60 else 500), label = "prog")

    val circulo: @Composable (Dp) -> Unit = { lado ->
        Box(Modifier.size(lado).pulsable(hundir = 0.97f) { empezar() }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { anilloOndulado(progreso, if (modo == "respira") PREP / total.toFloat() else null, tinta, acento, fondo) }
            Canvas(Modifier.size(lado * 0.64f).graphicsLayer {
                scaleX = tamGato * (1f + 0.06f * brinco); scaleY = scaleX
                translationY = -brinco * size.height * 0.08f
            }) { gato(ojos) }
            if (ojos > 0.01f) Text("Z z", color = tinta, fontFamily = dig, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = lado * 0.08f, top = lado * 0.04f)
                    .graphicsLayer { alpha = ojos; translationY = -sin(zeta * 2 * PI).toFloat().let { (it + 1f) / 2f } * 8.dp.toPx() })
        }
    }
    val pildora: @Composable (String, Boolean, () -> Unit) -> Unit = { s, on, accion ->
        val f by animateColorAsState(if (on) acento else Color.Transparent, label = "p")
        Text(s, color = if (on) Color.White else tinta, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            modifier = Modifier.pulsable(onClick = accion).clip(RoundedCornerShape(99.dp)).background(f)
                .border(1.5.dp, if (on) acento else Color(0x59808080), RoundedCornerShape(99.dp)).padding(horizontal = 22.dp, vertical = 10.dp))
    }
    val cabecera: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pildora("Respirar", modo == "respira") { if (modo != "respira") { modo = "respira"; prefs.edit().putString("enf_modo", modo).apply(); reiniciar() } }
            pildora("Enfocar", modo == "enfoque") { if (modo != "enfoque") { modo = "enfoque"; prefs.edit().putString("enf_modo", modo).apply(); reiniciar() } }
        }
        AnimatedVisibility(modo == "respira", enter = fadeIn(), exit = fadeOut()) {
            Text("⚙️ " + (if (durRes == 60) "1 min" else if (durRes == 180) "3 min" else "5 min") + " · ${t.nombre} ${t.patron}",
                color = tenue, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp).pulsable { ajustes = true }.clip(RoundedCornerShape(99.dp))
                    .border(1.5.dp, Color(0x59808080), RoundedCornerShape(99.dp)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    val textos: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (modo == "respira") {
                Text("$porc%", color = tinta, fontFamily = dig, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 52.sp)
                Text(fmt(restante), color = tenue, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            } else Text(fmt(restante), color = tinta, fontFamily = dig, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 52.sp)
            Cambiante(estado to clave, clave = { it.second }) {
                Text(it.first, color = tinta, fontFamily = UI, fontWeight = FontWeight.Black, fontSize = 28.sp, textAlign = TextAlign.Center, lineHeight = 34.sp)
            }
            if (modo == "respira") Box(Modifier.widthIn(max = 340.dp).heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
                Cambiante(guia) { Text(it, color = tinta, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center, lineHeight = 25.sp) }
            }
            Text(sesiones, color = tenue, fontFamily = UI, fontSize = 14.sp, letterSpacing = 0.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.pulsable { empezar() }.clip(RoundedCornerShape(99.dp)).background(acento).padding(horizontal = 26.dp, vertical = 13.dp)) {
                    Cambiante(if (corriendo) "Pausa" else if (restante < total) "Seguir" else "Empezar") { Text(it, color = Color.White, fontFamily = UI, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                Text("Reiniciar", color = tinta, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    modifier = Modifier.pulsable { reiniciar() }.clip(RoundedCornerShape(99.dp)).border(1.5.dp, Color(0x66808080), RoundedCornerShape(99.dp)).padding(horizontal = 26.dp, vertical = 13.dp))
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(fondo)) {
        /* Círculos suaves de fondo, que flotan despacio */
        val flota by saltos.animateFloat(0f, 1f, infiniteRepeatable(tween(14000), RepeatMode.Reverse), label = "flota")
        Canvas(Modifier.fillMaxSize()) {
            val g = Color(0x14808080)
            val d = 10.dp.toPx() * flota
            drawCircle(g, 90.dp.toPx(), Offset(50.dp.toPx() + d, size.height * 0.06f + 90.dp.toPx()))
            drawCircle(g, 60.dp.toPx(), Offset(size.width + 20.dp.toPx() - 60.dp.toPx() - d, size.height * 0.18f + 60.dp.toPx()))
            drawCircle(g, 110.dp.toPx(), Offset(size.width * 0.08f + 110.dp.toPx(), size.height + 60.dp.toPx() - 110.dp.toPx() - d))
            drawCircle(g, 45.dp.toPx(), Offset(size.width * 0.88f - 45.dp.toPx() + d, size.height * 0.88f - 45.dp.toPx()))
        }
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            val lado = min(maxHeight.value * 0.58f, 280f).dp
            Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { cabecera(); Spacer(Modifier.height(14.dp)); circulo(lado) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { textos() }
            }
        } else {
            val lado = min(min(maxWidth.value * 0.64f, 280f), maxHeight.value * 0.32f).dp
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                cabecera(); Spacer(Modifier.height(16.dp)); circulo(lado); Spacer(Modifier.height(16.dp)); textos()
            }
        }

        /* Ajustes de Respira: duración y técnica */
        AnimatedVisibility(ajustes, Modifier.fillMaxSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            val entrada = remember { Animatable(0.85f) }
            LaunchedEffect(Unit) { entrada.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
            Box(Modifier.fillMaxSize().background(Color(0x8C000000)).clickable(remember { MutableInteractionSource() }, null) { ajustes = false }, contentAlignment = Alignment.Center) {
                Column(
                    Modifier.graphicsLayer { scaleX = entrada.value; scaleY = entrada.value }.widthIn(max = 360.dp).fillMaxWidth(0.86f)
                        .clip(RoundedCornerShape(24.dp)).background(fondo).border(1.dp, Color(0x4D808080), RoundedCornerShape(24.dp))
                        .clickable(remember { MutableInteractionSource() }, null) {}.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Ajustes de Respira", color = tinta, fontFamily = UI, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Duración", color = tenue, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "1 min", 180 to "3 min", 300 to "5 min").forEach { (s, n) ->
                            pildora(n, durRes == s) { durRes = s; prefs.edit().putInt("enf_dur", s).apply(); reiniciar() }
                        }
                    }
                    Text("Técnica", color = tenue, fontFamily = UI, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TECNICAS.forEach { (id, x) ->
                            pildora("${x.nombre} ${x.patron}", tec == id) {
                                tec = id; prefs.edit().putString("enf_tec", id).apply(); reiniciar()
                                mensaje = "${x.nombre} · ${x.desc}"; guiaFija = x.fases.joinToString(" · ") { "${it.n} ${it.d}" }
                            }
                        }
                    }
                    Box(Modifier.pulsable { ajustes = false }.clip(RoundedCornerShape(99.dp)).background(acento).padding(horizontal = 26.dp, vertical = 13.dp)) {
                        Text("Listo", color = Color.White, fontFamily = UI, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

/* El anillo ondulado del original (r = 42 + 4·sen 14θ en un lienzo de 100), con el progreso encima y un punto rojo donde empieza la respiración */
private fun DrawScope.anilloOndulado(progreso: Float, marca: Float?, tinta: Color, acento: Color, fondo: Color) {
    val e = size.minDimension / 100f
    val c = Offset(size.width / 2, size.height / 2)
    fun punto(f: Float): Offset {
        val th = f * 2 * PI
        val r = 42 + 4 * sin(14 * th)
        val a = th - PI / 2
        return Offset(c.x + (r * cos(a)).toFloat() * e, c.y + (r * sin(a)).toFloat() * e)
    }
    fun camino(hasta: Float) = Path().apply {
        val n = (720 * hasta).toInt().coerceAtLeast(1)
        for (i in 0..n) { val p = punto(hasta * i / n); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    }
    drawPath(camino(1f).apply { close() }, Color(0x40808080), style = Stroke(7 * e))
    if (progreso > 0.001f) drawPath(camino(progreso), tinta, style = Stroke(7 * e, cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (marca != null && marca in 0f..1f) { val p = punto(marca); drawCircle(fondo, 4.5f * e, p); drawCircle(acento, 3f * e, p) }
}

/* El gato negro con pecho y patitas blancas del original (lienzo de 120×112). ojos: 0 abiertos, 1 cerrados */
private fun DrawScope.gato(ojos: Float) {
    val e = min(size.width / 120f, size.height / 112f)
    val oscuro = Color(0xFF26262A); val claro = Color(0xFFF5F2EE)
    translate((size.width - 120 * e) / 2, (size.height - 112 * e) / 2) {
        scale(e, e, pivot = Offset.Zero) {
            drawPath(Path().apply { moveTo(94f, 86f); quadraticTo(116f, 90f, 112f, 68f) }, oscuro, style = Stroke(8f, cap = StrokeCap.Round))
            drawCircle(claro, 4.5f, Offset(112f, 68f))
            drawOval(oscuro, Offset(20f, 37f), androidx.compose.ui.geometry.Size(80f, 66f))
            drawOval(claro, Offset(20f, 37f), androidx.compose.ui.geometry.Size(80f, 66f), style = Stroke(2.5f))
            drawOval(claro, Offset(37f, 69f), androidx.compose.ui.geometry.Size(46f, 30f))
            val oreja = { a: Offset, b: Offset, d: Offset -> Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close() } }
            listOf(oreja(Offset(32f, 46f), Offset(27f, 19f), Offset(50f, 34f)), oreja(Offset(88f, 46f), Offset(93f, 19f), Offset(70f, 34f))).forEach {
                drawPath(it, oscuro); drawPath(it, claro, style = Stroke(2.5f, join = StrokeJoin.Round))
            }
            drawPath(oreja(Offset(34f, 41f), Offset(32f, 27f), Offset(43f, 33f)), Color(0xFFF0A0A8))
            drawPath(oreja(Offset(86f, 41f), Offset(88f, 27f), Offset(77f, 33f)), Color(0xFFF0A0A8))
            /* Ojos: puntitos que se vuelven rayitas al dormir */
            listOf(46f, 74f).forEach { x ->
                if (ojos < 1f) drawCircle(claro.copy(alpha = 1f - ojos), 4.5f * (1f - 0.6f * ojos), Offset(x, 62f))
                if (ojos > 0f) drawPath(Path().apply { moveTo(x - 5f, 62f); quadraticTo(x, 67f, x + 5f, 62f) }, claro.copy(alpha = ojos), style = Stroke(3f, cap = StrokeCap.Round))
            }
            drawPath(oreja(Offset(57f, 72f), Offset(63f, 72f), Offset(60f, 76.5f)), Color(0xFFD97B7B))
            drawPath(Path().apply { moveTo(60f, 76.5f); quadraticTo(60f, 80.5f, 55.5f, 80.5f); moveTo(60f, 76.5f); quadraticTo(60f, 80.5f, 64.5f, 80.5f) }, oscuro, style = Stroke(2f, cap = StrokeCap.Round))
            listOf(33f, 67f).forEach { x ->
                drawOval(claro, Offset(x, 94f), androidx.compose.ui.geometry.Size(20f, 10f))
                drawOval(oscuro, Offset(x, 94f), androidx.compose.ui.geometry.Size(20f, 10f), style = Stroke(1.5f))
            }
        }
    }
}

/* Dos notitas suaves (880 y 1318 Hz) al terminar, como el original */
private fun campanita() = thread {
    runCatching {
        val tasa = 44100
        val n = (tasa * 0.9).toInt()
        val datos = ShortArray(n)
        listOf(880.0 to 0.0, 1318.0 to 0.25).forEach { (fr, ini) ->
            val i0 = (ini * tasa).toInt()
            for (i in 0 until (tasa * 0.6).toInt()) {
                val k = i0 + i; if (k >= n) break
                val tt = i.toDouble() / tasa
                val env = min(1.0, tt / 0.05) * exp(-tt * 6)
                datos[k] = (datos[k] + (sin(2 * PI * fr * tt) * env * 0.18 * Short.MAX_VALUE)).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        val pista = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(tasa).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(n * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
        pista.write(datos, 0, n); pista.play()
        Thread.sleep(1200); pista.release()
    }
}
