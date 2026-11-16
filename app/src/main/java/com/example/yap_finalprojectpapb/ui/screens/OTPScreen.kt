package com.example.yap_finalprojectpapb.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
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
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel

@Composable
fun OTPScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onConfirmClick: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsState()
    OTPScreen(
        email = authState.pendingEmail,
        isLoading = authState.isLoading,
        cooldownSeconds = authState.otpCooldownSeconds,
        authErrorMessage = authState.errorMessage,
        onBackClick = {
            authViewModel.clearFeedback()
            onBackClick()
        },
        onClearError = authViewModel::clearFeedback,
        onResendClick = authViewModel::resendRegistrationOtp,
        onConfirmClick = { otpCode ->
            authViewModel.verifyRegistrationOtp(
                inputCode = otpCode,
                onSuccess = onConfirmClick
            )
        }
    )
}

@Composable
fun OTPScreen(
    email: String = "",
    isLoading: Boolean = false,
    cooldownSeconds: Int = 0,
    authErrorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onClearError: () -> Unit = {},
    onResendClick: () -> Unit = {},
    onConfirmClick: (String) -> Unit = {}
) {
    OTPContent(
        email = email,
        isLoading = isLoading,
        cooldownSeconds = cooldownSeconds,
        authErrorMessage = authErrorMessage,
        onBackClick = onBackClick,
        onClearError = onClearError,
        onResendClick = onResendClick,
        onConfirmClick = onConfirmClick
    )
}

@Composable
fun OTPContent(
    email: String = "",
    isLoading: Boolean = false,
    cooldownSeconds: Int = 0,
    authErrorMessage: String? = null,
    onBackClick: () -> Unit,
    onClearError: () -> Unit = {},
    onResendClick: () -> Unit = {},
    onConfirmClick: (String) -> Unit
) {
    var otpCode by rememberSaveable { mutableStateOf("") }
    var localErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val targetEmail = email.trim()
    val displayEmailText = if (targetEmail.isNotBlank()) targetEmail else "email pendaftaran kamu"
    val displayedError = authErrorMessage ?: localErrorMessage

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
                    title = "OTP Verification",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Kode OTP 6-Digit telah dikirimkan ke:\n$displayEmailText\nSilakan periksa inbox Gmail Anda.",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(32.dp))

                AppTextField(
                    label = "Enter 6-Digit OTP Code",
                    value = otpCode,
                    onValueChange = {
                        val digitsOnly = it.filter { ch -> ch.isDigit() }.take(6)
                        otpCode = digitsOnly
                        localErrorMessage = null
                        if (authErrorMessage != null) onClearError()
                    },
                    placeholder = "123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = displayedError != null,
                    errorMessage = displayedError
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (cooldownSeconds > 0) {
                        "Belum menerima email? Kirim ulang kode OTP (${cooldownSeconds}d)"
                    } else {
                        "Belum menerima email? Kirim ulang kode OTP"
                    },
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BlueDarker,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .align(Alignment.Start)
                        .clickable(enabled = !isLoading) {
                            if (cooldownSeconds > 0) {
                                Toast.makeText(
                                    context,
                                    "Tunggu ${cooldownSeconds} detik untuk kirim ulang",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onResendClick()
                                Toast.makeText(
                                    context,
                                    "Kode OTP dikirim ulang ke $displayEmailText",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = if (isLoading) "Memverifikasi..." else "Confirm & Continue",
                    onClick = {
                        val inputTrimmed = otpCode.trim()
                        if (inputTrimmed.length == 6) {
                            localErrorMessage = null
                            onConfirmClick(inputTrimmed)
                        } else {
                            localErrorMessage = "Masukkan 6 digit angka kode OTP verifikasi."
                        }
                    },
                    enabled = !isLoading && otpCode.isNotBlank(),
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Blue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OTPScreenPreview() {
    YAPFinalProjectPAPBTheme {
        OTPScreen(email = "edgar@gmail.com")
    }
}
