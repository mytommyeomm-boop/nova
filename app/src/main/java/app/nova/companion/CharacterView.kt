package app.nova.companion

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlinx.coroutines.delay
import kotlin.random.Random

private val HAIR = Color(0xFF14141C)
private val SKIN = Color(0xFFFFE3D6)
private val SKIN_SH = Color(0xFFF2C9B8)
private val AMBER = Color(0xFFF0A92B)
private val AMBER_D = Color(0xFFB36A12)
private val SOCK = Color(0xFFF4F4FA)
private val SHORTS = Color(0xFF191922)
private val MOUTH = Color(0xFF7A2E3A)
private val BLUSH = Color(0xFFFF8FA3)
private val EAR_IN = Color(0xFF4A3358)

/**
 * Original layered vector character, drawn entirely in code (no bitmaps).
 * Parts: tail, legs/socks, shorts, hood, torso, arms, ears, back hair, head, eyes, brows, mouth, bangs.
 * Every part reacts to [mood] (expression) and [phase] (what NOVA is doing).
 */
@Composable
fun CharacterView(
    mood: Mood,
    phase: Phase,
    skinIndex: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    animate: Boolean = true,
    onTap: (() -> Unit)? = null
) {
    val skin = Skins[skinIndex.coerceIn(0, Skins.lastIndex)]
    val inf = rememberInfiniteTransition(label = "nova")
    val breathe = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val sway = inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(1900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "s")
    val flap = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "f")
    val pulse = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")

    var blink by remember { mutableStateOf(false) }
    val twitch = remember { Animatable(0f) }

    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2200, 5200))
            blink = true
            delay(130)
            blink = false
        }
    }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(3000, 7000))
            twitch.animateTo(1f, tween(80))
            twitch.animateTo(0f, tween(140))
        }
    }

    val measurer = rememberTextMeasurer()
    val tapMod = if (onTap != null) Modifier.pointerInput(onTap) { detectTapGestures { onTap() } } else Modifier

    Canvas(
        modifier
            .aspectRatio(100f / (if (compact) 100f else 200f))
            .clipToBounds()
            .then(tapMod)
    ) {
        val a = if (animate) 1f else 0f
        val u = size.width / 100f
        scale(u, u, Offset.Zero) {
            drawNova(
                skin, mood, phase,
                br = breathe.value * a, sw = sway.value * a, fl = flap.value,
                pu = pulse.value, blink = blink, tw = twitch.value * a, compact = compact
            )
        }
        // floating symbols are drawn in pixel space so text stays crisp
        val fs = (size.width * 0.085f).toSp()
        val bob = -sway.value * a * size.width * 0.012f
        fun sym(text: String, color: Color, x: Float, y: Float) {
            drawText(measurer, text, Offset(size.width * x, size.width * y + bob), TextStyle(color = color, fontSize = fs, fontWeight = FontWeight.Bold))
        }
        if (phase == Phase.THINKING) sym("...", Color(0xFF22D3EE), 0.68f, 0.14f)
        else when (mood) {
            Mood.SLEEPY -> sym("Zzz", Color(0xFF60A5FA), 0.68f, 0.14f)
            Mood.SURPRISED -> sym("!?", Color(0xFFEF4444), 0.70f, 0.14f)
            Mood.CONFUSED -> sym("?", Color(0xFFA78BFA), 0.72f, 0.14f)
            Mood.HAPPY -> sym("\u2665", Color(0xFFFF8FA3), 0.72f, 0.16f)
            else -> {}
        }
    }
}

