package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.YapMascot
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun WelcomeScreen(
    onLogInClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {}
) {
    WelcomeContent(
        onLogInClick = onLogInClick,
        onRegisterClick = onRegisterClick
    )
}

@Composable
fun WelcomeContent(
    onLogInClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                YapMascot(
                    size = 140.dp,
                    showSpeechBubble = false
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome to YAP!",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 24.sp, // Judul 24px
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Just YAP to make your APP",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    color = BlueDarker
                )
            }

            // Tombol di bagian paling bawah dengan jarak 48px dari bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = "Register",
                    onClick = onRegisterClick,
                    variant = AppButtonVariant.Secondary,
                    colorStyle = AppButtonColorStyle.Blue
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppButton(
                    text = "Log In",
                    onClick = onLogInClick,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Blue
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    YAPFinalProjectPAPBTheme {
        WelcomeScreen()
    }
}
