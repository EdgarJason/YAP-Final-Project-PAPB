package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedLight
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel

@Composable
fun ChangeEmailScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onSendClick: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.clearFeedback()
    }

    ChangeEmailContent(
        isLoading = authState.isLoading,
        errorMessage = authState.errorMessage,
        onBackClick = onBackClick,
        onSendSubmit = { email ->
            authViewModel.changeEmail(
                newEmail = email,
                onSuccess = onSendClick
            )
        }
    )
}

@Composable
fun ChangeEmailContent(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onSendSubmit: (String) -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }

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
                    title = "Change Email",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter your new email. We will sent code to your email to verify.",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = RedLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = RedNormal,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                AppTextField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "new.email@example.com"
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = if (isLoading) "Mengirim..." else "Send",
                    onClick = { onSendSubmit(email) },
                    enabled = !isLoading,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Blue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChangeEmailScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ChangeEmailContent()
    }
}
