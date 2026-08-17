package com.better.nothing.music.visualizer.service

import com.better.nothing.music.visualizer.ui.MainActivity
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GlyphNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }
}
