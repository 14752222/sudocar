package com.sudocar.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 监听 QQ 音乐车机版广播，获取歌曲信息和播放状态
 */
class QQMusicBroadcastReceiver : BroadcastReceiver() {

    data class MusicInfo(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean
    )

    /** Fragment 设置此回调以接收歌曲信息更新（已在主线程回调） */
    var onMusicInfoChanged: ((MusicInfo) -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("QQMusicReceiver", "收到广播: action=${intent.action}")
        Log.d("QQMusicReceiver", "extras=${intent.extras?.keySet()?.joinToString()}")
        
        // 打印所有 extras 的值
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                try {
                    Log.d("QQMusicReceiver", "  [$key] = ${extras.get(key)}")
                } catch (e: Exception) {
                    Log.d("QQMusicReceiver", "  [$key] = (无法读取)")
                }
            }
        }

        // 尝试获取歌曲信息
        val title = intent.getStringExtra("track") 
            ?: intent.getStringExtra("title")
            ?: intent.getStringExtra("song")
            ?: intent.getStringExtra("song_name")
            ?: "未知歌曲"
            
        val artist = intent.getStringExtra("artist")
            ?: intent.getStringExtra("singer")
            ?: intent.getStringExtra("song_artist")
            ?: ""
            
        val album = intent.getStringExtra("album")
            ?: intent.getStringExtra("album_name")
            ?: ""
            
        // 播放状态
        var isPlaying = intent.getBooleanExtra("playing", false)
        if (!isPlaying) {
            isPlaying = intent.getBooleanExtra("playState", false)
        }
        if (!isPlaying) {
            val state = intent.getIntExtra("playback_state", -1)
            isPlaying = (state == 2) // PLAYING 状态
        }

        Log.d("QQMusicReceiver", "解析结果: title=$title, artist=$artist, playing=$isPlaying")

        onMusicInfoChanged?.invoke(MusicInfo(title, artist, album, isPlaying))
    }
}
