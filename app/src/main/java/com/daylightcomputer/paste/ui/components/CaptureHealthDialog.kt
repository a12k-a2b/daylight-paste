package com.daylightcomputer.paste.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daylightcomputer.paste.service.DaylightPasteManager
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.LocalContext

@Composable
fun CaptureHealthDialog(
    accessState: DaylightPasteManager.ClipboardAccessState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .background(DaylightColors.PaperBg)
                .border(2.dp, DaylightColors.InkBlack, RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (accessState.isPrivileged) DaylightColors.ForestGreen else DaylightColors.Amber
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CAPTURE SYSTEM HEALTH",
                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DaylightColors.InkBlack,
                        letterSpacing = 1.6.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Current Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaylightColors.SurfaceCream)
                        .border(1.dp, DaylightColors.BorderSubtle, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "CURRENT ACCESS STATE",
                            fontFamily = DaylightFontFamilies.RomExtendedLight,
                            fontSize = 10.sp,
                            color = DaylightColors.TextMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = accessState.label,
                            fontFamily = DaylightFontFamilies.ArizonaSans,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DaylightColors.InkBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Architectural explanation
                Text(
                    text = "Why a keyboard?",
                    fontFamily = DaylightFontFamilies.ArizonaMix,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DaylightColors.InkBlack
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android 13 only lets two kinds of app read the clipboard while you're in a different app: system apps with a signature-gated permission, and the keyboard you currently have selected. To save every copy automatically, Daylight Paste must be your selected keyboard. If you switch to another keyboard, copies made in other apps are not saved until you switch back.",
                    fontFamily = DaylightFontFamilies.ArizonaSans,
                    fontSize = 13.sp,
                    color = DaylightColors.InkSubtle,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!accessState.isPrivileged) {
                    Text(
                        text = "Turn it on (two steps):",
                        fontFamily = DaylightFontFamilies.ArizonaMix,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DaylightColors.InkBlack
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DaylightColors.InkBlack),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 · ENABLE DAYLIGHT PASTE KEYBOARD", color = DaylightColors.PaperBg,
                            fontFamily = DaylightFontFamilies.RomExtendedLight, fontSize = 12.sp, letterSpacing = 1.2.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val imm = context.getSystemService(InputMethodManager::class.java)
                            imm?.showInputMethodPicker()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DaylightColors.Amber),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2 · PICK IT AS YOUR KEYBOARD", color = DaylightColors.InkBlack,
                            fontFamily = DaylightFontFamilies.RomExtendedLight, fontSize = 12.sp, letterSpacing = 1.2.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = "Other ways to use it:",
                    fontFamily = DaylightFontFamilies.ArizonaMix,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DaylightColors.InkBlack
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "Selection menu: Daylight Copy / Daylight Paste",
                    desc = "Select text in any app, open the ⋮ overflow in the selection menu, tap 'Daylight Copy' to save it or 'Daylight Paste' to insert a saved clip. Works with any keyboard. Some apps (e.g. many Compose apps) don't show these entries."
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "Keyboard clip strip (unlimited length)",
                    desc = "Tap a card in the strip above the Daylight keyboard. Text is typed into the field in 8 KB pieces, so even multi-megabyte clips go in without hitting Android's ~1 MB Binder limit."
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "Open the app",
                    desc = "Whatever is on the clipboard when you open Daylight Paste is saved, no keyboard needed."
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = DaylightColors.InkBlack),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "DISMISS",
                        fontFamily = DaylightFontFamilies.RomExtendedLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DaylightColors.PaperBg,
                        letterSpacing = 1.4.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeItem(title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(DaylightColors.SurfaceCream.copy(alpha = 0.5f))
            .border(1.dp, DaylightColors.BorderSubtle, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            fontFamily = DaylightFontFamilies.ArizonaSans,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DaylightColors.InkBlack
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = desc,
            fontFamily = DaylightFontFamilies.ArizonaSans,
            fontSize = 12.sp,
            color = DaylightColors.InkSubtle,
            lineHeight = 16.sp
        )
    }
}
