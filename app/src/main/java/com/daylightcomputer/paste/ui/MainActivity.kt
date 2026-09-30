package com.daylightcomputer.paste.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.WindowCompat
import com.daylightcomputer.paste.ui.theme.DaylightColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable modern edge-to-edge
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // 2. Eliminate system bar colors and contrast scrims
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        // Disable Android automatic scrim enforcement
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = DaylightColors.Amber595nm,
                    background = DaylightColors.PaperBg,
                    surface = DaylightColors.CardBg,
                    onPrimary = DaylightColors.PaperBg,
                    onBackground = DaylightColors.InkBlack,
                    onSurface = DaylightColors.InkBlack
                )
            ) {
                PasteScreen()
            }
        }
    }
}
