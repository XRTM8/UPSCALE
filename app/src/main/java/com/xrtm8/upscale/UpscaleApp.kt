package com.xrtm8.upscale

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.xrtm8.upscale.engine.ExportRepository

class UpscaleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val isArabic = resources.configuration.locales.get(0)?.language == "ar"
        val channel = NotificationChannel(
            ExportRepository.EXPORT_CHANNEL_ID,
            if (isArabic) "تصدير الفيديو" else "Video Export",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = if (isArabic) {
                "يعرض تقدم تحسين وتصدير الفيديو محلياً."
            } else {
                "Shows progress while enhancing and exporting videos locally."
            }
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
