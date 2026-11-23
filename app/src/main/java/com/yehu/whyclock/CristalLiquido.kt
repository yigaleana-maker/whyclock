package com.yehu.whyclock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Cristal líquido: lo que hay detrás de la barra (la lista, las portadas) se ve a través de ella,
 * un poco difuminado y doblado en las orillas como por una lente, con un brillo en el borde.
 * Viene de Headpod. Se graba el contenido de la pantalla en una capa (FondoCristal.capa) y la barra la dibuja en su lugar.
 * Necesita Android 13 o más; en los anteriores la barra usa el vidrio de siempre.
 */
object FondoCristal {
    var capa: GraphicsLayer? = null
    var origen by mutableStateOf(Offset.Zero)
}

/* Un fondo propio para el cristal (el reproductor tiene el suyo: su portada), que tiene preferencia */
class FondoLocal(val capa: GraphicsLayer) { var origen by mutableStateOf(Offset.Zero) }
val LocalFondoLocal = androidx.compose.runtime.staticCompositionLocalOf<FondoLocal?> { null }

@Composable
fun rememberFondoLocal(): FondoLocal {
    val capa = androidx.compose.ui.graphics.rememberGraphicsLayer()
    return remember(capa) { FondoLocal(capa) }
}

fun Modifier.grabarFondoLocal(f: FondoLocal): Modifier = this
    .onGloballyPositioned { f.origen = it.positionInRoot() }
    .drawWithContent {
        f.capa.record { this@drawWithContent.drawContent() }
        drawLayer(f.capa)
    }

/* Lo que se ve detrás del cristal: se pone en el contenedor de la pantalla */
@Composable
fun Modifier.grabarFondoCristal(activo: Boolean): Modifier {
    if (!activo || android.os.Build.VERSION.SDK_INT < 33) return this
    val capa = androidx.compose.ui.graphics.rememberGraphicsLayer()
    FondoCristal.capa = capa
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (FondoCristal.capa === capa) FondoCristal.capa = null } }
    return this
        .onGloballyPositioned { FondoCristal.origen = it.positionInRoot() }
        .drawWithContent {
            capa.record { this@drawWithContent.drawContent() }
            drawLayer(capa)
        }
}

/* La lente: dobla lo de atrás cerca de las orillas (más cerca, más), separa un poquito los colores
   como un prisma y agrega el brillo del borde, más fuerte arriba a la izquierda */
private const val SHADER_LENTE = """
uniform shader contenido;
uniform float2 tam;
uniform float radio;
uniform float banda;
uniform float2 toque;
uniform float fuerza;
uniform float radioToque;
uniform float2 luzDir;

float caja(float2 p) {
    float2 q = abs(p - tam * 0.5) - (tam * 0.5 - radio);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radio;
}

half4 main(float2 p) {
    float d = -caja(p);
    float e = 1.0;
    float2 n = float2(caja(p + float2(e, 0.0)) - caja(p - float2(e, 0.0)),
                      caja(p + float2(0.0, e)) - caja(p - float2(0.0, e)));
    n = n / max(length(n), 0.0001);
    /* Vidrio grueso: casi nada en el centro y mucho más doblado al llegar a la orilla */
    float k = clamp(1.0 - d / banda, 0.0, 1.0);
    k = k * k * (3.0 - 2.0 * k);
    float curva = 1.0 - sqrt(max(1.0 - k * k, 0.0));
    float2 corre = -n * (curva * 1.25 + k * 0.25) * banda * 1.15;
    /* Lupa ligera: lo de adentro se ve apenas más grande */
    corre += (tam * 0.5 - p) * 0.035;
    /* Donde está el dedo el vidrio se abomba: aumenta lo de atrás como una gota, y brilla */
    float2 v = p - toque;
    float cae = clamp(1.0 - length(v) / radioToque, 0.0, 1.0);
    cae = cae * cae * (3.0 - 2.0 * cae);
    corre -= v * 0.30 * fuerza * cae;
    half4 c;
    c.r = contenido.eval(p + corre * 1.14).r;
    c.g = contenido.eval(p + corre).g;
    c.b = contenido.eval(p + corre * 0.86).b;
    c.a = 1.0;
    float orilla = smoothstep(3.0, 0.0, d);
    float luz = clamp(dot(n, normalize(luzDir)), 0.0, 1.0);
    float sombra = clamp(dot(n, -normalize(luzDir)), 0.0, 1.0);
    c.rgb += half3(orilla * (luz * 0.55 + sombra * 0.18) + k * 0.05 + cae * fuerza * 0.16);
    return c;
}
"""

