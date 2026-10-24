package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.BlueLightActive
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral100
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.RegisterUiState
import com.example.yap_finalprojectpapb.ui.viewmodels.RegisterViewModel

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = viewModel(),
    onRegisterSuccess: (String) -> Unit = {},
    onLogInClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    RegisterContent(
        uiState = uiState,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onToggleConfirmPassword = viewModel::toggleConfirmPasswordVisibility,
        onSubmit = {
            if (viewModel.validateAndSubmit()) {
                onRegisterSuccess(viewModel.uiState.value.combinedFullName)
            }
        },
        onLogInClick = onLogInClick
    )
}

@Composable
fun RegisterContent(
    uiState: RegisterUiState,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleConfirmPassword: () -> Unit,
    onSubmit: () -> Unit,
    onLogInClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Register",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = BlueDarker
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Lengkapi data akun untuk memulai eksplorasi YAP",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 14.sp,
                color = Neutral500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Baris Nama Depan & Nama Belakang (Dua input berbeda yang digabungkan saat submit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppTextField(
                    modifier = Modifier.weight(1f),
                    label = "Nama Depan",
                    value = uiState.firstName,
                    onValueChange = onFirstNameChange,
                    placeholder = "Edgar",
                    isError = uiState.firstNameError != null,
                    errorMessage = uiState.firstNameError
                )

                AppTextField(
                    modifier = Modifier.weight(1f),
                    label = "Nama Belakang",
                    value = uiState.lastName,
                    onValueChange = onLastNameChange,
                    placeholder = "Jason",
                    isError = uiState.lastNameError != null,
                    errorMessage = uiState.lastNameError
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Email
            AppTextField(
                label = "Email",
                value = uiState.email,
                onValueChange = onEmailChange,
                placeholder = "contoh@mail.com",
                isError = uiState.emailError != null,
                errorMessage = uiState.emailError
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            AppTextField(
                label = "Kata Sandi",
                value = uiState.password,
                onValueChange = onPasswordChange,
                placeholder = "Minimal 8 karakter",
                isError = uiState.passwordError != null,
                errorMessage = uiState.passwordError,
                visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onTogglePassword() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_closed
                            ),
                            contentDescription = "Toggle Visibility",
                            tint = if (uiState.isPasswordVisible) BlueNormal else Neutral500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password
            AppTextField(
                label = "Konfirmasi Kata Sandi",
                value = uiState.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                placeholder = "Ketik ulang kata sandi",
                isError = uiState.confirmPasswordError != null,
                errorMessage = uiState.confirmPasswordError,
                visualTransformation = if (uiState.isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { onToggleConfirmPassword() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isConfirmPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_closed
                            ),
                            contentDescription = "Toggle Visibility",
                            tint = if (uiState.isConfirmPasswordVisible) BlueNormal else Neutral500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            // Kartu Bukti Penggabungan Data (Hasil olah Nama Depan + Nama Belakang)
            AnimatedVisibility(
                visible = uiState.isSubmitSuccess && uiState.combinedFullName.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .background(
                            color = Neutral100,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = BlueLightActive,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "✓ Data Berhasil Diolah:",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueNormal
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Nama Lengkap: ${uiState.combinedFullName}",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BlueDarker
                    )
                    Text(
                        text = "Email Terdaftar: ${uiState.email}",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        color = Neutral500
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            AppButton(
                text = "Register",
                onClick = onSubmit,
                variant = AppButtonVariant.Primary,
                colorStyle = AppButtonColorStyle.Blue
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sudah punya akun? ",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    color = BlueDarker
                )
                Text(
                    text = "Log In",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onLogInClick() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    YAPFinalProjectPAPBTheme {
        RegisterContent(
            uiState = RegisterUiState(
                firstName = "Edgar",
                lastName = "Jason",
                email = "edgar@yap.app",
                combinedFullName = "Edgar Jason",
                isSubmitSuccess = true
            ),
            onFirstNameChange = {},
            onLastNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePassword = {},
            onToggleConfirmPassword = {},
            onSubmit = {},
            onLogInClick = {}
        )
    }
}
