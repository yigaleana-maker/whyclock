package com.yehu.whyclock

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/*
 * Animaciones inspiradas en Expressive Clock (Sam Beckman, licencia MIT):
 * texto de rodillo, toque luminoso y elástico, orillas suaves y el tambor para elegir números.
 */

/* ——— Texto de rodillo: cada letra que cambia gira como un odómetro, una tras otra ——— */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TextoRodillo(texto: String, estilo: TextStyle, color: Color, modifier: Modifier = Modifier, tabular: Boolean = true) {
    val medidor = rememberTextMeasurer()
    val densidad = LocalDensity.current
    /* Todos los dígitos ocupan lo mismo: así nada brinca de lado al cambiar */
    val anchoDigito = remember(estilo) { (0..9).maxOf { medidor.measure(it.toString(), estilo).size.width } }
    var anterior by remember { mutableStateOf(texto) }
    val cambiados = remember(texto) { texto.indices.filter { texto[it] != anterior.getOrNull(it) } }
    LaunchedEffect(texto) { anterior = texto }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        texto.forEachIndexed { i, ch ->
            val c = ch.toString()
            /* tabular: todos los dígitos del mismo ancho. Si no, cada uno el suyo y el cambio de ancho es suave */
            val anchoReal = remember(c, estilo) { medidor.measure(c, estilo).size.width }
            val anchoMeta = if (tabular && ch.isDigit()) anchoDigito else anchoReal
            val ancho by animateFloatAsState(anchoMeta.toFloat(), spring(dampingRatio = 1f, stiffness = 420f), label = "anchoRodillo")
            var mostrado by remember { mutableStateOf(c) }
            val turno = cambiados.indexOf(i).coerceAtLeast(0)
            LaunchedEffect(c) {
                if (c != mostrado) { delay((turno * 18L).coerceAtMost(72L)); mostrado = c }
            }
            AnimatedContent(
                mostrado,
                modifier = Modifier.width(with(densidad) { ancho.toDp() })
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        /* Arriba y abajo la letra se desvanece, como una ventanita */
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.16f to Color.Black, 0.84f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    },
                contentAlignment = Alignment.Center,
                transitionSpec = {
                    ((slideInVertically(spring(dampingRatio = 1f, stiffness = 420f)) { it } + fadeIn(tween(100))) togetherWith
                        (slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(100))))
                        .using(SizeTransform(clip = false))
                },
                label = "rodillo"
            ) { s ->
                /* Al pasar se inclina un poco hacia atrás, como un rodillo de verdad */
                val inclina by transition.animateFloat(transitionSpec = { tween(230) }, label = "inclina") { e ->
                    when (e) { EnterExitState.PreEnter -> -35f; EnterExitState.Visible -> 0f; EnterExitState.PostExit -> 35f }
                }
                Text(s, style = estilo, color = color, maxLines = 1, softWrap = false,
                    modifier = Modifier.graphicsLayer { rotationX = inclina; cameraDistance = size.height * 8f })
            }
        }
    }
}

/* ——— Toque luminoso y elástico ———
   Se agrega a un botón (no reemplaza su clic): se estira hacia donde pones el dedo, se mueve un poquito,
   brilla siguiendo tu dedo y deja una onda de luz; al soltar regresa con rebote.
   forma: si se da, el brillo se dibuja dentro de esa forma */
fun Modifier.elastico(forma: Shape? = null, brillo: Color = Color.White, intensidad: Float = 1f): Modifier = composed {
    var dedo by remember { mutableStateOf<Offset?>(null) }
    var ultimo by remember { mutableStateOf(Offset.Zero) }
    var origen by remember { mutableStateOf(Offset.Zero) }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val presionado = dedo != null
    val adx by animateFloatAsState(dx, if (presionado) spring(dampingRatio = 0.82f, stiffness = 550f) else spring(dampingRatio = 0.52f, stiffness = 350f), label = "edx")
    val ady by animateFloatAsState(dy, if (presionado) spring(dampingRatio = 0.82f, stiffness = 550f) else spring(dampingRatio = 0.52f, stiffness = 350f), label = "edy")
    val escala by animateFloatAsState(if (presionado) 0.95f else 1f, if (presionado) tween(90, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.62f, stiffness = 420f), label = "eesc")
    val luz by animateFloatAsState(if (presionado) 1f else 0f, tween(if (presionado) 120 else 260, easing = FastOutSlowInEasing), label = "eluz")
    val onda = remember { Animatable(1f) }
    val alcance = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                    val ch = ev.changes.firstOrNull() ?: continue
                    if (ch.pressed) {
                        if (dedo == null) { origen = ch.position; alcance.launch { onda.snapTo(0f); onda.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) } }
                        dedo = ch.position; ultimo = ch.position
                        val cx = size.width / 2f; val cy = size.height / 2f
                        if (cx > 0f && cy > 0f) { dx = ((ch.position.x - cx) / cx).coerceIn(-1f, 1f); dy = ((ch.position.y - cy) / cy).coerceIn(-1f, 1f) }
                    } else { dedo = null; dx = 0f; dy = 0f }
                }
            }
        }
        .graphicsLayer {
            val k = 0.092f * intensidad
            val sx = 1f + k * abs(adx) - 0.03f * abs(ady)
            val sy = 1f + k * 1.06f * abs(ady) - 0.03f * abs(adx)
            scaleX = escala * sx; scaleY = escala * sy
            transformOrigin = TransformOrigin((0.5f - 0.3f * adx).coerceIn(0.15f, 0.85f), (0.5f - 0.3f * ady).coerceIn(0.15f, 0.85f))
            translationX = adx * 2.6.dp.toPx() * intensidad
            translationY = ady * 2.6.dp.toPx() * intensidad
        }
        .then(if (forma == null) Modifier else Modifier.drawWithContent {
            drawContent()
            if (luz > 0.01f || onda.value < 0.999f) {
                val contorno = Path().apply { addOutline(forma.createOutline(size, layoutDirection, this@drawWithContent)) }
                clipPath(contorno) {
                    val r = size.maxDimension * 0.75f
                    if (luz > 0.01f) drawCircle(Brush.radialGradient(listOf(brillo.copy(alpha = 0.32f * luz), brillo.copy(alpha = 0.10f * luz), Color.Transparent), center = ultimo, radius = r), r, ultimo)
                    if (onda.value < 0.999f) {
                        val ro = size.maxDimension * (0.15f + 0.95f * onda.value)
                        drawCircle(brillo.copy(alpha = 0.22f * (1f - onda.value)), ro, origen)
                    }
                }
            }
        })
}

