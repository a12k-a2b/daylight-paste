package com.daylightcomputer.paste.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.daylightcomputer.paste.R

object DaylightFontFamilies {
    // Primary Editorial Serif - Titles, Headers, Quotes
    val ArizonaMix = FontFamily(
        Font(R.font.abc_arizona_mix_variable, FontWeight.Normal)
    )

    // Technical UI Monospaced Sans - Tracking Labels, Section Headers, Status
    val RomExtendedLight = FontFamily(
        Font(R.font.abc_rom_extended_light, FontWeight.Light)
    )

    // Humanist UI Sans - Body, Lists, Dialogs, Controls
    val ArizonaSans = FontFamily(
        Font(R.font.abc_arizona_sans_variable, FontWeight.Normal)
    )

    // Long-form Reading Serif
    val Garamond = FontFamily(
        Font(R.font.eb_garamond_regular, FontWeight.Normal)
    )
}
