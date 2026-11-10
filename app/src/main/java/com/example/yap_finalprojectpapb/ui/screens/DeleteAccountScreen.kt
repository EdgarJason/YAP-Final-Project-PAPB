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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedLight
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel

@Composable
fun DeleteAccountScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsState()
    val hasPassword = authState.userProfile?.hasPassword ?: true

    LaunchedEffect(Unit) {
        authViewModel.clearFeedback()
    }

    DeleteAccountContent(
        hasPassword = hasPassword,
        isLoading = authState.isLoading,
        errorMessage = authState.errorMessage,
        onBackClick = onBackClick,
        onDeleteSubmit = { password ->
            authViewModel.deleteAccount(
                password = password,
                onSuccess = onDeleteClick
            )
        },
        onForgotPasswordClick = onForgotPasswordClick
    )
}

@Composable
fun DeleteAccountContent(
    hasPassword: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onBackClick: () -> Unit = {},
    onDeleteSubmit: (String) -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = "Kembali",
                            tint = BlueDarker,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = "Delete Account",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueDarker,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (hasPassword) {
                        "Enter your password to confirm account deletion."
                    } else {
                        "Konfirmasi penghapusan akun YAP. Seluruh data profil dan riwayat percakapan Anda akan dihapus permanen."
                    },
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

                if (hasPassword) {
                    AppTextField(
                        label = "Your Password",
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Enter your password to confirm",
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

                    Spacer(modifier = Modifier.height(32.dp))

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
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = if (isLoading) "Menghapus..." else "Delete My Account",
                    onClick = { onDeleteSubmit(if (hasPassword) password else "") },
                    enabled = !isLoading,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Red
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeleteAccountScreenPreview() {
    YAPFinalProjectPAPBTheme {
        DeleteAccountContent()
    }
}
