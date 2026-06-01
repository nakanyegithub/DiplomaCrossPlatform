package ru.zona.app.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val phase: Float,
    val color: Color,
)

/**
 * Анимированный космический фон: радиальные туманности + мерцающие звёзды.
 * Кладётся под контент экрана ([content] рисуется поверх).
 */
@Composable
fun CosmicBackground(
    modifier: Modifier = Modifier,
    starCount: Int = 70,
    content: @Composable () -> Unit,
) {
    val stars =
        remember {
            val rnd = Random(42)
            List(starCount) {
                Star(
                    x = rnd.nextFloat(),
                    y = rnd.nextFloat(),
                    radius = 0.6f + rnd.nextFloat() * 1.8f,
                    phase = rnd.nextFloat() * 6.283f,
                    color =
                        when (rnd.nextInt(5)) {
                            0 -> CosmosCyan
                            1 -> CosmosMagenta
                            2 -> CosmosVioletBright
                            else -> StarWhite
                        },
                )
            }
        }

    val transition = rememberInfiniteTransition(label = "stars")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "twinkle",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Базовый вертикальный градиент пустоты.
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(SpaceVoid, SpaceDeep, Color(0xFF0E1330)),
                    ),
            )
            // Туманности — мягкие радиальные пятна.
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(CosmosViolet.copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.16f),
                        radius = size.maxDimension * 0.45f,
                    ),
                radius = size.maxDimension * 0.45f,
                center = Offset(size.width * 0.18f, size.height * 0.16f),
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(CosmosMagenta.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.78f),
                        radius = size.maxDimension * 0.5f,
                    ),
                radius = size.maxDimension * 0.5f,
                center = Offset(size.width * 0.85f, size.height * 0.78f),
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(CosmosCyan.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.6f, size.height * 0.1f),
                        radius = size.maxDimension * 0.35f,
                    ),
                radius = size.maxDimension * 0.35f,
                center = Offset(size.width * 0.6f, size.height * 0.1f),
            )
            // Звёзды с мерцанием.
            stars.forEach { star ->
                val alpha = 0.35f + 0.65f * ((sin(twinkle + star.phase) + 1f) / 2f)
                drawCircle(
                    color = star.color.copy(alpha = alpha),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height),
                )
            }
        }
        content()
    }
}
