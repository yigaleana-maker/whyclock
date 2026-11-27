package com.yehu.whyclock

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt

private val JOST_A = FontFamily(Font(R.font.jost_300, FontWeight.Light))
private val INTER_A = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold), Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black))

/* ——— Colores de Alarmas y Temporizador (los del original, claro y oscuro) ——— */
private data class TonosA(val fondo: Color, val tinta: Color, val tarjeta: Color, val tenue: Color, val boton: Color, val rojo: Color, val chipOn: Color, val chipOnTinta: Color)

private fun tonosA() = if (Ajustes.oscuro)
    TonosA(Color(0xFF111111), Color(0xFFFFC23A), Color(0xFF1C1C1E), Color(0xFFA08A55), Color(0xFF2A2A2C), Color(0xFFFF453A), Color(0xFFFFC23A), Color(0xFF111111))
else TonosA(Color(0xFFEFEEE9), Color(0xFF1C1C1E), Color.White, Color(0xFF6B6456), Color(0xFFE2DED3), Color(0xFFE0392B), Color(0xFF1C1C1E), Color(0xFFEFEEE9))

/* Arriba: "← Anterior   Título   Siguiente →", tocables */
@Composable
fun Encabezado(titulo: String, atras: Pair<String, Int>?, adelante: Pair<String, Int>?, tinta: Color) {
    val ir = LocalIr.current
    val f = familiaDigitos()
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (atras != null) Text("← ${atras.first}", color = tinta, fontFamily = JOST_A, fontSize = 16.sp, modifier = Modifier.pulsable { ir(atras.second) }.padding(4.dp))
        }
        Text(titulo, color = tinta, fontFamily = f, fontSize = 22.sp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (adelante != null) Text("${adelante.first} →", color = tinta, fontFamily = JOST_A, fontSize = 16.sp, modifier = Modifier.pulsable { ir(adelante.second) }.padding(4.dp))
        }
    }
}

/* Botón principal en píldora que se aplana al presionarlo */
@Composable
private fun Pildora(texto: String, fondo: Color, tinta: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BotonExpresivo(fondo, modifier.widthIn(min = 200.dp), onClick = onClick) {
        Text(texto, color = tinta, fontFamily = familiaDigitos(), fontSize = 16.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 13.dp))
    }
}

/* Columna ▲ número ▼: el número va sobre una galleta que gira un poquito con cada cambio */
@Composable
private fun Selector(valor: Int, t: TonosA, etiqueta: String?, max: Int, onCambio: (Int) -> Unit) {
    val f = familiaDigitos()
    var vueltas by remember { mutableIntStateOf(0) }
    val giro by animateFloatAsState(vueltas * 40f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow), label = "giro")
    /* El número gira como rodillo, y abajo el tambor: lo giras con el dedo a los lados */
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.width(104.dp).height(80.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(80.dp).graphicsLayer { rotationZ = giro }.clip(Galleta(9, 0.1f)).background(t.boton.copy(alpha = 0.55f)))
            TextoRodillo("%02d".format(valor), androidx.compose.ui.text.TextStyle(fontFamily = f, fontSize = 48.sp), t.tinta)
        }
        Tambor(valor, 0, max, { n -> vueltas += (n - valor); onCambio(n - valor) }, t.tinta, t.boton, Modifier.width(104.dp).height(44.dp))
        if (etiqueta != null) Text(etiqueta, color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp)
    }
}

@Composable
private fun Separador(t: TonosA) = Text(":", color = t.tinta, fontFamily = familiaDigitos(), fontSize = 44.sp, modifier = Modifier.padding(bottom = 60.dp))

private val LETRAS = listOf("L", "M", "X", "J", "V", "S", "D")

private fun diasTexto(d: Int) = when (d) { 0 -> "Una vez"; 127 -> "Todos los días"; 31 -> "Entre semana"; 96 -> "Fin de semana"; else -> LETRAS.filterIndexed { i, _ -> d and (1 shl i) != 0 }.joinToString(" ") }

