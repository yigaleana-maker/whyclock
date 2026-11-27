package com.yehu.whyclock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs

/* Para que una pantalla pueda llevarte a otra (los enlaces "← Música" / "Temporizador →") */
val LocalIr = staticCompositionLocalOf<(Int) -> Unit> { {} }

/* Botón que se hunde un poquito al tocarlo y regresa con resorte, como en Headpod y WhyType */
fun Modifier.pulsable(enabled: Boolean = true, hundir: Float = 0.92f, onClick: () -> Unit): Modifier = composed {
    val fuente = remember { MutableInteractionSource() }
    val presionado by fuente.collectIsPressedAsState()
    val escala by animateFloatAsState(
        if (presionado) hundir else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pulsable"
    )
    this.graphicsLayer { scaleX = escala; scaleY = escala }
        .elastico(intensidad = 0.7f)
        .clickable(interactionSource = fuente, indication = null, enabled = enabled, onClick = onClick)
}

/* Al deslizar entre pantallas, la que se va se aleja un poco y se desvanece */
fun Modifier.paginaFluida(estado: PagerState, pagina: Int): Modifier = this.graphicsLayer {
    val f = abs((estado.currentPage - pagina) + estado.currentPageOffsetFraction).coerceIn(0f, 1f)
    val e = 1f - 0.08f * f
    scaleX = e; scaleY = e
    alpha = 1f - 0.4f * f
}

/* Texto que aparece subiendo suavecito cuando cambia (clave decide cuándo se anima) */
@Composable
fun <T> Cambiante(valor: T, clave: (T) -> Any? = { it }, contenido: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = valor, contentKey = clave,
        transitionSpec = { (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 3 }) togetherWith fadeOut(tween(200)) },
        label = "cambiante"
    ) { contenido(it) }
}

/* Números que ruedan hacia arriba o hacia abajo según suban o bajen */
@Composable
fun NumeroRodante(valor: Int, contenido: @Composable (Int) -> Unit) {
    AnimatedContent(
        targetState = valor,
        transitionSpec = {
            val sube = targetState > initialState
            (slideInVertically(spring(stiffness = Spring.StiffnessMediumLow)) { if (sube) it else -it } + fadeIn()) togetherWith
                (slideOutVertically(spring(stiffness = Spring.StiffnessMediumLow)) { if (sube) -it else it } + fadeOut())
        }, label = "numero"
    ) { contenido(it) }
}

/* Del analógico al digital: en lugar de deslizarse, uno se desvanece y el otro aparece en el mismo lugar,
   al ritmo de tu dedo (el analógico se va en el primer 45 %, el digital llega en el último 45 %) */
fun Modifier.relojQueSeConvierte(estado: PagerState, pagina: Int): Modifier = this.graphicsLayer {
    val pos = estado.currentPage + estado.currentPageOffsetFraction
    if (pos > 1f) {
        /* Hacia la pantalla 3: el digital se va como las demás */
        val f = abs(pos - pagina).coerceIn(0f, 1f)
        val e = 1f - 0.08f * f
        scaleX = e; scaleY = e; alpha = 1f - 0.4f * f
        return@graphicsLayer
    }
    val t = pos.coerceIn(0f, 1f)
    /* Se queda en su lugar: se cancela el deslizamiento de la página */
    translationX = (pos - pagina) * size.width
    if (pagina == 0) {
        val a = (1f - t / 0.45f).coerceIn(0f, 1f)
        alpha = a; val e = 0.94f + 0.06f * a; scaleX = e; scaleY = e
    } else {
        val a = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
        alpha = a; val e = 1.06f - 0.06f * a; scaleX = e; scaleY = e
    }
}
