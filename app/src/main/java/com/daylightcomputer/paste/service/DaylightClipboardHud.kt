package com.daylightcomputer.paste.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.daylightcomputer.paste.data.DaylightClip
import com.daylightcomputer.paste.markdown.ClipType
import com.daylightcomputer.paste.ui.MainActivity

/**
 * SolOS LivePaper Minimal Floating Clipboard HUD.
 * Replaces the stock Android 13 SystemUI clipboard overlay.
 * Displays a non-intrusive 2-second ambient SolOS amber pill toast that never steals
 * focus or blocks reading, with a tap-to-open gesture for full history inspection.
 * Built with a high-performance native View hierarchy for instant 90Hz rendering
 * and zero ViewTreeLifecycleOwner crash risks across background services.
 */
class DaylightClipboardHud(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: View? = null
    private val autoDismissRunnable = Runnable { dismiss() }

    fun show(clip: DaylightClip) {
        mainHandler.post {
            dismiss() // Clear any existing HUD immediately

            val density = context.resources.displayMetrics.density

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

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
                ClipType.MARKDOWN -> if (countText.isNotEmpty()) "✓ Captured Markdown · $countText" else "✓ Captured Markdown"
                ClipType.CODE -> if (countText.isNotEmpty()) "✓ Captured Code · $countText" else "✓ Captured Code"
                ClipType.URL -> "✓ Captured Link"
                ClipType.IMAGE -> "✓ Captured Image"
                ClipType.TEXT -> if (countText.isNotEmpty()) "✓ Captured Text · $countText" else "✓ Captured Text"
            }

            // Outer SolOS Amber Pill Container
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (18 * density).toInt()
                val padV = (10 * density).toInt()
                setPadding(padH, padV, padH, padV)

                // Pure SolOS LivePaper styling: Amber pill (#D97706), Deep Amber border (#C87D20), 0dp elevation
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 24f * density
                    setColor(0xFFD97706.toInt()) // DaylightColors.Amber595nm
                    setStroke((1.5f * density).toInt(), 0xFFC87D20.toInt()) // DaylightColors.AmberDeep
                }
                elevation = 0f

                setOnClickListener {
                    try {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                    } catch (ignored: Exception) {}
                    dismiss()
                }
            }

            // White Checkmark Icon
            val iconSize = (16 * density).toInt()
            val checkBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(checkBitmap)
            val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 2.4f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val checkPath = Path().apply {
                val w = iconSize.toFloat()
                val h = iconSize.toFloat()
                moveTo(w * 0.20f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.74f)
                lineTo(w * 0.80f, h * 0.28f)
            }
            canvas.drawPath(checkPath, checkPaint)

            val checkImageView = ImageView(context).apply {
                setImageBitmap(checkBitmap)
                val marginEnd = (8 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    setMargins(0, 0, marginEnd, 0)
                }
            }
            container.addView(checkImageView)

            // Label TextView
            val labelView = TextView(context).apply {
                text = toastLabel.removePrefix("✓ ") // Handled by checkmark icon
                setTextColor(android.graphics.Color.WHITE)
                textSize = 12f // sp
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                letterSpacing = 0.06f
                isSingleLine = true
            }
            container.addView(labelView)

            hudView = container
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

