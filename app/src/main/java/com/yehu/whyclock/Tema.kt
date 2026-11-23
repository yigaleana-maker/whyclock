package com.yehu.whyclock

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.res.ResourcesCompat

/* Los colores de WhyClock, los mismos del original (modo claro y modo oscuro ámbar) */
data class Colores(
    val fondo: Color,
    val tinta: Color,
    val rayo: Color,
    val acento: Color,
    val dTinta: Color,
    val dMarca: Color,
    val dMarcaTenue: Color,
    val dApagado: Color,
    val dLinea: Color,
    val dFlecha: Color,
    val fecha: Color,
    val calendario: Color,
    val hoy: Color
)

val CLARO = Colores(
    fondo = Color(0xFFEFEEE9),
    tinta = Color(0xFF1C1C1E),
    rayo = Color(0xFFDCD6CF),
    acento = Color(0xFFE0403A),
    dTinta = Color(0xFF1C1C1E),
    dMarca = Color(0xFF34312F),
    dMarcaTenue = Color(0xFF8F8A85),
    dApagado = Color(0xFF9A927F),
    dLinea = Color(0xFFC0392B),
    dFlecha = Color(0xFFE0392B),
    fecha = Color(0xFF6B6456),
    calendario = Color(0xFFF2B233),
    hoy = Color(0xFFFF3B30)
)

val OSCURO = Colores(
    fondo = Color(0xFF11110F),
    tinta = Color(0xFFF0B23A),
    rayo = Color(0xFF26241F),
    acento = Color(0xFFFF4A3D),
    dTinta = Color(0xFFFFC53D),
    dMarca = Color(0xFFE9A92F),
    dMarcaTenue = Color(0xFF8A6A22),
    dApagado = Color(0xFF5C4A1D),
    dLinea = Color(0xB3FF4A3D),
    dFlecha = Color(0xFFFF4A3D),
    fecha = Color(0xFF9A8250),
    calendario = Color(0xFFF2B233),
    hoy = Color(0xFFFF3B30)
)

/* Lo que eliges en la barra de ajustes (se guarda) */
object Ajustes {
    private const val PREFS = "whyclock"
    var oscuro by mutableStateOf(false)
    /* "original" (Bruno Ace), "concert" o "oswald" (condensada) */
    var letra by mutableStateOf("original")
    var suave by mutableStateOf(true)
    var pagina by mutableStateOf(0)

    fun cargar(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        oscuro = p.getBoolean("oscuro", false)
        letra = p.getString("letra", "original") ?: "original"
        suave = p.getBoolean("suave", true)
        pagina = p.getInt("pagina", 0)
    }

    fun guardar(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("oscuro", oscuro).putString("letra", letra).putBoolean("suave", suave).putInt("pagina", pagina).apply()
    }

    fun colores() = if (oscuro) OSCURO else CLARO

    fun nombreLetra() = when (letra) { "concert" -> "Concert"; "oswald" -> "Condensada"; else -> "Original" }
    fun siguienteLetra() { letra = when (letra) { "original" -> "concert"; "concert" -> "oswald"; else -> "original" } }
}

/* Las letras del original */
object Letras {
    private val cache = HashMap<Int, Typeface>()
    fun de(ctx: Context, id: Int): Typeface = cache.getOrPut(id) { ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT }
    fun digitos(ctx: Context): Typeface = de(ctx, when (Ajustes.letra) { "concert" -> R.font.concert_one; "oswald" -> R.font.oswald_700; else -> R.font.bruno_ace })
    fun fecha(ctx: Context): Typeface = de(ctx, R.font.jost_300)
}
