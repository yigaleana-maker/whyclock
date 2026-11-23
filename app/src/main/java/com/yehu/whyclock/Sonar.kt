package com.yehu.whyclock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val JOST_S = FontFamily(Font(R.font.jost_300, FontWeight.Light))
private val INTER_S = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold), Font(R.font.inter_700, FontWeight.Bold))
private val ROJO = Color(0xFFE0392B)

/* ——— La pantalla de alarma, encima de la pantalla de bloqueo. El sonido lo lleva el servicio Sonando ——— */
class AlarmaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        /* Primero se pide mostrarse sobre el bloqueo y prender la pantalla, antes de crear la ventana */
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        super.onCreate(savedInstanceState)
        Ajustes.cargar(this)
        val prefs = getSharedPreferences("whyclock_alarmas", MODE_PRIVATE)
        /* Atrás no la quita (igual que un despertador): hay que deslizar o tocar Detener */
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        val hora = Sonando.hora.ifBlank { SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis()) }
        setContent {
            var vista by remember { mutableStateOf(false) }
            /* Si se apagó desde la notificación, se cierra sola (con margen, para no cerrarse antes de que arranque el sonido) */
            LaunchedEffect(Sonando.activo, vista) { if (vista && !Sonando.activo) finish() }
            LaunchedEffect(Unit) { delay(3000); vista = true }
            PantallaSonando(
                hora = hora,
                temporizador = Sonando.esTemporizador,
                nombre = Sonando.nombre.takeIf { it.isNotBlank() && it != "Alarma" && it != "Temporizador" },
                posponerInicial = prefs.getInt("posponer", 10),
                onPosponer = { min ->
                    prefs.edit().putInt("posponer", min).apply()
                    runCatching { startService(Intent(this, Sonando::class.java).setAction("posponer").putExtra("min", min)) }
                    finish()
                },
                onDetener = {
                    runCatching { startService(Intent(this, Sonando::class.java).setAction("detener")) }
                    finish()
                }
            )
        }
    }
}

/* Como la original: negro, el relojito gris y "Alarma" en rojo, la hora enorme en blanco, Posponer por 5/10/15/20,
   el botón rojo Posponer y "Deslizar para apagar". En horizontal: Detener y Posponer lado a lado */
@Composable
internal fun PantallaSonando(hora: String, temporizador: Boolean, nombre: String?, posponerInicial: Int, onPosponer: (Int) -> Unit, onDetener: () -> Unit) {
    val f = familiaDigitos()
    var min by remember { mutableIntStateOf(posponerInicial.takeIf { it in listOf(5, 10, 15, 20) } ?: 10) }
    /* Entrada suave: sube un poquito (nunca empieza invisible) */
    val sube = remember { Animatable(40f) }
    LaunchedEffect(Unit) { sube.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
    val latido = rememberInfiniteTransition(label = "latido")
    val pulso by latido.animateFloat(1f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulso")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 24.dp, vertical = 24.dp), contentAlignment = Alignment.Center) {
        val horizontal = maxWidth > maxHeight
        /* La hora, del tamaño que quepa a lo ancho (5 caracteres) */
        val porAncho = (if (horizontal) maxWidth.value * 0.55f else maxWidth.value) / 3.4f
        val tamHora = if (horizontal) min(porAncho, maxHeight.value * 0.34f) else min(porAncho, 200f)

        val etiqueta: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { scaleX = pulso; scaleY = pulso }) {
                Despertador(Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Text(if (temporizador) "Temporizador" else "Alarma", color = ROJO, fontFamily = INTER_S, fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = 0.5.sp)
            }
        }
        val horaTexto: @Composable () -> Unit = {
            Text(hora, color = Color.White, fontFamily = f, fontSize = tamHora.sp, lineHeight = tamHora.sp, maxLines = 1, softWrap = false)
        }
        val nombreTexto: @Composable () -> Unit = {
            if (nombre != null) Text(nombre, color = Color.White.copy(alpha = 0.65f), fontFamily = JOST_S, fontSize = 20.sp, maxLines = 1)
        }
        val duraciones: @Composable () -> Unit = {
            if (!temporizador) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Posponer por", color = Color.White.copy(alpha = 0.55f), fontFamily = JOST_S, fontSize = 14.sp)
                listOf(5, 10, 15, 20).forEach { d ->
                    val on = d == min
                    val fondo by animateColorAsState(if (on) ROJO else Color.Transparent, label = "d")
                    BotonExpresivo(fondo, Modifier.height(38.dp).border(1.dp, if (on) ROJO else Color.White.copy(alpha = 0.25f), RoundedCornerShape(50)), onClick = { min = d }) {
                        Text("$d min", color = Color.White, fontFamily = INTER_S, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 14.dp))
                    }
                }
            }
        }
        val alto = if (horizontal) 72.dp else 88.dp
        val posponer: @Composable (Modifier) -> Unit = { m ->
            BotonExpresivo(ROJO, m.height(alto), onClick = { onPosponer(min) }) {
                Text("Posponer", color = Color.White, fontFamily = JOST_S, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
            }
        }

        Column(
            Modifier.graphicsLayer { translationY = sube.value * density }.widthIn(max = 860.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (horizontal) 14.dp else 22.dp)
        ) {
            if (horizontal) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { etiqueta(); nombreTexto() }
                    horaTexto()
                }
                duraciones()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BotonExpresivo(Color(0xFF2C2C2E), Modifier.weight(1f).height(alto), onClick = onDetener) {
                        Text("Detener", color = Color.White, fontFamily = JOST_S, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
                    }
                    if (!temporizador) posponer(Modifier.weight(1f))
                }
            } else {
                etiqueta(); horaTexto(); nombreTexto(); duraciones()
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!temporizador) posponer(Modifier.fillMaxWidth())
                    DeslizarCristal(onDetener)
                }
            }
        }
    }
}