/* ——— Orillas suaves: lo que se va por arriba o por abajo se desvanece en vez de cortarse ——— */
fun Modifier.bordesSuaves(arriba: Dp = 24.dp, abajo: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height.coerceAtLeast(1f)
        val a = (arriba.toPx() / h).coerceIn(0f, 0.45f)
        val b = (abajo.toPx() / h).coerceIn(0f, 0.45f)
        drawRect(Brush.verticalGradient(0f to Color.Transparent, a to Color.Black, 1f - b to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
    }

/* ——— Tambor: un cilindro visto de lado que giras con el dedo para elegir un número ———
   Las rayitas se juntan hacia las orillas como en una perilla de verdad; la del centro es la más
   gruesa y brillante. Cada paso da un toquecito. */
@Composable
fun Tambor(
    valor: Int, min: Int, max: Int, onCambio: (Int) -> Unit,
    color: Color, pista: Color, modifier: Modifier, ciclo: Boolean = true, pasoDp: Dp = 16.dp
) {
    val vibra = LocalHapticFeedback.current
    val alcance = rememberCoroutineScope()
    val actual by rememberUpdatedState(valor)
    val cambia by rememberUpdatedState(onCambio)
    /* Cuánto se ha girado entre un número y otro (de -1 a 1), para que se mueva con el dedo */
    val resto = remember { Animatable(0f) }
    val densidad = LocalDensity.current
    val paso = with(densidad) { pasoDp.toPx() }
    Box(modifier.pointerInput(min, max) {
        var acumulado = 0f
        detectHorizontalDragGestures(
            onDragStart = { acumulado = 0f },
            onDragEnd = { alcance.launch { resto.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 380f)) } },
            onDragCancel = { alcance.launch { resto.animateTo(0f, spring(dampingRatio = 0.6f)) } }
        ) { ch, d ->
            ch.consume()
            acumulado -= d
            var pasos = 0
            while (acumulado >= paso) { acumulado -= paso; pasos++ }
            while (acumulado <= -paso) { acumulado += paso; pasos-- }
            if (pasos != 0) {
                var n = actual + pasos
                n = if (ciclo) { val r = max - min + 1; ((n - min) % r + r) % r + min } else n.coerceIn(min, max)
                if (n != actual) { cambia(n); vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
            }
            alcance.launch { resto.snapTo((acumulado / paso).coerceIn(-1f, 1f)) }
        }
    }) {
        Canvas(Modifier.matchParentSize()) {
            val r = 18.dp.toPx().coerceAtMost(size.height / 2)
            drawRoundRect(pista, cornerRadius = CornerRadius(r))
            val cx = size.width / 2; val cy = size.height / 2
            val radio = size.width / 2 - 10.dp.toPx()
            val grados = 12f
            val alto = size.height * 0.56f
            for (k in -8..8) {
                val pos = k - resto.value
                val th = pos * grados * PI / 180
                if (abs(th) >= PI / 2) continue
                val prof = cos(th).toFloat()
                val x = cx + (radio * sin(th)).toFloat()
                val n = actual + k
                val mayor = ((n % 5) + 5) % 5 == 0
                val grueso = (1.2.dp.toPx() + 3.2.dp.toPx() * prof * prof) * (if (mayor) 1.25f else 1f)
                val h = alto * (if (mayor) 1f else 0.78f)
                drawLine(color.copy(alpha = (0.18f + 0.82f * prof * prof).coerceIn(0f, 1f)), Offset(x, cy - h / 2), Offset(x, cy + h / 2), grueso, StrokeCap.Round)
            }
            /* La marca del centro: una rayita más larga, del color fuerte */
            drawLine(color, Offset(cx, cy - alto * 0.68f), Offset(cx, cy + alto * 0.68f), 4.dp.toPx(), StrokeCap.Round)
        }
    }
}
