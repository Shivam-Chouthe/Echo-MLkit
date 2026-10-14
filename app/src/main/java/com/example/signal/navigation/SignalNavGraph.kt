package com.example.signal.navigation

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.signal.ui.capture.CaptureScreen
import com.example.signal.ui.details.DetailsScreen
import com.example.signal.ui.home.HomeScreen
import com.example.signal.ui.library.LibraryScreen
import com.example.signal.ui.search.SearchScreen

@androidx.compose.runtime.Composable
fun SignalNavGraph(
    navController: NavHostController,
    sharedText: String?,
    sharedImageUri: Uri? = null,
    shareRequestId: Long = 0L,
    openedMemoryId: Long? = null
) {

    NavHost(
        navController = navController,
        startDestination = SignalRoutes.HOME
    ) {

        composable(
            SignalRoutes.HOME
        ) {
            HomeScreen(
                onSearchClick = {
                    navController.navigate(
                        SignalRoutes.SEARCH
                    )
                },
                onCaptureClick = {
                    navController.navigate(
                        SignalRoutes.CAPTURE
                    )
                },
                onLibraryClick = {
                    navController.navigate(
                        SignalRoutes.LIBRARY
                    )
                },
                onMemoryClick = { memoryId ->
                    navController.navigate(
                        SignalRoutes.details(
                            memoryId
                        )
                    )
                }
            )
        }

        composable(
            SignalRoutes.SEARCH
        ) {
            SearchScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            SignalRoutes.CAPTURE
        ) {
            CaptureScreen(
                sharedText = sharedText,
                sharedImageUri = sharedImageUri,
                shareRequestId = shareRequestId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            SignalRoutes.LIBRARY
        ) {
            LibraryScreen(
                onBack = {
                    navController.popBackStack()
                },
                onMemoryClick = { memoryId ->
                    navController.navigate(
                        SignalRoutes.details(
                            memoryId
                        )
                    )
                }
            )
        }

        composable(
            route = SignalRoutes.DETAILS_WITH_ID,
            arguments = listOf(
                navArgument("memoryId") {
                    type = NavType.LongType
                }
            )
        ) { backStackEntry ->

            val memoryId =
                backStackEntry
                    .arguments
                    ?.getLong("memoryId")
                    ?: return@composable

            DetailsScreen(
                memoryId = memoryId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }

    LaunchedEffect(openedMemoryId) {

        if (openedMemoryId != null) {

            navController.navigate(
                SignalRoutes.details(
                    openedMemoryId
                )
            ) {
                launchSingleTop = true
            }
        }
    }
}
