package com.mindforge.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mindforge.app.ui.screens.AddEditNoteScreen
import com.mindforge.app.ui.screens.GroupsScreen
import com.mindforge.app.ui.screens.MergeSuggestionsScreen
import com.mindforge.app.ui.screens.NotesListScreen
import com.mindforge.app.ui.screens.SearchScreen
import com.mindforge.app.ui.viewmodel.NotesViewModel

sealed class Screen(val route: String) {
    object NotesList : Screen("notes_list")

    object AddEditNote : Screen("add_edit_note/{noteId}") {
        fun createRoute(noteId: Long? = null) = "add_edit_note/${noteId ?: -1}"
    }

    object Search : Screen("search")
    object Groups : Screen("groups")
    object MergeSuggestions : Screen("merge_suggestions")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: NotesViewModel,
    onNavigateBack: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.NotesList.route
    ) {
        composable(Screen.NotesList.route) {
            NotesListScreen(
                viewModel = viewModel,
                onNavigateToAddNote = {
                    navController.navigate(Screen.AddEditNote.createRoute())
                },
                onNavigateToEditNote = { noteId ->
                    navController.navigate(Screen.AddEditNote.createRoute(noteId))
                },
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToGroups = {
                    navController.navigate(Screen.Groups.route)
                },
                onNavigateToMerge = {
                    navController.navigate(Screen.MergeSuggestions.route)
                }
            )
        }

        composable(Screen.AddEditNote.route) { backStackEntry ->
            val noteIdString = backStackEntry.arguments?.getString("noteId") ?: "-1"
            val noteId = noteIdString.toLongOrNull()?.takeIf { it != -1L }

            AddEditNoteScreen(
                viewModel = viewModel,
                noteId = noteId,
                onNavigateBack = onNavigateBack,
                onOpenRelatedNote = { relatedId ->
                    navController.navigate(Screen.AddEditNote.createRoute(relatedId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onNavigateToNote = { noteId ->
                    navController.navigate(Screen.AddEditNote.createRoute(noteId))
                }
            )
        }

        composable(Screen.Groups.route) {
            GroupsScreen(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack,
                onNavigateToNote = { noteId ->
                    navController.navigate(Screen.AddEditNote.createRoute(noteId))
                }
            )
        }

        composable(Screen.MergeSuggestions.route) {
            MergeSuggestionsScreen(
                viewModel = viewModel,
                onNavigateBack = onNavigateBack
            )
        }
    }
}
