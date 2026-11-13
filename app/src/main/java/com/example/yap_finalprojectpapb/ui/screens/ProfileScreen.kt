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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.BlueLight
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onChangeNameClick: () -> Unit = {},
    onChangeEmailClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {}
) {
    val authState by authViewModel.uiState.collectAsState()
    val displayName = authState.userProfile?.fullName?.takeIf { it.isNotBlank() } ?: "Pengguna YAP"
    val displayEmail = authState.userProfile?.email?.takeIf { it.isNotBlank() } ?: "user@yap.app"
    val photoUrl = authState.userProfile?.photoUrl.orEmpty()
    val hasPassword = authState.userProfile?.hasPassword ?: true

    ViewProfileContent(
        name = displayName,
        email = displayEmail,
        photoUrl = photoUrl,
        hasPassword = hasPassword,
        onBackClick = onBackClick,
        onChangeNameClick = onChangeNameClick,
        onChangeEmailClick = onChangeEmailClick,
        onChangePasswordClick = onChangePasswordClick,
        onLogoutClick = {
            authViewModel.logout(onSuccess = onLogoutClick)
        },
        onDeleteAccountClick = onDeleteAccountClick
    )
}

@Composable
fun ViewProfileContent(
    name: String = "Willy Purba",
    email: String = "willypurba@gmail.com",
    photoUrl: String = "",
    hasPassword: Boolean = true,
    onBackClick: () -> Unit = {},
    onChangeNameClick: () -> Unit = {},
    onChangeEmailClick: () -> Unit = {},
    onChangePasswordClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {}
) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "Y" }

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
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueDarker,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar: Foto Profil Google asli jika ada, fallback ke Inisial
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(BlueLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl.isNotBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Foto Profil $name",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Text(
                            text = initials,
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BlueNormal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = name,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neutral900
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = email,
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
                        onValueChange = {},
                        placeholder = "Masukkan nama lengkap",
                        readOnly = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.clickable { onChangeEmailClick() }) {
                    AppTextField(
                        label = "Email",
                        value = email,
                        onValueChange = {},
                        placeholder = "Masukkan email",
                        readOnly = true
                    )
                }

                // Field Password hanya ditampilkan jika akun terdaftar dengan Email & Password
                if (hasPassword) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.clickable { onChangePasswordClick() }) {
                        AppTextField(
                            label = "Password",
                            value = "••••••••",
                            onValueChange = {},
                            placeholder = "Masukkan kata sandi",
                            readOnly = true
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Keterangan khusus pengguna Google OAuth
                    Surface(
                        color = BlueLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Metode Masuk: Akun Google",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueDarker
                                )
                                Text(
                                    text = "Autentikasi dikelola langsung oleh Google tanpa password.",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = Neutral500
                                )
                            }
                        }
                    }
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
        ViewProfileContent()
    }
}
