package com.yehu.whyclock

import android.service.dreams.DreamService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* Modo reposo: cuando el teléfono se carga, Android muestra WhyClock en lugar de apagar la pantalla.
   Se puede deslizar entre los relojes; un toque muestra "Salir". */
class Reposo : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val ciclo = LifecycleRegistry(this)
    private val estado = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = ciclo
    override val savedStateRegistry: SavedStateRegistry get() = estado.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        estado.performRestore(null)
        ciclo.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        Ajustes.cargar(this)
        Alarmas.cargar(this)
        val vista = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@Reposo)
            setViewTreeSavedStateRegistryOwner(this@Reposo)
            setContent { ConNoche({ b -> window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = b } } }) { Contenido { finish() } } }
        }
        setContentView(vista)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_START); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    override fun onDreamingStopped() { ciclo.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_STOP); super.onDreamingStopped() }
    override fun onDestroy() { ciclo.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy() }
}

private val EN_REPOSO = (0 until PAGINAS).toList()

@androidx.compose.runtime.Composable
private fun Contenido(salir: () -> Unit) {
    val ahora = rememberAhora()
    val inicio = EN_REPOSO.indexOf(Ajustes.reposo).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = inicio) { EN_REPOSO.size }
    val alcance = rememberCoroutineScope()
    var boton by remember { mutableStateOf(false) }
    var toques by remember { mutableIntStateOf(0) }
    LaunchedEffect(boton, toques) { if (boton) { delay(4000); boton = false } }
    CompositionLocalProvider(LocalIr provides { n -> EN_REPOSO.indexOf(n).takeIf { it >= 0 }?.let { alcance.launch { pager.animateScrollToPage(it) } } }) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures(onTap = { boton = !boton; toques++ }) }) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i -> Box(Modifier.fillMaxSize().paginaFluida(pager, i)) { Pagina(EN_REPOSO[i], ahora) } }
            AnimatedVisibility(boton, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), enter = fadeIn(), exit = fadeOut()) {
                Text("Salir", color = Color(0xFFF0E9E2), fontSize = 15.sp,
                    modifier = Modifier.pulsable(onClick = salir).clip(RoundedCornerShape(99.dp)).background(Color(0xE61B1613)).padding(horizontal = 22.dp, vertical = 11.dp))
            }
        }
    }
}
