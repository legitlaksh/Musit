package com.musit.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musit.player.ui.navigation.MusitNavGraph
import com.musit.player.ui.theme.MusitTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            // Force dark theme always (user requested)
            MusitTheme(themeMode = com.musit.player.ui.theme.ThemeMode.DARK) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MusitNavGraph(mainViewModel = mainViewModel)
                }
            }
        }
    }
}
