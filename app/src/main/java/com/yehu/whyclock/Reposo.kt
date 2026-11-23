package com.yehu.whyclock

import android.service.dreams.DreamService
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/* Modo reposo: cuando el teléfono se carga, Android muestra WhyClock (Ajustes > Pantalla > Protector de pantalla) */
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
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        Ajustes.cargar(this)
        val vista = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@Reposo)
            setViewTreeSavedStateRegistryOwner(this@Reposo)
            /* Muestra la última pantalla que dejaste (si era una interactiva, el reloj analógico) */
            setContent { val ahora = rememberAhora(); Pagina(Ajustes.pagina.let { if (it == 6 || it == 8) 0 else it }, ahora) }
        }
        setContentView(vista)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_START); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    override fun onDreamingStopped() { ciclo.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE); ciclo.handleLifecycleEvent(Lifecycle.Event.ON_STOP); super.onDreamingStopped() }
    override fun onDestroy() { ciclo.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY); super.onDestroy() }
}
