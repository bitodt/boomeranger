package com.boomeranger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.ui.BoomerangAppScreen
import com.boomeranger.app.ui.BoomerangViewModel
import com.boomeranger.app.ui.theme.BoomerangerTheme
import com.boomeranger.app.ui.theme.Ink

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: BoomerangViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoomerangerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink,
                ) {
                    BoomerangAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}
