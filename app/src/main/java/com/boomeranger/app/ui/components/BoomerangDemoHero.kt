package com.boomeranger.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boomeranger.app.ui.theme.Ink
import com.boomeranger.app.ui.theme.Leaf
import com.boomeranger.app.ui.theme.Mist
import com.boomeranger.app.ui.theme.Moss
import com.boomeranger.app.ui.theme.Sand
import kotlin.math.abs
import kotlin.math.sin

/**
 * Start-screen visual that demos the boomerang idea: a playhead runs forward, then back,
 * looping forever — plus a short catchy line under the brand.
 */
@Composable
fun BoomerangDemoHero(
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "boomerangDemo")
    // 0 → 1 over the cycle; we map to a triangle wave for forward/back motion.
    val cycle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cycle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Triangle wave: 0→1→0 across the cycle (forward then reverse).
    val playhead = if (cycle < 0.5f) cycle * 2f else (1f - cycle) * 2f
    val goingForward = cycle < 0.5f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Flip it. Loop it. Own the scroll.",
            style = MaterialTheme.typography.headlineMedium,
            color = Mist,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Turn a tiny clip into a seamless forward–back loop — video or GIF.",
            style = MaterialTheme.typography.bodyLarge,
            color = Mist.copy(alpha = 0.78f),
            textAlign = TextAlign.Center,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val trackLeft = size.width * 0.08f
                val trackRight = size.width * 0.92f
                val trackWidth = trackRight - trackLeft
                val trackY = size.height * 0.58f
                val frameHeight = size.height * 0.42f
                val frameTop = trackY - frameHeight

                // Soft stage
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Moss.copy(alpha = 0.9f), Ink.copy(alpha = 0.2f)),
                    ),
                    topLeft = Offset(trackLeft - 8f, frameTop - 12f),
                    size = Size(trackWidth + 16f, frameHeight + 36f),
                    cornerRadius = CornerRadius(18f, 18f),
                )

                // Mini “frames” that light up as the playhead passes
                val frameCount = 5
                val gap = 10f
                val frameW = (trackWidth - gap * (frameCount - 1)) / frameCount
                for (i in 0 until frameCount) {
                    val x = trackLeft + i * (frameW + gap)
                    val centerT = (i + 0.5f) / frameCount
                    val lit = 1f - (abs(playhead - centerT) * 2.4f).coerceIn(0f, 1f)
                    drawRoundRect(
                        color = Mist.copy(alpha = 0.12f + lit * 0.55f),
                        topLeft = Offset(x, frameTop),
                        size = Size(frameW, frameHeight),
                        cornerRadius = CornerRadius(10f, 10f),
                    )
                    // Tiny “content” bars inside each frame
                    val barAlpha = 0.15f + lit * 0.45f
                    drawRoundRect(
                        color = Leaf.copy(alpha = barAlpha),
                        topLeft = Offset(x + frameW * 0.18f, frameTop + frameHeight * 0.28f),
                        size = Size(frameW * 0.64f, frameHeight * 0.16f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                    drawRoundRect(
                        color = Sand.copy(alpha = barAlpha * 0.85f),
                        topLeft = Offset(x + frameW * 0.18f, frameTop + frameHeight * 0.55f),
                        size = Size(frameW * 0.42f, frameHeight * 0.12f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }

                // Timeline
                drawLine(
                    color = Mist.copy(alpha = 0.25f),
                    start = Offset(trackLeft, trackY),
                    end = Offset(trackRight, trackY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )

                // Direction chevrons
                val chevronY = trackY + 22f
                val chevronColor = if (goingForward) Leaf.copy(alpha = pulse) else Sand.copy(alpha = pulse)
                val midX = size.width / 2f
                if (goingForward) {
                    drawLine(
                        color = chevronColor,
                        start = Offset(midX - 18f, chevronY - 8f),
                        end = Offset(midX, chevronY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = chevronColor,
                        start = Offset(midX - 18f, chevronY + 8f),
                        end = Offset(midX, chevronY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawLine(
                        color = chevronColor,
                        start = Offset(midX + 18f, chevronY - 8f),
                        end = Offset(midX, chevronY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = chevronColor,
                        start = Offset(midX + 18f, chevronY + 8f),
                        end = Offset(midX, chevronY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round,
                    )
                }

                // Playhead
                val headX = trackLeft + trackWidth * playhead
                val bob = sin(cycle * Math.PI * 2).toFloat() * 3f
                drawCircle(
                    color = Leaf.copy(alpha = 0.25f),
                    radius = 16f * pulse,
                    center = Offset(headX, trackY + bob),
                )
                drawCircle(
                    color = Leaf,
                    radius = 8f,
                    center = Offset(headX, trackY + bob),
                )
                drawCircle(
                    color = Mist,
                    radius = 3.2f,
                    center = Offset(headX, trackY + bob),
                )

                // Loop arc hint above
                drawArc(
                    color = Sand.copy(alpha = 0.35f + pulse * 0.25f),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(trackLeft + trackWidth * 0.18f, 4f),
                    size = Size(trackWidth * 0.64f, 36f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round),
                )
            }
        }

        Text(
            text = if (goingForward) "Playing forward…" else "Playing backward…",
            style = MaterialTheme.typography.labelLarge,
            color = if (goingForward) Leaf else Sand,
        )
    }
}
