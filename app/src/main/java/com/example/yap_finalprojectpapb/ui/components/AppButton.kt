package com.example.yap_finalprojectpapb.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.theme.BlueDark
import com.example.yap_finalprojectpapb.ui.theme.BlueLight
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral400
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedDark
import com.example.yap_finalprojectpapb.ui.theme.RedLight
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

enum class AppButtonVariant {
    Primary,
    Secondary
}

enum class AppButtonColorStyle {
    Blue,
    Red
}

/**
 * Custom AppButton adhering to Design System specifications:
 * - Corner Radius: 16px (16dp)
 * - Padding: Samping 24px (24dp), Atas 16px (16dp)
 * - Typography: Plus Jakarta Sans 16px (16sp)
 * - Primary Blue: #0C83FF (BlueNormal), Pressed: #0962BF (BlueDark)
 * - Primary Red: #EF5350 (RedNormal), Pressed: #B33E3C (RedDark)
 * - Secondary: Outline with same design tokens and pressed states
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    colorStyle: AppButtonColorStyle = AppButtonColorStyle.Blue,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val cornerRadius = 16.dp
    val shape = RoundedCornerShape(cornerRadius)

    val effectiveModifier = if (fullWidth) modifier.fillMaxWidth() else modifier

    when (variant) {
        AppButtonVariant.Primary -> {
            val backgroundColor by animateColorAsState(
                targetValue = when {
                    !enabled -> Neutral400
                    isPressed && colorStyle == AppButtonColorStyle.Blue -> BlueDark // #0962BF
                    isPressed && colorStyle == AppButtonColorStyle.Red -> RedDark // #B33E3C
                    colorStyle == AppButtonColorStyle.Blue -> BlueNormal // #0C83FF
                    else -> RedNormal // #EF5350
                },
                label = "primaryButtonBgAnimation"
            )

            Button(
                onClick = onClick,
                modifier = effectiveModifier,
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = backgroundColor,
                    contentColor = NeutralWhite,
                    disabledContainerColor = Neutral400,
                    disabledContentColor = NeutralWhite
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ButtonContent(text = text, leadingIcon = leadingIcon, textColor = NeutralWhite)
            }
        }

        AppButtonVariant.Secondary -> {
            val strokeColor by animateColorAsState(
                targetValue = when {
                    !enabled -> Neutral400
                    isPressed && colorStyle == AppButtonColorStyle.Blue -> BlueDark
                    isPressed && colorStyle == AppButtonColorStyle.Red -> RedDark
                    colorStyle == AppButtonColorStyle.Blue -> BlueNormal
                    else -> RedNormal
                },
                label = "secondaryButtonStrokeAnimation"
            )

            val backgroundColor by animateColorAsState(
                targetValue = when {
                    !enabled -> Color.Transparent
                    isPressed && colorStyle == AppButtonColorStyle.Blue -> BlueLight
                    isPressed && colorStyle == AppButtonColorStyle.Red -> RedLight
                    else -> Color.Transparent
                },
                label = "secondaryButtonBgAnimation"
            )

            val textColor = when {
                !enabled -> Neutral500
                isPressed && colorStyle == AppButtonColorStyle.Blue -> BlueDark
                isPressed && colorStyle == AppButtonColorStyle.Red -> RedDark
                colorStyle == AppButtonColorStyle.Blue -> BlueNormal
                else -> RedNormal
            }

            OutlinedButton(
                onClick = onClick,
                modifier = effectiveModifier,
                enabled = enabled,
                shape = shape,
                interactionSource = interactionSource,
                border = BorderStroke(1.5.dp, strokeColor),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = backgroundColor,
                    contentColor = textColor,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Neutral500
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ButtonContent(text = text, leadingIcon = leadingIcon, textColor = textColor)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: (@Composable () -> Unit)?,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AppButtonPreview() {
    YAPFinalProjectPAPBTheme {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton(
                text = "Primary Blue",
                onClick = {},
                fullWidth = false,
                variant = AppButtonVariant.Primary,
                colorStyle = AppButtonColorStyle.Blue
            )
            AppButton(
                text = "Secondary Red",
                onClick = {},
                fullWidth = false,
                variant = AppButtonVariant.Secondary,
                colorStyle = AppButtonColorStyle.Red
            )
        }
    }
}
