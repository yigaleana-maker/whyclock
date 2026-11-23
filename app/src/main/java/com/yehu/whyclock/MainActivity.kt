package com.yehu.whyclock

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* WhyClock: tu reloj de mesa. Pantalla completa, siempre encendida, y deslizas entre sus pantallas */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ajustes.cargar(this)
        Alarmas.cargar(this)
        Alarmas.programar(this)
        Temporizador.cargar(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { ConNoche({ b -> window.attributes = window.attributes.apply { screenBrightness = b } }) { Pantallas() } }
    }
}

const val PAGINAS = 8
/* Para LocalIr: 0-7 son relojes; estos dos son las pantallas de abajo */
const val IR_ALARMAS = 100
const val IR_TEMPORIZADOR = 101
private val INTER = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold))

@androidx.compose.runtime.Composable
private fun Pantallas() {
    val ctx = LocalContext.current
    val ahora = rememberAhora()
    val estado = rememberPagerState(initialPage = Ajustes.pagina.coerceIn(0, PAGINAS - 1)) { PAGINAS }
    val alcance = rememberCoroutineScope()
    /* Arriba los relojes (se deslizan a los lados); deslizando hacia arriba, Alarmas y luego Temporizador, como en el original */
    val vertical = rememberPagerState { 3 }
    var barra by remember { mutableStateOf(false) }
    var toques by remember { mutableIntStateOf(0) }
    var verReposo by remember { mutableStateOf(false) }
    /* La barra de ajustes aparece al tocar y se esconde sola */
    LaunchedEffect(barra, toques) { if (barra) { delay(4500); barra = false } }
    LaunchedEffect(estado) { snapshotFlow { estado.currentPage }.collect { Ajustes.pagina = it; Ajustes.guardar(ctx) } }

    val ir: (Int) -> Unit = { n ->
        alcance.launch {
            when (n) {
                IR_ALARMAS -> vertical.animateScrollToPage(1)
                IR_TEMPORIZADOR -> vertical.animateScrollToPage(2)
                else -> { launch { vertical.animateScrollToPage(0) }; if (n >= 0) estado.animateScrollToPage(n.coerceIn(0, PAGINAS - 1)) }
            }
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalIr provides ir) {
    androidx.compose.foundation.pager.VerticalPager(vertical, Modifier.fillMaxSize().background(Color.Black), beyondViewportPageCount = 1) { v ->
    Box(Modifier.fillMaxSize().paginaFluida(vertical, v)) {
    when (v) {
    1 -> PantallaAlarmas("Reloj" to -1, "Música" to 6)
    2 -> PantallaTemporizador("Música" to 6, "Alarmas" to IR_ALARMAS)
    else -> Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTapGestures(
                onTap = { barra = !barra; toques++ },
                /* Doble toque: del analógico al digital y de regreso, como en el original */
                onDoubleTap = {
                    val p = estado.currentPage
                    if (p <= 1) alcance.launch { estado.animateScrollToPage(1 - p) }
                }
            )
        }
    ) {
        HorizontalPager(state = estado, modifier = Modifier.fillMaxSize()) { p ->
            Box(Modifier.fillMaxSize().paginaFluida(estado, p)) { Pagina(p, ahora) }
        }
        AnimatedVisibility(
            visible = barra,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chip: @androidx.compose.runtime.Composable (String, () -> Unit) -> Unit = { texto, accion ->
                    Text(
                        texto, color = Color(0xFFF0E9E2), fontFamily = INTER, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xE61B1613))
                            .clickable { accion(); toques++; Ajustes.guardar(ctx) }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                chip(if (Ajustes.oscuroElegido) "Modo claro" else "Modo oscuro") { Ajustes.oscuroElegido = !Ajustes.oscuroElegido }
                chip("Modo noche: " + Ajustes.nombreNoche()) { Ajustes.siguienteNoche() }
                chip("Letra: " + Ajustes.nombreLetra()) { Ajustes.siguienteLetra() }
                chip("Ciudad") { Clima.pedir = true; barra = false }
                chip("Pantalla de reposo") { verReposo = true; barra = false }
                chip(if (Ajustes.suave) "Movimiento suave" else "Movimiento por pasos") { Ajustes.suave = !Ajustes.suave }
            }
        }
        if (verReposo) DialogoReposo { verReposo = false; Ajustes.guardar(ctx) }
    }
    }
    }
    }
    DialogoCiudad()
    }
}

/* Los relojes, en el orden del original. Alarmas y Temporizador están abajo (deslizando hacia arriba) */
val NOMBRES = listOf("Analógico", "Digital", "Dual", "Clima", "Fecha", "Calendario", "Música", "Hora grande")

@androidx.compose.runtime.Composable
fun Pagina(p: Int, ahora: Long) {
    when (p) {
        0 -> RelojAnalogico(ahora)
        1 -> RelojDigital(ahora)
        2 -> RelojDual(ahora)
        3 -> PantallaClima(ahora)
        4 -> PantallaFecha(ahora)
        5 -> PantallaCalendario(ahora)
        6 -> PantallaMusica(ahora)
        else -> PantallaHoraGrande(ahora)
    }
}

/* Elegir qué pantalla se ve cuando el teléfono se carga */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@androidx.compose.runtime.Composable
fun DialogoReposo(onCerrar: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Color(0xFF1B1613),
        title = { Text("Pantalla de reposo", color = Color(0xFFF0E9E2), fontFamily = INTER) },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Elige qué reloj se ve cuando el teléfono está cargando.", color = Color(0xB3F0E9E2), fontSize = 13.sp, fontFamily = INTER)
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NOMBRES.forEachIndexed { i, n ->
                        val on = Ajustes.reposo == i
                        val fondo by androidx.compose.animation.animateColorAsState(if (on) Color(0xFFE0403A) else Color(0xFF2A2420), label = "r")
                        Text(n, color = Color(0xFFF0E9E2), fontFamily = INTER, fontSize = 14.sp,
                            modifier = Modifier.pulsable { Ajustes.reposo = i }.clip(RoundedCornerShape(99.dp)).background(fondo).padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onCerrar) { Text("Listo", color = Color(0xFFE0403A), fontFamily = INTER) } }
    )
}
