package com.github.kate1234567

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.kate1234567.ui.navigation.Screen
import com.github.kate1234567.ui.screens.*
import com.github.kate1234567.ui.theme.ChatAppTheme
import com.github.kate1234567.ui.viewmodel.AuthViewModel
import com.github.kate1234567.ui.viewmodel.ChatListViewModel
import com.github.kate1234567.ui.viewmodel.MessageViewModel
import com.github.kate1234567.ui.viewmodel.AppViewModel
import com.github.kate1234567.ui.viewmodel.NavigationViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val appViewModel: AppViewModel = hiltViewModel()
                    val navigationViewModel: NavigationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                    val chatListViewModel: ChatListViewModel = hiltViewModel()
                    val messageViewModel: MessageViewModel = hiltViewModel()

                    val currentUser by appViewModel.currentUser.collectAsState()
                    val authToken by appViewModel.authToken.collectAsState()
                    val selectedChannel by navigationViewModel.selectedChannel.collectAsState()
                    val openedImageLink by navigationViewModel.openedImageLink.collectAsState()

                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                    LaunchedEffect(authToken) {
                        if (authToken != null && navController.currentDestination?.route == Screen.Login.route) {
                            navController.navigate(Screen.ChatList.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    }

                    if (isLandscape) {

                        if (openedImageLink != null) {
                            BackHandler {
                                navigationViewModel.closeImage()
                            }
                            ImageScreen(
                                imageLink = openedImageLink!!,
                                onClose = { navigationViewModel.closeImage() }
                            )
                        } else {
                            BackHandler {
                                if (selectedChannel != null) {
                                    navigationViewModel.clearChannel()
                                } else {
                                    finish()
                                }
                            }

                            LandscapeChatScreen(
                                username = currentUser ?: "",
                                selectedChannel = selectedChannel,
                                onChannelSelected = { channel ->
                                    navigationViewModel.selectChannel(channel)
                                },
                                onLogout = {
                                    appViewModel.logout()
                                    navigationViewModel.clearChannel()
                                },
                                onImageClick = { link ->
                                    navigationViewModel.openImage(link)
                                },
                                chatListViewModel = chatListViewModel,
                                messageViewModel = messageViewModel
                            )
                        }
                    } else {
                        LaunchedEffect(selectedChannel, openedImageLink) {
                            val currentRoute = navController.currentDestination?.route

                            if (openedImageLink != null && currentRoute != Screen.Image.route) {
                                navController.navigate(Screen.Image.createRoute(openedImageLink!!))
                            }
                            else if (selectedChannel != null && !currentRoute.orEmpty().startsWith("messages/") && currentRoute != Screen.Image.route) {
                                navController.navigate(Screen.Messages.createRoute(selectedChannel!!))
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = if (authToken != null) Screen.ChatList.route else Screen.Login.route
                        ) {
                            composable(Screen.Login.route) {
                                val viewModel: AuthViewModel = hiltViewModel()

                                BackHandler {
                                    finish()
                                }

                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(Screen.ChatList.route) {
                                            popUpTo(Screen.Login.route) { inclusive = true }
                                        }
                                    },
                                    viewModel = viewModel
                                )
                            }

                            composable(Screen.ChatList.route) {
                                BackHandler {
                                    finish()
                                }

                                ChatListScreen(
                                    onChatSelected = { channel ->
                                        navigationViewModel.selectChannel(channel)
                                        navController.navigate(Screen.Messages.createRoute(channel))
                                    },
                                    onLogout = {
                                        appViewModel.logout()
                                        navController.navigate(Screen.Login.route) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    },
                                    viewModel = chatListViewModel
                                )
                            }

                            composable(
                                route = Screen.Messages.route,
                                arguments = listOf(navArgument("channel") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val channel = backStackEntry.arguments?.getString("channel") ?: ""

                                LaunchedEffect(channel) {
                                    navigationViewModel.selectChannel(channel)
                                }

                                MessageScreen(
                                    channel = channel,
                                    username = currentUser ?: "",
                                    onBack = {
                                        navigationViewModel.clearChannel()
                                        navController.popBackStack()
                                    },
                                    onImageClick = { link ->
                                        navigationViewModel.openImage(link)
                                        navController.navigate(Screen.Image.createRoute(link))
                                    },
                                    viewModel = messageViewModel
                                )
                            }

                            composable(
                                route = Screen.Image.route,
                                arguments = listOf(navArgument("link") { type = NavType.StringType })
                            ) { backStackEntry ->
                                val link = backStackEntry.arguments?.getString("link") ?: ""

                                LaunchedEffect(link) {
                                    navigationViewModel.openImage(link)
                                }

                                ImageScreen(
                                    imageLink = link,
                                    onClose = {
                                        navigationViewModel.closeImage()
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