private fun DrawScope.drawNova(
    skin: Skin, mood: Mood, phase: Phase,
    br: Float, sw: Float, fl: Float, pu: Float, blink: Boolean, tw: Float, compact: Boolean
) {
    val bob = br * 1.3f
    val tilt = when (mood) {
        Mood.CONFUSED -> 7f
        Mood.SLEEPY -> 5f + sw
        Mood.SHY -> -4f
        else -> 0f
    } + sw * 0.6f

    if (!compact) {
        // soft glow under feet
        drawOval(skin.main.copy(alpha = 0.22f), Offset(28f, 191f), Size(44f, 6f))
        // tail (rotates around hip)
        rotate(sw * 7f, Offset(64f, 140f)) {
            val p = Path().apply {
                moveTo(64f, 140f)
                cubicTo(84f, 152f, 98f, 140f, 94f, 122f)
                cubicTo(91f, 110f, 80f, 112f, 82f, 120f)
            }
            drawPath(p, HAIR, style = Stroke(7f, cap = StrokeCap.Round))
        }
        // legs: skin + white thigh-high socks
        for (x in listOf(37f, 54f)) {
            drawRoundRect(SKIN, Offset(x, 148f), Size(9f, 14f), CornerRadius(3f))
            drawRoundRect(SOCK, Offset(x - 0.5f, 158f), Size(10.5f, 34f), CornerRadius(5f))
            drawRoundRect(Color(0xFFC9C9DC), Offset(x - 0.5f, 158f), Size(10.5f, 34f), CornerRadius(5f), style = Stroke(0.6f))
        }
        // shorts
        drawRoundRect(SHORTS, Offset(31f, 130f), Size(38f, 22f), CornerRadius(5f))
    }

    translate(0f, bob * 0.5f) {
        // hood behind neck
        drawOval(skin.dark, Offset(32f, 72f), Size(36f, 14f))
        // torso
        val torso = Path().apply {
            moveTo(33f, 78f); lineTo(67f, 78f)
            cubicTo(70f, 95f, 72f, 115f, 72f, 134f)
            lineTo(28f, 134f)
            cubicTo(28f, 115f, 30f, 95f, 33f, 78f); close()
        }
        drawPath(torso, skin.main)
        drawRoundRect(skin.dark, Offset(28f, 128f), Size(44f, 7f), CornerRadius(3f))
        val neck = Path().apply { moveTo(43f, 78f); lineTo(57f, 78f); lineTo(50f, 90f); close() }
        drawPath(neck, SKIN_SH)
        drawLine(skin.light, Offset(46f, 86f), Offset(45f, 101f), 1.3f, StrokeCap.Round)
        drawLine(skin.light, Offset(54f, 86f), Offset(55f, 101f), 1.3f, StrokeCap.Round)

        // arms (rotate around shoulders)
        val extra = when (mood) {
            Mood.HAPPY -> 14f + sw * 8f
            Mood.SURPRISED -> 22f
            Mood.ANGRY -> -2f
            else -> sw * 1.5f
        }
        rotate(6f + extra, Offset(31f, 84f)) { drawArm(skin, 22f) }
        rotate(-6f - extra, Offset(69f, 84f)) { drawArm(skin, 67f) }
    }

    if (compact) {
        // upper body only needs a little chest; nothing more to draw below
    }

    translate(0f, bob) {
        rotate(tilt, Offset(50f, 72f)) {
            // back hair
            drawOval(HAIR, Offset(26f, 28f), Size(48f, 44f))
            drawRoundRect(HAIR, Offset(30f, 56f), Size(40f, 16f), CornerRadius(6f))
            // ears (twitch)
            rotate(-tw * 9f, Offset(36f, 34f)) { drawEar(30f, 36f, 27f, 12f, 44f, 26f) }
            rotate(tw * 5f, Offset(64f, 34f)) { drawEar(70f, 36f, 73f, 12f, 56f, 26f) }
            // head + face
            drawOval(SKIN, Offset(29f, 32f), Size(42f, 40f))
            val blush = when (mood) { Mood.SHY -> 0.6f; Mood.HAPPY -> 0.32f; else -> 0.14f }
            drawOval(BLUSH.copy(alpha = blush), Offset(32f, 61f), Size(8f, 4.5f))
            drawOval(BLUSH.copy(alpha = blush), Offset(60f, 61f), Size(8f, 4.5f))

            drawEye(41f, 56f, 1f, mood, phase, blink)
            drawEye(59f, 56f, -1f, mood, phase, blink)
            drawBrows(mood)
            drawMouth(mood, phase, fl)

            // bangs + side locks + ahoge
            val bangs = Path().apply {
                moveTo(27f, 54f)
                cubicTo(24f, 26f, 76f, 26f, 73f, 54f)
                lineTo(69f, 44f); lineTo(64f, 51f); lineTo(58f, 41f); lineTo(52f, 50f)
                lineTo(46f, 41f); lineTo(40f, 50f); lineTo(35f, 42f); close()
            }
            drawPath(bangs, HAIR)
            drawPath(Path().apply { moveTo(27f, 50f); lineTo(32f, 50f); lineTo(30f, 68f); lineTo(26f, 62f); close() }, HAIR)
            drawPath(Path().apply { moveTo(73f, 50f); lineTo(68f, 50f); lineTo(70f, 68f); lineTo(74f, 62f); close() }, HAIR)
            val ahoge = Path().apply { moveTo(50f, 29f); quadraticBezierTo(48f, 20f, 55f, 17f) }
            drawPath(ahoge, HAIR, style = Stroke(1.8f, cap = StrokeCap.Round))

            if (mood == Mood.ANGRY) drawAnger(67f, 36f)
            if (phase == Phase.LISTENING) {
                val c = Color(0xFF22D3EE)
                drawArc(c.copy(alpha = 0.4f + pu * 0.5f), -45f, 90f, false, Offset(70f, 44f), Size(10f, 16f), style = Stroke(1.2f, cap = StrokeCap.Round))
                drawArc(c.copy(alpha = 0.9f - pu * 0.5f), -45f, 90f, false, Offset(73f, 38f), Size(16f, 28f), style = Stroke(1.2f, cap = StrokeCap.Round))
                drawArc(c.copy(alpha = 0.4f + pu * 0.5f), 135f, 90f, false, Offset(20f, 44f), Size(10f, 16f), style = Stroke(1.2f, cap = StrokeCap.Round))
                drawArc(c.copy(alpha = 0.9f - pu * 0.5f), 135f, 90f, false, Offset(11f, 38f), Size(16f, 28f), style = Stroke(1.2f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawArm(skin: Skin, x: Float) {
    drawCircle(SKIN, 3.9f, Offset(x + 5.5f, 127f))
    drawRoundRect(skin.main, Offset(x, 82f), Size(11f, 42f), CornerRadius(5.5f))
    drawRoundRect(skin.dark, Offset(x, 118f), Size(11f, 6f), CornerRadius(3f))
}

private fun DrawScope.drawEar(bx: Float, by: Float, tx: Float, ty: Float, ix: Float, iy: Float) {
    val outer = Path().apply { moveTo(bx, by); lineTo(tx, ty); lineTo(ix, iy); close() }
    drawPath(outer, HAIR)
    val inner = Path().apply {
        moveTo(bx + (tx - bx) * 0.25f + (ix - bx) * 0.2f, by + (ty - by) * 0.25f + (iy - by) * 0.2f + 6f)
        lineTo(bx + (tx - bx) * 0.8f, by + (ty - by) * 0.8f + 2f)
        lineTo(bx + (ix - bx) * 0.6f + (tx - bx) * 0.2f, by + (iy - by) * 0.6f + (ty - by) * 0.2f + 3f)
        close()
    }
    drawPath(inner, EAR_IN)
}

private fun DrawScope.drawEye(cx: Float, cy: Float, dir: Float, mood: Mood, phase: Phase, blink: Boolean) {
    val stroke = Stroke(1.8f, cap = StrokeCap.Round)
    when {
        mood == Mood.HAPPY -> {
            val p = Path().apply { moveTo(cx - 4.5f, cy + 1.5f); quadraticBezierTo(cx, cy - 4.5f, cx + 4.5f, cy + 1.5f) }
            drawPath(p, HAIR, style = stroke)
        }
        mood == Mood.SLEEPY || blink -> {
            val p = Path().apply { moveTo(cx - 4.5f, cy + 0.5f); quadraticBezierTo(cx, cy + 2.8f, cx + 4.5f, cy + 0.5f) }
            drawPath(p, HAIR, style = stroke)
        }
        else -> {
            val wide = mood == Mood.SURPRISED
            val grow = if (phase == Phase.LISTENING) 1.1f else 1f
            if (wide) drawOval(Color.White, Offset(cx - 5.2f, cy - 6.2f), Size(10.4f, 12.4f))
            val rx = (if (wide) 3.4f else 4.4f) * grow
            val ry = (if (wide) 4.4f else 5.4f) * grow
            drawOval(AMBER_D, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))
            drawOval(AMBER, Offset(cx - rx * 0.9f, cy - ry * 0.7f), Size(rx * 1.8f, ry * 1.7f))
            var px = 0f
            var py = 0f
            if (phase == Phase.THINKING) { px = 1.2f * dir * -1f; py = -1.4f }
            if (mood == Mood.SHY) { px = 1.3f * dir; py = 1.2f }
            drawOval(HAIR, Offset(cx - 1.9f + px, cy - 3f + py), Size(3.8f, 6f))
            drawCircle(Color.White, 1.3f, Offset(cx - 1.4f, cy - 1.8f))
            drawCircle(Color.White, 0.7f, Offset(cx + 1.5f, cy + 1.8f))
            // lids for angry / sulky
            if (mood == Mood.ANGRY) {
                val lid = Path().apply {
                    moveTo(cx - dir * 5.8f, cy - 7f); lineTo(cx + dir * 5.8f, cy - 7f)
                    lineTo(cx + dir * 5.8f, cy - 1f); lineTo(cx - dir * 5.8f, cy - 4.6f); close()
                }
                drawPath(lid, SKIN)
            } else if (mood == Mood.SAD) {
                drawRect(SKIN, Offset(cx - 5.8f, cy - 7f), Size(11.6f, 4.6f))
            }
            val lash = Path().apply { moveTo(cx - 5f, cy - 1.5f); quadraticBezierTo(cx, cy - 7.5f, cx + 5f, cy - 1.5f) }
            drawPath(lash, HAIR, style = Stroke(1.4f, cap = StrokeCap.Round))
        }
    }
}

private fun DrawScope.drawBrows(mood: Mood) {
    fun brow(cx: Float, dir: Float, outer: Float, inner: Float) {
        val y = 47f
        drawLine(HAIR, Offset(cx - dir * 5f, y + outer), Offset(cx + dir * 5f, y + inner), 1.3f, StrokeCap.Round)
    }
    val (o, i) = when (mood) {
        Mood.ANGRY -> -1.8f to 1.8f
        Mood.SAD, Mood.SHY -> 1f to -1.6f
        Mood.SURPRISED -> -3.5f to -3.5f
        else -> 0f to 0f
    }
    if (mood == Mood.CONFUSED) {
        brow(41f, 1f, -3f, -3.5f); brow(59f, -1f, 0f, 0f)
    } else {
        brow(41f, 1f, o, i); brow(59f, -1f, o, i)
    }
}

private fun DrawScope.drawMouth(mood: Mood, phase: Phase, fl: Float) {
    val line = Stroke(1.5f, cap = StrokeCap.Round)
    when {
        phase == Phase.TALKING -> {
            val h = 1.6f + 3.4f * fl
            drawOval(MOUTH, Offset(46.8f, 66.2f - h / 2f + 0.8f), Size(6.4f, h))
        }
        mood == Mood.HAPPY -> {
            val p = Path().apply { moveTo(45f, 64.5f); quadraticBezierTo(50f, 73f, 55f, 64.5f); close() }
            drawPath(p, MOUTH)
        }
        mood == Mood.SURPRISED -> drawOval(MOUTH, Offset(47.6f, 63.5f), Size(4.8f, 6.4f))
        mood == Mood.SAD -> {
            val p = Path().apply { moveTo(46f, 68f); quadraticBezierTo(50f, 64.5f, 54f, 68f) }
            drawPath(p, HAIR, style = line)
        }
        mood == Mood.ANGRY -> drawLine(HAIR, Offset(46f, 67f), Offset(54f, 67.5f), 1.5f, StrokeCap.Round)
        mood == Mood.CONFUSED -> {
            val p = Path().apply { moveTo(45.5f, 67f); quadraticBezierTo(48f, 64.5f, 50f, 67f); quadraticBezierTo(52f, 69.5f, 54.5f, 66.5f) }
            drawPath(p, HAIR, style = line)
        }
        mood == Mood.SLEEPY -> drawOval(MOUTH, Offset(48.5f, 65.5f), Size(3f, 2.4f))
        mood == Mood.SHY -> {
            val p = Path().apply { moveTo(47.5f, 66f); quadraticBezierTo(50f, 67.8f, 52.5f, 66f) }
            drawPath(p, HAIR, style = line)
        }
        else -> {
            val p = Path().apply { moveTo(46f, 65.5f); quadraticBezierTo(50f, 69f, 54f, 65.5f) }
            drawPath(p, HAIR, style = line)
        }
    }
}

private fun DrawScope.drawAnger(cx: Float, cy: Float) {
    val red = Color(0xFFE53935)
    for (k in 0 until 4) {
        val ang = Math.toRadians(45.0 + 90.0 * k)
        val dx = Math.cos(ang).toFloat()
        val dy = Math.sin(ang).toFloat()
        drawLine(red, Offset(cx + dx * 1.6f, cy + dy * 1.6f), Offset(cx + dx * 4.6f, cy + dy * 4.6f), 1.4f, StrokeCap.Round)
    }
}
