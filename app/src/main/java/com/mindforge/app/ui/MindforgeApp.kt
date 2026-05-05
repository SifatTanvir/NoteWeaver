package com.mindforge.app.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindforge.app.ui.navigation.NavGraph
import com.mindforge.app.ui.viewmodel.NotesViewModel

private const val BACK_NAV_THROTTLE_MS = 450L

@Composable
fun MindforgeApp(viewModel: NotesViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    var lastBackNavigationUptimeMs by remember { mutableLongStateOf(0L) }

    val canNavigateUp = remember(navBackStackEntry) {
        navController.previousBackStackEntry != null
    }

    val navigateBackThrottled: () -> Unit = {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackNavigationUptimeMs >= BACK_NAV_THROTTLE_MS &&
            navController.previousBackStackEntry != null
        ) {
            lastBackNavigationUptimeMs = now
            navController.popBackStack()
        }
    }

    NavGraph(
        navController = navController,
        viewModel = viewModel,
        onNavigateBack = navigateBackThrottled
    )

    BackHandler(enabled = canNavigateUp) {
        navigateBackThrottled()
    }
}
