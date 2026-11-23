package com.yehu.whyclock

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/* dias: bit 0 = lunes … bit 6 = domingo. 0 = suena una sola vez. tono vacío = el de alarma del teléfono */
data class Alarma(val id: Int, val hora: Int, val minuto: Int, val dias: Int, val activa: Boolean, val nombre: String, val tono: String = "")

object Alarmas {
    private const val PREFS = "whyclock_alarmas"
    const val CANAL = "alarmas"
    const val CANAL_SONANDO = "sonando"
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

    fun faltaTexto(a: Alarma): String {
        val m = ((siguiente(a) - System.currentTimeMillis()) / 60_000L).toInt() + 1
        val d = m / 1440; val h = m / 60 % 24; val mm = m % 60
        return "Suena en " + listOfNotNull(if (d > 0) "$d d" else null, if (h > 0) "$h h" else null, if (mm > 0 || (d == 0 && h == 0)) "$mm min" else null).joinToString(" ")
    }

    private fun alarmManager(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun abrirApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /* Lo que se dispara a la hora: el receptor, que arranca el servicio que suena (como la app original) */
    fun disparo(ctx: Context, codigo: Int, nombre: String, id: Int, tono: String = ""): PendingIntent = PendingIntent.getBroadcast(
        ctx, codigo, Intent(ctx, Receptor::class.java).setAction("sonar").putExtra("nombre", nombre).putExtra("id", id).putExtra("tono", tono),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun puedeExacta(ctx: Context) = Build.VERSION.SDK_INT < 31 || alarmManager(ctx).canScheduleExactAlarms()

    fun programar(ctx: Context) {
        cargar(ctx)
        val am = alarmManager(ctx)
        am.cancel(disparo(ctx, 100, "", 0))
        val (a, t) = proxima() ?: return
        if (!puedeExacta(ctx)) return
        am.setAlarmClock(AlarmManager.AlarmClockInfo(t, abrirApp(ctx)), disparo(ctx, 100, a.nombre, a.id, a.tono))
    }

    fun posponer(ctx: Context, nombre: String, tono: String, minutos: Int) {
        if (!puedeExacta(ctx)) return
        alarmManager(ctx).setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + minutos * 60_000L, abrirApp(ctx)), disparo(ctx, 101, nombre, -1, tono))
    }

    fun yaSono(ctx: Context, id: Int) {
        cargar(ctx)
        val i = lista.indexOfFirst { it.id == id }
        if (i >= 0 && lista[i].dias == 0) lista[i] = lista[i].copy(activa = false)
        guardar(ctx)
    }

    fun canales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) == null)
            nm.createNotificationChannel(NotificationChannel(CANAL, "Temporizador en curso", NotificationManager.IMPORTANCE_LOW))
        if (nm.getNotificationChannel(CANAL_SONANDO) == null)
            nm.createNotificationChannel(NotificationChannel(CANAL_SONANDO, "Alarma sonando", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC; setBypassDnd(true)
            })
    }

    fun nombreTono(ctx: Context, tono: String): String =
        if (tono.isBlank()) "Tono del teléfono" else runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(tono))?.getTitle(ctx) }.getOrNull() ?: "Tono elegido"
}

/* ——— Temporizador (se puede pausar y sumar un minuto) ——— */
object Temporizador {
    private const val PREFS = "whyclock_alarmas"
    var fin by mutableLongStateOf(0L)
    var resta by mutableLongStateOf(0L)
    var total by mutableLongStateOf(0L)

    fun cargar(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        fin = p.getLong("fin", 0L).let { if (it < System.currentTimeMillis()) 0L else it }
        resta = p.getLong("resta", 0L)
        total = p.getLong("total", 0L)
    }

    private fun guardar(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("fin", fin).putLong("resta", resta).putLong("total", total).apply()

    private fun pi(ctx: Context) = Alarmas.disparo(ctx, 102, "Temporizador", -2)

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

    fun sumarMinuto(ctx: Context) {
        total += 60_000L
        if (fin > 0) iniciar(ctx, fin - System.currentTimeMillis() + 60_000L, nuevo = false)
        else if (resta > 0) { resta += 60_000L; guardar(ctx); avisar(ctx) }
    }

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

    private fun accion(ctx: Context, texto: String, que: String, codigo: Int) = Notification.Action.Builder(null, texto,
        PendingIntent.getBroadcast(ctx, codigo, Intent(ctx, Receptor::class.java).setAction(que), PendingIntent.FLAG_IMMUTABLE)).build()

    /* Notificación con la cuenta regresiva y botones +1 min, Pausar/Reanudar y Cancelar (como la original) */
    private fun avisar(ctx: Context) {
        Alarmas.canales(ctx)
        val b = Notification.Builder(ctx, Alarmas.CANAL)
            .setSmallIcon(R.drawable.ic_noti).setOngoing(true).setOnlyAlertOnce(true)
            .setContentIntent(Alarmas.abrirApp(ctx))
            .addAction(accion(ctx, "+1 min", "mas_min", 4))
        if (fin > 0) b.setContentTitle("Temporizador").setWhen(fin).setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
            .addAction(accion(ctx, "Pausar", "pausar", 5))
        else { val s = resta / 1000; b.setContentTitle("Temporizador en pausa").setContentText("%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)).addAction(accion(ctx, "Reanudar", "reanudar", 6)) }
        b.addAction(accion(ctx, "Cancelar", "cancelar_temporizador", 3))
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(7, b.build()) }
    }
}

/* Recibe la hora de sonar, los botones de las notificaciones y el reinicio del teléfono */
class Receptor : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "sonar" -> {
                val s = Intent(ctx, Sonando::class.java).putExtras(intent)
                runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(s) else ctx.startService(s) }
            }
            "cancelar_temporizador" -> { Temporizador.cargar(ctx); Temporizador.cancelar(ctx) }
            "pausar" -> { Temporizador.cargar(ctx); Temporizador.pausar(ctx) }
            "reanudar" -> { Temporizador.cargar(ctx); Temporizador.seguir(ctx) }
            "mas_min" -> { Temporizador.cargar(ctx); Temporizador.sumarMinuto(ctx) }
            else -> Alarmas.programar(ctx)
        }
    }
}

