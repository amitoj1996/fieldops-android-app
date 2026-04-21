package com.fieldops.app.ui.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Spring animation specification for smooth, natural motion
 */
fun <T> springSpec(
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMedium
): SpringSpec<T> = spring(
    dampingRatio = dampingRatio,
    stiffness = stiffness
)

/**
 * Smooth spring animation for values
 */
@Composable
fun animateFloatAsSpring(
    targetValue: Float,
    dampingRatio: Float = Spring.DampingRatioMediumBouncy,
    stiffness: Float = Spring.StiffnessMedium,
    label: String = ""
): State<Float> {
    return animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness
        ),
        label = label
    )
}

/**
 * Scale on press micro-interaction
 * Scales to 0.95 when pressed, springs back when released
 */
fun Modifier.scaleOnPress(
    pressedScale: Float = 0.95f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsSpring(
        targetValue = if (isPressed) pressedScale else 1f,
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

/**
 * Shimmer loading effect for skeleton screens
 */
@Composable
fun rememberShimmerAnimation(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
}

/**
 * Staggered animation for list items
 * Each item animates in with a delay based on its index
 */
@Composable
fun rememberStaggeredAnimation(
    index: Int,
    delayPerItem: Int = 50
): State<Float> {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(index) {
        kotlinx.coroutines.delay((index * delayPerItem).toLong())
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    
    return alpha.asState()
}

/**
 * Bounce animation for emphasis
 */
@Composable
fun rememberBounceAnimation(trigger: Boolean): State<Float> {
    val scale = remember { Animatable(1f) }
    
    LaunchedEffect(trigger) {
        if (trigger) {
            scale.animateTo(
                targetValue = 1.2f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
    
    return scale.asState()
}
