package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun ChangePasswordScreen(
    onBackClick: () -> Unit = {},
    onSendClick: () -> Unit = {},
    onForgotPasswordClick: () -> Unit = {}
) {
    ChangePasswordContent(
        onBackClick = onBackClick,
        onSendClick = onSendClick,
        onForgotPasswordClick = onForgotPasswordClick
    )
}

@Composable
fun ChangePasswordContent(
    onBackClick: () -> Unit,
    onSendClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }

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
                    title = "Change Password",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter your new password.",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(32.dp))

                AppTextField(
                    label = "Current Password",
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    placeholder = "Placeholder"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = "New Password",
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "Placeholder"
                )

                Spacer(modifier = Modifier.height(16.dp))

                AppTextField(
                    label = "Confirm New Password",
                    value = confirmNewPassword,
                    onValueChange = { confirmNewPassword = it },
                    placeholder = "Placeholder"
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = "Send",
                    onClick = onSendClick,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Blue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChangePasswordScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ChangePasswordScreen()
    }
}
