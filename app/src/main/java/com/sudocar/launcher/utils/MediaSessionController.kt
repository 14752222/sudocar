package com.sudocar.launcher.utils

import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import android.view.KeyEvent

/**
 * MediaSession 控制器
 * 通过 MediaSessionManager 获取当前活跃的媒体会话，支持所有支持 MediaSession 的音乐 APP
 *
 * 前提：需要 NotificationListenerService 权限
 * AndroidManifest.xml 中声明：
 *   <service android:name=".service.MediaNotificationListenerService"
 *       android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
 *       <intent-filter>
 *           <action android:name="android.service.notification.NotificationListenerService" />
 *       </intent-filter>
 *   </service>
 *
 * 并在使用前引导用户开启通知访问权限：
 *   startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
 */
class MediaSessionController(private val context: Context) {

    private val sessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private var activeController: MediaController? = null
    private var registeredCallback: MediaController.Callback? = null

    // 当前音乐包名，用于发送广播控制
    private var currentPackageName: String = ""

    data class MediaInfo(
        val packageName: String,
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,     // 毫秒，-1 表示未知
        val position: Long,     // 毫秒
        val isPlaying: Boolean,
        val playbackSpeed: Float
    )

    var onMediaInfoChanged: ((MediaInfo) -> Unit)? = null

    /**
     * 注册监听，需要先确保 NotificationListenerService 已授权
     * @param listenerComponent 你的 NotificationListenerService 的 ComponentName
     */
    fun register(listenerComponent: android.content.ComponentName) {
        try {
            // 获取当前活跃的 MediaController 列表
            val controllers = sessionManager.getActiveSessions(listenerComponent)
            Log.d(TAG, "活跃 MediaSession 数量: ${controllers.size}")
            controllers.forEach {
                Log.d(TAG, "  - package: ${it.packageName}")
            }

            // 取第一个（优先级最高的）
            bindController(controllers.firstOrNull())

            // 监听 session 列表变化（切换音乐 APP 时触发）
            sessionManager.addOnActiveSessionsChangedListener(
                { newControllers ->
                    Log.d(TAG, "活跃 Session 变化，数量: ${newControllers?.size}")
                    bindController(newControllers?.firstOrNull())
                },
                listenerComponent
            )

            Log.d(TAG, "MediaSessionManager 注册成功")
        } catch (e: SecurityException) {
            Log.e(TAG, "没有通知访问权限，请引导用户开启: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "注册失败: ${e.message}")
        }
    }

    private fun bindController(controller: MediaController?) {
        // 先解绑旧的
        registeredCallback?.let { activeController?.unregisterCallback(it) }
        registeredCallback = null
        activeController = null

        if (controller == null) {
            Log.d(TAG, "没有活跃的 MediaController")
            return
        }

        activeController = controller
        currentPackageName = controller.packageName ?: ""

        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                Log.d(TAG, "onMetadataChanged: title=${metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}")
                notifyMediaInfo()
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                Log.d(TAG, "onPlaybackStateChanged: state=${state?.state}")
                notifyMediaInfo()
            }

            override fun onSessionDestroyed() {
                Log.d(TAG, "onSessionDestroyed: ${controller.packageName}")
                registeredCallback?.let { activeController?.unregisterCallback(it) }
                activeController = null
                currentPackageName = ""
                registeredCallback = null
            }
        }

        controller.registerCallback(cb)
        registeredCallback = cb

        // 立即推送一次当前状态
        notifyMediaInfo()
        Log.d(TAG, "已绑定 MediaController: ${controller.packageName}")
    }

    fun unregister() {
        try {
            registeredCallback?.let { activeController?.unregisterCallback(it) }
            sessionManager.removeOnActiveSessionsChangedListener { }
            activeController = null
            registeredCallback = null
            currentPackageName = ""
            Log.d(TAG, "MediaSession 已注销")
        } catch (e: Exception) {
            Log.e(TAG, "注销失败: ${e.message}")
        }
    }

    private fun notifyMediaInfo() {
        val info = getCurrentMediaInfo() ?: return
        onMediaInfoChanged?.invoke(info)
    }

    /** 主动查询当前媒体信息 */
    fun getCurrentMediaInfo(): MediaInfo? {
        val controller = activeController ?: return null
        val metadata = controller.metadata
        val state = controller.playbackState

        return MediaInfo(
            packageName = controller.packageName ?: "",
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: "未知歌曲",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: "未知艺术家",
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L,
            position = state?.position ?: 0L,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            playbackSpeed = state?.playbackSpeed ?: 1.0f
        )
    }

    // ---- 控制方法 ----

    /**
     * 播放/暂停
     * 优先使用 MediaSession，失败则使用广播
     */
    fun playPause(): Boolean {
        // 1. 尝试 MediaSession
        val ctrl = activeController
        val controls = ctrl?.transportControls
        
        if (ctrl != null && controls != null) {
            try {
                if (ctrl.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controls.pause()
                } else {
                    controls.play()
                }
                Log.d(TAG, "playPause: via MediaSession, package=${ctrl.packageName}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "MediaSession playPause failed: ${e.message}")
            }
        }

        // 2. 备用：发送广播到当前音乐包
        if (currentPackageName.isNotEmpty()) {
            return sendMediaButtonBroadcast(currentPackageName, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
        
        // 3. 最后备用：发送广播到所有音乐APP
        return sendMediaButtonBroadcast(null, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    /**
     * 下一首
     */
    fun next(): Boolean {
        val ctrl = activeController
        val controls = ctrl?.transportControls
        
        if (ctrl != null && controls != null) {
            try {
                controls.skipToNext()
                Log.d(TAG, "next: via MediaSession")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "MediaSession next failed: ${e.message}")
            }
        }

        if (currentPackageName.isNotEmpty()) {
            return sendMediaButtonBroadcast(currentPackageName, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        
        return sendMediaButtonBroadcast(null, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    /**
     * 上一首
     */
    fun previous(): Boolean {
        val ctrl = activeController
        val controls = ctrl?.transportControls
        
        if (ctrl != null && controls != null) {
            try {
                controls.skipToPrevious()
                Log.d(TAG, "previous: via MediaSession")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "MediaSession previous failed: ${e.message}")
            }
        }

        if (currentPackageName.isNotEmpty()) {
            return sendMediaButtonBroadcast(currentPackageName, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        
        return sendMediaButtonBroadcast(null, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(positionMs: Long): Boolean {
        val ctrl = activeController ?: return false
        val controls = ctrl.transportControls ?: return false
        return try {
            controls.seekTo(positionMs)
            true
        } catch (e: Exception) {
            Log.e(TAG, "seekTo failed: ${e.message}")
            false
        }
    }

    /**
     * 发送媒体按键广播
     * @param targetPackage 目标包名，null 表示不限制
     */
    private fun sendMediaButtonBroadcast(targetPackage: String?, keyCode: Int): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                targetPackage?.let { setPackage(it) }
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            context.sendBroadcast(intent)

            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                targetPackage?.let { setPackage(it) }
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendBroadcast(upIntent)

            Log.d(TAG, "sendMediaButtonBroadcast: keyCode=$keyCode, target=$targetPackage")
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendMediaButtonBroadcast failed: ${e.message}")
            false
        }
    }

    /** 获取当前包名 */
    fun getCurrentPackageName(): String = currentPackageName

    companion object {
        private const val TAG = "MediaSessionController"
    }
}
