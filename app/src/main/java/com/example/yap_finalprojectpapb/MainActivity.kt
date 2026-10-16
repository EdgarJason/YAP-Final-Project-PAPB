package com.example.yap_finalprojectpapb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

object Routes {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val ALL_SET = "all_set"
    const val FORGOT_PASSWORD = "forgot_password"
    const val OTP = "otp"
    const val CHANGE_PASSWORD = "change_password"
    const val CHANGE_EMAIL = "change_email"
    const val CHANGE_NAME = "change_name"
    const val PROFILE = "profile"
    const val DELETE_ACCOUNT = "delete_account"
    const val CHAT_STUDIO = "chat_studio"
    const val CHAT_HISTORY = "chat_history"
    const val API_SETTINGS = "api_settings"
    const val PREVIEW = "preview"
    const val CODE_VIEWER = "code_viewer"
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
    val navController = rememberNavController()

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
            NavHost(
                navController = navController,
                startDestination = Routes.WELCOME
            ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onLogInClick = { navController.navigate(Routes.LOGIN) },
                        onRegisterClick = { navController.navigate(Routes.REGISTER) }
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        onLogInClick = { navController.navigate(Routes.CHAT_STUDIO) },
                        onSignUpClick = { navController.navigate(Routes.REGISTER) },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onRegisterClick = { navController.navigate(Routes.ALL_SET) },
                        onLogInClick = { navController.navigate(Routes.LOGIN) }
                    )
                }
                composable(Routes.ALL_SET) {
                    AllSetScreen(
                        onStartClick = { navController.navigate(Routes.CHAT_STUDIO) }
                    )
                }
                composable(Routes.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.navigate(Routes.OTP) }
                    )
                }
                composable(Routes.OTP) {
                    OTPScreen(
                        onBackClick = { navController.popBackStack() },
                        onConfirmClick = { navController.navigate(Routes.CHANGE_PASSWORD) }
                    )
                }
                composable(Routes.CHANGE_PASSWORD) {
                    ChangePasswordScreen(
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.navigate(Routes.PROFILE) },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.CHANGE_EMAIL) {
                    ChangeEmailScreen(
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.navigate(Routes.OTP) }
                    )
                }
                composable(Routes.CHANGE_NAME) {
                    ChangeNameScreen(
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onBackClick = { navController.popBackStack() },
                        onChangeNameClick = { navController.navigate(Routes.CHANGE_NAME) },
                        onChangeEmailClick = { navController.navigate(Routes.CHANGE_EMAIL) },
                        onChangePasswordClick = { navController.navigate(Routes.CHANGE_PASSWORD) },
                        onLogoutClick = {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDeleteAccountClick = { navController.navigate(Routes.DELETE_ACCOUNT) }
                    )
                }
                composable(Routes.DELETE_ACCOUNT) {
                    DeleteAccountScreen(
                        onBackClick = { navController.popBackStack() },
                        onDeleteClick = {
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.CHAT_STUDIO) {
                    ChatStudioScreen(
                        onMenuClick = { navController.navigate(Routes.CHAT_HISTORY) },
                        onApiSettingsClick = { navController.navigate(Routes.API_SETTINGS) },
                        onNewChatClick = { navController.navigate(Routes.CHAT_STUDIO) },
                        onProfileClick = { navController.navigate(Routes.PROFILE) },
                        onRecentSelect = { navController.navigate(Routes.CHAT_STUDIO) }
                    )
                }
                composable(Routes.CHAT_HISTORY) {
                    ChatHistoryScreen(
                        onBackClick = { navController.popBackStack() },
                        onChatSelect = { navController.navigate(Routes.CHAT_STUDIO) },
                        onNewChatClick = { navController.navigate(Routes.CHAT_STUDIO) }
                    )
                }
                composable(Routes.API_SETTINGS) {
                    APISettingsScreen(
                        onBackClick = { navController.popBackStack() },
                        onApplyClick = { navController.popBackStack() },
                        onCancelClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.PREVIEW) {
                    PreviewScreen(
                        onBackClick = { navController.popBackStack() },
                        onGoToChatClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.CODE_VIEWER) {
                    CodeViewerScreen(
                        onBackClick = { navController.popBackStack() }
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
