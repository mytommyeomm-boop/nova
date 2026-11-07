package app.nova.companion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import app.nova.R
import app.nova.container

private const val FACE_RATIO = 1.199f
private const val BODY_RATIO = 0.3684f

/** Maps what NOVA is feeling/doing to one of the painted expression sprites. */
private fun faceFor(mood: Mood, phase: Phase): Int {
    if (phase == Phase.THINKING) return R.drawable.nova_face_thinking
    return when (mood) {
        Mood.IDLE -> R.drawable.nova_face_idle
        Mood.HAPPY -> R.drawable.nova_face_happy
        Mood.ANGRY -> R.drawable.nova_face_angry
        Mood.SHY -> R.drawable.nova_face_shy
        Mood.SURPRISED -> R.drawable.nova_face_surprised
        Mood.SAD -> R.drawable.nova_face_sad
        Mood.SLEEPY -> R.drawable.nova_face_sleepy
        Mood.CONFUSED -> R.drawable.nova_face_confused
    }
}

/**
 * NOVA drawn from the painted character art.
 *  - compact = true  -> head-and-shoulders portrait, one painted sprite per expression (9 total)
 *  - compact = false -> full-body painted sprite (neutral pose)
 * Motion (breathing, sway, squash on every voice blip) is applied to the sprite, so new sprite sets
 * can be dropped in by replacing the drawables. [skinIndex] is reserved for future sprite packs.
 */
@Composable
fun CharacterView(
    mood: Mood,
    phase: Phase,
    skinIndex: Int = 0,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animate: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val tick by ctx.container.blip.blipTick.collectAsState()
    val inf = rememberInfiniteTransition(label = "nova")
    val breathe = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val sway = inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val pulseAnim = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    val squash = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (animate && phase == Phase.TALKING) {
            squash.snapTo(1f)
            squash.animateTo(0f, tween(130))
        }
    }

    val tapMod = if (onTap != null) Modifier.pointerInput(onTap) { detectTapGestures { onTap() } } else Modifier
    val res = if (compact) faceFor(mood, phase) else R.drawable.nova_body

    Box(modifier.aspectRatio(if (compact) FACE_RATIO else BODY_RATIO).then(tapMod)) {
        Image(
            painter = painterResource(res),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    if (animate) {
                        val b = breathe.value
                        val sq = squash.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 1f + 0.012f * b + 0.04f * sq
                        scaleX = 1f - 0.006f * b - 0.02f * sq
                        rotationZ = sway.value * (if (compact) 0.9f else 0.6f) +
                            (if (mood == Mood.CONFUSED) 3f else 0f)
                        translationY = -size.height * 0.006f * b
                    }
                }
        )
        if (phase == Phase.LISTENING) {
            val p = pulseAnim
            Canvas(Modifier.matchParentSize()) {
                val c = Color(0xFF22D3EE)
                val w = size.width
                val h = size.height
                val sw = w * 0.012f
                for (k in 1..2) {
                    val a = if (k == 1) 0.4f + p.value * 0.5f else 0.9f - p.value * 0.5f
                    val r = w * (0.05f + 0.05f * k)
                    drawArc(c.copy(alpha = a), -50f, 100f, false, Offset(w * 0.94f - r, h * 0.35f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(c.copy(alpha = a), 130f, 100f, false, Offset(w * 0.06f - r, h * 0.35f - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                }
            }
        }
    }
}
