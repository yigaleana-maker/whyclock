package com.yehu.whyclock

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/* La letra de los números elegida en la barra (Original, Concert o Condensada) */
@Composable
fun familiaDigitos(): FontFamily = remember(Ajustes.letra) {
    FontFamily(Font(when (Ajustes.letra) { "concert" -> R.font.concert_one; "oswald" -> R.font.oswald_700; else -> R.font.bruno_ace }))
}
private val JOST = FontFamily(Font(R.font.jost_300, FontWeight.Light))
private val INTER = FontFamily(Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black), Font(R.font.inter_400, FontWeight.Normal))
private val ES = Locale("es", "MX")

private fun cal(ahora: Long) = Calendar.getInstance().apply { timeInMillis = ahora }

/* ——— Pantalla 4: Fecha. A la izquierda el día de la semana, el mes, el día enorme y el mes chiquito;
   a la derecha el reloj digital con su regla ——— */
@Composable
fun PantallaFecha(ahora: Long) {
    val c = Ajustes.colores()
    val f = familiaDigitos()
    val ca = cal(ahora)
    val semana = SimpleDateFormat("EEE", ES).format(ahora).replace(".", "").replaceFirstChar { it.uppercase() }
    val mes = SimpleDateFormat("MMM", ES).format(ahora).replace(".", "").replaceFirstChar { it.uppercase() }
    val tf = Letras.digitos(LocalContext.current)
    BoxWithConstraints(Modifier.fillMaxSize().background(if (Ajustes.oscuro) Color(0xFF111111) else Color(0xFFF6F3EC))) {
        val horizontal = maxWidth > maxHeight
        val lado = if (horizontal) maxWidth / 2 else maxWidth
        val izquierda: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(semana, color = Color(0xFFF2B233), fontFamily = f, fontSize = (lado.value * 0.075f).sp)
                    Spacer(Modifier.width(10.dp))
                    Text(mes, color = c.dTinta, fontFamily = f, fontSize = (lado.value * 0.075f).sp)
                }
                Text("${ca.get(Calendar.DAY_OF_MONTH)}", color = c.dTinta, fontFamily = f, fontSize = (lado.value * 0.27f).sp, lineHeight = (lado.value * 0.27f).sp)
                Text(SimpleDateFormat("MMMM yyyy", ES).format(ahora).uppercase(), color = Color(0xFFF2B233), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Box(Modifier.width(lado * 0.5f).padding(top = 6.dp)) { CalendarioMes(ahora, c.copy(calendario = c.dTinta)) }
            }
        }
        val derecha: @Composable (Modifier) -> Unit = { m -> Canvas(m) { dibujarDigitalPublico(ahora, c, tf) } }
        if (horizontal) Row(Modifier.fillMaxSize()) {
            izquierda(Modifier.weight(1f).fillMaxHeight())
            derecha(Modifier.weight(1f).fillMaxHeight())
        } else Column(Modifier.fillMaxSize()) {
            izquierda(Modifier.weight(1f).fillMaxWidth())
            derecha(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/* ——— Pantalla 6: Calendario grande. Blanco, el mes en rojo, la hora arriba y el día gigante detrás ——— */
@Composable
fun PantallaCalendario(ahora: Long) {
    val f = familiaDigitos()
    val ca = cal(ahora)
    val rojo = Color(0xFFE53935)
    val fondo = if (Ajustes.oscuro) Color(0xFF111111) else Color.White
    val tinta = if (Ajustes.oscuro) Color(0xFFF2B233) else Color(0xFF2A2522)
    val hoy = ca.get(Calendar.DAY_OF_MONTH)
    BoxWithConstraints(Modifier.fillMaxSize().background(fondo)) {
        val alto = maxHeight.value
        val ancho = maxWidth.value
        /* El día enorme, detrás de todo */
        Text(
            "$hoy", color = rojo, fontFamily = f, fontSize = (min(alto * 0.95f, ancho * 0.55f)).sp, lineHeight = (alto).sp,
            textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center)
        )
        Column(
            Modifier.align(Alignment.Center).width((min(ancho * 0.62f, alto * 1.15f)).dp).padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(SimpleDateFormat("MMMM yyyy", ES).format(ahora).replaceFirstChar { it.uppercase() }, color = rojo, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                val h = (ca.get(Calendar.HOUR) .let { if (it == 0) 12 else it })
                Text("$h:%02d".format(ca.get(Calendar.MINUTE)), color = rojo, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(6.dp))
            /* El mes encima del número: domingo primero, como el original */
            val primero = (ca.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val inicio = primero.get(Calendar.DAY_OF_WEEK) - 1
            val dias = ca.getActualMaximum(Calendar.DAY_OF_MONTH)
            val anterior = (primero.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
            val celdas = ceil((inicio + dias) / 7f).toInt() * 7
            Row { listOf("D", "L", "M", "X", "J", "V", "S").forEach { Text(it, color = rojo, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
            for (fila in 0 until celdas / 7) Row(Modifier.padding(top = 4.dp)) {
                for (col in 0 until 7) {
                    val k = fila * 7 + col
                    val (n, tenue) = when { k < inicio -> (anterior - inicio + k + 1) to true; k >= inicio + dias -> (k - inicio - dias + 1) to true; else -> (k - inicio + 1) to false }
                    val esHoy = !tenue && n == hoy
                    Box(Modifier.weight(1f).aspectRatio(1.35f), contentAlignment = Alignment.Center) {
                        if (esHoy) Box(Modifier.fillMaxHeight(0.95f).aspectRatio(1f).clip(CircleShape).background(Color(0xFFF2B233)))
                        Text("$n", fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (esHoy) Color(0xFF2A2522) else tinta.copy(alpha = if (tenue) 0.3f else 0.85f))
                    }
                }
            }
        }
    }
}

/* ——— Pantalla 8: la hora enorme en rojo, la fecha y la próxima alarma ——— */
@Composable
fun PantallaHoraGrande(ahora: Long) {
    val f = familiaDigitos()
    val ca = cal(ahora)
    val ctx = LocalContext.current
    val fondo = if (Ajustes.oscuro) Color(0xFF0A0908) else Color(0xFFEFEEE9)
    val proxima = remember(ahora / 30000) { Alarmas.proximaTexto(ctx) }
    BoxWithConstraints(Modifier.fillMaxSize().background(fondo)) {
        val tam = min(maxWidth.value * 0.3f, maxHeight.value * 0.62f)
        Text(
            "%02d:%02d".format(ca.get(Calendar.HOUR_OF_DAY), ca.get(Calendar.MINUTE)),
            color = Color(0xFFE53328), fontFamily = f, fontSize = tam.sp, lineHeight = tam.sp,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(SimpleDateFormat("EEE d", ES).format(ahora).replace(".", "").uppercase(), color = Color(0xFFF2B233), fontFamily = f, fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            /* Un anillito que se llena con los segundos */
            Canvas(Modifier.size(24.dp)) {
                val g = 3.dp.toPx()
                drawCircle(Color(0x33F2B233), radius = size.minDimension / 2 - g / 2, style = Stroke(g))
                drawArc(Color(0xFFF2B233), -90f, 360f * (ca.get(Calendar.SECOND) / 60f), false,
                    topLeft = Offset(g / 2, g / 2), size = Size(size.width - g, size.height - g), style = Stroke(g, cap = StrokeCap.Round))
            }
        }
        if (proxima != null) Text(
            "⏰ $proxima", color = Color(0xFFF2B233).copy(alpha = 0.8f), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp)
        )
    }
}

/* ——— Pantalla 9: Respira. El gatito en su galleta, que crece al inhalar y se encoge al exhalar ——— */
@Composable
fun PantallaRespira() {
    val c = Ajustes.colores()
    val minutos = listOf(1, 3, 5, 10)
    var dur by remember { mutableIntStateOf(3) }
    var activo by remember { mutableStateOf(false) }
    var progreso by remember { mutableFloatStateOf(0f) }
    var fase by remember { mutableStateOf("Toca empezar para respirar") }
    val escala = remember { Animatable(0.82f) }
    LaunchedEffect(activo, dur) {
        if (!activo) { escala.animateTo(0.82f, tween(600)); return@LaunchedEffect }
        val total = dur * 60_000L
        val inicio = System.currentTimeMillis()
        progreso = 0f
        while (activo && System.currentTimeMillis() - inicio < total) {
            fase = "Inhala…"; launch { escala.animateTo(1.12f, tween(4000, easing = LinearEasing)) }; esperar(4000) { progreso = ((System.currentTimeMillis() - inicio).toFloat() / total).coerceAtMost(1f) }
            fase = "Sostén…"; esperar(2000) { progreso = ((System.currentTimeMillis() - inicio).toFloat() / total).coerceAtMost(1f) }
            fase = "Exhala…"; launch { escala.animateTo(0.82f, tween(6000, easing = LinearEasing)) }; esperar(6000) { progreso = ((System.currentTimeMillis() - inicio).toFloat() / total).coerceAtMost(1f) }
        }
        if (activo) { progreso = 1f; fase = "¡Listo! Bien hecho" }
        activo = false
    }
    val fondo = if (Ajustes.oscuro) Color(0xFF151311) else Color(0xFFEFEDE8)
    Box(Modifier.fillMaxSize().background(fondo)) {
        /* Burbujas suaves de fondo, como el original */
        Canvas(Modifier.fillMaxSize()) {
            val tono = if (Ajustes.oscuro) Color(0x14FFFFFF) else Color(0x14000000)
            drawCircle(tono, size.minDimension * 0.28f, Offset(size.width * 0.08f, size.height * 0.4f))
            drawCircle(tono, size.minDimension * 0.22f, Offset(size.width * 0.92f, size.height * 0.75f))
            drawCircle(tono, size.minDimension * 0.18f, Offset(size.width * 0.75f, size.height * 0.15f))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp).scale(escala.value), contentAlignment = Alignment.Center) { Gatito(Modifier.fillMaxSize(), c) }
            Spacer(Modifier.height(14.dp))
            Text("${(progreso * 100).toInt()}%", color = c.dTinta, fontFamily = INTER, fontWeight = FontWeight.Black, fontSize = 34.sp)
            Text("$dur:00", color = c.dTinta.copy(alpha = 0.7f), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(fase, color = c.dTinta, fontFamily = INTER, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                minutos.forEach { m ->
                    Text("$m min", color = if (m == dur) fondo else c.dTinta, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (m == dur) c.dTinta else c.dTinta.copy(alpha = 0.1f))
                            .clickable(enabled = !activo) { dur = m }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (activo) "Detener" else "Empezar", color = Color.White, fontFamily = INTER, fontWeight = FontWeight.Black, fontSize = 15.sp,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(c.acento).clickable { activo = !activo; if (!activo) fase = "Toca empezar para respirar" }
                    .padding(horizontal = 26.dp, vertical = 11.dp))
        }
    }
}

private suspend fun esperar(ms: Long, cada: () -> Unit) {
    val fin = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < fin) { cada(); delay(100) }
}

/* El gatito negro dentro de su galleta con ondas, como la mascota del original (dibujo propio) */
@Composable
fun Gatito(modifier: Modifier, c: Colores) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val centro = Offset(size.width / 2, size.height / 2)
        val galleta = Path()
        val puntas = 12
        for (i in 0..360) {
            val t = i * PI / 180
            val rr = r * (0.9f + 0.06f * cos(puntas * t).toFloat())
            val p = Offset(centro.x + rr * cos(t).toFloat(), centro.y + rr * sin(t).toFloat())
            if (i == 0) galleta.moveTo(p.x, p.y) else galleta.lineTo(p.x, p.y)
        }
        galleta.close()
        drawPath(galleta, Color(0xFFB9B4AC), style = Stroke(r * 0.06f))
        drawPath(galleta, Color(0xFFF7F5F1))
        val negro = Color(0xFF1E1C1B)
        /* Orejas */
        listOf(-1f, 1f).forEach { lado ->
            drawPath(Path().apply {
                moveTo(centro.x + lado * r * 0.18f, centro.y - r * 0.1f)
                lineTo(centro.x + lado * r * 0.52f, centro.y - r * 0.62f)
                lineTo(centro.x + lado * r * 0.55f, centro.y - r * 0.02f); close()
            }, negro)
            drawPath(Path().apply {
                moveTo(centro.x + lado * r * 0.3f, centro.y - r * 0.12f)
                lineTo(centro.x + lado * r * 0.5f, centro.y - r * 0.46f)
                lineTo(centro.x + lado * r * 0.5f, centro.y - r * 0.08f); close()
            }, Color(0xFFE9A0A8))
        }
        /* Cabeza */
        drawOval(negro, Offset(centro.x - r * 0.6f, centro.y - r * 0.32f), Size(r * 1.2f, r * 0.95f))
        /* Carita clara */
        drawOval(Color(0xFFF7F5F1), Offset(centro.x - r * 0.38f, centro.y + r * 0.02f), Size(r * 0.76f, r * 0.5f))
        /* Ojos */
        listOf(-1f, 1f).forEach { lado ->
            drawCircle(negro, r * 0.075f, Offset(centro.x + lado * r * 0.17f, centro.y + r * 0.14f))
            drawCircle(Color.White, r * 0.025f, Offset(centro.x + lado * r * 0.17f - r * 0.02f, centro.y + r * 0.11f))
        }
        /* Naricita y boca */
        drawCircle(Color(0xFFE9A0A8), r * 0.04f, Offset(centro.x, centro.y + r * 0.25f))
        drawLine(negro, Offset(centro.x, centro.y + r * 0.28f), Offset(centro.x - r * 0.06f, centro.y + r * 0.34f), r * 0.025f, StrokeCap.Round)
        drawLine(negro, Offset(centro.x, centro.y + r * 0.28f), Offset(centro.x + r * 0.06f, centro.y + r * 0.34f), r * 0.025f, StrokeCap.Round)
    }
}

/* ——— Pantalla 4: Clima. A la izquierda tu ciudad, la temperatura y los próximos días; a la derecha la hora ——— */
object Clima {
    data class Dia(val nombre: String, val max: Int, val min: Int, val codigo: Int)
    data class Estado(val ciudad: String, val temp: Int, val humedad: Int, val codigo: Int, val max: Int, val min: Int, val dias: List<Dia>)

    var estado by mutableStateOf<Estado?>(null)
    /* Se abre desde la barra (chip "Ciudad") o la primera vez */
    var pedir by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun ciudad(ctx: Context): Triple<String, Double, Double>? {
        val p = ctx.getSharedPreferences("whyclock", Context.MODE_PRIVATE)
        val n = p.getString("ciudad", null) ?: return null
        return Triple(n, p.getFloat("lat", 0f).toDouble(), p.getFloat("lon", 0f).toDouble())
    }

    /* Busca la ciudad por su nombre (open-meteo, gratis y sin cuenta) y la guarda */
    fun buscarYGuardar(ctx: Context, nombre: String): Boolean {
        val q = URLEncoder.encode(nombre.trim(), "UTF-8")
        val r = JSONObject(URL("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=es").readText()).optJSONArray("results") ?: return false
        if (r.length() == 0) return false
        val o = r.getJSONObject(0)
        ctx.getSharedPreferences("whyclock", Context.MODE_PRIVATE).edit()
            .putString("ciudad", o.optString("name")).putFloat("lat", o.optDouble("latitude").toFloat()).putFloat("lon", o.optDouble("longitude").toFloat()).apply()
        return true
    }

    fun cargar(ctx: Context) {
        val (n, lat, lon) = ciudad(ctx) ?: return
        runCatching {
            val o = JSONObject(URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4").readText())
            val cur = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            val fechas = d.getJSONArray("time")
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dias = (1 until fechas.length()).map { i ->
                Dia(SimpleDateFormat("EEE", ES).format(f.parse(fechas.getString(i))!!).replace(".", "").replaceFirstChar { it.uppercase() },
                    d.getJSONArray("temperature_2m_max").getDouble(i).toInt(), d.getJSONArray("temperature_2m_min").getDouble(i).toInt(), d.getJSONArray("weather_code").getInt(i))
            }
            estado = Estado(n, cur.getDouble("temperature_2m").toInt(), cur.getInt("relative_humidity_2m"), cur.getInt("weather_code"),
                d.getJSONArray("temperature_2m_max").getDouble(0).toInt(), d.getJSONArray("temperature_2m_min").getDouble(0).toInt(), dias)
            error = null
        }.onFailure { error = "Sin conexión" }
    }

    fun texto(codigo: Int) = when (codigo) {
        0 -> "Despejado"; 1, 2 -> "Poco nublado"; 3 -> "Nublado"; 45, 48 -> "Niebla"
        51, 53, 55, 56, 57 -> "Llovizna"; 61, 63, 65, 66, 67, 80, 81, 82 -> "Lluvia"
        71, 73, 75, 77, 85, 86 -> "Nieve"; 95, 96, 99 -> "Tormenta"; else -> ""
    }
    fun icono(codigo: Int) = when (codigo) {
        0 -> "☀️"; 1, 2 -> "🌤️"; 3 -> "☁️"; 45, 48 -> "🌫️"; in 51..67, in 80..82 -> "🌧️"; in 71..86 -> "❄️"; in 95..99 -> "⛈️"; else -> "·"
    }
}

@Composable
fun PantallaClima(ahora: Long) {
    val ctx = LocalContext.current
    val f = familiaDigitos()
    LaunchedEffect(Unit) { if (Clima.ciudad(ctx) == null) Clima.pedir = true }
    LaunchedEffect(Unit) { while (true) { withContext(Dispatchers.IO) { Clima.cargar(ctx) }; delay(30 * 60_000L) } }
    val oscuro = Ajustes.oscuro
    val fondo = if (oscuro) Color(0xFF111111) else Color(0xFFEFEEE9)
    val tinta = if (oscuro) Color(0xFFFFC23A) else Color(0xFF1C1C1E)
    val rojo = if (oscuro) Color(0xFFFF453A) else Color(0xFFE0392B)
    val ca = cal(ahora)
    BoxWithConstraints(Modifier.fillMaxSize().background(fondo)) {
        val horizontal = maxWidth > maxHeight
        val h = maxHeight.value
        val w = maxWidth.value
        val clima: @Composable (Modifier) -> Unit = { m ->
            Column(m.clickable { Clima.pedir = true }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val e = Clima.estado
                Text(e?.ciudad ?: "Elige tu ciudad", color = tinta, fontFamily = f, fontSize = (h * 0.06f).sp)
                if (e != null) {
                    Text("Humedad ${e.humedad}%", color = tinta.copy(alpha = 0.6f), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${e.temp}°", color = rojo, fontFamily = f, fontSize = (h * 0.24f).sp, lineHeight = (h * 0.26f).sp)
                    Text("${Clima.icono(e.codigo)} ${Clima.texto(e.codigo)}", color = tinta, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("↓ ${e.min}°   ↑ ${e.max}°", color = tinta.copy(alpha = 0.7f), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        e.dias.forEach { d ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.nombre, color = tinta, fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(Clima.icono(d.codigo), fontSize = 20.sp)
                                Text("${d.max}° ${d.min}°", color = tinta.copy(alpha = 0.7f), fontFamily = INTER, fontSize = 11.sp)
                            }
                        }
                    }
                } else Text(Clima.error ?: "Toca para elegir", color = tinta.copy(alpha = 0.6f), fontFamily = INTER, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        val hora: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val hh = ca.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                val t = min(w * (if (horizontal) 0.2f else 0.32f), h * (if (horizontal) 0.34f else 0.2f))
                Text("%02d".format(hh), color = tinta, fontFamily = f, fontSize = t.sp, lineHeight = t.sp)
                Text("%02d".format(ca.get(Calendar.MINUTE)), color = tinta, fontFamily = f, fontSize = t.sp, lineHeight = t.sp)
                Text(if (ca.get(Calendar.HOUR_OF_DAY) >= 12) "PM" else "AM", color = tinta.copy(alpha = 0.7f), fontFamily = f, fontSize = 18.sp)
            }
        }
        if (horizontal) Row(Modifier.fillMaxSize()) { clima(Modifier.weight(1f).fillMaxHeight()); hora(Modifier.weight(1f).fillMaxHeight()) }
        else Column(Modifier.fillMaxSize()) { clima(Modifier.weight(1f).fillMaxWidth()); hora(Modifier.weight(1f).fillMaxWidth()) }
    }
}

/* El cuadro para elegir tu ciudad (se abre desde cualquier pantalla) */
@Composable
fun DialogoCiudad() {
    val ctx = LocalContext.current
    val alcance = rememberCoroutineScope()
    if (Clima.pedir) {
        var texto by remember { mutableStateOf(Clima.ciudad(ctx)?.first ?: "") }
        var buscando by remember { mutableStateOf(false) }
        var falla by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { Clima.pedir = false },
            title = { Text("Tu ciudad") },
            text = {
                Column {
                    BasicTextField(texto, { texto = it; falla = false }, singleLine = true, textStyle = TextStyle(fontSize = 18.sp, color = Color(0xFF1C1C1E)),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF9A927F), RoundedCornerShape(12.dp)).padding(12.dp))
                    if (falla) Text("No la encontré. Prueba con otro nombre.", color = Color(0xFFE0392B), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = {
                TextButton(enabled = !buscando && texto.isNotBlank(), onClick = {
                    buscando = true
                    alcance.launch {
                        val ok = withContext(Dispatchers.IO) { runCatching { Clima.buscarYGuardar(ctx, texto) }.getOrDefault(false) }
                        buscando = false
                        if (ok) { Clima.pedir = false; withContext(Dispatchers.IO) { Clima.cargar(ctx) } } else falla = true
                    }
                }) { Text(if (buscando) "Buscando…" else "Listo") }
            },
            dismissButton = { TextButton(onClick = { Clima.pedir = false }) { Text("Cancelar") } }
        )
    }
}
