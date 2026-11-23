package com.yehu.whyclock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
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
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt

private val JOST_A = FontFamily(Font(R.font.jost_300, FontWeight.Light))
private val INTER_A = FontFamily(Font(R.font.inter_600, FontWeight.SemiBold), Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black))

/* dias: bit 0 = lunes … bit 6 = domingo. 0 = suena una sola vez. tono vacío = el de alarma del teléfono */
data class Alarma(val id: Int, val hora: Int, val minuto: Int, val dias: Int, val activa: Boolean, val nombre: String, val tono: String = "")

object Alarmas {
    private const val PREFS = "whyclock_alarmas"
    const val CANAL = "alarmas"
    val lista = mutableStateListOf<Alarma>()
    private var cargadas = false

    fun cargar(ctx: Context) {
        if (cargadas) return
        cargadas = true
        lista.clear()
        val a = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lista", "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let {
            lista += Alarma(it.getInt("id"), it.getInt("h"), it.getInt("m"), it.optInt("d"), it.optBoolean("on", true), it.optString("n"), it.optString("t"))
        }
    }

    fun guardar(ctx: Context) {
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("id", it.id).put("h", it.hora).put("m", it.minuto).put("d", it.dias).put("on", it.activa).put("n", it.nombre).put("t", it.tono)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lista", a.toString()).apply()
        programar(ctx)
    }

    fun nuevoId() = (lista.maxOfOrNull { it.id } ?: 0) + 1

    /* Cuándo suena la próxima vez */
    fun siguiente(a: Alarma, desde: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = desde; set(Calendar.HOUR_OF_DAY, a.hora); set(Calendar.MINUTE, a.minuto); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= desde) c.add(Calendar.DAY_OF_YEAR, 1)
        if (a.dias != 0) repeat(7) {
            val bit = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
            if (a.dias and (1 shl bit) != 0) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    private fun proxima(): Pair<Alarma, Long>? = lista.filter { it.activa }.map { it to siguiente(it) }.minByOrNull { it.second }

    fun proximaTexto(ctx: Context): String? {
        cargar(ctx)
        val (_, t) = proxima() ?: return null
        return SimpleDateFormat("EEE H:mm", Locale("es", "MX")).format(t).replace(".", "").replaceFirstChar { it.uppercase() }
    }

    /* "Suena en 7 h 20 min", como confirmación al guardar */
    fun faltaTexto(a: Alarma): String {
        val m = ((siguiente(a) - System.currentTimeMillis()) / 60_000L).toInt() + 1
        val d = m / 1440; val h = m / 60 % 24; val mm = m % 60
        return "Suena en " + listOfNotNull(if (d > 0) "$d d" else null, if (h > 0) "$h h" else null, if (mm > 0 || (d == 0 && h == 0)) "$mm min" else null).joinToString(" ")
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun abrirApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /* La pantalla que se abre cuando suena */
    fun sonar(ctx: Context, codigo: Int, nombre: String, id: Int, tono: String = ""): PendingIntent = PendingIntent.getActivity(
        ctx, codigo, Intent(ctx, AlarmaActivity::class.java).putExtra("nombre", nombre).putExtra("id", id).putExtra("tono", tono).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun puedeExacta(ctx: Context) = Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    /* Solo dejo programada la más próxima; al sonar se programa la siguiente */
    fun programar(ctx: Context) {
        cargar(ctx)
        val am = alarmManager(ctx)
        am.cancel(sonar(ctx, 100, "", 0))
        val (a, t) = proxima() ?: return
        if (!puedeExacta(ctx)) return
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, abrirApp(ctx)), sonar(ctx, 100, a.nombre, a.id, a.tono))
    }

    fun posponer(ctx: Context, nombre: String, tono: String, minutos: Int) {
        if (!puedeExacta(ctx)) return
        alarmManager(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + minutos * 60_000L, abrirApp(ctx)), sonar(ctx, 101, nombre, -1, tono))
    }

    /* Tras sonar: las de una sola vez se apagan, y se programa la que sigue */
    fun yaSono(ctx: Context, id: Int) {
        cargar(ctx)
        val i = lista.indexOfFirst { it.id == id }
        if (i >= 0 && lista[i].dias == 0) lista[i] = lista[i].copy(activa = false)
        guardar(ctx)
    }