/* ——— Pantalla: Alarmas ——— */
@Composable
fun PantallaAlarmas(atras: Pair<String, Int>?, adelante: Pair<String, Int>?) {
    val ctx = LocalContext.current
    val t = tonosA()
    val f = familiaDigitos()
    LaunchedEffect(Unit) { Alarmas.cargar(ctx) }
    var editando by remember { mutableStateOf<Alarma?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(aviso) { if (aviso != null) { delay(2600); aviso = null } }
    var tonoDe by remember { mutableIntStateOf(0) }
    val elegirTono = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        @Suppress("DEPRECATION") val uri = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val i = Alarmas.lista.indexOfFirst { it.id == tonoDe }
        if (r.resultCode == android.app.Activity.RESULT_OK && i >= 0) { Alarmas.lista[i] = Alarmas.lista[i].copy(tono = uri?.toString().orEmpty()); Alarmas.guardar(ctx) }
    }

    Box(Modifier.fillMaxSize().background(t.fondo)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Encabezado("Alarmas", atras, adelante, t.tinta)
            Pildora("+ Nueva alarma", t.rojo, Color.White) { editando = Alarma(Alarmas.nuevoId(), 7, 0, 31, true, "") }
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f).bordesSuaves(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Alarmas.lista.sortedBy { it.hora * 60 + it.minuto }, key = { it.id }) { a ->
                    val apagada by animateFloatAsState(if (a.activa) 1f else 0.45f, tween(300), label = "apagada")
                    /* Tarjeta con esquinas grandes; activa se vuelve más redonda */
                    val esquina by animateFloatAsState(if (a.activa) 28f else 16f, spring(dampingRatio = 0.6f), label = "tarjeta")
                    Row(
                        Modifier.animateItem().fillMaxWidth(0.72f).widthIn(max = 340.dp).clip(RoundedCornerShape(esquina.dp)).background(t.tarjeta).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).graphicsLayer { alpha = apagada }.pulsable(hundir = 0.97f) { editando = a }) {
                            Text("%02d:%02d".format(a.hora, a.minuto), color = t.tinta, fontFamily = f, fontSize = 30.sp)
                            Text(a.nombre.ifBlank { "Alarma" }, color = t.tenue, fontFamily = JOST_A, fontSize = 14.sp)
                            Text(diasTexto(a.dias), color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val fondoAct by animateColorAsState(if (a.activa) t.chipOn else t.boton, label = "act")
                            BotonExpresivo(fondoAct, Modifier.width(92.dp).height(38.dp), redondo = a.activa, onClick = {
                                val i = Alarmas.lista.indexOfFirst { it.id == a.id }
                                if (i >= 0) { val n = a.copy(activa = !a.activa); Alarmas.lista[i] = n; Alarmas.guardar(ctx); if (n.activa) aviso = Alarmas.faltaTexto(n) }
                            }) { Cambiante(if (a.activa) "Activa" else "Apagada") { Text(it, color = if (a.activa) t.chipOnTinta else t.tinta, fontFamily = f, fontSize = 14.sp) } }
                            BotonExpresivo(t.boton, Modifier.width(92.dp).height(38.dp), redondo = false, onClick = {
                                tonoDe = a.id
                                elegirTono.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de la alarma")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (a.tono.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(a.tono)))
                            }) { Text("Tono", color = t.tinta, fontFamily = f, fontSize = 14.sp) }
                            BotonExpresivo(t.rojo, Modifier.width(92.dp).height(38.dp), redondo = false, onClick = { Alarmas.lista.removeAll { it.id == a.id }; Alarmas.guardar(ctx) }) {
                                Text("Borrar", color = Color.White, fontFamily = f, fontSize = 14.sp)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        AnimatedVisibility(aviso != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            Text(aviso.orEmpty(), color = t.fondo, fontFamily = JOST_A, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(t.tinta).padding(horizontal = 18.dp, vertical = 10.dp))
        }
        AnimatedVisibility(editando != null, Modifier.fillMaxSize(), enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)).clickable(remember { MutableInteractionSource() }, null) { editando = null }, contentAlignment = Alignment.Center) {
                val e = editando
                if (e != null) EditorAlarma(e, t, onCerrar = { editando = null }, onGuardar = { n ->
                    val i = Alarmas.lista.indexOfFirst { it.id == n.id }
                    if (i >= 0) Alarmas.lista[i] = n else Alarmas.lista += n
                    Alarmas.guardar(ctx); editando = null; aviso = Alarmas.faltaTexto(n)
                })
            }
        }
    }
}

