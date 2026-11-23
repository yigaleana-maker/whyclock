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
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val INTER_A = FontFamily(Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black), Font(R.font.inter_400, FontWeight.Normal))

/* dias: bit 0 = lunes … bit 6 = domingo. 0 = suena una sola vez */
data class Alarma(val id: Int, val hora: Int, val minuto: Int, val dias: Int, val activa: Boolean, val nombre: String)

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
            lista += Alarma(it.getInt("id"), it.getInt("h"), it.getInt("m"), it.optInt("d"), it.optBoolean("on", true), it.optString("n"))
        }
    }

    fun guardar(ctx: Context) {
        val a = JSONArray()
        lista.forEach { a.put(JSONObject().put("id", it.id).put("h", it.hora).put("m", it.minuto).put("d", it.dias).put("on", it.activa).put("n", it.nombre)) }
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
        val f = SimpleDateFormat("EEE h:mm a", Locale("es", "MX")).format(t).replace(".", "")
        return f.replaceFirstChar { it.uppercase() }
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    private fun abrirApp(ctx: Context) = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /* La pantalla que se abre cuando suena */
    fun sonar(ctx: Context, codigo: Int, nombre: String, id: Int) = PendingIntent.getActivity(
        ctx, codigo, Intent(ctx, AlarmaActivity::class.java).putExtra("nombre", nombre).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun puedeExacta(ctx: Context) = Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    /* Solo dejo programada la más próxima; al sonar se programa la siguiente */
    fun programar(ctx: Context) {
        cargar(ctx)
        val am = alarmManager(ctx)
        val pi = sonar(ctx, 100, "", 0)
        am.cancel(pi)
        val (a, t) = proxima() ?: return
        if (!puedeExacta(ctx)) return
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, abrirApp(ctx)), sonar(ctx, 100, a.nombre, a.id))
    }

    fun posponer(ctx: Context, nombre: String, minutos: Int = 10) {
        if (!puedeExacta(ctx)) return
        alarmManager(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + minutos * 60_000L, abrirApp(ctx)), sonar(ctx, 101, nombre, -1))
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
}

/* ——— Temporizador ——— */
object Temporizador {
    private const val PREFS = "whyclock_alarmas"
    var fin by mutableLongStateOf(0L)

    fun cargar(ctx: Context) {
        fin = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("fin", 0L).let { if (it < System.currentTimeMillis()) 0L else it }
    }

    fun iniciar(ctx: Context, ms: Long) {
        fin = System.currentTimeMillis() + ms
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fin", fin).apply()
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(fin, PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)),
            Alarmas.sonar(ctx, 102, "Temporizador", -2)
        )
        avisar(ctx)
    }

    fun cancelar(ctx: Context) {
        fin = 0L
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fin", 0L).apply()
        ctx.getSystemService(AlarmManager::class.java).cancel(Alarmas.sonar(ctx, 102, "Temporizador", -2))
        ctx.getSystemService(NotificationManager::class.java).cancel(7)
    }

    /* Notificación con la cuenta regresiva */
    private fun avisar(ctx: Context) {
        Alarmas.canal(ctx)
        val n = Notification.Builder(ctx, Alarmas.CANAL)
            .setSmallIcon(R.drawable.ic_noti).setContentTitle("Temporizador")
            .setWhen(fin).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(Notification.Action.Builder(null, "Cancelar", PendingIntent.getBroadcast(ctx, 3, Intent(ctx, Receptor::class.java).setAction("cancelar_temporizador"), PendingIntent.FLAG_IMMUTABLE)).build())
            .build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(7, n) }
    }
}

/* Al reiniciar el teléfono se vuelven a programar; y cancela el temporizador desde la notificación */
class Receptor : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "cancelar_temporizador" -> Temporizador.cancelar(ctx)
            else -> Alarmas.programar(ctx)
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
        val id = intent.getIntExtra("id", 0)
        when {
            id == -2 -> { getSharedPreferences("whyclock_alarmas", MODE_PRIVATE).edit().putLong("fin", 0L).apply(); Temporizador.fin = 0L; getSystemService(NotificationManager::class.java).cancel(7) }
            id > 0 -> Alarmas.yaSono(this, id)
        }
        sonar()
        setContent { PantallaSonando(nombre.ifBlank { "Alarma" }, temporizador = id == -2, onPosponer = { Alarmas.posponer(this, nombre); finish() }, onDetener = { finish() }) }
    }

    private fun sonar() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
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

@Composable
private fun PantallaSonando(nombre: String, temporizador: Boolean, onPosponer: () -> Unit, onDetener: () -> Unit) {
    val f = familiaDigitos()
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { ahora = System.currentTimeMillis(); delay(1000) } }
    val c = Calendar.getInstance().apply { timeInMillis = ahora }
    Box(Modifier.fillMaxSize().background(Color(0xFF0A0908))) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(nombre, color = Color(0xFFF2B233), fontFamily = INTER_A, fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("%d:%02d".format(c.get(Calendar.HOUR).let { if (it == 0) 12 else it }, c.get(Calendar.MINUTE)), color = Color(0xFFE53328), fontFamily = f, fontSize = 96.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!temporizador) Text("Posponer 10 min", color = Color(0xFFF0E9E2), fontFamily = INTER_A, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xFF2A2522)).clickable { onPosponer() }.padding(horizontal = 26.dp, vertical = 16.dp))
                Text("Detener", color = Color.White, fontFamily = INTER_A, fontWeight = FontWeight.Black, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xFFE53328)).clickable { onDetener() }.padding(horizontal = 34.dp, vertical = 16.dp))
            }
        }
    }
}

