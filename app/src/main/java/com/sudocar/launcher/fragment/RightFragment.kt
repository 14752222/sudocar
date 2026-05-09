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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.sudocar.launcher.adapter.LyricAdapter
import com.sudocar.launcher.databinding.FragmentRightBinding
import com.sudocar.launcher.receiver.QQMusicBroadcastReceiver
import com.sudocar.launcher.service.MediaNotificationListenerService
import com.sudocar.launcher.utils.LyricController
import com.sudocar.launcher.utils.LyricLine
import com.sudocar.launcher.utils.LyricParser
import com.sudocar.launcher.utils.MediaSessionController
import com.sudocar.launcher.utils.QQMusicController
import com.sudocar.launcher.utils.QQMusicLyricController

/**
 * 右侧音乐控制面板 Fragment
 *
 * 功能：
 * 1. 通过 MediaSession 获取歌曲信息（支持所有音乐 APP）
 * 2. 通过 QQ 音乐 AIDL 获取歌词（仅 QQ 音乐车机版）
 * 3. 通过网络 API 获取歌词（备用方案）
 * 4. 播放控制（上一首/播放暂停/下一首）
 * 5. 歌词逐行滚动显示
 */
class RightFragment : Fragment() {

    private var _binding: FragmentRightBinding? = null
    private val binding get() = _binding!!

    private val musicReceiver by lazy { QQMusicBroadcastReceiver() }
    private val qqLyricController by lazy { QQMusicLyricController(requireContext()) }
    private val lyricController by lazy { LyricController() }
    private val mediaSessionController by lazy { MediaSessionController(requireContext()) }

    // 歌词适配器
    private lateinit var lyricAdapter: LyricAdapter

    // 当前歌曲信息
    private var currentSongTitle = ""
    private var currentArtist = ""
    private var isPlaying = false
    private var hasLyricFromQQMusic = false

    // 歌词数据
    private var lyricLines = listOf<LyricLine>()

