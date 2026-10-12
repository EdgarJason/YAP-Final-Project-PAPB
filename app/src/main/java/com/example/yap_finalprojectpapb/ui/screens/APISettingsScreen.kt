package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun APISettingsScreen(
    onBackClick: () -> Unit = {},
    onApplyClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    var aiProvider by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var geminiModel by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                HeaderWithTitle(
                    title = "API Settings",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(32.dp))

                AppTextField(
                    label = "AI Provider",
                    value = aiProvider,
                    onValueChange = { aiProvider = it },
                    placeholder = "Placeholder"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = "API Key",
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    placeholder = "Placeholder"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = "Gemini Model",
                    value = geminiModel,
                    onValueChange = { geminiModel = it },
                    placeholder = "Placeholder"
                )
            }

            // Bottom Action Buttons (Cancel & Apply) dengan jarak 48px dari bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        text = "Cancel",
                        onClick = onCancelClick,
                        variant = AppButtonVariant.Secondary,
                        colorStyle = AppButtonColorStyle.Blue,
                        fullWidth = true
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        text = "Apply",
                        onClick = onApplyClick,
                        variant = AppButtonVariant.Primary,
                        colorStyle = AppButtonColorStyle.Blue,
                        fullWidth = true
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun APISettingsScreenPreview() {
    YAPFinalProjectPAPBTheme {
        APISettingsScreen()
    }
}
