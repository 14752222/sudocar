package com.sudocar.launcher.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 媒体通知监听服务
 * 用于获取系统媒体信息，需要用户手动授权"通知访问"权限
 *
 * 使用方式：
 * 1. 在 AndroidManifest.xml 中声明此服务
 * 2. 引导用户开启通知访问：startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
 * 3. 在需要的地方使用 MediaSessionController.register(ComponentName(this, MediaNotificationListenerService::class.java))
 */
class MediaNotificationListenerService : NotificationListenerService() {

    companion object {
        // 这里用静态变量存储回调，供外部访问（简化实现）
        // 生产环境建议用 LiveData / EventBus / 依赖注入
        var onNotificationPosted: ((StatusBarNotification) -> Unit)? = null
        var onNotificationRemoved: ((StatusBarNotification) -> Unit)? = null

        var isServiceConnected = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isServiceConnected = true
        Log.d("MediaNotification", "服务已创建")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceConnected = false
        Log.d("MediaNotification", "服务已销毁")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let {
            // 只关心媒体通知
            if (it.packageName.contains("music") || it.packageName.contains("player") || it.packageName.contains("audio")) {
                Log.d("MediaNotification", "收到媒体通知: ${it.packageName}, tag=${it.tag}, id=${it.id}")
                onNotificationPosted?.invoke(it)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let {
            Log.d("MediaNotification", "移除通知: ${it.packageName}")
            onNotificationRemoved?.invoke(it)
        }
    }
}
