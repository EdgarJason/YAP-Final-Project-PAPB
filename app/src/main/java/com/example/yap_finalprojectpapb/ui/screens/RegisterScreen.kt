package com.example.yap_finalprojectpapb.ui.screens

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral200
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedLight
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel
import com.example.yap_finalprojectpapb.ui.viewmodels.RegisterUiState
import com.example.yap_finalprojectpapb.ui.viewmodels.RegisterViewModel

@Composable
fun RegisterScreen(
    authViewModel: AuthViewModel = viewModel(),
    registerViewModel: RegisterViewModel = viewModel(),
    onRegisterSuccess: () -> Unit = {},
    onLogInClick: () -> Unit = {}
) {
    val uiState by registerViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val combinedAuthError = authState.errorMessage ?: uiState.authError
    val isLoading = authState.isLoading || uiState.isLoading

    RegisterContent(
        uiState = uiState.copy(authError = combinedAuthError, isLoading = isLoading),
        onEmailChange = registerViewModel::onEmailChange,
        onPasswordChange = registerViewModel::onPasswordChange,
        onConfirmPasswordChange = registerViewModel::onConfirmPasswordChange,
        onTogglePassword = registerViewModel::togglePasswordVisibility,
        onToggleConfirmPassword = registerViewModel::toggleConfirmPasswordVisibility,
        onSubmit = {
            if (registerViewModel.validateAndSubmit()) {
                authViewModel.registerWithEmail(
                    email = uiState.email.trim(),
                    password = uiState.password,
                    onSuccess = onRegisterSuccess
                )
            }
        },
        onGoogleSignInClick = {
            authViewModel.signInWithGoogle(context = context, onSuccess = onRegisterSuccess)
        },
        onLogInClick = onLogInClick
    )
}

@Composable
fun RegisterContent(
    uiState: RegisterUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleConfirmPassword: () -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignInClick: () -> Unit = {},
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BlueDarker
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Lengkapi email & kata sandi untuk membuat akun YAP",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 14.sp,
                color = Neutral500,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (!uiState.authError.isNullOrBlank()) {
                Surface(
                    color = RedLight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = uiState.authError,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = RedNormal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

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

            Spacer(modifier = Modifier.height(28.dp))

            AppButton(
                text = if (uiState.isLoading) "Mendaftarkan..." else "Register",
                onClick = onSubmit,
                enabled = !uiState.isLoading,
                variant = AppButtonVariant.Primary,
                colorStyle = AppButtonColorStyle.Blue
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Neutral200)
                Text(
                    text = "atau",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 13.sp,
                    color = Neutral500
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Neutral200)
            }

            Spacer(modifier = Modifier.height(14.dp))

            AppButton(
                text = "Continue with Google",
                onClick = onGoogleSignInClick,
                enabled = !uiState.isLoading,
                variant = AppButtonVariant.Secondary,
                colorStyle = AppButtonColorStyle.Blue,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_google_logo),
                        contentDescription = "Google",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                email = "edgar@yap.app",
                isSubmitSuccess = true
            ),
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