    fun canal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) == null)
            nm.createNotificationChannel(NotificationChannel(CANAL, "Alarmas y temporizador", NotificationManager.IMPORTANCE_LOW))
    }

    fun nombreTono(ctx: Context, tono: String): String =
        if (tono.isBlank()) "Tono del teléfono" else runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(tono))?.getTitle(ctx) }.getOrNull() ?: "Tono elegido"
}

/* ——— Temporizador (se puede pausar) ——— */
object Temporizador {
    private const val PREFS = "whyclock_alarmas"
    /* Corriendo: fin > 0. En pausa: resta > 0. total para el anillo */
    var fin by mutableLongStateOf(0L)
    var resta by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)

    val activo get() = fin > System.currentTimeMillis() || resta > 0

    fun cargar(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fin = p.getLong("fin", 0L).let { if (it < System.currentTimeMillis()) 0L else it }
        resta = p.getLong("resta", 0L)
        total = p.getLong("total", 0L)
    }

    private fun guardar(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fin", fin).putLong("resta", resta).putLong("total", total).apply()

    private fun pi(ctx: Context) = Alarmas.sonar(ctx, 102, "Temporizador", -2)

    fun iniciar(ctx: Context, ms: Long, nuevo: Boolean = true) {
        if (nuevo) total = ms
        fin = System.currentTimeMillis() + ms
        resta = 0L
        guardar(ctx)
        if (Alarmas.puedeExacta(ctx))
            ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(fin, Alarmas.abrirApp(ctx)), pi(ctx))
        avisar(ctx)
    }

    fun pausar(ctx: Context) {
        if (fin == 0L) return
        resta = (fin - System.currentTimeMillis()).coerceAtLeast(1000L)
        fin = 0L
        guardar(ctx)
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        avisar(ctx)
    }

    fun seguir(ctx: Context) { if (resta > 0) iniciar(ctx, resta, nuevo = false) }

    fun cancelar(ctx: Context) {
        fin = 0L; resta = 0L
        guardar(ctx)
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx))
        ctx.getSystemService(NotificationManager::class.java).cancel(7)
    }

    fun termino(ctx: Context) {
        fin = 0L; resta = 0L
        guardar(ctx)
        ctx.getSystemService(NotificationManager::class.java).cancel(7)
    }

    /* Notificación con la cuenta regresiva (o "En pausa") */
    private fun avisar(ctx: Context) {
        Alarmas.canal(ctx)
        val b = Notification.Builder(ctx, Alarmas.CANAL)
            .setSmallIcon(R.drawable.ic_noti).setOngoing(true)
            .setContentIntent(Alarmas.abrirApp(ctx))
            .addAction(Notification.Action.Builder(null, "Cancelar", PendingIntent.getBroadcast(ctx, 3, Intent(ctx, Receptor::class.java).setAction("cancelar_temporizador"), PendingIntent.FLAG_IMMUTABLE)).build())
        if (fin > 0) b.setContentTitle("Temporizador").setWhen(fin).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
        else { val s = resta / 1000; b.setContentTitle("Temporizador en pausa").setContentText("%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)) }
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(7, b.build()) }
    }
}

/* Al reiniciar el teléfono se vuelven a programar; y cancela el temporizador desde la notificación */
class Receptor : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "cancelar_temporizador" -> { Temporizador.cargar(ctx); Temporizador.cancelar(ctx) }
            else -> Alarmas.programar(ctx)
        }
    }
}

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

/* Botón en píldora: rojo (principal) o del color de los botones */
@Composable
private fun Pildora(texto: String, fondo: Color, tinta: Color, modifier: Modifier = Modifier, ancho: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.then(if (ancho) Modifier.fillMaxWidth() else Modifier.widthIn(min = 200.dp)).pulsable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp)).background(fondo).padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(texto, color = tinta, fontFamily = familiaDigitos(), fontSize = 16.sp) }
}

