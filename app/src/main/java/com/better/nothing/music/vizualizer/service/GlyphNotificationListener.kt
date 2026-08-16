package com.better.nothing.music.vizualizer.service

import com.better.nothing.music.vizualizer.ui.MainActivity
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GlyphNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }
}
