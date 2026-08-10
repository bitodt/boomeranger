package com.boomeranger.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boomeranger.app.ui.theme.Ink
import com.boomeranger.app.ui.theme.Leaf
import com.boomeranger.app.ui.theme.Mist
import com.boomeranger.app.ui.theme.Moss
import com.boomeranger.app.ui.theme.Sand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Branded launch splash shown after the system SplashScreen API hands off.
 *
 * Motions:
 * 1) Mark scale + fade in
 * 2) Breathing green arc on the boomerang path
 * 3) Title rise/fade, then whole-screen dissolve into the app
 */
@Composable
fun BoomerangerSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val markScale = remember { Animatable(0.72f) }
    val markAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(22f) }
    val screenAlpha = remember { Animatable(1f) }

    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val arcSweep by infinite.animateFloat(
        initialValue = 20f,
        targetValue = 58f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arcSweep",
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.16f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
    )

    LaunchedEffect(Unit) {
        launch {
            markAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        }
        launch {
            markScale.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        }
        delay(280)
        launch {
            titleAlpha.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(550, easing = FastOutSlowInEasing))
        }
        delay(1500)
        screenAlpha.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(screenAlpha.value)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Ink,
                        Moss.copy(alpha = 0.95f),
                        Ink,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Leaf.copy(alpha = glowPulse), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.minDimension * 0.42f,
                ),
                radius = size.minDimension * 0.42f,
                center = Offset(size.width * 0.5f, size.height * 0.42f),
            )
            drawCircle(
                color = Sand.copy(alpha = 0.06f),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.78f, size.height * 0.72f),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.offset(y = titleOffsetY.value.dp),
        ) {
            BoomerangMark(
                sweepDegrees = arcSweep,
                modifier = Modifier
                    .size(132.dp)
                    .scale(markScale.value)
                    .alpha(markAlpha.value),
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "Boomeranger",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = Mist,
                modifier = Modifier.alpha(titleAlpha.value),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Forward. Back. Again.",
                style = MaterialTheme.typography.bodyLarge,
                color = Sand.copy(alpha = 0.9f),
                modifier = Modifier.alpha(titleAlpha.value * 0.95f),
            )
        }
    }
}

@Composable
private fun BoomerangMark(
    sweepDegrees: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.085f
        val inset = stroke * 1.6f
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val topLeft = Offset(inset, inset)

        drawArc(
            color = Mist.copy(alpha = 0.92f),
            startAngle = 210f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = Leaf,
            startAngle = 40f,
            sweepAngle = sweepDegrees,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * 0.92f, cap = StrokeCap.Round),
        )
        rotate(degrees = -18f) {
            drawCircle(
                color = Sand.copy(alpha = 0.85f),
                radius = stroke * 0.55f,
                center = Offset(size.width * 0.5f, size.height * 0.52f),
            )
        }
    }
}
