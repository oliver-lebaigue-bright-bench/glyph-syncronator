package com.glyphix.app.service

import com.glyphix.app.ui.MainActivity
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class GlyphNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
    }
}