/* La barra de cristal líquido. Si no se puede (Android viejo o sin fondo grabado), devuelve false
   y quien la llama dibuja el vidrio de siempre */
@Composable
fun BoxScope.CristalLiquido(forma: Shape, radio: Dp, base: Color, toque: () -> Offset = { Offset.Zero }, fuerza: () -> Float = { 0f }, banda: Dp = 26.dp): Boolean {
    val local = LocalFondoLocal.current
    val capa = local?.capa ?: FondoCristal.capa
    if (android.os.Build.VERSION.SDK_INT < 33 || capa == null) return false
    var mio by remember { mutableStateOf(Offset.Zero) }
    /* Si el teléfono no puede compilar la lente, no se rompe nada: se usa el vidrio de siempre */
    val shader = remember { runCatching { android.graphics.RuntimeShader(SHADER_LENTE) }.getOrNull() } ?: return false
    /* El brillo del borde sigue la inclinación del teléfono, como un reflejo de verdad */
    val ctxLuz = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        Inclinacion.empezar(ctxLuz)
        onDispose { Inclinacion.parar() }
    }
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { mio = it.positionInRoot() }
            .graphicsLayer {
                shader.setFloatUniform("tam", size.width, size.height)
                shader.setFloatUniform("radio", radio.toPx())
                shader.setFloatUniform("banda", banda.toPx())
                val tq = toque()
                shader.setFloatUniform("toque", tq.x, tq.y)
                shader.setFloatUniform("fuerza", fuerza())
                val inc = Inclinacion.valor
                shader.setFloatUniform("luzDir", -0.55f + inc.x * 1.4f, -1f + inc.y * 1.4f)
                shader.setFloatUniform("radioToque", 64.dp.toPx())
                val difumina = android.graphics.RenderEffect.createBlurEffect(5.dp.toPx(), 5.dp.toPx(), android.graphics.Shader.TileMode.CLAMP)
                val lente = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "contenido")
                renderEffect = android.graphics.RenderEffect.createChainEffect(lente, difumina).asComposeRenderEffect()
                this.shape = forma
                clip = true
            }
            .drawBehind {
                drawRect(base)
                val o = (local?.origen ?: FondoCristal.origen) - mio
                translate(o.x, o.y) { drawLayer(capa) }
            }
    )
    return true
}

/* Cuánto se inclina el teléfono (sensor de gravedad), para mover el brillo del vidrio.
   Un solo sensor para todas las tarjetas; se apaga cuando no hay ninguna en pantalla. */
object Inclinacion : android.hardware.SensorEventListener {
    var valor by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
        private set
    private var usuarios = 0
    private var gestor: android.hardware.SensorManager? = null
    private var rapidoX = 0f; private var rapidoY = 0f
    private var lentoX = 0f; private var lentoY = 0f
    private var primero = true
    fun empezar(ctx: android.content.Context) {
        usuarios++
        if (usuarios > 1) return
        val g = ctx.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager ?: return
        val sensor = g.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: g.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER) ?: return
        gestor = g; primero = true
        g.registerListener(this, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
    }
    fun parar() {
        usuarios = (usuarios - 1).coerceAtLeast(0)
        if (usuarios == 0) { gestor?.unregisterListener(this); gestor = null; valor = androidx.compose.ui.geometry.Offset.Zero }
    }
    override fun onSensorChanged(e: android.hardware.SensorEvent) {
        val x = e.values[0] / 9.81f; val y = e.values[1] / 9.81f
        if (primero) { rapidoX = x; rapidoY = y; lentoX = x; lentoY = y; primero = false }
        rapidoX += (x - rapidoX) * 0.25f; rapidoY += (y - rapidoY) * 0.25f
        /* La posición en la que lo sostienes se vuelve el punto de reposo poco a poco */
        lentoX += (x - lentoX) * 0.01f; lentoY += (y - lentoY) * 0.01f
        val n = androidx.compose.ui.geometry.Offset(
            (-(rapidoX - lentoX) * 2.5f).coerceIn(-1f, 1f),
            ((rapidoY - lentoY) * 2.5f).coerceIn(-1f, 1f))
        if ((n - valor).getDistance() > 0.01f) valor = n
    }
    override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
}

