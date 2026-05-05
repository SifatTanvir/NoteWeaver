package com.mindforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindforge.app.ui.MindforgeApp
import com.mindforge.app.ui.screens.SplashScreen
import com.mindforge.app.ui.theme.MindforgeTheme
import com.mindforge.app.ui.viewmodel.NotesViewModel
import com.mindforge.app.ui.viewmodel.NotesViewModelFactory
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MindforgeTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1000)
                    showSplash = false
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val application = application as MindforgeApplication
                    val viewModel: NotesViewModel = viewModel(
                        factory = NotesViewModelFactory(application.repository)
                    )

                    if (showSplash) {
                        SplashScreen()
                    } else {
                        MindforgeApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
