package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.example.yap_finalprojectpapb.ui.theme.BlueDark
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.BlueLight
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral400
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedLight
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel

private enum class ProfileEditField {
    NAME,
    EMAIL,
    PASSWORD
}

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

    LaunchedEffect(Unit) {
        authViewModel.refreshProfile()
    }

    val displayName = authState.userProfile?.fullName?.takeIf { it.isNotBlank() } ?: "Pengguna YAP"
    val displayEmail = authState.userProfile?.email?.takeIf { it.isNotBlank() } ?: "user@yap.app"
    val photoUrl = authState.userProfile?.photoUrl.orEmpty()
    val hasPassword = authState.userProfile?.hasPassword ?: true

    ViewProfileContent(
        name = displayName,
        email = displayEmail,
        photoUrl = photoUrl,
        hasPassword = hasPassword,
        isLoading = authState.isLoading,
        errorMessage = authState.errorMessage,
        successMessage = authState.successMessage,
        onClearFeedback = authViewModel::clearFeedback,
        onBackClick = {
            authViewModel.clearFeedback()
            onBackClick()
        },
        onSaveName = { newName, onDone ->
            authViewModel.updateName(newName = newName, onSuccess = onDone)
        },
        onSaveEmail = { newEmail, currentPassword, onDone ->
            authViewModel.changeEmail(
                newEmail = newEmail,
                currentPassword = currentPassword.takeIf { it.isNotBlank() },
                onSuccess = onDone
            )
        },
        onSavePassword = { currentPassword, newPassword, confirmPassword, onDone ->
            authViewModel.changePassword(
                currentPassword = currentPassword,
                newPassword = newPassword,
                confirmNewPassword = confirmPassword,
                onSuccess = onDone
            )
        },
        onLogoutClick = {
            authViewModel.logout(onSuccess = onLogoutClick)
        },
        onDeleteAccountClick = {
            authViewModel.clearFeedback()
            onDeleteAccountClick()
        }
    )
}

