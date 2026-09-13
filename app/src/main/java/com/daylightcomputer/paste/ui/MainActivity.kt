package com.daylightcomputer.paste.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.daylightcomputer.paste.ui.theme.DaylightColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
