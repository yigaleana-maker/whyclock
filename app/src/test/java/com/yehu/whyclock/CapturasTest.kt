package com.yehu.whyclock

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/* Toma fotos de las pantallas para revisarlas sin teléfono */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasTest {
    @get:Rule val compose = createComposeRule()

    private fun foto(nombre: String, avanzar: Long = 1500, contenido: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(contenido)
        compose.mainClock.advanceTimeBy(avanzar)
        compose.onRoot().captureRoboImage("build/capturas/$nombre.png")
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun alarmaVertical() = foto("alarma_vertical") { PantallaSonando("07:30", false, "Gym", 10, {}, {}) }

    @Test @Config(qualifiers = "w891dp-h411dp-xhdpi")
    fun alarmaHorizontal() = foto("alarma_horizontal") { PantallaSonando("07:30", false, null, 10, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun temporizadorSonando() = foto("temporizador_sonando") { PantallaSonando("16:05", true, null, 10, {}, {}) }

    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun temporizadorElegir() = foto("temporizador_elegir") { PantallaTemporizador("Música" to 6, "Alarmas" to IR_ALARMAS) }

    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun temporizadorCorriendo() {
        Temporizador.total = 300_000L; Temporizador.fin = System.currentTimeMillis() + 190_000L
        foto("temporizador_corriendo") { PantallaTemporizador("Música" to 6, "Alarmas" to IR_ALARMAS) }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-xhdpi")
    fun alarmas() {
        Alarmas.cargar(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Alarmas.lista.clear()
        Alarmas.lista += Alarma(1, 7, 0, 31, true, "Trabajo")
        Alarmas.lista += Alarma(2, 9, 30, 96, false, "")
        foto("alarmas") { PantallaAlarmas("Reloj" to -1, "Música" to 6) }
    }
}