/* Columna ▲ número ▼, como en el original; el número rueda al cambiar */
@Composable
private fun Selector(valor: Int, t: TonosA, etiqueta: String?, onCambio: (Int) -> Unit) {
    val f = familiaDigitos()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(64.dp).pulsable { onCambio(1) }.clip(RoundedCornerShape(12.dp)).background(t.boton).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("▲", color = t.tinta, fontSize = 18.sp)
        }
        Box(Modifier.width(118.dp), contentAlignment = Alignment.Center) {
            NumeroRodante(valor) { Text("%02d".format(it), color = t.tinta, fontFamily = f, fontSize = 56.sp, textAlign = TextAlign.Center) }
        }
        Box(Modifier.width(64.dp).pulsable { onCambio(-1) }.clip(RoundedCornerShape(12.dp)).background(t.boton).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text("▼", color = t.tinta, fontSize = 18.sp)
        }
        if (etiqueta != null) Text(etiqueta, color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp)
    }
}

@Composable
private fun Separador(t: TonosA) = Text(":", color = t.tinta, fontFamily = familiaDigitos(), fontSize = 48.sp, modifier = Modifier.padding(bottom = 28.dp))

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
            LazyColumn(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(Alarmas.lista.sortedBy { it.hora * 60 + it.minuto }, key = { it.id }) { a ->
                    val apagada by animateFloatAsState(if (a.activa) 1f else 0.45f, tween(300), label = "apagada")
                    Row(
                        Modifier.animateItem().fillMaxWidth(0.72f).widthIn(max = 340.dp).clip(RoundedCornerShape(14.dp)).background(t.tarjeta).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).graphicsLayer { alpha = apagada }.pulsable(hundir = 0.97f) { editando = a }) {
                            Text("%02d:%02d".format(a.hora, a.minuto), color = t.tinta, fontFamily = f, fontSize = 30.sp)
                            Text(a.nombre.ifBlank { "Alarma" }, color = t.tenue, fontFamily = JOST_A, fontSize = 14.sp)
                            Text(diasTexto(a.dias), color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Column(Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val boton: @Composable (String, Color, Color, () -> Unit) -> Unit = { s, fo, ti, accion ->
                                Box(Modifier.width(92.dp).pulsable(onClick = accion).clip(RoundedCornerShape(12.dp)).background(fo).padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                    Cambiante(s) { Text(it, color = ti, fontFamily = f, fontSize = 14.sp) }
                                }
                            }
                            boton(if (a.activa) "Activa" else "Apagada", if (a.activa) t.chipOn else t.boton, if (a.activa) t.chipOnTinta else t.tinta) {
                                val i = Alarmas.lista.indexOfFirst { it.id == a.id }
                                if (i >= 0) { val n = a.copy(activa = !a.activa); Alarmas.lista[i] = n; Alarmas.guardar(ctx); if (n.activa) aviso = Alarmas.faltaTexto(n) }
                            }
                            boton("Tono", t.boton, t.tinta) {
                                tonoDe = a.id
                                elegirTono.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tono de la alarma")
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, if (a.tono.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(a.tono)))
                            }
                            boton("Borrar", t.rojo, Color.White) { Alarmas.lista.removeAll { it.id == a.id }; Alarmas.guardar(ctx) }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        /* Confirmación que aparece abajo: "Suena en 7 h 20 min" */
        AnimatedVisibility(aviso != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            Text(aviso.orEmpty(), color = t.fondo, fontFamily = JOST_A, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(t.tinta).padding(horizontal = 18.dp, vertical = 10.dp))
        }
        /* El editor: un panel que crece desde el centro */
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
                .clip(RoundedCornerShape(18.dp)).background(t.tarjeta).clickable(remember { MutableInteractionSource() }, null) {}.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val reloj: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Selector(h, t, null) { h = (h + it + 24) % 24 }
                    Separador(t)
                    Selector(m, t, null) { m = (m + it + 60) % 60 }
                }
            }
            val resto: @Composable () -> Unit = {
                Column {
                    BasicTextField(
                        nombre, { nombre = it }, singleLine = true, cursorBrush = SolidColor(t.tinta),
                        textStyle = TextStyle(color = t.tinta, fontFamily = JOST_A, fontSize = 18.sp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (Ajustes.oscuro) Color(0xFF111111) else Color(0xFFF7F6F2))
                            .border(1.dp, if (Ajustes.oscuro) Color(0xFF333333) else Color(0xFFCFC9BA), RoundedCornerShape(10.dp)).padding(10.dp),
                        decorationBox = { campo -> Box { if (nombre.isEmpty()) Text("Nombre (opcional)", color = t.tenue, fontFamily = JOST_A, fontSize = 18.sp); campo() } }
                    )
                    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LETRAS.forEachIndexed { i, l ->
                            val on = dias and (1 shl i) != 0
                            val fondo by androidx.compose.animation.animateColorAsState(if (on) t.chipOn else t.boton, label = "dia")
                            Box(Modifier.pulsable { dias = dias xor (1 shl i) }.clip(RoundedCornerShape(12.dp)).background(fondo).padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(l, color = if (on) t.chipOnTinta else t.tinta, fontFamily = INTER_A, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                    Cambiante(diasTexto(dias)) { Text(if (it == "Una vez") "Suena una sola vez" else it, color = t.tenue, fontFamily = JOST_A, fontSize = 13.sp) }
                    Spacer(Modifier.height(6.dp))
                    Pildora("Guardar", t.rojo, Color.White, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                        onGuardar(e.copy(hora = h, minuto = m, dias = dias, nombre = nombre.trim(), activa = true))
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).pulsable(hundir = 0.97f, onClick = onCerrar).clip(RoundedCornerShape(12.dp)).background(t.boton).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
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
    val f = familiaDigitos()
    var h by remember { mutableIntStateOf(0) }
    var m by remember { mutableIntStateOf(5) }
    var s by remember { mutableIntStateOf(0) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { Temporizador.cargar(ctx); while (true) { ahora = System.currentTimeMillis(); delay(50) } }
    val corriendo = Temporizador.fin > ahora
    val pausado = !corriendo && Temporizador.resta > 0
    val queda = if (corriendo) Temporizador.fin - ahora else Temporizador.resta
    Column(Modifier.fillMaxSize().background(t.fondo), horizontalAlignment = Alignment.CenterHorizontally) {
        Encabezado("Temporizador", atras, adelante, t.tinta)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedContent(corriendo || pausado, transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(tween(350), initialScale = 0.9f)) togetherWith fadeOut(tween(200))
            }, label = "modo") { activo ->
                if (!activo) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Selector(h, t, "horas") { h = (h + it + 24) % 24 }
                        Separador(t)
                        Selector(m, t, "min") { m = (m + it + 60) % 60 }
                        Separador(t)
                        Selector(s, t, "seg") { s = (s + it + 60) % 60 }
                    }
                    Spacer(Modifier.height(14.dp))
                    Pildora("Iniciar", t.rojo, Color.White) { val ms = (h * 3600L + m * 60L + s) * 1000L; if (ms > 0) Temporizador.iniciar(ctx, ms) }
                } else BoxWithConstraints(Modifier.fillMaxSize()) {
                    val horizontal = maxWidth > maxHeight
                    val lado = min(if (horizontal) maxHeight.value * 0.8f else maxWidth.value * 0.72f, 280f).dp
                    val frac = if (Temporizador.total > 0) (queda.toFloat() / Temporizador.total).coerceIn(0f, 1f) else 1f
                    val anillo: @Composable () -> Unit = {
                        Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val g = size.width * 8f / 200f
                                drawCircle(if (Ajustes.oscuro) Color(0x24FFFFFF) else Color(0x1F000000), size.width * 88f / 200f, style = Stroke(g))
                                val r = size.width * 88f / 200f
                                drawArc(if (Ajustes.oscuro) Color(0xFFFFC23A) else Color(0xFFE0392B), -90f, 360f * frac, false,
                                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2), style = Stroke(g, cap = StrokeCap.Round))
                            }
                            val seg = ((queda + 999) / 1000).toInt()
                            Text("%02d:%02d:%02d".format(seg / 3600, seg / 60 % 60, seg % 60), color = t.tinta, fontFamily = f, fontSize = (lado.value * 0.16f).sp,
                                modifier = Modifier.graphicsLayer { alpha = if (pausado) (0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(ahora / 500.0)).toFloat()) else 1f })
                        }
                    }
                    val botones: @Composable () -> Unit = {
                        Row(Modifier.widthIn(max = 360.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f).pulsable { if (pausado) Temporizador.seguir(ctx) else Temporizador.pausar(ctx) }.clip(RoundedCornerShape(12.dp)).background(t.boton).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Cambiante(if (pausado) "Reanudar" else "Pausar") { Text(it, color = t.tinta, fontFamily = f, fontSize = 15.sp) }
                            }
                            Box(Modifier.weight(1f).pulsable { Temporizador.cancelar(ctx) }.clip(RoundedCornerShape(12.dp)).background(t.rojo).padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text("Cancelar", color = Color.White, fontFamily = f, fontSize = 15.sp)
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
        }
    }
}

/* ——— La pantalla que aparece cuando suena (también con el teléfono bloqueado) ——— */
class AlarmaActivity : ComponentActivity() {
    private var reproductor: MediaPlayer? = null
    private var vibrador: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Ajustes.cargar(this)
        val nombre = intent.getStringExtra("nombre").orEmpty()
        val tono = intent.getStringExtra("tono").orEmpty()
        val id = intent.getIntExtra("id", 0)
        val esTemporizador = id == -2
        when {
            esTemporizador -> Temporizador.termino(this)
            id > 0 -> Alarmas.yaSono(this, id)
        }
        sonar(tono)
        val prefs = getSharedPreferences("whyclock_alarmas", MODE_PRIVATE)
        setContent {
            PantallaSonando(
                titulo = if (esTemporizador) "Temporizador" else "Alarma",
                nombre = nombre.takeIf { it.isNotBlank() && it != "Alarma" && it != "Temporizador" },
                temporizador = esTemporizador,
                posponerInicial = prefs.getInt("posponer", 10),
                onPosponer = { min -> prefs.edit().putInt("posponer", min).apply(); Alarmas.posponer(this, nombre, tono, min); finish() },
                onDetener = { finish() }
            )
        }
    }

    private fun sonar(tono: String) {
        val uri = tono.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        reproductor = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@AlarmaActivity, uri); isLooping = true; prepare(); start()
            }
        }.getOrNull()
        vibrador = getSystemService(Vibrator::class.java)
        runCatching { vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0)) }
    }

    override fun onDestroy() {
        reproductor?.run { runCatching { stop() }; release() }
        vibrador?.cancel()
        super.onDestroy()
    }
}

