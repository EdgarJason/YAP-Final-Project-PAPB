package com.example.yap_finalprojectpapb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yap_finalprojectpapb.data.repository.NetworkMonitorImpl
import com.example.yap_finalprojectpapb.ui.components.AppUpdateDialog
import com.example.yap_finalprojectpapb.ui.components.OfflineBanner
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
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import com.google.firebase.auth.FirebaseAuth

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
        installSplashScreen()
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
    val isInspection = LocalInspectionMode.current
    val context = LocalContext.current
    val studioViewModel: StudioViewModel = viewModel()
    val studioState by studioViewModel.state.collectAsState()
    var showUpdates by rememberSaveable { mutableStateOf(false) }

    val networkMonitor = remember { NetworkMonitorImpl(context) }
    val isOnline by networkMonitor.isOnline.collectAsState(initial = true)

    val startDest = remember {
        if (!isInspection && runCatching { FirebaseAuth.getInstance().currentUser != null }.getOrDefault(false)) {
            Routes.CHAT_STUDIO
        } else {
            Routes.WELCOME
        }
    }

    if (!isInspection) {
        AppUpdateDialog(showUpdates, onShow = { showUpdates = true }, onDismiss = { showUpdates = false })
    }

    val authViewModel: AuthViewModel = viewModel()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OfflineBanner(isOnline = isOnline)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = startDest,
                    enterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(300)
                        ) + fadeIn(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            towards = AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(300)
                        ) + fadeOut(animationSpec = tween(300))
                    }
                ) {
                composable(Routes.WELCOME) {
                    WelcomeScreen(
                        onLogInClick = { navController.navigate(Routes.LOGIN) },
                        onRegisterClick = { navController.navigate(Routes.REGISTER) }
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLogInClick = {
                            navController.navigate(Routes.CHAT_STUDIO) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                            }
                        },
                        onSignUpClick = { navController.navigate(Routes.REGISTER) },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.REGISTER) {
                    RegisterScreen(
                        onRegisterSuccess = { _ ->
                            navController.navigate(Routes.ALL_SET) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                            }
                        },
                        onLogInClick = { navController.navigate(Routes.LOGIN) }
                    )
                }
                composable(Routes.ALL_SET) {
                    AllSetScreen(
                        onStartClick = {
                            navController.navigate(Routes.CHAT_STUDIO) {
                                popUpTo(Routes.ALL_SET) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.popBackStack() }
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
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.popBackStack() },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.CHANGE_EMAIL) {
                    ChangeEmailScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.CHANGE_NAME) {
                    ChangeNameScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onSendClick = { navController.popBackStack() }
                    )
                }
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onChangeNameClick = { navController.navigate(Routes.CHANGE_NAME) },
                        onChangeEmailClick = { navController.navigate(Routes.CHANGE_EMAIL) },
                        onChangePasswordClick = { navController.navigate(Routes.CHANGE_PASSWORD) },
                        onLogoutClick = {
                            studioViewModel.reset()
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDeleteAccountClick = { navController.navigate(Routes.DELETE_ACCOUNT) }
                    )
                }
                composable(Routes.DELETE_ACCOUNT) {
                    DeleteAccountScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onDeleteClick = {
                            studioViewModel.reset()
                            navController.navigate(Routes.WELCOME) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onForgotPasswordClick = { navController.navigate(Routes.FORGOT_PASSWORD) }
                    )
                }
                composable(Routes.CHAT_STUDIO) {
                    ChatStudioScreen(
                        studioViewModel = studioViewModel,
                        onMenuClick = { navController.navigate(Routes.CHAT_HISTORY) },
                        onApiSettingsClick = { navController.navigate(Routes.API_SETTINGS) },
                        onUpdateClick = { showUpdates = true },
                        onNewChatClick = { studioViewModel.reset() },
                        onProfileClick = { navController.navigate(Routes.PROFILE) },
                        onRecentSelect = { navController.navigate(Routes.CHAT_STUDIO) }
                    )
                }
                composable(Routes.CHAT_HISTORY) {
                    ChatHistoryScreen(
                        conversations = studioState.conversations,
                        onBackClick = { navController.popBackStack() },
                        onChatSelect = { item ->
                            studioViewModel.openConversation(item.id)
                            navController.popBackStack(Routes.CHAT_STUDIO, false)
                        },
                        onNewChatClick = {
                            studioViewModel.reset()
                            navController.popBackStack(Routes.CHAT_STUDIO, false)
                        }
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
}

@Preview(showBackground = true)
@Composable
fun MainAppContentPreview() {
    YAPFinalProjectPAPBTheme {
        WelcomeScreen()
    }
}
