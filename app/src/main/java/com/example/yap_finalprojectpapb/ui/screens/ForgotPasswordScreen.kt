package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun ForgotPasswordScreen(
    onBackClick: () -> Unit = {},
    onSendClick: () -> Unit = {}
) {
    ForgotPasswordContent(
        onBackClick = onBackClick,
        onSendClick = onSendClick
    )
}

@Composable
fun ForgotPasswordContent(
    onBackClick: () -> Unit,
    onSendClick: () -> Unit
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
                    title = "Forgot Password?",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Enter your email associated w/ your account to change your password.",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(32.dp))

                AppTextField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Placeholder"
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

@Composable
fun HeaderWithTitle(
    title: String,
    onBackClick: () -> Unit
) {
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
            text = title,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 24.sp, // Judul atas 24px
            fontWeight = FontWeight.Bold,
            color = BlueDarker,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ForgotPasswordScreen()
    }
}
