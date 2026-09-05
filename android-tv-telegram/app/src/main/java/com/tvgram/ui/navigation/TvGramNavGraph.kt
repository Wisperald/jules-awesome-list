package com.tvgram.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tvgram.data.auth.AuthStage
import com.tvgram.di.LocalAppContainer
import com.tvgram.ui.auth.AuthScreen
import com.tvgram.ui.chat.ChatScreen
import com.tvgram.ui.chats.ChatListScreen
import com.tvgram.ui.media.PhotoViewerScreen
import com.tvgram.ui.media.VideoPlayerScreen
import com.tvgram.ui.settings.SettingsScreen

/**
 * The navigation graph.
 *
 * Authorization is not a screen the user navigates to — it is a property of the session,
 * so the graph watches TDLib's state and resets the back stack whenever it flips. That
 * way a log-out from Settings, or a session revoked from another device, both land on the
 * sign-in screen with nothing stale behind them.
 */
@Composable
fun TvGramNavGraph(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val navController = rememberNavController()
    val stage by container.auth.stage.collectAsStateWithLifecycle()

    val authorized = stage is AuthStage.Ready

    LaunchedEffect(authorized) {
        val target = if (authorized) Routes.CHATS else Routes.AUTH
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.AUTH,
        modifier = modifier,
    ) {
        composable(Routes.AUTH) {
            AuthScreen(
                onAuthorized = {
                    // The LaunchedEffect above owns the transition; nothing to do here
                    // beyond letting the screen know it succeeded.
                },
            )
        }

        composable(Routes.CHATS) {
            ChatListScreen(
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument(Routes.ARG_CHAT_ID) { type = NavType.LongType }),
        ) { entry ->
            val chatId = entry.arguments?.getLong(Routes.ARG_CHAT_ID) ?: 0L
            ChatScreen(
                chatId = chatId,
                onOpenPhoto = { fileId -> navController.navigate(Routes.photo(fileId)) },
                onOpenVideo = { fileId, title -> navController.navigate(Routes.video(fileId, title)) },
            )
        }

        composable(
            route = Routes.PHOTO,
            arguments = listOf(navArgument(Routes.ARG_FILE_ID) { type = NavType.IntType }),
        ) { entry ->
            val fileId = entry.arguments?.getInt(Routes.ARG_FILE_ID) ?: 0
            PhotoViewerScreen(fileId = fileId, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.VIDEO,
            arguments = listOf(
                navArgument(Routes.ARG_FILE_ID) { type = NavType.IntType },
                navArgument(Routes.ARG_TITLE) {
                    type = NavType.StringType
                    defaultValue = "Video"
                },
            ),
        ) { entry ->
            val fileId = entry.arguments?.getInt(Routes.ARG_FILE_ID) ?: 0
            val title = entry.arguments?.getString(Routes.ARG_TITLE) ?: "Video"
            VideoPlayerScreen(
                fileId = fileId,
                title = title,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
