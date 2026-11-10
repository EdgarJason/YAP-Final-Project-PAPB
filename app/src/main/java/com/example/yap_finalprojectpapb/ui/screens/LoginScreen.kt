package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel = viewModel(),
    onLogInClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        authViewModel.clearFeedback()
    }

    LogInContent(
        isLoading = authState.isLoading,
        errorMessage = authState.errorMessage,
        onLogInSubmit = { email, password ->
            authViewModel.loginWithEmail(
                email = email,
                password = password,
                onSuccess = onLogInClick
            )
        },
        onGoogleSignInClick = {
            authViewModel.signInWithGoogle(
                context = context,
                onSuccess = onLogInClick
            )
        },
        onSignUpClick = onSignUpClick,
        onForgotPasswordClick = onForgotPasswordClick
    )
}

@Composable
fun LogInContent(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLogInSubmit: (String, String) -> Unit = { _, _ -> },
    onGoogleSignInClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

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
                text = "Log In",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BlueDarker
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                placeholder = "name@example.com"
            )

            Spacer(modifier = Modifier.height(16.dp))

            AppTextField(
                label = "Password",
                value = password,
                onValueChange = { password = it },
                placeholder = "Enter your password",
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { isPasswordVisible = !isPasswordVisible },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_closed
                            ),
                            contentDescription = "Toggle Visibility",
                            tint = if (isPasswordVisible) BlueNormal else Neutral500,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Forgot Password?",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlueDarker,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.Start)
                    .clickable { onForgotPasswordClick() }
            )

            Spacer(modifier = Modifier.height(32.dp))

            AppButton(
                text = if (isLoading) "Memproses..." else "Log In",
                onClick = { onLogInSubmit(email, password) },
                enabled = !isLoading,
                variant = AppButtonVariant.Primary,
                colorStyle = AppButtonColorStyle.Blue
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            AppButton(
                text = "Continue with Google",
                onClick = onGoogleSignInClick,
                enabled = !isLoading,
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

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dont have an account? ",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    color = BlueDarker
                )
                Text(
                    text = "Sign Up",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onSignUpClick() }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    YAPFinalProjectPAPBTheme {
        LogInContent()
    }
}
