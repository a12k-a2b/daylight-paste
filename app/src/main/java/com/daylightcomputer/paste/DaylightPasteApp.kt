package com.daylightcomputer.paste

import android.app.Application
import android.content.Intent
import android.os.Build
import com.daylightcomputer.paste.service.ClipboardWatcherService

class DaylightPasteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val intent = Intent(this, ClipboardWatcherService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
