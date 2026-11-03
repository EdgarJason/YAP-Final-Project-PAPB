package com.example.yap_finalprojectpapb.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.BlueLightActive
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral100
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

/**
 * Custom AppTextField Revisi Design System:
 * - Outline Box: Ketebalan tepat 1px (1.dp)
 * - Height Konsisten: Tinggi sama persis antara textfield biasa & textfield ber-icon
 * - Local Font: Plus Jakarta Sans dari res/font/plus_jakarta_sans.ttf
 * - Margin/Padding Samping Internal: 20px (20dp)
 * - Jarak dari Judul Label ke Input Textfield: 8px (8dp)
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Placeholder",
    label: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    readOnly: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> RedNormal
            isFocused -> BlueNormal
            else -> BlueLightActive // Passive outline: #B4D9FF
        },
        label = "textFieldBorderColor"
    )

    val textColor = if (isFocused || value.isNotEmpty()) {
        Neutral900 // Active state text color: Black
    } else {
        Neutral500 // Passive state text color: Gray
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        if (label != null) {
            Text(
                text = label,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 16.sp, // Base 16px
                fontWeight = FontWeight.Medium,
                color = BlueDarker, // Dark label #042E59
                modifier = Modifier.padding(bottom = 8.dp) // Jarak dari judul label ke textfield: 8px
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = TextStyle(
                fontFamily = PlusJakartaSansFamily,
                fontSize = 16.sp, // Inside font size: 16px
                fontWeight = FontWeight.Normal,
                color = textColor
            ),
            cursorBrush = SolidColor(BlueNormal),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp) // Height konsisten 52dp untuk semua textfield
                        .background(
                            color = if (enabled) NeutralWhite else Neutral100,
                            shape = RoundedCornerShape(16.dp) // Corner Radius 16px
                        )
                        .border(
                            width = 1.dp, // Ketebalan outline tepat 1px
                            color = borderColor,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp), // Padding samping 20px
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal,
                                color = Neutral500 // Passive placeholder: Gray
                            )
                        }
                        innerTextField()
                    }

                    if (trailingIcon != null) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(24.dp), // Fixed size 24dp agar tidak membuat textfield "gemuk"
                            contentAlignment = Alignment.Center
                        ) {
                            trailingIcon()
                        }
                    }
                }
            }
        )

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = RedNormal,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppTextFieldPreview() {
    YAPFinalProjectPAPBTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AppTextField(
                label = "Current Password",
                value = "",
                onValueChange = {},
                placeholder = "Placeholder"
            )
        }
    }
}
