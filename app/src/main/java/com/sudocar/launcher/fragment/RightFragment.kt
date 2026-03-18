package com.sudocar.launcher.fragment

import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sudocar.launcher.databinding.FragmentRightBinding
import com.sudocar.launcher.receiver.QQMusicBroadcastReceiver
import com.sudocar.launcher.utils.QQMusicController
import com.sudocar.launcher.utils.QQMusicLyricController

class RightFragment : Fragment() {

    private var _binding: FragmentRightBinding? = null
    private val binding get() = _binding!!

    private val musicReceiver by lazy { QQMusicBroadcastReceiver() }
    private val lyricController by lazy { QQMusicLyricController(requireContext()) }

    private var isPlaying = false

    private val lyricHandler = Handler(Looper.getMainLooper())
    private val lyricRunnable = object : Runnable {
        override fun run() {
            // 仅在播放时轮询歌词，减少 CPU 消耗
            if (isPlaying) {
                lyricController.requestLyricManual()
            }
            lyricHandler.postDelayed(this, LYRIC_REFRESH_INTERVAL)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initControllers()
        setupControlButtons()
        lyricHandler.post(lyricRunnable)
    }

    private fun initControllers() {
        // 歌词控制器 - 延迟初始化
        lyricController.onLyricReceived = { lyric ->
            binding.tvLyric.text = lyric
        }
        lyricController.bindService()

        // 广播接收器
        musicReceiver.onMusicInfoChanged = { info ->
            binding.tvSongTitle.text = info.title
            binding.tvArtist.text = info.artist
            isPlaying = info.isPlaying
            updatePlayPauseIcon()
            Log.d(TAG, "歌曲: ${info.title} - ${info.artist}, playing=$isPlaying")
        }

        val filter = IntentFilter().apply {
            // QQ 音乐车机版广播
            addAction("com.tencent.qqmusiccar.action.META_CHANGED")
            addAction("com.tencent.qqmusiccar.action.PLAYSTATE_CHANGED")
            addAction("com.tencent.qqmusiccar.action.MUSIC_INFO_CHANGED")
            addAction("com.tencent.qqmusiccar.playstate_changed")
            addAction("com.tencent.qqmusiccar.metadata_changed")
            // QQ 音乐手机版广播
            addAction("com.tencent.qqmusic.playstate_changed")
            addAction("com.tencent.qqmusic.metadata_changed")
            // 通用媒体广播
            addAction("android.media.metadata")
            addAction("android.media.playstate")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.android.music.metachanged")
            addAction("com.android.music.queuechanged")
        }
        
        // 动态注册广播接收器
        try {
            requireContext().registerReceiver(musicReceiver, filter, Context.RECEIVER_EXPORTED)
            Log.d(TAG, "广播接收器注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "广播接收器注册失败: ${e.message}")
        }
        
        // 初始显示默认文本
        binding.tvSongTitle.text = "未播放"
        binding.tvArtist.text = "--"
    }

    private fun setupControlButtons() {
        val ctx = requireContext()
        
        binding.btnPrev.setOnClickListener { 
            Log.d(TAG, "点击: 上一首")
            QQMusicController.prev(ctx)
        }
        
        binding.btnPlayPause.setOnClickListener { 
            Log.d(TAG, "点击: 播放/暂停")
            QQMusicController.playPause(ctx)
            isPlaying = !isPlaying
            updatePlayPauseIcon()
        }
        
        binding.btnNext.setOnClickListener { 
            Log.d(TAG, "点击: 下一首")
            QQMusicController.next(ctx)
        }
    }

    // 供外部调用刷新主题
    fun refreshTheme() {
        // 刷新主题颜色（字体颜色等）
        try {
            val isNightMode = com.sudocar.launcher.dialog.SettingsDialog.isNightModeEnabled(requireContext())
            val textColor = requireContext().getColor(com.sudocar.launcher.R.color.music_text_primary)
            val secondaryColor = requireContext().getColor(com.sudocar.launcher.R.color.music_text_secondary)
            
            binding.tvSongTitle.setTextColor(textColor)
            binding.tvArtist.setTextColor(secondaryColor)
            binding.tvLyric.setTextColor(secondaryColor)
            
            Log.d("RightFragment", "主题刷新: nightMode=$isNightMode")
        } catch (e: Exception) {
            Log.e("RightFragment", "主题刷新失败: ${e.message}")
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(icon)
    }

    /** 处理方向盘多媒体按键 */
    fun handleKeyEvent(keyCode: Int): Boolean {
        val ctx = requireContext()
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                QQMusicController.prev(ctx)
                Log.d(TAG, "方向盘: 上一首")
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                QQMusicController.next(ctx)
                Log.d(TAG, "方向盘: 下一首")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                QQMusicController.playPause(ctx)
                isPlaying = !isPlaying
                updatePlayPauseIcon()
                Log.d(TAG, "方向盘: 播放/暂停")
                true
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                // 方向盘音量+（如需自定义处理）
                false
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                // 方向盘音量-（如需自定义处理）
                false
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lyricHandler.removeCallbacksAndMessages(null)
        try { 
            requireContext().unregisterReceiver(musicReceiver) 
        } catch (_: Exception) {}
        lyricController.unbindService()
        _binding = null
    }

    companion object {
        private const val TAG = "RightFragment"
        private const val LYRIC_REFRESH_INTERVAL = 3000L  // 改成 3 秒，减少 CPU
    }
}
