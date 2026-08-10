package com.boomeranger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.boomeranger.app.ui.BoomerangAppScreen
import com.boomeranger.app.ui.BoomerangViewModel
import com.boomeranger.app.ui.BoomerangerSplash
import com.boomeranger.app.ui.theme.BoomerangerTheme
import com.boomeranger.app.ui.theme.Ink

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: BoomerangViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate for Android 12+ splash handoff.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Survives rotation: do not replay splash after config change once finished.
            var showBrandedSplash by rememberSaveable { mutableStateOf(true) }

            BoomerangerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        BoomerangAppScreen(viewModel = viewModel)

                        AnimatedVisibility(
                            visible = showBrandedSplash,
                            exit = fadeOut(),
                        ) {
                            BoomerangerSplash(
                                onFinished = { showBrandedSplash = false },
                            )
                        }
                    }
                }
            }
        }
    }
}