@Composable
private fun EditorAlarma(e: Alarma, t: TonosA, onCerrar: () -> Unit, onGuardar: (Alarma) -> Unit) {
    var h by remember { mutableIntStateOf(e.hora) }
    var m by remember { mutableIntStateOf(e.minuto) }
    var dias by remember { mutableIntStateOf(e.dias) }
    var nombre by remember { mutableStateOf(e.nombre) }
    val entrada = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        val horizontal = maxWidth > maxHeight
        Column(
            Modifier.graphicsLayer { scaleX = entrada.value; scaleY = entrada.value }.widthIn(max = if (horizontal) 640.dp else 380.dp).fillMaxWidth()
                .clip(RoundedCornerShape(32.dp)).background(t.tarjeta).clickable(remember { MutableInteractionSource() }, null) {}.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val reloj: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Selector(h, t, null, 23) { h = (h + it + 24) % 24 }
                    Separador(t)
                    Selector(m, t, null, 59) { m = (m + it + 60) % 60 }
                }
            }
            val resto: @Composable () -> Unit = {
                Column {
                    BasicTextField(
                        nombre, { nombre = it }, singleLine = true, cursorBrush = SolidColor(t.tinta),
                        textStyle = TextStyle(color = t.tinta, fontFamily = JOST_A, fontSize = 18.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(if (Ajustes.oscuro) Color(0xFF111111) else Color(0xFFF7F6F2))
                            .border(1.dp, if (Ajustes.oscuro) Color(0xFF333333) else Color(0xFFCFC9BA), RoundedCornerShape(16.dp)).padding(12.dp),
                        decorationBox = { campo -> Box { if (nombre.isEmpty()) Text("Nombre (opcional)", color = t.tenue, fontFamily = JOST_A, fontSize = 18.sp); campo() } }
                    )
                    /* Días: apagados son cuadritos redondeados, encendidos se vuelven círculos */
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LETRAS.forEachIndexed { i, l ->
                            val on = dias and (1 shl i) != 0
                            val fondo by animateColorAsState(if (on) t.chipOn else t.boton, label = "dia")
                            BotonExpresivo(fondo, Modifier.size(38.dp), redondo = on, onClick = { dias = dias xor (1 shl i) }) {
                                Text(l, color = if (on) t.chipOnTinta else t.tinta, fontFamily = INTER_A, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                    Cambiante(diasTexto(dias)) { Text(if (it == "Una vez") "Suena una sola vez" else it, color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp) }
                    Spacer(Modifier.height(6.dp))
                    Pildora("Guardar", t.rojo, Color.White, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                        onGuardar(e.copy(hora = h, minuto = m, dias = dias, nombre = nombre.trim(), activa = true))
                    }
                    BotonExpresivo(t.boton, Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp), redondo = false, onClick = onCerrar) {
                        Text("Cancelar", color = t.tinta, fontFamily = familiaDigitos(), fontSize = 15.sp)
                    }
                }
            }
            if (horizontal) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                reloj(); Box(Modifier.weight(1f)) { resto() }
            } else { reloj(); resto() }
        }
    }
}

/* ——— Pantalla: Temporizador ——— */
@Composable
fun PantallaTemporizador(atras: Pair<String, Int>?, adelante: Pair<String, Int>?) {
    val ctx = LocalContext.current
    val t = tonosA()
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { Temporizador.cargar(ctx); while (true) { ahora = System.currentTimeMillis(); delay(32) } }
    val corriendo = Temporizador.fin > ahora
    val pausado = !corriendo && Temporizador.resta > 0
    val queda = if (corriendo) Temporizador.fin - ahora else Temporizador.resta
    Column(Modifier.fillMaxSize().background(t.fondo), horizontalAlignment = Alignment.CenterHorizontally) {
        Encabezado("Temporizador", atras, adelante, t.tinta)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(corriendo || pausado, transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f)) togetherWith fadeOut(tween(150))
            }, label = "modo") { activo ->
                if (!activo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Selector(h, t, "horas", 23) { h = (h + it + 24) % 24 }
                        Separador(t)
                        Selector(m, t, "min", 59) { m = (m + it + 60) % 60 }
                        Separador(t)
                        Selector(s, t, "seg", 59) { s = (s + it + 60) % 60 }
                    }
                    Spacer(Modifier.height(14.dp))
                    Pildora("Iniciar", t.rojo, Color.White) { val ms = (h * 3600L + m * 60L + s) * 1000L; if (ms > 0) Temporizador.iniciar(ctx, ms) }
                } else Corriendo(t, queda, pausado, ahora)
            }
        }
    }
}