@Composable
fun ViewProfileContent(
    name: String = "Willy Purba",
    email: String = "willypurba@gmail.com",
    photoUrl: String = "",
    hasPassword: Boolean = true,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    successMessage: String? = null,
    onClearFeedback: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onSaveName: (String, () -> Unit) -> Unit = { _, _ -> },
    onSaveEmail: (String, String, () -> Unit) -> Unit = { _, _, _ -> },
    onSavePassword: (String, String, String, () -> Unit) -> Unit = { _, _, _, _ -> },
    onLogoutClick: () -> Unit = {},
    onDeleteAccountClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    var editingField by rememberSaveable { mutableStateOf<ProfileEditField?>(null) }

    var draftName by rememberSaveable { mutableStateOf(name) }
    var draftEmail by rememberSaveable { mutableStateOf("") }
    var emailConfirmPassword by rememberSaveable { mutableStateOf("") }
    var showEmailConfirmPassword by rememberSaveable { mutableStateOf(false) }

    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmNewPassword by rememberSaveable { mutableStateOf("") }
    var showCurrentPass by rememberSaveable { mutableStateOf(false) }
    var showNewPass by rememberSaveable { mutableStateOf(false) }
    var showConfirmPass by rememberSaveable { mutableStateOf(false) }

    val nameFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

    LaunchedEffect(editingField) {
        when (editingField) {
            ProfileEditField.NAME -> nameFocus.requestFocus()
            ProfileEditField.EMAIL -> emailFocus.requestFocus()
            ProfileEditField.PASSWORD -> passwordFocus.requestFocus()
            null -> Unit
        }
    }

    val closeEditor = {
        focusManager.clearFocus()
        editingField = null
        draftEmail = ""
        emailConfirmPassword = ""
        currentPassword = ""
        newPassword = ""
        confirmNewPassword = ""
    }

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
                .padding(horizontal = 24.dp)
                .imePadding(),
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
                } else if (!successMessage.isNullOrBlank()) {
                    Surface(
                        color = BlueLight,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = successMessage,
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = BlueDarker,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // === FIELD 1: NAMA (Inline Editable) ===
                val isEditingName = editingField == ProfileEditField.NAME
                ProfileInlineFieldRow(
                    label = "Nama",
                    value = if (isEditingName) draftName else name,
                    placeholder = "Masukkan nama lengkap",
                    editable = true,
                    isEditing = isEditingName,
                    focusRequester = nameFocus,
                    onValueChange = {
                        draftName = it
                        if (errorMessage != null || successMessage != null) onClearFeedback()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!isLoading) {
                                onSaveName(draftName) { closeEditor() }
                            }
                        }
                    ),
                    onStartEdit = {
                        onClearFeedback()
                        draftName = name
                        editingField = ProfileEditField.NAME
                    },
                    onCancelEdit = {
                        onClearFeedback()
                        closeEditor()
                    }
                )
                AnimatedVisibility(
                    visible = isEditingName,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    InlineSaveCancelRow(
                        saveText = if (isLoading) "Menyimpan..." else "Simpan",
                        isLoading = isLoading,
                        saveEnabled = draftName.trim().length >= 2,
                        onCancel = {
                            onClearFeedback()
                            closeEditor()
                        },
                        onSave = {
                            onSaveName(draftName) { closeEditor() }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // === FIELD 2: EMAIL (Inline Editable for Email/Password accounts) ===
                val isEditingEmail = editingField == ProfileEditField.EMAIL
                ProfileInlineFieldRow(
                    label = "Email",
                    value = if (isEditingEmail) draftEmail else email,
                    placeholder = if (isEditingEmail) "email.baru@example.com" else "Masukkan email",
                    editable = hasPassword,
                    isEditing = isEditingEmail,
                    focusRequester = emailFocus,
                    onValueChange = {
                        draftEmail = it
                        if (errorMessage != null || successMessage != null) onClearFeedback()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    onStartEdit = {
                        onClearFeedback()
                        draftEmail = ""
                        emailConfirmPassword = ""
                        editingField = ProfileEditField.EMAIL
                    },
                    onCancelEdit = {
                        onClearFeedback()
                        closeEditor()
                    }
                )
                AnimatedVisibility(
                    visible = isEditingEmail,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        PasswordInlineField(
                            label = "Password Saat Ini (untuk keamanan Firebase)",
                            value = emailConfirmPassword,
                            placeholder = "Masukkan password saat ini",
                            visible = showEmailConfirmPassword,
                            onToggleVisibility = { showEmailConfirmPassword = !showEmailConfirmPassword },
                            onValueChange = {
                                emailConfirmPassword = it
                                if (errorMessage != null || successMessage != null) onClearFeedback()
                            },
                            imeAction = ImeAction.Done,
                            onDone = {
                                if (!isLoading) {
                                    onSaveEmail(draftEmail, emailConfirmPassword) { closeEditor() }
                                }
                            }
                        )
                        InlineSaveCancelRow(
                            saveText = if (isLoading) "Mengirim..." else "Kirim Verifikasi",
                            isLoading = isLoading,
                            saveEnabled = draftEmail.trim().contains("@"),
                            onCancel = {
                                onClearFeedback()
                                closeEditor()
                            },
                            onSave = {
                                onSaveEmail(draftEmail, emailConfirmPassword) { closeEditor() }
                            }
                        )
                    }
                }

                // === FIELD 3: PASSWORD (Inline Editable) ===
                if (hasPassword) {
                    Spacer(modifier = Modifier.height(16.dp))

                    val isEditingPassword = editingField == ProfileEditField.PASSWORD
                    if (!isEditingPassword) {
                        ProfileInlineFieldRow(
                            label = "Password",
                            value = "••••••••",
                            placeholder = "Masukkan kata sandi",
                            editable = true,
                            isEditing = false,
                            onValueChange = {},
                            onStartEdit = {
                                onClearFeedback()
                                currentPassword = ""
                                newPassword = ""
                                confirmNewPassword = ""
                                editingField = ProfileEditField.PASSWORD
                            },
                            onCancelEdit = {}
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Ubah Password",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BlueDarker
                                )
                                IconButton(
                                    onClick = {
                                        onClearFeedback()
                                        closeEditor()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_x),
                                        contentDescription = "Batal ubah password",
                                        tint = Neutral500,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            PasswordInlineField(
                                label = "Password Saat Ini",
                                value = currentPassword,
                                placeholder = "Masukkan password saat ini",
                                visible = showCurrentPass,
                                focusRequester = passwordFocus,
                                onToggleVisibility = { showCurrentPass = !showCurrentPass },
                                onValueChange = {
                                    currentPassword = it
                                    if (errorMessage != null || successMessage != null) onClearFeedback()
                                },
                                imeAction = ImeAction.Next
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            PasswordInlineField(
                                label = "Password Baru (min. 8 karakter)",
                                value = newPassword,
                                placeholder = "Masukkan password baru",
                                visible = showNewPass,
                                onToggleVisibility = { showNewPass = !showNewPass },
                                onValueChange = {
                                    newPassword = it
                                    if (errorMessage != null || successMessage != null) onClearFeedback()
                                },
                                imeAction = ImeAction.Next
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            PasswordInlineField(
                                label = "Konfirmasi Password Baru",
                                value = confirmNewPassword,
                                placeholder = "Ulangi password baru",
                                visible = showConfirmPass,
                                onToggleVisibility = { showConfirmPass = !showConfirmPass },
                                onValueChange = {
                                    confirmNewPassword = it
                                    if (errorMessage != null || successMessage != null) onClearFeedback()
                                },
                                imeAction = ImeAction.Done,
                                onDone = {
                                    if (!isLoading) {
                                        onSavePassword(currentPassword, newPassword, confirmNewPassword) {
                                            closeEditor()
                                        }
                                    }
                                }
                            )

                            InlineSaveCancelRow(
                                saveText = if (isLoading) "Menyimpan..." else "Simpan Password",
                                isLoading = isLoading,
                                saveEnabled = currentPassword.isNotEmpty() && newPassword.length >= 8 && confirmNewPassword.isNotEmpty(),
                                onCancel = {
                                    onClearFeedback()
                                    closeEditor()
                                },
                                onSave = {
                                    onSavePassword(currentPassword, newPassword, confirmNewPassword) {
                                        closeEditor()
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))

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
                                    text = "Email & autentikasi dikelola langsung oleh Google.",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 12.sp,
                                    color = Neutral500
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            if (editingField == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
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
}

@Composable
private fun ProfileInlineFieldRow(
    label: String,
    value: String,
    placeholder: String,
    editable: Boolean,
    isEditing: Boolean,
    focusRequester: FocusRequester? = null,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = BlueDarker,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppTextField(
                    value = value,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    focusRequester = if (isEditing) focusRequester else null,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    readOnly = !isEditing
                )
                if (editable && !isEditing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onStartEdit)
                    )
                }
            }

            if (editable) {
                IconButton(
                    onClick = if (isEditing) onCancelEdit else onStartEdit,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isEditing) R.drawable.ic_x else R.drawable.ic_pencil
                        ),
                        contentDescription = if (isEditing) "Batal edit $label" else "Edit $label",
                        tint = if (isEditing) Neutral500 else Neutral900,
                        modifier = Modifier.size(if (isEditing) 18.dp else 22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordInlineField(
    label: String,
    value: String,
    placeholder: String,
    visible: Boolean,
    focusRequester: FocusRequester? = null,
    onToggleVisibility: () -> Unit,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {}
) {
    AppTextField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        focusRequester = focusRequester,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onToggleVisibility),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(
                        id = if (visible) R.drawable.ic_eye else R.drawable.ic_eye_closed
                    ),
                    contentDescription = "Tampilkan password",
                    tint = if (visible) BlueNormal else Neutral500,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}

@Composable
private fun InlineSaveCancelRow(
    saveText: String,
    isLoading: Boolean,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onCancel,
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Batal",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Neutral500
            )
        }

        Button(
            onClick = onSave,
            enabled = !isLoading && saveEnabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BlueDark,
                contentColor = NeutralWhite,
                disabledContainerColor = Neutral400,
                disabledContentColor = NeutralWhite
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = saveText,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
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
