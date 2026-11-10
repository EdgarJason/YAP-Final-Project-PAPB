package com.example.yap_finalprojectpapb.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
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
    Thinking,
    Wink,
    Surprised,
    Sad
}

/**
 * YapMascot - Maskot Resmi YAP Mobile AI Studio.
 * Menampilkan maskot biru ceria dengan berbagai pose ekspresi mata & wajah
 * (Normal, Happy, Thinking, Wink, Surprised, Sad) tanpa distorsi rasio,
 * tanpa emoji, dan tanpa clipping pada telinga/appbar.
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

    // Pose ekspresi yang sedang aktif saat diketuk
    var activeReactionMood by remember { mutableStateOf(MascotMood.Wink) }

    // Animasi lompat halus & rotasi kepala tanpa penyot
    val jumpAnim = remember { Animatable(0f) }
    val tiltAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }

    val haptics = LocalHapticFeedback.current

    // Kalimat dialog sopan & natural tanpa emoji
    val phrases = listOf(
        "Hai! Mau bikin aplikasi web apa hari ini?",
        "Wah, ide aplikasimu pasti menarik untuk dibuat!",
        "Hmm, Muse siap merancang arsitektur kodenya.",
        "YAP siap menemani kamu sampai APK selesai!",
        "Jangan khawatir, semua rancangan koding pasti bisa diwujudkan!"
    )

    // Reaksi saat maskot diketuk
    LaunchedEffect(interactionCount) {
        if (interactionCount == 0) return@LaunchedEffect
        isReacting = true

        // Berganti pose mata/ekspresi secara dinamis tiap kali diketuk
        activeReactionMood = when (interactionCount % 5) {
            1 -> MascotMood.Wink        // Pose mengedipkan mata
            2 -> MascotMood.Surprised   // Pose takjub / kagum
            3 -> MascotMood.Thinking    // Pose melirik berpikir
            4 -> MascotMood.Happy       // Pose tersenyum ceria
            else -> MascotMood.Sad      // Pose terharu / empati
        }

        // Lompat halus tanpa distorsi (proporsi 1:1 tetap terjaga)
        jumpAnim.snapTo(0f)
        scaleAnim.snapTo(1f)
        tiltAnim.snapTo(if (interactionCount % 2 == 0) -3.5f else 3.5f)

        // Naik lembut
        jumpAnim.animateTo(1f, tween(140))
        scaleAnim.animateTo(1.05f, tween(140))

        // Mendarat dengan pegas alami
        jumpAnim.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        scaleAnim.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        tiltAnim.animateTo(0f, tween(200))

        delay(2200)
        isReacting = false
    }

    // Tentukan drawable sesuai mood
    val currentMood = when {
        isPetting -> MascotMood.Happy
        isReacting -> activeReactionMood
        else -> mood
    }

    val drawableRes = when (currentMood) {
        MascotMood.Normal -> R.drawable.mascot_normal
        MascotMood.Happy -> R.drawable.mascot_happy
        MascotMood.Thinking -> R.drawable.mascot_thinking
        MascotMood.Wink -> R.drawable.mascot_wink
        MascotMood.Surprised -> R.drawable.mascot_surprised
        MascotMood.Sad -> R.drawable.mascot_sad
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Container maskot tanpa .clip() agar telinga & bintang tidak terpotong di AppBar maupun di layar utama
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                .size(size)
                .semantics {
                    contentDescription = "Maskot YAP"
                    stateDescription = when (currentMood) {
                        MascotMood.Happy -> "Senang"
                        MascotMood.Thinking -> "Sedang berpikir"
                        MascotMood.Wink -> "Mengedipkan mata"
                        MascotMood.Surprised -> "Takjub"
                        MascotMood.Sad -> "Terharu"
                        MascotMood.Normal -> "Siap membantu"
                    }
                }
                .combinedClickable(
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // Bersih tanpa ripple kotak yang menutupi bentuk maskot
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
                    // Lompatan vertikal proporsional tanpa squash/stretch distorsi
                    val jumpPx = -10.dp.toPx() * jumpAnim.value
                    translationY = jumpPx
                    rotationZ = tiltAnim.value
                    scaleX = scaleAnim.value
                    scaleY = scaleAnim.value
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showSpeechBubble) {
            Spacer(modifier = Modifier.height(8.dp))
            val speechText = customSpeechText ?: if (isReacting) {
                if (isPetting) "Terima kasih elusannya, YAP makin semangat membantu!"
                else phrases[(interactionCount - 1) % phrases.size]
            } else {
                "Ketuk untuk menyapa atau tahan untuk mengelus"
            }

            Text(
                text = speechText,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                fontWeight = if (isReacting) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isReacting || customSpeechText != null) BlueDarker else Neutral500,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 36.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}
