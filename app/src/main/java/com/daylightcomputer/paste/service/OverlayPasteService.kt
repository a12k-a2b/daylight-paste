package com.daylightcomputer.paste.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.daylightcomputer.paste.R
import com.daylightcomputer.paste.ui.MainActivity

class OverlayPasteService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingHandleView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createHandleNotificationChannel()
        startForeground(NOTIFICATION_ID, createHandleNotification())
        createFloatingHandle()
    }

    private fun createFloatingHandle() {
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 12
            y = 0
        }

        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()

        val handleLayout = android.widget.FrameLayout(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(0xFFFAF8F5.toInt()) // DaylightColors.PaperBg
                setStroke((2 * density).toInt(), 0xFF111111.toInt()) // DaylightColors.BorderStrong
            }
            elevation = 0f

            val iconSize = (24 * density).toInt()
            val imageView = android.widget.ImageView(context).apply {
                val iconBitmap = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(iconBitmap)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF111111.toInt()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 2.2f * density
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                val w = iconSize.toFloat()
                val h = iconSize.toFloat()
                canvas.drawRoundRect(w * 0.22f, h * 0.26f, w * 0.78f, h * 0.88f, 3f * density, 3f * density, paint)
                canvas.drawRoundRect(w * 0.35f, h * 0.12f, w * 0.65f, h * 0.30f, 2f * density, 2f * density, paint)
                setImageBitmap(iconBitmap)
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                )
            }
            addView(imageView)

            setOnClickListener {
                try {
                    val appIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(appIntent)
                } catch (ignored: Exception) {}
            }
        }

        floatingHandleView = handleLayout
        try {
            windowManager?.addView(floatingHandleView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        floatingHandleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (ignored: Exception) {}
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createHandleNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daylight Paste Handle",
                NotificationManager.IMPORTANCE_MIN
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createHandleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Daylight Paste Floating Handle")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "daylight_paste_handle_channel"
        private const val NOTIFICATION_ID = 5952
    }
}