/* El relojito despertador gris de la original */
@Composable
private fun Despertador(modifier: Modifier) {
    Canvas(modifier) {
        val e = size.width / 24f
        val gris = Color(0xFF8E8E93)
        drawCircle(gris, 8 * e, Offset(12 * e, 13 * e))
        drawLine(Color.Black, Offset(12 * e, 8.5f * e), Offset(12 * e, 13 * e), 1.8f * e, StrokeCap.Round)
        drawLine(Color.Black, Offset(12 * e, 13 * e), Offset(15 * e, 15 * e), 1.8f * e, StrokeCap.Round)
        drawLine(gris, Offset(5.5f * e, 3.5f * e), Offset(3 * e, 6 * e), 2 * e, StrokeCap.Round)
        drawLine(gris, Offset(18.5f * e, 3.5f * e), Offset(21 * e, 6 * e), 2 * e, StrokeCap.Round)
    }
}

/* Deslizar para apagar, de cristal: pista de vidrio ahumado, texto con un brillo que recorre las letras,
   y el mango redondo de vidrio (con su reflejo arriba y el cuadrito blanco) que se estira al arrastrarlo
   como una gota y regresa con resorte si lo sueltas antes de llegar */
@Composable
private fun DeslizarCristal(onApagar: () -> Unit) {
    val alcance = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val estira = remember { Animatable(0f) }
    val densidad = LocalDensity.current
    val vibra = LocalHapticFeedback.current
    var listo by remember { mutableStateOf(false) }
    val brillo = rememberInfiniteTransition(label = "brillo")
    val barrido by brillo.animateFloat(-0.4f, 1.4f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "barrido")

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(76.dp)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0DFFFFFF))))
            .background(Color(0xFF1C1C1E).copy(alpha = 0.85f))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF))), RoundedCornerShape(50))
    ) {
        val mangoPx = with(densidad) { 68.dp.toPx() }
        val margen = with(densidad) { 4.dp.toPx() }
        val max = (with(densidad) { maxWidth.toPx() } - mangoPx - margen * 2).coerceAtLeast(1f)
        val avance = (x.value / max).coerceIn(0f, 1f)
        val anchoPista = maxWidth

        /* Sombra interior de arriba, como vidrio hundido */
        Box(Modifier.fillMaxWidth().height(10.dp).background(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent))))
        /* El rastro rojo de cristal que va llenando la pista detrás del mango */
        Box(
            Modifier.fillMaxHeight().width(with(densidad) { (x.value + mangoPx + margen * 2).toDp() }.coerceAtMost(anchoPista))
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(ROJO.copy(alpha = 0.10f), ROJO.copy(alpha = 0.45f * avance + 0.1f))))
                .graphicsLayer { alpha = avance }
        )
        /* El texto con brillo que lo recorre (como el iPhone), y que se desvanece al deslizar */
        val estilo = TextStyle(
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.38f), (barrido - 0.15f).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.38f),
                barrido.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.95f),
                (barrido + 0.15f).coerceIn(0f, 1f) to Color.White.copy(alpha = 0.38f), 1f to Color.White.copy(alpha = 0.38f),
                start = Offset.Zero, end = Offset(with(densidad) { 260.dp.toPx() }, 0f)
            ),
            fontFamily = JOST_S, fontSize = 21.sp
        )
        Text("Deslizar para apagar", style = estilo,
            modifier = Modifier.align(Alignment.Center).padding(start = 68.dp).graphicsLayer { alpha = (1f - avance * 1.3f).coerceIn(0f, 1f) })

        /* El mango de vidrio */
        Box(
            Modifier
                .offset { IntOffset((x.value + margen).roundToInt(), margen.roundToInt()) }
                .size(68.dp)
                .graphicsLayer {
                    scaleX = 1f + estira.value; scaleY = 1f - estira.value * 0.45f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
                .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF4A4A4E), Color(0xFF2A2A2D))))
                .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), center = Offset(with(densidad) { 34.dp.toPx() }, with(densidad) { 9.dp.toPx() }), radius = with(densidad) { 34.dp.toPx() }))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))), CircleShape)
                .pointerInput(max) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            alcance.launch {
                                if (x.value >= max * 0.8f) {
                                    x.animateTo(max, tween(120))
                                    if (!listo) { listo = true; vibra.performHapticFeedback(HapticFeedbackType.LongPress); onApagar() }
                                } else x.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
                            }
                            alcance.launch { estira.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
                        },
                        onDragCancel = {
                            alcance.launch { x.animateTo(0f, spring(dampingRatio = 0.62f)) }
                            alcance.launch { estira.animateTo(0f, spring(dampingRatio = 0.4f)) }
                        }
                    ) { cambio, d ->
                        cambio.consume()
                        alcance.launch { x.snapTo((x.value + d).coerceIn(0f, max)) }
                        alcance.launch { estira.snapTo((abs(d) / 60f).coerceAtMost(0.16f)); estira.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape((5 + 8 * avance).dp)).background(Color.White))
        }
    }
}
