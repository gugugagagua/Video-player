package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.videoplayer.ui.home.HomeScreen
import com.example.videoplayer.ui.player.PlayerScreen
import com.example.videoplayer.ui.theme.VideoPlayerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoPlayerTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
private fun AppNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onOpenCollection = { collectionId, videoId, position ->
                    navController.navigate(
                        "player/$collectionId?videoid=${videoId ?: -1}&position=$position"
                    )
                }
            )
        }
        composable(
            route = "player/{collectionId}?videoid={videoid}&position={position}",
            arguments = listOf(
                navArgument("collectionId") { type = NavType.LongType },
                navArgument("videoid") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("position") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { entry ->
            PlayerScreen(
                collectionId = entry.arguments?.getLong("collectionId") ?: 0L,
                startVideoId = entry.arguments?.getLong("videoid")?.takeIf { it >= 0 },
                startPositionMs = entry.arguments?.getLong("position") ?: 0L,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