/* ——— El servicio que suena: tono en bucle, vibración, y la notificación de pantalla completa que abre la pantalla de alarma ——— */
class Sonando : Service() {
    companion object {
        /* La pantalla de alarma la observa: cuando deja de sonar, se cierra */
        var activo by mutableStateOf(false)
        var nombre by mutableStateOf("")
        var tono by mutableStateOf("")
        var esTemporizador by mutableStateOf(false)
        /* La hora a la que sonó (la pantalla la muestra fija, como la original) */
        var hora by mutableStateOf("")
    }
    private var reproductor: MediaPlayer? = null
    private var vibrador: Vibrator? = null
    private var despierto: PowerManager.WakeLock? = null
    private val mano = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "detener" -> { parar(); return START_NOT_STICKY }
            "posponer" -> { Alarmas.posponer(this, nombre, tono, intent.getIntExtra("min", 10)); parar(); return START_NOT_STICKY }
        }
        val id = intent?.getIntExtra("id", 0) ?: 0
        nombre = intent?.getStringExtra("nombre").orEmpty()
        tono = intent?.getStringExtra("tono").orEmpty()
        esTemporizador = id == -2
        hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
        Ajustes.cargar(this)
        when { esTemporizador -> Temporizador.termino(this); id > 0 -> Alarmas.yaSono(this, id) }

        Alarmas.canales(this)
        val pantalla = PendingIntent.getActivity(this, 200, Intent(this, AlarmaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun boton(texto: String, que: String, cod: Int) = Notification.Action.Builder(null, texto,
            PendingIntent.getService(this, cod, Intent(this, Sonando::class.java).setAction(que).putExtra("min", getSharedPreferences("whyclock_alarmas", MODE_PRIVATE).getInt("posponer", 10)), PendingIntent.FLAG_IMMUTABLE)).build()
        val n = Notification.Builder(this, Alarmas.CANAL_SONANDO)
            .setSmallIcon(R.drawable.ic_noti)
            .setContentTitle(if (esTemporizador) "Se acabó el tiempo" else nombre.ifBlank { "Alarma" })
            .setContentText(SimpleDateFormat("H:mm", Locale.getDefault()).format(System.currentTimeMillis()))
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setFullScreenIntent(pantalla, true).setContentIntent(pantalla)
            .apply { if (!esTemporizador) addAction(boton("Posponer", "posponer", 201)) }
            .addAction(boton("Detener", "detener", 202))
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(8, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(8, n)
        activo = true
        /* Si la app está abierta o la pantalla apagada, abre directo la pantalla de alarma */
        runCatching { startActivity(Intent(this, AlarmaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        if (reproductor == null) sonar()
        /* Si nadie la apaga, se calla sola a los 10 minutos */
        mano.removeCallbacksAndMessages(null)
        mano.postDelayed({ parar() }, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun sonar() {
        despierto = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "whyclock:alarma").apply { acquire(10 * 60_000L + 5000) }
        val uri = tono.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        reproductor = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@Sonando, uri); isLooping = true; prepare(); start()
            }
        }.getOrNull()
        vibrador = getSystemService(Vibrator::class.java)
        runCatching { vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 600), 0)) }
    }

    private fun parar() {
        activo = false
        mano.removeCallbacksAndMessages(null)
        reproductor?.run { runCatching { stop() }; release() }; reproductor = null
        vibrador?.cancel()
        despierto?.let { if (it.isHeld) it.release() }; despierto = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { if (activo) parar(); super.onDestroy() }
}

/* ——— Permisos que necesitan las alarmas para salir en pantalla completa y sonar a tiempo ——— */
object Permisos {
    fun pantallaCompleta(ctx: Context) = Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun notificaciones(ctx: Context) = Build.VERSION.SDK_INT < 33 || ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun bateria(ctx: Context) = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    fun exactas(ctx: Context) = Alarmas.puedeExacta(ctx)
    fun faltan(ctx: Context) = !pantallaCompleta(ctx) || !notificaciones(ctx) || !bateria(ctx) || !exactas(ctx)

    fun abrirPantallaCompleta(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 34) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    @android.annotation.SuppressLint("BatteryLife")
    fun abrirBateria(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun abrirExactas(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 31) runCatching { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun abrirNotificaciones(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
