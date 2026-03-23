package com.sudocar.launcher.fragment

import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sudocar.launcher.databinding.FragmentRightBinding
import com.sudocar.launcher.receiver.QQMusicBroadcastReceiver
import com.sudocar.launcher.service.MediaNotificationListenerService
import com.sudocar.launcher.utils.LyricController
import com.sudocar.launcher.utils.MediaSessionController
import com.sudocar.launcher.utils.QQMusicController
import com.sudocar.launcher.utils.QQMusicLyricController

class RightFragment : Fragment() {

    private var _binding: FragmentRightBinding? = null
    private val binding get() = _binding!!

    private val musicReceiver by lazy { QQMusicBroadcastReceiver() }
    private val qqLyricController by lazy { QQMusicLyricController(requireContext()) }
    private val lyricController by lazy { LyricController() }  // 通用歌词控制器
    private val mediaSessionController by lazy { MediaSessionController(requireContext()) }

    // 当前歌曲信息，用于歌词请求
    private var currentSongTitle = ""
    private var currentArtist = ""

    private var isPlaying = false

    private val lyricHandler = Handler(Looper.getMainLooper())
    private val lyricRunnable = object : Runnable {
        override fun run() {
            // 仅在播放时轮询歌词，减少 CPU 消耗
            if (isPlaying) {
                // 优先尝试 QQ 音乐歌词，失败则使用网络歌词
                if (mediaSessionController.getCurrentPackageName().contains("qqmusic")) {
                    qqLyricController.requestLyricManual()
                } else {
                    lyricController.requestLyric(currentSongTitle, currentArtist)
                }
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
        // QQ 音乐歌词控制器（AIDL 方式，仅对 QQ 音乐有效）
        qqLyricController.onLyricReceived = { lyric ->
            binding.tvLyric.text = lyric
        }
        qqLyricController.bindService()

        // 通用歌词控制器（网络 API，对所有音乐 APP 有效）
        lyricController.onLyricReceived = { lyric ->
            if (lyric.isNotEmpty()) {
                binding.tvLyric.text = lyric
            }
        }
        lyricController.onError = { error ->
            Log.d(TAG, "歌词获取失败: $error")
        }

        // ---- MediaSession 控制器 (优先) ----
        val listenerComponent = ComponentName(
            requireContext(),
            MediaNotificationListenerService::class.java
        )

        // 检查通知访问权限
        val enabledListeners = Settings.Secure.getString(
            requireContext().contentResolver,
            "enabled_notification_listeners"
        )
        val hasPermission = enabledListeners?.contains(requireContext().packageName) == true

        if (!hasPermission) {
            Log.w(TAG, "未开启通知访问权限，MediaSession 将不可用")
            // 可选：提示用户开启权限
            // Toast.makeText(context, "建议开启通知访问以获取媒体信息", Toast.LENGTH_LONG).show()
        }

        mediaSessionController.onMediaInfoChanged = { info ->
            binding.tvSongTitle.text = info.title
            binding.tvArtist.text = info.artist
            isPlaying = info.isPlaying
            updatePlayPauseIcon()

            // 更新当前歌曲信息，用于歌词请求
            val songChanged = currentSongTitle != info.title || currentArtist != info.artist
            currentSongTitle = info.title
            currentArtist = info.artist

            // 歌曲变化时请求歌词
            if (songChanged && info.title != "未知歌曲") {
                Log.d(TAG, "歌曲变化，请求歌词: ${info.title} - ${info.artist}")
                // 立即请求歌词
                if (!info.packageName.contains("qqmusic")) {
                    lyricController.requestLyric(info.title, info.artist)
                }
            }
            
            // 显示播放来源（调试用）
            Log.d(TAG, "MediaSession 歌曲: ${info.title} - ${info.artist}, source=${info.packageName}, playing=$isPlaying")
        }

        // 注册 MediaSession 监听
        try {
            mediaSessionController.register(listenerComponent)
            Log.d(TAG, "MediaSession 注册成功")
        } catch (e: SecurityException) {
            Log.e(TAG, "MediaSession 注册失败 (权限未授权): ${e.message}")
        }

        // ---- 广播接收器 (备用) ----
        musicReceiver.onMusicInfoChanged = { info ->
            // 仅当 MediaSession 没有数据时使用广播
            if (binding.tvSongTitle.text == "未播放") {
                binding.tvSongTitle.text = info.title
                binding.tvArtist.text = info.artist
                isPlaying = info.isPlaying
                updatePlayPauseIcon()
                Log.d(TAG, "Broadcast 歌曲: ${info.title} - ${info.artist}, playing=$isPlaying")
            }
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
            // 优先使用 MediaSession 控制，备用广播
            if (!tryMediaSession { it.previous() }) {
                QQMusicController.prev(ctx)
            }
        }
        
        binding.btnPlayPause.setOnClickListener { 
            Log.d(TAG, "点击: 播放/暂停")
            // 优先使用 MediaSession 控制，备用广播
            if (!tryMediaSession { it.playPause() }) {
                QQMusicController.playPause(ctx)
                isPlaying = !isPlaying
                updatePlayPauseIcon()
            }
        }
        
        binding.btnNext.setOnClickListener { 
            Log.d(TAG, "点击: 下一首")
            // 优先使用 MediaSession 控制，备用广播
            if (!tryMediaSession { it.next() }) {
                QQMusicController.next(ctx)
            }
        }
    }

    /** 尝试使用 MediaSessionController 执行操作，返回是否成功 */
    private fun tryMediaSession(action: (MediaSessionController) -> Boolean): Boolean {
        return try {
            action(mediaSessionController)
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession 控制失败: ${e.message}")
            false
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
                if (!tryMediaSession { it.previous() }) {
                    QQMusicController.prev(ctx)
                }
                Log.d(TAG, "方向盘: 上一首")
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                if (!tryMediaSession { it.next() }) {
                    QQMusicController.next(ctx)
                }
                Log.d(TAG, "方向盘: 下一首")
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!tryMediaSession { it.playPause() }) {
                    QQMusicController.playPause(ctx)
                    isPlaying = !isPlaying
                    updatePlayPauseIcon()
                }
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
        // 注销 MediaSession
        mediaSessionController.unregister()
        // 注销歌词控制器
        qqLyricController.unbindService()
        lyricController.destroy()
        // 注销广播接收器
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
