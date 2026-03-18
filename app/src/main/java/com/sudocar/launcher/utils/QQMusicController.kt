package com.sudocar.launcher.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * QQ 音乐车机版播放控制工具类
 * 通过发送 MediaButton 广播控制播放/暂停/上一首/下一首
 */
object QQMusicController {

    private const val PKG = "com.tencent.qqmusiccar"

    /**
     * 发送多媒体按键事件
     */
    private fun sendMediaKey(context: Context, keyCode: Int): Boolean {
        return try {
            // 发送 KEY_DOWN
            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(PKG)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }
            context.sendBroadcast(downIntent)
            
            // 发送 KEY_UP
            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage(PKG)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, keyCode))
            }
            context.sendBroadcast(upIntent)
            
            Log.d("QQMusicController", "发送媒体按键成功: $keyCode")
            true
        } catch (e: Exception) {
            Log.e("QQMusicController", "发送媒体按键失败: ${e.message}")
            false
        }
    }

    fun playPause(context: Context): Boolean = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(context: Context): Boolean = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun prev(context: Context): Boolean = sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
}
