package com.example.yap_finalprojectpapb.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import kotlinx.coroutines.delay

enum class MascotMood {
    Normal,
    Happy,
    Thinking
}

/**
 * YapMascot - Maskot Resmi YAP Mobile AI Studio (Desain Vektor Asli Git)
 * Menampilkan maskot berbentuk pir biru ceria dengan ekspresi dinamis,
 * interaksi tap/pet haptik, dan balon dialog ramah.
 */
@Composable
fun YapMascot(
    modifier: Modifier = Modifier,
    mood: MascotMood = MascotMood.Normal,
    size: Dp = 132.dp,
    showSpeechBubble: Boolean = true,
    customSpeechText: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    var interactionCount by remember { mutableIntStateOf(0) }
    var isPetting by remember { mutableStateOf(false) }
    var isReacting by remember { mutableStateOf(false) }
    val bounceAnim = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    val defaultPhrases = listOf(
        "Hai! Mau bikin aplikasi web apa hari ini?",
        "Tulis idemu, biar YAP yang racik kodenya!",
        "Mulai dari ide kecil dulu juga gak apa-apa kok."
    )

    LaunchedEffect(interactionCount) {
        if (interactionCount == 0) return@LaunchedEffect
        isReacting = true
        bounceAnim.snapTo(0f)
        bounceAnim.animateTo(1f, tween(160))
        bounceAnim.animateTo(0f, tween(300))
        delay(2400)
        isReacting = false
    }

    val activeMood = when {
        isPetting || (isReacting && interactionCount % 2 == 1) -> MascotMood.Happy
        else -> mood
    }

    val drawableRes = when (activeMood) {
        MascotMood.Happy -> R.drawable.mascot_happy
        MascotMood.Thinking -> R.drawable.mascot_thinking
        MascotMood.Normal -> R.drawable.mascot_normal
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .size(size)
                .clip(RoundedCornerShape(24.dp))
                .semantics {
                    contentDescription = "Maskot YAP"
                    stateDescription = when (activeMood) {
                        MascotMood.Happy -> "Senang"
                        MascotMood.Thinking -> "Sedang berpikir"
                        MascotMood.Normal -> "Siap membantu"
                    }
                }
                .combinedClickable(
                    role = Role.Button,
                    onClickLabel = "Sapa YAP",
                    onLongClickLabel = "Elus YAP",
                    onClick = {
                        isPetting = false
                        interactionCount++
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick?.invoke()
                    },
                    onLongClick = {
                        isPetting = true
                        interactionCount++
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick?.invoke()
                    }
                )
                .graphicsLayer {
                    translationY = -10.dp.toPx() * bounceAnim.value
                    rotationZ = (if (isPetting) -6f else 4f) * bounceAnim.value
                    scaleX = 1f + 0.05f * bounceAnim.value
                    scaleY = 1f - 0.03f * bounceAnim.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        }

        if (showSpeechBubble) {
            Spacer(modifier = Modifier.height(8.dp))
            val speechText = customSpeechText ?: if (isReacting) {
                if (isPetting) "Makasih elusannya! YAP makin semangat nemenin koding!"
                else defaultPhrases[(interactionCount - 1) % defaultPhrases.size]
            } else {
                "Ketuk untuk menyapa · tahan untuk mengelus"
            }

            Text(
                text = speechText,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                color = if (customSpeechText != null) BlueDarker else Neutral500,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 36.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
