package com.daylightcomputer.paste.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.ui.MainActivity
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightTypography

/**
 * SolOS LivePaper Floating Clipboard HUD.
 * Replaces the stock Android 13 SystemUI clipboard overlay.
 * Appears at the bottom of the screen upon copy, showing content type, stats,
 * and quick-copy actions before auto-dismissing.
 */
class DaylightClipboardHud(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: View? = null
    private val autoDismissRunnable = Runnable { dismiss() }

    fun show(clip: DaylightClip) {
        mainHandler.post {
            dismiss() // Clear any existing HUD

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                (340 * context.resources.displayMetrics.density).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = (24 * context.resources.displayMetrics.density).toInt()
                y = (48 * context.resources.displayMetrics.density).toInt()
            }

            val composeView = ComposeView(context).apply {
                setContent {
                    MaterialTheme {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, DaylightColors.BorderStrong, RoundedCornerShape(12.dp))
                                .clickable {
                                    // Open full Daylight Paste on tap
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    context.startActivity(intent)
                                    dismiss()
                                },
                            color = DaylightColors.PaperBg,
                            shadowElevation = 6.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                // Header: Type badge & close
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = DaylightColors.AmberAccent,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = clip.contentType,
                                                color = DaylightColors.PaperBg,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "${clip.charCount} chars · ${clip.wordCount} words",
                                            fontFamily = DaylightTypography.RomExtended,
                                            fontSize = 10.sp,
                                            color = DaylightColors.InkSubtle
                                        )
                                    }

                                    IconButton(
                                        onClick = { dismiss() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = DaylightColors.InkBlack,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Title
                                Text(
                                    text = clip.title,
                                    fontFamily = DaylightTypography.ArizonaMix,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DaylightColors.InkBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Snippet Preview
                                Text(
                                    text = clip.markdownContent.trim(),
                                    fontFamily = DaylightTypography.ArizonaSans,
                                    fontSize = 12.sp,
                                    color = DaylightColors.InkBlack,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            DaylightPasteManager.copyCleanMarkdownToClipboard(
                                                context,
                                                clip.markdownContent,
                                                clip.title
                                            )
                                            dismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = DaylightColors.AmberAccent,
                                            contentColor = DaylightColors.PaperBg
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text(
                                            text = "COPY MARKDOWN",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            DaylightPasteManager.copyPlainTextToClipboard(
                                                context,
                                                clip.textContent,
                                                clip.title
                                            )
                                            dismiss()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = DaylightColors.InkBlack
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DaylightColors.BorderStrong),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(32.dp)
                                    ) {
                                        Text(
                                            text = "PLAIN",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            hudView = composeView
            try {
                windowManager.addView(hudView, params)
                // Schedule auto-dismiss after 4.0 seconds
                mainHandler.postDelayed(autoDismissRunnable, 4000)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        hudView?.let {
            try {
                windowManager.removeView(it)
            } catch (ignored: Exception) {}
            hudView = null
        }
    }
}
