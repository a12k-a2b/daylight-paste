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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.ui.MainActivity
import com.daylightcomputer.paste.ui.theme.DaylightColors
import com.daylightcomputer.paste.ui.theme.DaylightFontFamilies

/**
 * SolOS LivePaper Minimal Floating Clipboard HUD.
 * Replaces the stock Android 13 SystemUI clipboard overlay.
 * Displays a non-intrusive 2-second ambient SolOS amber pill toast that never steals
 * focus or blocks reading, with a tap-to-open gesture for full history inspection.
 */
class DaylightClipboardHud(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: View? = null
    private val autoDismissRunnable = Runnable { dismiss() }

    fun show(clip: DaylightClip) {
        mainHandler.post {
            dismiss() // Clear any existing HUD immediately

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = context.resources.displayMetrics.density
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (56 * density).toInt() // Position safely above home gesture indicator
            }

            val countText = when {
                clip.wordCount >= 1000 -> {
                    val kWords = String.format(java.util.Locale.US, "%.1fk", clip.wordCount / 1000.0)
                    "$kWords words"
                }
                clip.wordCount > 1 -> "${clip.wordCount} words"
                clip.charCount > 0 -> "${clip.charCount} chars"
                else -> ""
            }

            val toastLabel = when (clip.clipType) {
                ClipType.MARKDOWN -> if (countText.isNotEmpty()) "Captured Markdown · $countText" else "Captured Markdown"
                ClipType.CODE -> if (countText.isNotEmpty()) "Captured Code · $countText" else "Captured Code"
                ClipType.URL -> "Captured Link"
                ClipType.IMAGE -> "Captured Image"
                ClipType.TEXT -> if (countText.isNotEmpty()) "Captured Text · $countText" else "Captured Text"
            }

            val composeView = ComposeView(context).apply {
                setContent {
                    MaterialTheme {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, DaylightColors.AmberDeep, RoundedCornerShape(24.dp))
                                .clickable {
                                    val intent = Intent(context, MainActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    context.startActivity(intent)
                                    dismiss()
                                },
                            color = DaylightColors.Amber595nm,
                            shadowElevation = 0.dp // LivePaper rule: zero gaussian shadows
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Success",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )

                                Spacer(modifier = Modifier.width(7.dp))

                                Text(
                                    text = toastLabel,
                                    fontFamily = DaylightFontFamilies.RomExtendedLight,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }

            hudView = composeView
            try {
                windowManager.addView(hudView, params)
                // SolOS rule: ambient 2.0-second auto-dismiss
                mainHandler.postDelayed(autoDismissRunnable, 2000)
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