    // 歌词刷新
    private val lyricHandler = Handler(Looper.getMainLooper())
    private val lyricUpdateRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && lyricLines.isNotEmpty()) {
                updateCurrentLyricLine()
            }
            lyricHandler.postDelayed(this, 500) // 500ms 更新一次
        }
    }

    // 歌词获取轮询
    private val lyricFetchRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && currentSongTitle.isNotEmpty() && currentSongTitle != "未知歌曲") {
                val isQQMusicCar = mediaSessionController.getCurrentPackageName() == PKG_QQMUSICCAR

                if (isQQMusicCar) {
                    if (!hasLyricFromQQMusic) {
                        qqLyricController.requestLyricManual()
                        lyricHandler.postDelayed({
                            if (!hasLyricFromQQMusic) {
                                Log.d(TAG, "AIDL 未获取到歌词，尝试网络 API")
                                lyricController.requestLyric(currentSongTitle, currentArtist)
                            }
                        }, 1000)
                    }
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
        initLyricRecyclerView()
        initControllers()
        setupControlButtons()
        startLyricUpdate()
    }

    private fun initLyricRecyclerView() {
        lyricAdapter = LyricAdapter()

        // 获取颜色
        val normalColor = requireContext().getColor(com.sudocar.launcher.R.color.music_text_secondary)
        val highlightColor = requireContext().getColor(com.sudocar.launcher.R.color.music_accent)
        lyricAdapter.setColors(normalColor, highlightColor)

        binding.rvLyric.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLyric.adapter = lyricAdapter
    }

    private fun initControllers() {
        // ===== 1. QQ 音乐 AIDL 歌词控制器 =====
        qqLyricController.onLyricReceived = { lyric ->
            if (lyric.isNotEmpty()) {
                loadLyric(lyric, "QQ音乐")
                hasLyricFromQQMusic = true
            }
        }
        qqLyricController.bindService()

        // ===== 2. 网络歌词控制器 =====
        lyricController.onLyricReceived = { lyric ->
            Log.d(TAG, "🎵 网络歌词获取成功，长度=${lyric.length}")
            if (lyric.isNotEmpty()) {
                loadLyric(lyric, "网络")
            } else {
                showEmptyLyric("暂无歌词")
            }
        }
        lyricController.onError = { error ->
            Log.e(TAG, "❌ 网络歌词获取失败: $error")
            showEmptyLyric("歌词获取失败")
        }

        // ===== 3. MediaSession 控制器 =====
        setupMediaSessionController()

        // ===== 4. 广播接收器 =====
        setupBroadcastReceiver()

        // 初始显示
        binding.tvSongTitle.text = "未播放"
        binding.tvArtist.text = "--"
        showEmptyLyric("等待播放...")
    }

    private fun setupMediaSessionController() {
        val listenerComponent = ComponentName(
            requireContext(),
            MediaNotificationListenerService::class.java
        )

        val hasPermission = checkNotificationPermission()
        if (!hasPermission) {
            Log.w(TAG, "⚠️ 未开启通知访问权限，MediaSession 将不可用")
        }

        mediaSessionController.onMediaInfoChanged = { info ->
            binding.tvSongTitle.text = info.title
            binding.tvArtist.text = info.artist
            isPlaying = info.isPlaying
            updatePlayPauseIcon()

            val songChanged = currentSongTitle != info.title || currentArtist != info.artist
            if (songChanged && info.title != "未知歌曲") {
                Log.d(TAG, "🎵 歌曲变化: ${info.title} - ${info.artist}")
                currentSongTitle = info.title
                currentArtist = info.artist

                // 重置状态
                hasLyricFromQQMusic = false
                lyricLines = emptyList()
                showEmptyLyric("正在获取歌词...")

                // 请求歌词
                if (info.packageName != PKG_QQMUSICCAR) {
                    lyricController.requestLyric(info.title, info.artist)
                } else {
                    qqLyricController.requestLyricManual()
                    lyricHandler.postDelayed({
                        if (!hasLyricFromQQMusic) {
                            lyricController.requestLyric(currentSongTitle, currentArtist)
                        }
                    }, 2000)
                }
            }
        }

        try {
            mediaSessionController.register(listenerComponent)
            Log.i(TAG, "✅ MediaSession 注册成功")
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ MediaSession 注册失败: ${e.message}")
        }
    }

    private fun setupBroadcastReceiver() {
        musicReceiver.onLyricReceived = { lyric ->
            if (lyric.isNotEmpty()) {
                loadLyric(lyric, "QQ音乐广播")
                hasLyricFromQQMusic = true
            }
        }

        musicReceiver.onMusicInfoChanged = { info ->
            if (info.lyric.isNotEmpty()) {
                loadLyric(info.lyric, "QQ音乐广播")
                hasLyricFromQQMusic = true
            }

            if (binding.tvSongTitle.text == "未播放" || binding.tvSongTitle.text == "未知歌曲") {
                binding.tvSongTitle.text = info.title
                binding.tvArtist.text = info.artist
                isPlaying = info.isPlaying
                updatePlayPauseIcon()

                if (currentSongTitle != info.title) {
                    currentSongTitle = info.title
                    currentArtist = info.artist
                    hasLyricFromQQMusic = false
                    showEmptyLyric("正在获取歌词...")
                    lyricController.requestLyric(info.title, info.artist)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.tencent.qqmusiccar.action")
            addAction("com.tencent.qqmusiccar.action.META_CHANGED")
            addAction("com.tencent.qqmusiccar.action.PLAYSTATE_CHANGED")
            addAction("com.tencent.qqmusiccar.playstate_changed")
            addAction("com.tencent.qqmusiccar.metadata_changed")
            addAction("com.tencent.qqmusiccar.action.LYRIC_CHANGED")
            addAction("com.tencent.qqmusiccar.lyric_changed")
            addAction("com.tencent.qqmusic.playstate_changed")
            addAction("com.tencent.qqmusic.metadata_changed")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.metachanged")
        }

        try {
            requireContext().registerReceiver(musicReceiver, filter, Context.RECEIVER_EXPORTED)
            Log.i(TAG, "✅ 广播接收器注册成功")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 广播接收器注册失败: ${e.message}")
        }
    }

    private fun setupControlButtons() {
        val ctx = requireContext()

        binding.btnPrev.setOnClickListener {
            Log.d(TAG, "⏮️ 点击: 上一首")
            val success = tryMediaSession { it.previous() }
            if (!success) {
                QQMusicController.prev(ctx)
            }
        }

        binding.btnPlayPause.setOnClickListener {
            Log.d(TAG, "⏯️ 点击: 播放/暂停")
            val success = tryMediaSession { it.playPause() }
            if (!success) {
                QQMusicController.playPause(ctx)
            }
            isPlaying = !isPlaying
            updatePlayPauseIcon()
        }

        binding.btnNext.setOnClickListener {
            Log.d(TAG, "⏭️ 点击: 下一首")
            val success = tryMediaSession { it.next() }
            if (!success) {
                QQMusicController.next(ctx)
            }
        }
    }

    private fun startLyricUpdate() {
        lyricHandler.post(lyricUpdateRunnable)
        lyricHandler.post(lyricFetchRunnable)
    }

    /**
     * 加载歌词并显示
     */
    private fun loadLyric(lyric: String, source: String) {
        // 解析歌词
        lyricLines = LyricParser.parse(lyric)

        if (lyricLines.isEmpty()) {
            showEmptyLyric("暂无歌词")
            return
        }

        // 更新适配器数据
        lyricAdapter.setData(lyricLines)

        // 添加空行作为头部和尾部，使第一行和最后一行可以滚动到中间
        // 这里通过 padding 实现，不需要修改数据

        Log.d(TAG, "📝 歌词加载成功 ($source): 共 ${lyricLines.size} 行")
    }

    /**
     * 显示空歌词状态
     */
    private fun showEmptyLyric(message: String) {
        lyricLines = emptyList()
        lyricAdapter.setData(listOf(LyricLine(0, message)))
    }

    /**
     * 更新当前歌词行（根据播放进度）
     */
    private fun updateCurrentLyricLine() {
        if (lyricLines.isEmpty()) return

        // 获取当前播放位置
        val position = mediaSessionController.getCurrentPosition()

        // 找到当前应该高亮的行
        val currentIndex = LyricParser.getCurrentLineIndex(lyricLines, position)

        if (currentIndex >= 0 && currentIndex != lyricAdapter.getCurrentLineIndex()) {
            lyricAdapter.setCurrentLine(currentIndex)

            // 滚动到当前行
            val layoutManager = binding.rvLyric.layoutManager as LinearLayoutManager
            val recyclerViewHeight = binding.rvLyric.height
            val itemHeight = recyclerViewHeight / 5 // 假设显示5行
            val offset = recyclerViewHeight / 2 - itemHeight / 2

            layoutManager.scrollToPositionWithOffset(currentIndex, offset)
        }
    }

    private fun checkNotificationPermission(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            requireContext().contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(requireContext().packageName) == true
    }

    private fun tryMediaSession(action: (MediaSessionController) -> Boolean): Boolean {
        return try {
            action(mediaSessionController)
        } catch (e: Exception) {
            Log.w(TAG, "MediaSession 控制失败: ${e.message}")
            false
        }
    }

    fun refreshTheme() {
        try {
            val normalColor = requireContext().getColor(com.sudocar.launcher.R.color.music_text_secondary)
            val highlightColor = requireContext().getColor(com.sudocar.launcher.R.color.music_accent)
            lyricAdapter.setColors(normalColor, highlightColor)
            lyricAdapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Log.e(TAG, "主题刷新失败: ${e.message}")
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play
        binding.btnPlayPause.setImageResource(icon)
    }

    /** 处理方向盘多媒体按键 */
    fun handleKeyEvent(keyCode: Int): Boolean {
        val ctx = requireContext()
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                val success = tryMediaSession { it.previous() }
                if (!success) QQMusicController.prev(ctx)
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                val success = tryMediaSession { it.next() }
                if (!success) QQMusicController.next(ctx)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val success = tryMediaSession { it.playPause() }
                if (!success) QQMusicController.playPause(ctx)
                isPlaying = !isPlaying
                updatePlayPauseIcon()
                true
            }
            else -> false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lyricHandler.removeCallbacksAndMessages(null)
        mediaSessionController.unregister()
        qqLyricController.unbindService()
        lyricController.destroy()
        try {
            requireContext().unregisterReceiver(musicReceiver)
        } catch (_: Exception) {}
        _binding = null
    }

    companion object {
        private const val TAG = "RightFragment"
        private const val LYRIC_REFRESH_INTERVAL = 5000L
        const val PKG_QQMUSICCAR = "com.tencent.qqmusiccar"
    }
}
