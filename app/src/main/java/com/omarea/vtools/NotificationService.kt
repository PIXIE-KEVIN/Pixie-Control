package com.omarea.vtools

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.omarea.shared.SceneMode

class NotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) {
            return
        }
        if (sbn.isClearable) {
            val instance = SceneMode.getInstanceOrInit(null, null)
            if (instance == null) {
                Log.e("vtool-disnotice", "辅助服务未启动")
                return
            } else {
                /*
                if (sbn.isOngoing) {
                    // 正在前台运行！！？
                }
                */
                if (instance.onNotificationPosted()) {
                    cancelNotification(sbn.key)
                }
            }
        }
    }
}
