package com.yehu.whyclock

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/* Modo noche: con el cuarto oscuro, todo se ve rojo sobre negro y el brillo baja al mínimo */
object Noche {
    var activa by mutableStateOf(false)
}

private const val ENTRA_LUX = 3f      // más oscuro que esto: entra
private const val SALE_LUX = 10f      // más claro que esto: sale
private const val ESPERA = 10_000L    // tiene que durar 10 s para cambiar

@Composable
fun ConNoche(brillo: (Float) -> Unit, contenido: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var lux by remember { mutableStateOf<Float?>(null) }
    var oscuro by remember { mutableStateOf(false) }

    /* El sensor de luz (no pide permiso). Si el teléfono no tiene, el automático no se activa */
    DisposableEffect(Ajustes.noche) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { lux = e.values[0] }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (Ajustes.noche == "auto" && sensor != null) sm.registerListener(oyente, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(oyente) }
    }
    /* Cada segundo revisa: solo cambia si lleva 10 segundos seguidos queriendo cambiar */
    LaunchedEffect(Ajustes.noche) {
        var desde = 0L
        while (true) {
            val l = lux
            val quiere = when (Ajustes.noche) {
                "siempre" -> true
                "apagado" -> false
                else -> if (l == null) false else if (oscuro) l < SALE_LUX else l < ENTRA_LUX
            }
            if (Ajustes.noche != "auto") { oscuro = quiere; desde = 0L }
            else if (quiere != oscuro) {
                val ahora = System.currentTimeMillis()
                if (desde == 0L) desde = ahora else if (ahora - desde >= ESPERA) { oscuro = quiere; desde = 0L }
            } else desde = 0L
            delay(1000)
        }
    }
    Noche.activa = oscuro
    LaunchedEffect(oscuro) {
        brillo(if (oscuro) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
    }
    /* El paso a rojo se hace poco a poco, en 2 segundos */
    val p by animateFloatAsState(if (oscuro) 1f else 0f, tween(2000), label = "noche")
    val pincel = remember { Paint() }
    Box(
        Modifier.fillMaxSize().drawWithContent {
            if (p <= 0.001f) drawContent()
            else {
                pincel.colorFilter = ColorFilter.colorMatrix(matrizNoche(p))
                drawIntoCanvas { c ->
                    c.saveLayer(Rect(0f, 0f, size.width, size.height), pincel)
                    drawContent()
                    c.restore()
                }
            }
        }
    ) { contenido() }
}

/* Mezcla entre los colores normales (p = 0) y solo rojo según lo claro de cada punto (p = 1) */
private fun matrizNoche(p: Float): ColorMatrix {
    val q = 1f - p
    val rojo = floatArrayOf(0.40f, 0.62f, 0.12f)
    return ColorMatrix(floatArrayOf(
        q + p * rojo[0], p * rojo[1], p * rojo[2], 0f, 0f,
        0f, q, 0f, 0f, 0f,
        0f, 0f, q, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
}
