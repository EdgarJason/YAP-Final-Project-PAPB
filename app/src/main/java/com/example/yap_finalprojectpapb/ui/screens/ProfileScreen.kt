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
import androidx.compose.ui.draw.clip
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
import com.example.yap_finalprojectpapb.ui.theme.Neutral200
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onChangeNameClick: () -> Unit = {},
    onChangeEmailClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {}
) {
    ViewProfileContent(
        onBackClick = onBackClick,
        onChangeNameClick = onChangeNameClick,
        onChangeEmailClick = onChangeEmailClick,
        onChangePasswordClick = onChangePasswordClick,
        onLogoutClick = onLogoutClick,
        onDeleteAccountClick = onDeleteAccountClick
    )
}

@Composable
fun ViewProfileContent(
    onBackClick: () -> Unit,
    onChangeNameClick: () -> Unit,
    onChangeEmailClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

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
                        text = "Profile",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = BlueDarker,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Neutral200)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Willy Purba",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neutral900
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "willypurba@gmail.com",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Neutral900
                )

                Spacer(modifier = Modifier.height(32.dp))

                Box(modifier = Modifier.clickable { onChangeNameClick() }) {
                    AppTextField(
                        label = "Nama",
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Placeholder"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.clickable { onChangeEmailClick() }) {
                    AppTextField(
                        label = "Email",
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Placeholder"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.clickable { onChangePasswordClick() }) {
                    AppTextField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Placeholder"
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp)
            ) {
                AppButton(
                    text = "Logout",
                    onClick = onLogoutClick,
                    variant = AppButtonVariant.Secondary,
                    colorStyle = AppButtonColorStyle.Blue
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppButton(
                    text = "Delete Account",
                    onClick = onDeleteAccountClick,
                    variant = AppButtonVariant.Primary,
                    colorStyle = AppButtonColorStyle.Red
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ProfileScreen()
    }
}