/* Negro, la palabra "Alarma" en rojo con su campanita, la hora enorme en blanco,
   posponer por 5/10/15/20, el botón rojo Posponer y "Deslizar para apagar" */
@Composable
private fun PantallaSonando(titulo: String, nombre: String?, temporizador: Boolean, posponerInicial: Int, onPosponer: (Int) -> Unit, onDetener: () -> Unit) {
    val f = familiaDigitos()
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { ahora = System.currentTimeMillis(); delay(1000) } }
    var min by remember { mutableIntStateOf(posponerInicial) }
    val c = Calendar.getInstance().apply { timeInMillis = ahora }
    val rojo = Color(0xFFE0392B)
    /* La campanita y la palabra laten despacio */
    val latido = remember { Animatable(1f) }
    LaunchedEffect(Unit) { while (true) { latido.animateTo(1.08f, tween(500)); latido.animateTo(1f, tween(700)) } }
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrada.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).padding(24.dp), contentAlignment = Alignment.Center) {
        val horizontal = maxWidth > maxHeight
        val tamHora = if (horizontal) min(maxHeight.value * 0.3f, 140f) else min(maxWidth.value * 0.3f, 200f)
        val arriba: @Composable () -> Unit = {
            Column(horizontalAlignment = if (horizontal) Alignment.Start else Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { scaleX = latido.value; scaleY = latido.value }) {
                    Campana(Modifier.size(30.dp), Color(0xFF8E8E93))
                    Spacer(Modifier.width(10.dp))
                    Text(titulo, color = rojo, fontFamily = INTER_A, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                if (nombre != null) Text(nombre, color = Color.White.copy(alpha = 0.65f), fontFamily = JOST_A, fontSize = 20.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val hora: @Composable () -> Unit = {
            Text("%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)), color = Color.White, fontFamily = f, fontSize = tamHora.sp, lineHeight = tamHora.sp, maxLines = 1, softWrap = false)
        }
        val duraciones: @Composable () -> Unit = {
            if (!temporizador) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Posponer por", color = Color.White.copy(alpha = 0.55f), fontFamily = JOST_A, fontSize = 14.sp)
                listOf(5, 10, 15, 20).forEach { d ->
                    val on = d == min
                    val fondo by androidx.compose.animation.animateColorAsState(if (on) rojo else Color.Transparent, label = "d")
                    Text("$d min", color = Color.White, fontFamily = INTER_A, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        modifier = Modifier.pulsable { min = d }.clip(RoundedCornerShape(99.dp)).background(fondo)
                            .border(1.dp, if (on) rojo else Color.White.copy(alpha = 0.25f), RoundedCornerShape(99.dp)).padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        val alto = if (horizontal) 64.dp else 88.dp
        val posponer: @Composable (Modifier) -> Unit = { m ->
            Box(m.height(alto).pulsable(hundir = 0.96f) { onPosponer(min) }.clip(RoundedCornerShape(99.dp)).background(rojo), contentAlignment = Alignment.Center) {
                Text("Posponer", color = Color.White, fontFamily = JOST_A, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
            }
        }
        Column(
            Modifier.graphicsLayer { alpha = entrada.value; translationY = (1f - entrada.value) * 60f }.widthIn(max = 860.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (horizontal) 14.dp else 22.dp)
        ) {
            if (horizontal) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { arriba(); hora() }
                duraciones()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!temporizador) posponer(Modifier.weight(1f))
                    Box(Modifier.weight(1f).height(alto).pulsable(hundir = 0.96f, onClick = onDetener).clip(RoundedCornerShape(99.dp)).background(Color(0xFF2C2C2E)), contentAlignment = Alignment.Center) {
                        Text("Detener", color = Color.White, fontFamily = JOST_A, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
                    }
                }
            } else {
                arriba(); hora(); duraciones()
                Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!temporizador) posponer(Modifier.fillMaxWidth())
                    DeslizarParaApagar(onDetener)
                }
            }
        }
    }
}

/* El reloj despertador gris de la fila de arriba */
@Composable
private fun Campana(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        drawCircle(color, w * 0.36f, Offset(w * 0.5f, w * 0.56f))
        drawLine(Color.Black, Offset(w * 0.5f, w * 0.56f), Offset(w * 0.5f, w * 0.36f), w * 0.08f, StrokeCap.Round)
        drawLine(Color.Black, Offset(w * 0.5f, w * 0.56f), Offset(w * 0.64f, w * 0.64f), w * 0.08f, StrokeCap.Round)
        drawArc(color, 200f, 60f, false, topLeft = Offset(0f, w * 0.02f), size = Size(w * 0.5f, w * 0.5f), style = Stroke(w * 0.1f, cap = StrokeCap.Round))
        drawArc(color, 280f, 60f, false, topLeft = Offset(w * 0.5f, w * 0.02f), size = Size(w * 0.5f, w * 0.5f), style = Stroke(w * 0.1f, cap = StrokeCap.Round))
    }
}

/* Pista oscura con el mango redondo y su cuadrito blanco; si lo sueltas antes del final, regresa con resorte */
@Composable
private fun DeslizarParaApagar(onApagar: () -> Unit) {
    val alcance = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    val densidad = LocalDensity.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFF1C1C1E))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(99.dp))
    ) {
        val mango = with(densidad) { 68.dp.toPx() }
        val max = with(densidad) { maxWidth.toPx() } - mango - with(densidad) { 8.dp.toPx() }
        val avance = if (max > 0) (x.value / max).coerceIn(0f, 1f) else 0f
        /* Rastro que se ilumina detrás del mango */
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = avance * 0.5f }.background(Brush.horizontalGradient(listOf(Color(0x00E0392B), Color(0x66E0392B)))))
        Text("Deslizar para apagar", color = Color.White.copy(alpha = 0.6f * (1f - avance)), fontFamily = JOST_A, fontSize = 20.sp,
            modifier = Modifier.align(Alignment.Center).padding(start = 68.dp))
        Box(
            Modifier.offset { IntOffset(x.value.roundToInt() + with(densidad) { 4.dp.roundToPx() }, with(densidad) { 4.dp.roundToPx() }) }
                .size(68.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Color(0xFF454548), Color(0xFF2A2A2D))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                .pointerInput(max) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            alcance.launch {
                                if (x.value > max * 0.85f) { x.animateTo(max, tween(120)); onApagar() }
                                else x.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
                            }
                        },
                        onDragCancel = { alcance.launch { x.animateTo(0f, spring(dampingRatio = 0.55f)) } }
                    ) { cambio, d -> cambio.consume(); alcance.launch { x.snapTo((x.value + d).coerceIn(0f, max)) } }
                },
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(26.dp).clip(RoundedCornerShape(5.dp)).background(Color.White)) }
    }
}