/* El temporizador en marcha: anillo expresivo ondulado que se mueve, y se alisa al pausar */
@Composable
private fun Corriendo(t: TonosA, queda: Long, pausado: Boolean, ahora: Long) {
    val ctx = LocalContext.current
    val f = familiaDigitos()
    val ondas = rememberInfiniteTransition(label = "ondas")
    val fase by ondas.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "fase")
    val amplitud by animateFloatAsState(if (pausado) 0f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow), label = "amp")
    val giro by ondas.animateFloat(0f, 360f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart), label = "giro")
    val frac = if (Temporizador.total > 0) (queda.toFloat() / Temporizador.total).coerceIn(0f, 1f) else 1f
    val fracSuave by animateFloatAsState(frac, spring(stiffness = Spring.StiffnessLow), label = "frac")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontal = maxWidth > maxHeight
        val lado = min(if (horizontal) maxHeight.value * 0.85f else maxWidth.value * 0.78f, 300f).dp
        val anillo: @Composable () -> Unit = {
            Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
                Box(Modifier.size(lado * 0.74f).graphicsLayer { rotationZ = if (pausado) 0f else giro }.clip(Galleta(12, 0.06f)).background(t.boton.copy(alpha = 0.45f)))
                Canvas(Modifier.fillMaxSize()) {
                    anilloExpresivo(fracSuave, fase, amplitud, if (Ajustes.oscuro) Color(0x33FFFFFF) else Color(0x1F000000),
                        if (Ajustes.oscuro) Color(0xFFFFC23A) else Color(0xFFE0392B), size.width * 9f / 200f)
                }
                val seg = ((queda + 999) / 1000).toInt()
                TextoRodillo("%02d:%02d:%02d".format(seg / 3600, seg / 60 % 60, seg % 60), androidx.compose.ui.text.TextStyle(fontFamily = f, fontSize = (lado.value * 0.13f).sp), t.tinta,
                    modifier = Modifier.graphicsLayer { alpha = if (pausado) (0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(ahora / 500.0)).toFloat()) else 1f })
            }
        }
        val botones: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonExpresivo(t.boton, Modifier.height(40.dp).width(120.dp), onClick = { Temporizador.sumarMinuto(ctx) }) {
                    Text("+1:00", color = t.tinta, fontFamily = f, fontSize = 15.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonExpresivo(t.boton, Modifier.weight(1f).height(52.dp), redondo = pausado, onClick = { if (pausado) Temporizador.seguir(ctx) else Temporizador.pausar(ctx) }) {
                        Cambiante(if (pausado) "Reanudar" else "Pausar") { Text(it, color = t.tinta, fontFamily = f, fontSize = 15.sp) }
                    }
                    BotonExpresivo(t.rojo, Modifier.weight(1f).height(52.dp), redondo = false, onClick = { Temporizador.cancelar(ctx) }) {
                        Text("Cancelar", color = Color.White, fontFamily = f, fontSize = 15.sp)
                    }
                }
            }
        }
        if (horizontal) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            anillo(); Box(Modifier.width(300.dp)) { botones() }
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            anillo(); Spacer(Modifier.height(14.dp)); botones()
        }
    }
}

/* ——— Aviso de permisos: lo que falta para que las alarmas salgan en pantalla completa y suenen a tiempo ——— */
@Composable
fun DialogoPermisos(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var vuelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); vuelta++ } }
    val items = remember(vuelta) {
        listOfNotNull(
            if (!Permisos.notificaciones(ctx)) Triple("Notificaciones", "Para el temporizador y la alarma sonando") { Permisos.abrirNotificaciones(ctx) } else null,
            if (!Permisos.pantallaCompleta(ctx)) Triple("Pantalla completa", "Para que la alarma aparezca aunque el teléfono esté bloqueado") { Permisos.abrirPantallaCompleta(ctx) } else null,
            if (!Permisos.exactas(ctx)) Triple("Alarmas exactas", "Para que suenen al minuto") { Permisos.abrirExactas(ctx) } else null,
            if (!Permisos.encima(ctx)) Triple("Mostrar sobre otras apps", "Para que la pantalla de alarma salga aunque estés usando el teléfono") { Permisos.abrirEncima(ctx) } else null,
            if (!Permisos.bateria(ctx)) Triple("Sin restricción de batería", "Para que Android no la duerma") { Permisos.abrirBateria(ctx) } else null
        )
    }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) onCerrar() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Color(0xFF1B1613),
        shape = RoundedCornerShape(32.dp),
        title = { Text("Para que las alarmas funcionen bien", color = Color(0xFFF0E9E2), fontFamily = INTER_A, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { (n, d, accion) ->
                    BotonExpresivo(Color(0xFF2A2420), Modifier.fillMaxWidth(), redondo = false, onClick = accion) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n, color = Color(0xFFF0E9E2), fontFamily = INTER_A, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(d, color = Color(0xB3F0E9E2), fontFamily = INTER_A, fontSize = 12.sp)
                            }
                            Text("Activar", color = Color(0xFFE0403A), fontFamily = INTER_A, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onCerrar) { Text("Ahora no", color = Color(0xFFE0403A), fontFamily = INTER_A) } }
    )
}
