package com.sudocar.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 监听 QQ 音乐车机版广播，获取歌曲信息和歌词
 */
class QQMusicBroadcastReceiver : BroadcastReceiver() {

    data class MusicInfo(
        val title: String,
        val artist: String,
        val album: String,
        val isPlaying: Boolean,
        val lyric: String = ""  // 新增歌词字段
    )

    /** Fragment 设置此回调以接收歌曲信息更新（已在主线程回调） */
    var onMusicInfoChanged: ((MusicInfo) -> Unit)? = null

    /** Fragment 设置此回调以接收歌词更新 */
    var onLyricReceived: ((String) -> Unit)? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("QQMusicReceiver", "收到广播: action=${intent.action}")
        Log.d("QQMusicReceiver", "extras=${intent.extras?.keySet()?.joinToString()}")
        
        // 打印所有 extras 的值
        intent.extras?.let { extras ->
            for (key in extras.keySet()) {
                try {
                    Log.d("QQMusicReceiver", "  [$key]${extras.keySet()}")
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

        // 尝试获取歌词（QQ音乐可能通过广播推送歌词）
        val lyric = intent.getStringExtra("lyric")
            ?: intent.getStringExtra("lyric_content")
            ?: intent.getStringExtra("currentLyric")
            ?: intent.getStringExtra("playingLyric")
            ?: intent.getStringExtra("lrc")
            ?: ""

        if (lyric.isNotEmpty()) {
            Log.i("QQMusicReceiver", "🎉 收到歌词广播，长度=${lyric.length}")
            onLyricReceived?.invoke(lyric)
        }

        Log.d("QQMusicReceiver", "解析结果: title=$title, artist=$artist, playing=$isPlaying, hasLyric=${lyric.isNotEmpty()}")

        onMusicInfoChanged?.invoke(MusicInfo(title, artist, album, isPlaying, lyric))
    }
}
