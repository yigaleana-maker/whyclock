package com.yehu.whyclock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.min

/* Solo existe para que Android nos deje ver qué música está sonando */
class Escucha : NotificationListenerService()

private val INTER_M = FontFamily(Font(R.font.inter_700, FontWeight.Bold), Font(R.font.inter_900, FontWeight.Black), Font(R.font.inter_400, FontWeight.Normal))

private data class Cancion(val titulo: String, val artista: String, val portada: Bitmap?, val sonando: Boolean, val app: String)

private fun permitido(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true

private fun controlador(ctx: Context): MediaController? = runCatching {
    val sesiones = ctx.getSystemService(MediaSessionManager::class.java).getActiveSessions(ComponentName(ctx, Escucha::class.java))
    sesiones.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sesiones.firstOrNull()
}.getOrNull()

private fun leer(ctx: Context, m: MediaController?): Cancion? {
    m ?: return null
    val d = m.metadata ?: return null
    val nombreApp = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(m.packageName, 0)).toString() }.getOrDefault("")
    return Cancion(
        d.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
        (d.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: d.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
        d.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: d.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: d.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        m.playbackState?.state == PlaybackState.STATE_PLAYING, nombreApp
    )
}

/* ——— Pantalla 7: Música. Un tocadiscos con la portada en el disco, que gira mientras suena ——— */
@Composable
fun PantallaMusica(ahora: Long) {
    val ctx = LocalContext.current
    var ok by remember { mutableStateOf(permitido(ctx)) }
    var cancion by remember { mutableStateOf<Cancion?>(null) }
    var ctl by remember { mutableStateOf<MediaController?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            ok = permitido(ctx)
            if (ok) { ctl = controlador(ctx); cancion = leer(ctx, ctl) }
            delay(1000)
        }
    }
    val oscuro = Ajustes.oscuro
    val fondo = if (oscuro) Color(0xFF11100E) else Color(0xFFEFEEE9)
    val tinta = if (oscuro) Color(0xFFFFC53D) else Color(0xFF1C1C1E)
    val acento = if (oscuro) Color(0xFFFF4A3D) else Color(0xFFE0403A)
    val sonando = cancion?.sonando == true

    val giro = rememberInfiniteTransition(label = "disco")
    val angulo by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "a")
    var base by remember { mutableStateOf(0f) }
    var ultimo by remember { mutableStateOf(0f) }
    /* El disco solo avanza mientras suena (sin brincos al pausar) */
    LaunchedEffect(angulo) { val d = (angulo - ultimo + 360f) % 360f; if (sonando) base = (base + d) % 360f; ultimo = angulo }
    val brazo by animateFloatAsState(if (sonando) 24f else 0f, tween(700), label = "brazo")

    Column(Modifier.fillMaxSize().background(fondo)) {
    Encabezado("Música", null, "Alarmas" to IR_ALARMAS, tinta)
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val horizontal = maxWidth > maxHeight
        val lado = (if (horizontal) min(maxHeight.value * 0.78f, maxWidth.value * 0.3f) else min(maxWidth.value * 0.62f, maxHeight.value * 0.3f)).dp
        /* El reloj de Música: tocarlo cambia entre analógico y digital, como la original */
        val reloj: @Composable () -> Unit = {
            RelojCambiable(ahora, Variantes.musicaDigital, if (horizontal) lado * 0.92f else lado * 0.8f, { Variantes.musicaDigital = !Variantes.musicaDigital; Variantes.guardar(ctx) })
        }
        val tocadiscos: @Composable () -> Unit = {
            Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
                /* Base de madera */
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF6B4A33), Color(0xFF3F2A1D)))))
                Box(Modifier.size(lado * 0.84f).rotate(base), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2
                        drawCircle(Color(0xFF111111), r)
                        for (i in 1..14) drawCircle(Color(0x16FFFFFF), r * (0.42f + i * 0.04f), style = Stroke(1f))
                        /* Brillo que se queda fijo mientras el disco gira */
                        rotate(-base) { drawArc(Brush.sweepGradient(listOf(Color.Transparent, Color(0x22FFFFFF), Color.Transparent)), 200f, 50f, true, alpha = 0.9f) }
                    }
                    val p = cancion?.portada
                    if (p != null) Image(p.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(lado * 0.36f).clip(CircleShape))
                    else Box(Modifier.size(lado * 0.36f).clip(CircleShape).background(acento))
                    Box(Modifier.size(lado * 0.035f).clip(CircleShape).background(Color(0xFFDDDDDD)))
                }
                /* El brazo, que baja al disco cuando suena */
                Canvas(Modifier.fillMaxSize()) {
                    val eje = Offset(size.width * 0.9f, size.height * 0.1f)
                    rotate(brazo, eje) {
                        drawLine(Color(0xFFCFCFCF), eje, Offset(size.width * 0.84f, size.height * 0.62f), size.width * 0.022f, StrokeCap.Round)
                        drawLine(Color(0xFFCFCFCF), Offset(size.width * 0.84f, size.height * 0.62f), Offset(size.width * 0.74f, size.height * 0.74f), size.width * 0.022f, StrokeCap.Round)
                        drawCircle(Color(0xFF2A2A2A), size.width * 0.035f, Offset(size.width * 0.74f, size.height * 0.74f))
                    }
                    drawCircle(Color(0xFF9A9A9A), size.width * 0.05f, eje)
                    drawCircle(Color(0xFF555555), size.width * 0.022f, eje)
                }
            }
        }
        val info: @Composable (Modifier) -> Unit = { m ->
            Column(m.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (!ok) {
                    Text("Para ver lo que suena, dale permiso a WhyClock", color = tinta, fontFamily = INTER_M, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("Dar permiso", color = Color.White, fontFamily = INTER_M, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(acento)
                            .clickable { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .padding(horizontal = 22.dp, vertical = 11.dp))
                } else {
                    val s = cancion
                    Text(s?.titulo?.ifBlank { null } ?: "Nada sonando", color = tinta, fontFamily = INTER_M, fontWeight = FontWeight.Black, fontSize = 22.sp,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s != null) {
                        Text(s.artista, color = tinta.copy(alpha = 0.7f), fontFamily = INTER_M, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(s.app, color = tinta.copy(alpha = 0.45f), fontFamily = INTER_M, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        val boton: @Composable (String, Float, Color, Color, () -> Unit) -> Unit = { t, tam, fon, col, accion ->
                            Box(Modifier.size(tam.dp).clip(CircleShape).background(fon).clickable { accion() }, contentAlignment = Alignment.Center) {
                                Text(t, color = col, fontSize = (tam * 0.38f).sp, fontWeight = FontWeight.Black)
                            }
                        }
                        boton("⏮", 52f, tinta.copy(alpha = 0.1f), tinta) { ctl?.transportControls?.skipToPrevious() }
                        boton(if (sonando) "⏸" else "▶", 68f, acento, Color.White) { ctl?.transportControls?.let { if (sonando) it.pause() else it.play() } }
                        boton("⏭", 52f, tinta.copy(alpha = 0.1f), tinta) { ctl?.transportControls?.skipToNext() }
                    }
                }
            }
        }
        /* Tres zonas, como la original: reloj, tocadiscos y canción */
        if (horizontal) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { reloj() }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { tocadiscos() }
            info(Modifier.weight(1f))
        } else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            reloj(); tocadiscos()
            info(Modifier.fillMaxWidth())
        }
    }
}
}