/* ——— Lista de alarmas (se abre desde la barra) ——— */
private val LETRAS = listOf("L", "M", "X", "J", "V", "S", "D")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoAlarmas(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Alarmas.cargar(ctx) }
    var editando by remember { mutableStateOf<Alarma?>(null) }
    val e = editando
    if (e != null) {
        val estado = rememberTimePickerState(e.hora, e.minuto, false)
        var dias by remember { mutableIntStateOf(e.dias) }
        AlertDialog(
            onDismissRequest = { editando = null },
            title = { Text(if (Alarmas.lista.any { it.id == e.id }) "Editar alarma" else "Nueva alarma") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimeInput(estado)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LETRAS.forEachIndexed { i, l ->
                            val on = dias and (1 shl i) != 0
                            Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) Color(0xFFE0403A) else Color(0x22000000)).clickable { dias = dias xor (1 shl i) }, contentAlignment = Alignment.Center) {
                                Text(l, color = if (on) Color.White else Color(0xFF1C1C1E), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                    Text(if (dias == 0) "Suena una sola vez" else "Se repite los días marcados", fontSize = 12.sp, color = Color(0xFF6B6456), modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val nueva = e.copy(hora = estado.hour, minuto = estado.minute, dias = dias, activa = true)
                    val i = Alarmas.lista.indexOfFirst { it.id == e.id }
                    if (i >= 0) Alarmas.lista[i] = nueva else Alarmas.lista += nueva
                    Alarmas.guardar(ctx); editando = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (Alarmas.lista.any { it.id == e.id }) TextButton(onClick = { Alarmas.lista.removeAll { it.id == e.id }; Alarmas.guardar(ctx); editando = null }) { Text("Borrar", color = Color(0xFFE0403A)) }
                    TextButton(onClick = { editando = null }) { Text("Cancelar") }
                }
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Alarmas") },
        text = {
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                if (Alarmas.lista.isEmpty()) Text("Todavía no tienes alarmas.", color = Color(0xFF6B6456))
                Alarmas.lista.sortedBy { it.hora * 60 + it.minuto }.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable { editando = a }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("%d:%02d %s".format(a.hora % 12 .let { if (it == 0) 12 else it }, a.minuto, if (a.hora >= 12) "PM" else "AM"),
                                fontFamily = INTER_A, fontWeight = FontWeight.Black, fontSize = 26.sp, color = if (a.activa) Color(0xFF1C1C1E) else Color(0xFF9A927F))
                            Text(if (a.dias == 0) "Una vez" else if (a.dias == 127) "Todos los días" else LETRAS.filterIndexed { i, _ -> a.dias and (1 shl i) != 0 }.joinToString(" "),
                                fontSize = 12.sp, color = Color(0xFF6B6456))
                        }
                        Switch(a.activa, { on ->
                            val i = Alarmas.lista.indexOfFirst { it.id == a.id }
                            if (i >= 0) { Alarmas.lista[i] = a.copy(activa = on); Alarmas.guardar(ctx) }
                        })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { editando = Alarma(Alarmas.nuevoId(), 7, 0, 0, true, "") }) { Text("Añadir") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}

/* ——— Temporizador (se abre desde la barra) ——— */
@Composable
fun DialogoTemporizador(onCerrar: () -> Unit) {
    val ctx = LocalContext.current
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { ahora = System.currentTimeMillis(); delay(250) } }
    var minutos by remember { mutableIntStateOf(5) }
    val corriendo = Temporizador.fin > ahora
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Temporizador") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (corriendo) {
                    val s = ((Temporizador.fin - ahora) / 1000).toInt()
                    Text(if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60),
                        fontFamily = INTER_A, fontWeight = FontWeight.Black, fontSize = 48.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("−", fontSize = 34.sp, modifier = Modifier.clip(CircleShape).clickable { minutos = (minutos - 1).coerceAtLeast(1) }.padding(horizontal = 16.dp))
                        Text("$minutos min", fontFamily = INTER_A, fontWeight = FontWeight.Black, fontSize = 36.sp, modifier = Modifier.width(150.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("+", fontSize = 34.sp, modifier = Modifier.clip(CircleShape).clickable { minutos = (minutos + 1).coerceAtMost(600) }.padding(horizontal = 16.dp))
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 5, 10, 15, 30, 60).forEach { m ->
                            Text("$m", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (m == minutos) Color.White else Color(0xFF1C1C1E),
                                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (m == minutos) Color(0xFFE0403A) else Color(0x18000000)).clickable { minutos = m }.padding(horizontal = 11.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (corriendo) TextButton(onClick = { Temporizador.cancelar(ctx) }) { Text("Cancelar", color = Color(0xFFE0403A)) }
            else TextButton(onClick = { Temporizador.iniciar(ctx, minutos * 60_000L); onCerrar() }) { Text("Iniciar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}
