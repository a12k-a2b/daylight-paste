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

@Composable
fun CaptureHealthDialog(
    accessState: DaylightPasteManager.ClipboardAccessState,
    onDismiss: () -> Unit
) {
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
                    text = "AOSP Android 13 Background Clipboard Enforcement",
                    fontFamily = DaylightFontFamilies.ArizonaMix,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DaylightColors.InkBlack
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "In Android 10+ (including SolOS / Android 13), ClipboardService denies background clipboard reads to third-party apps unless the calling package has the role/signature-gated READ_CLIPBOARD_IN_BACKGROUND permission or is the currently focused input method (IME).",
                    fontFamily = DaylightFontFamilies.ArizonaSans,
                    fontSize = 13.sp,
                    color = DaylightColors.InkSubtle,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Available Operating Modes:",
                    fontFamily = DaylightFontFamilies.ArizonaMix,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DaylightColors.InkBlack
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "1. Tooltip Menu (Daylight Copy / Paste)",
                    desc = "Select any text in any app, tap 'Daylight Copy' or 'Daylight Paste' in the OS selection menu. Works universally across SolOS with zero special privileges."
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "2. Daylight Paste IME (Unlimited Chunking)",
                    desc = "Switch to Daylight Paste keyboard to stream unlimited-length clips directly into generic editors without Binder IPC caps. As an active IME, background clipboard read is granted by AOSP."
                )
                Spacer(modifier = Modifier.height(8.dp))

                ModeItem(
                    title = "3. Privileged SolOS System Deployment",
                    desc = "Deployed as an integrated SolOS system subsystem in /system/priv-app/ with privapp-permissions whitelist XML, granting genuine seamless OS-wide background capture."
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
