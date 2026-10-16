package com.example.yap_finalprojectpapb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.yap_finalprojectpapb.ui.screens.APISettingsScreen
import com.example.yap_finalprojectpapb.ui.screens.AllSetScreen
import com.example.yap_finalprojectpapb.ui.screens.ChangeEmailScreen
import com.example.yap_finalprojectpapb.ui.screens.ChangeNameScreen
import com.example.yap_finalprojectpapb.ui.screens.ChangePasswordScreen
import com.example.yap_finalprojectpapb.ui.screens.ChatHistoryScreen
import com.example.yap_finalprojectpapb.ui.screens.ChatStudioScreen
import com.example.yap_finalprojectpapb.ui.screens.CodeViewerScreen
import com.example.yap_finalprojectpapb.ui.screens.DeleteAccountScreen
import com.example.yap_finalprojectpapb.ui.screens.ForgotPasswordScreen
import com.example.yap_finalprojectpapb.ui.screens.LoginScreen
import com.example.yap_finalprojectpapb.ui.screens.OTPScreen
import com.example.yap_finalprojectpapb.ui.screens.PreviewScreen
import com.example.yap_finalprojectpapb.ui.screens.ProfileScreen
import com.example.yap_finalprojectpapb.ui.screens.RegisterScreen
import com.example.yap_finalprojectpapb.ui.screens.WelcomeScreen
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

enum class AppScreen {
    Welcome,
    LogIn,
    Register,
    AllSet,
    ForgotPassword,
    OTP,
    ChangePassword,
    ChangeEmail,
    ChangeName,
    Profile,
    DeleteAccount,
    ChatStudio,
    ChatHistory,
    APISettings,
    Preview,
    CodeViewer
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YAPFinalProjectPAPBTheme {
                MainAppContent()
            }
        }
    }
}

@Composable
fun MainAppContent() {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.ChatStudio) }
    val screenState = rememberSaveableStateHolder()
    BackHandler(currentScreen != AppScreen.ChatStudio) {
        currentScreen = when (currentScreen) {
            AppScreen.OTP -> AppScreen.ForgotPassword
            AppScreen.ForgotPassword -> AppScreen.LogIn
            AppScreen.ChangeName, AppScreen.ChangeEmail, AppScreen.ChangePassword, AppScreen.DeleteAccount -> AppScreen.Profile
            AppScreen.Preview, AppScreen.CodeViewer -> AppScreen.ChatStudio
            else -> AppScreen.ChatStudio
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            screenState.SaveableStateProvider(currentScreen.name) {
                when (currentScreen) {
                    AppScreen.Welcome -> WelcomeScreen(
                        onLogInClick = { currentScreen = AppScreen.LogIn },
                        onRegisterClick = { currentScreen = AppScreen.Register }
                    )
                    AppScreen.LogIn -> LoginScreen(
                        onLogInClick = { currentScreen = AppScreen.ChatStudio },
                        onSignUpClick = { currentScreen = AppScreen.Register },
                        onForgotPasswordClick = { currentScreen = AppScreen.ForgotPassword }
                    )
                    AppScreen.Register -> RegisterScreen(
                        onRegisterClick = { currentScreen = AppScreen.AllSet },
                        onLogInClick = { currentScreen = AppScreen.LogIn }
                    )
                    AppScreen.AllSet -> AllSetScreen(
                        onStartClick = { currentScreen = AppScreen.ChatStudio }
                    )
                    AppScreen.ForgotPassword -> ForgotPasswordScreen(
                        onBackClick = { currentScreen = AppScreen.LogIn },
                        onSendClick = { currentScreen = AppScreen.OTP }
                    )
                    AppScreen.OTP -> OTPScreen(
                        onBackClick = { currentScreen = AppScreen.ForgotPassword },
                        onConfirmClick = { currentScreen = AppScreen.ChangePassword }
                    )
                    AppScreen.ChangePassword -> ChangePasswordScreen(
                        onBackClick = { currentScreen = AppScreen.Profile },
                        onSendClick = { currentScreen = AppScreen.Profile },
                        onForgotPasswordClick = { currentScreen = AppScreen.ForgotPassword }
                    )
                    AppScreen.ChangeEmail -> ChangeEmailScreen(
                        onBackClick = { currentScreen = AppScreen.Profile },
                        onSendClick = { currentScreen = AppScreen.OTP }
                    )
                    AppScreen.ChangeName -> ChangeNameScreen(
                        onBackClick = { currentScreen = AppScreen.Profile },
                        onSendClick = { currentScreen = AppScreen.Profile }
                    )
                    AppScreen.Profile -> ProfileScreen(
                        onLogoutClick = { currentScreen = AppScreen.LogIn },
                        onDeleteAccountClick = { currentScreen = AppScreen.DeleteAccount }
                    )
                    AppScreen.DeleteAccount -> DeleteAccountScreen(
                        onBackClick = { currentScreen = AppScreen.Profile },
                        onDeleteClick = { currentScreen = AppScreen.LogIn },
                        onForgotPasswordClick = { currentScreen = AppScreen.ForgotPassword }
                    )
                    AppScreen.ChatStudio -> ChatStudioScreen(
                        onMenuClick = { currentScreen = AppScreen.ChatHistory },
                        onApiSettingsClick = { currentScreen = AppScreen.APISettings },
                        onNewChatClick = { currentScreen = AppScreen.ChatStudio },
                        onProfileClick = { currentScreen = AppScreen.Profile }
                    )
                    AppScreen.ChatHistory -> ChatHistoryScreen(
                        onBackClick = { currentScreen = AppScreen.ChatStudio },
                        onChatSelect = { currentScreen = AppScreen.ChatStudio },
                        onNewChatClick = {
                            screenState.removeState(AppScreen.ChatStudio.name)
                            currentScreen = AppScreen.ChatStudio
                        }
                    )
                    AppScreen.APISettings -> APISettingsScreen(
                        onBackClick = { currentScreen = AppScreen.ChatStudio },
                        onApplyClick = { currentScreen = AppScreen.ChatStudio },
                        onCancelClick = { currentScreen = AppScreen.ChatStudio }
                    )
                    AppScreen.Preview -> PreviewScreen(
                        onBackClick = { currentScreen = AppScreen.ChatStudio },
                        onGoToChatClick = { currentScreen = AppScreen.ChatStudio }
                    )
                    AppScreen.CodeViewer -> CodeViewerScreen(
                        onBackClick = { currentScreen = AppScreen.ChatStudio }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppContentPreview() {
    YAPFinalProjectPAPBTheme {
        MainAppContent()
    }
}
