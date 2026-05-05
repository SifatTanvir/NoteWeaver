package com.mindforge.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mindforge.app.R

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val title = stringResource(R.string.app_name)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = title,
            modifier =
                Modifier
                    .fillMaxSize(1.0f)
                    .padding(32.dp),
            contentScale = ContentScale.Fit
        )
    }
}

