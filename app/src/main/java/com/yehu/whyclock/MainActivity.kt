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
        setContent { Pantallas() }
    }
}

private const val PAGINAS = 9
private val INTER = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold))

@androidx.compose.runtime.Composable
private fun Pantallas() {
    val ctx = LocalContext.current
    val ahora = rememberAhora()
    val estado = rememberPagerState(initialPage = Ajustes.pagina.coerceIn(0, PAGINAS - 1)) { PAGINAS }
    val alcance = rememberCoroutineScope()
    var barra by remember { mutableStateOf(false) }
    var toques by remember { mutableIntStateOf(0) }
    var verAlarmas by remember { mutableStateOf(false) }
    var verTemporizador by remember { mutableStateOf(false) }
    /* La barra de ajustes aparece al tocar y se esconde sola */
    LaunchedEffect(barra, toques) { if (barra) { delay(4500); barra = false } }
    LaunchedEffect(estado) { snapshotFlow { estado.currentPage }.collect { Ajustes.pagina = it; Ajustes.guardar(ctx) } }

    Box(
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
            Pagina(p, ahora)
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
                chip(if (Ajustes.oscuro) "Modo claro" else "Modo oscuro") { Ajustes.oscuro = !Ajustes.oscuro }
                chip("Letra: " + Ajustes.nombreLetra()) { Ajustes.siguienteLetra() }
                chip("Alarmas") { verAlarmas = true; barra = false }
                chip("Temporizador") { verTemporizador = true; barra = false }
                chip("Ciudad") { Clima.pedir = true; barra = false }
                chip(if (Ajustes.suave) "Movimiento suave" else "Movimiento por pasos") { Ajustes.suave = !Ajustes.suave }
            }
        }
        if (verAlarmas) DialogoAlarmas { verAlarmas = false }
        if (verTemporizador) DialogoTemporizador { verTemporizador = false }
        DialogoCiudad()
    }
}

/* Las nueve pantallas, en el orden del original */
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
        7 -> PantallaHoraGrande(ahora)
        else -> PantallaRespira()
    }
}
