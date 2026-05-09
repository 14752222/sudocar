package com.sudocar.launcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.Executors

/**
 * 通过 Binder IPC 向 QQ 音乐车机版请求歌词
 *
 * 来源：逆向分析 com.tencent.qqmusiccar v3.11.0.5 的 AndroidManifest
 *
 * 服务信息（已确认）：
 *   包名:    com.tencent.qqmusiccar
 *   服务类:  com.tencent.qqmusiccar.third.api.QQMusicApiService
 *   Action:  com.tencent.qqmusiccar.third.api.QQMusicApiService
 *   exported: true，无 permission 限制，可直接绑定
 *
 * 广播命令（已确认）：
 *   Action: com.tencent.qqmusiccar.action
 *   接收器: BroadcastReceiverCenterForThird（exported=true）
 *
 * ContentProvider（已确认，需要权限）：
 *   Authority: com.tencent.qqmusiccar.common.provider
 *   Permission: com.tencent.qqmusiccar.common.provider
 *
 * PlayProcessProvider（新发现）：
 *   Authority: com.tencent.qqmusiccar.business.provider.PlayProcessProvider
 *   用途: 查询播放状态、歌词、歌曲信息
 */
class QQMusicLyricController(private val context: Context) {

    private var musicService: IBinder? = null
    private var isConnected = false
    private var binderCallback: IQQMusicApiCallback? = null

    /** Fragment 设置此回调以接收歌词更新（已切换到主线程） */
    var onLyricReceived: ((String) -> Unit)? = null

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val PKG             = "com.tencent.qqmusiccar"
        const val SERVICE_CLASS   = "com.tencent.qqmusiccar.third.api.QQMusicApiService"
        // 服务绑定用 Action（Manifest 中 intent-filter 声明的 action）
        const val SERVICE_ACTION  = "com.tencent.qqmusiccar.third.api.QQMusicApiService"
        // 广播命令 Action（BroadcastReceiverCenterForThird，exported=true）
        const val ACTION_COMMAND  = "com.tencent.qqmusiccar.action"
        // ContentProvider authority（需要 com.tencent.qqmusiccar.common.provider 权限）
        const val PROVIDER_AUTH   = "com.tencent.qqmusiccar.common.provider"
        // PlayProcessProvider authority（新发现，用于查询播放状态）
        const val PLAY_PROVIDER_AUTH = "com.tencent.qqmusiccar.business.provider"

        // AIDL descriptor — 从日志确认的真实值
        // 日志显示: 真实 descriptor = com.tencent.qqmusic.third.api.contract.IQQMusicApi
        const val DESCRIPTOR = "com.tencent.qqmusic.third.api.contract.IQQMusicApi"

        // 保留其他可能的 descriptor 作为备选
        val DESCRIPTORS = listOf(
            DESCRIPTOR,
            "com.tencent.qqmusiccar.third.api.IQQMusicApi",
            "com.tencent.qqmusiccar.third.api.QQMusicApiService",
            "com.tencent.qqmusiccar.IQQMusicApi"
        )

        // Transaction code：AIDL 默认从 1 开始
        val TRANSACTION_CODES = listOf(1, 2, 3)

        // 命令字符串（向服务发送的指令）
        val LYRIC_COMMANDS = listOf(
            "getCurrentLyric",
            "getLyric",
            "lyric",
            "currentLyric"
        )

        // IBinder.FIRST_CALL_TRANSACTION = 1
        const val FIRST_CALL_TRANSACTION = 1

        private const val TAG = "QQMusicLyric"
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = service
            isConnected = true

            // 打印真实 descriptor，帮助调试
            try {
                Log.i(TAG, "✅ 服务连接成功，真实 descriptor = ${service?.interfaceDescriptor}")
            } catch (e: Exception) {
                Log.i(TAG, "✅ 服务连接成功，descriptor 不可读: ${e.message}")
            }

            try {
                binderCallback = createCallback()
                // 稍等 500ms 再请求，让服务完全就绪
                mainHandler.postDelayed({ tryRequest() }, 500)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 创建回调失败: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isConnected = false
            binderCallback = null
            Log.w(TAG, "⚠️ 服务断开")
        }
    }

    /** 绑定 QQ 音乐车机版服务（优先用 Action 绑定，更规范） */
    fun bindService() {
        if (isConnected) return
        mainHandler.postDelayed({
            // 方式一：用 Action 绑定（Manifest 中 intent-filter 声明的方式）
            val intent = Intent(SERVICE_ACTION).apply {
                setPackage(PKG)
            }
            try {
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Log.i(TAG, "🔗 Action 绑定结果: $bound")
                if (!bound) {
                    // 方式二：回退到 ClassName 绑定
                    bindByClassName()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Action 绑定异常: ${e.message}，尝试 ClassName 绑定")
                bindByClassName()
            }
        }, 500)
    }

    private fun bindByClassName() {
        val intent = Intent().setClassName(PKG, SERVICE_CLASS)
        try {
            val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "🔗 ClassName 绑定结果: $bound")
        } catch (e: Exception) {
            Log.e(TAG, "❌ ClassName 绑定异常: ${e.message}")
        }
    }

    fun unbindService() {
        if (isConnected) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            isConnected = false
            musicService = null
        }
    }

    /** 手动请求当前歌词，由 Fragment 定时调用 */
    fun requestLyricManual() {
        Log.d(TAG, "🔄 开始请求歌词...")

        // 先尝试 PlayProcessProvider 查询（最推荐的方式）
        val lyricFromPlayProvider = queryLyricFromPlayProvider()
        if (lyricFromPlayProvider.isNotEmpty()) {
            Log.i(TAG, "🎉 从 PlayProcessProvider 获取歌词成功")
            mainHandler.post { onLyricReceived?.invoke(lyricFromPlayProvider) }
            return
        }

        // 尝试 common.provider ContentProvider
        val lyricFromProvider = queryLyricFromProvider()
        if (lyricFromProvider.isNotEmpty()) {
            Log.i(TAG, "🎉 从 common.provider 获取歌词成功")
            mainHandler.post { onLyricReceived?.invoke(lyricFromProvider) }
            return
        }

        // 发送广播请求歌词（QQ音乐可能需要收到请求后才推送）
        sendLyricRequestBroadcast()

        // 回退到 AIDL 绑定方式
        if (!isConnected || musicService == null) {
            Log.w(TAG, "⚠️ AIDL 服务未连接，尝试重新绑定...")
            bindService()
            return
        }
        tryRequest()
    }

    /**
     * 快速检查：QQ 音乐车机版是否支持歌词获取
     * 返回 true 表示支持（通过任意方式获取到歌词）
     */
    fun isLyricAvailable(): Boolean {
        // 检查 ContentProvider 是否可用
        val lyric1 = queryLyricFromPlayProvider()
        if (lyric1.isNotEmpty()) return true

        val lyric2 = queryLyricFromProvider()
        if (lyric2.isNotEmpty()) return true

        return false
    }

    /**
     * 发送广播请求歌词
     * QQ 音乐车机版收到广播后可能会主动推送歌词
     */
    private fun sendLyricRequestBroadcast() {
        try {
            // 主广播 - 请求歌词
            val intent = android.content.Intent(ACTION_COMMAND).apply {
                setPackage(PKG)
                putExtra("cmd", "getLyric")
                putExtra("command", "getCurrentLyric")
                putExtra("action", "requestLyric")
                putExtra("type", "lyric")
                putExtra("needLyric", true)
                putExtra("from", context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "📢 已发送歌词请求广播: $ACTION_COMMAND")

            // 尝试 WeDrive 协议
            val wedriveIntent = android.content.Intent("com.wedrive.action.COMMAND_SEND").apply {
                setPackage(PKG)
                putExtra("cmd", "getLyric")
                putExtra("action", "requestLyric")
            }
            context.sendBroadcast(wedriveIntent)
            Log.d(TAG, "📢 已发送 WeDrive 歌词请求广播")

            // 尝试启动桌面歌词 Activity（如果 QQ 音乐支持）
            try {
                val lyricIntent = android.content.Intent().apply {
                    setClassName(PKG, "com.tencent.qqmusiccar.business.lyricnew.desklyric.DeskHomeDialogActivity")
                    putExtra("showLyric", true)
                    putExtra("from", context.packageName)
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(lyricIntent)
                Log.d(TAG, "📢 尝试启动桌面歌词 Activity")
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ 启动桌面歌词 Activity 失败: ${e.message}")
            }

        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 发送广播失败: ${e.message}")
        }
    }

    /**
     * 通过 ContentProvider 查询歌词
     * 权限: com.tencent.qqmusiccar.common.provider
     */
    fun queryLyricFromProvider(): String {
        return try {
            val uri = Uri.parse("content://$PROVIDER_AUTH/lyric")
                .buildUpon()
                .appendQueryParameter("current", "true")
                .build()

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                return extractLyricFromCursor(cursor)
            } ?: ""
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ ContentProvider 权限不足: ${e.message}")
            ""
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ ContentProvider 查询失败: ${e.message}")
            ""
        }
    }

    /**
     * 通过 ContentProvider 查询当前播放歌曲信息
     */
    fun queryCurrentSong(): Bundle? {
        return try {
            val uri = Uri.parse("content://$PROVIDER_AUTH/song")
                .buildUpon()
                .appendQueryParameter("current", "true")
                .build()

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val bundle = Bundle()
                    for (i in 0 until cursor.columnCount) {
                        val key = cursor.getColumnName(i)
                        val value = cursor.getString(i)
                        bundle.putString(key, value)
                    }
                    Log.d(TAG, "📦 ContentProvider 歌曲信息: ${bundle.keySet().joinToString()}")
                    return bundle
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 查询歌曲信息失败: ${e.message}")
            null
        }
    }

    private fun extractLyricFromCursor(cursor: Cursor): String {
        if (!cursor.moveToFirst()) return ""

        // 打印所有列名，帮助调试
        val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
        Log.d(TAG, "📦 ContentProvider 列名: ${columns.joinToString()}")

        // 尝试常见的歌词字段名
        val lyricFields = listOf(
            "lyric", "lyric_content", "lyricContent", "content",
            "data", "current_lyric", "currentLyric", "text",
            "playing_lyric", "playingLyric", "lrc"
        )

        for (field in lyricFields) {
            val index = cursor.getColumnIndex(field)
            if (index >= 0) {
                val lyric = cursor.getString(index)
                if (!lyric.isNullOrEmpty()) {
                    Log.i(TAG, "✅ 从 ContentProvider 字段 '$field' 获取歌词")
                    return lyric
                }
            }
        }

        // 如果没有匹配的字段，打印所有字段值帮助调试
        for (i in 0 until cursor.columnCount) {
            val value = cursor.getString(i)?.take(100)
            Log.d(TAG, "  [${cursor.getColumnName(i)}] = $value")
        }

        return ""
    }

    /**
     * 通过 PlayProcessProvider 查询歌词（新发现的 Provider）
     * Authority: com.tencent.qqmusiccar.business.provider.PlayProcessProvider
     */
    fun queryLyricFromPlayProvider(): String {
        return try {
            // 尝试多种可能的 URI 路径
            val uriPaths = listOf(
                "content://$PLAY_PROVIDER_AUTH/lyric",
                "content://$PLAY_PROVIDER_AUTH/current_lyric",
                "content://$PLAY_PROVIDER_AUTH/playing",
                "content://$PLAY_PROVIDER_AUTH/song/lyric"
            )

            for (uriStr in uriPaths) {
                try {
                    val uri = Uri.parse(uriStr)
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val lyric = extractLyricFromCursor(cursor)
                        if (lyric.isNotEmpty()) {
                            Log.i(TAG, "✅ PlayProcessProvider 成功，URI=$uriStr")
                            return lyric
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "PlayProcessProvider URI 尝试失败: $uriStr - ${e.message}")
                }
            }
            ""
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ PlayProcessProvider 权限不足: ${e.message}")
            ""
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ PlayProcessProvider 查询失败: ${e.message}")
            ""
        }
    }

    /**
     * 诊断方法：测试所有歌词获取途径
     * 运行后查看日志，看哪种方式可用
     */
    fun diagnose(): String {
        val sb = StringBuilder()
        sb.appendLine("═══ 歌词获取诊断报告 ═══")

        // 1. 检查 QQ 音乐是否安装
        val pm = context.packageManager
        val isInstalled = try {
            pm.getPackageInfo(PKG, 0)
            true
        } catch (e: Exception) { false }
        sb.appendLine("QQ音乐车机版安装状态: ${if (isInstalled) "✅ 已安装" else "❌ 未安装"}")
        Log.i(TAG, "QQ音乐车机版安装状态: $isInstalled")

        // 2. 测试 PlayProcessProvider
        Log.i(TAG, "测试 PlayProcessProvider...")
        val lyric1 = queryLyricFromPlayProvider()
        val playProviderResult = if (lyric1.isEmpty()) "❌ 无数据" else "✅ 成功 (${lyric1.length} 字符)"
        sb.appendLine("PlayProcessProvider: $playProviderResult")
        Log.i(TAG, "PlayProcessProvider 结果: $playProviderResult")

        // 3. 测试 common.provider
        Log.i(TAG, "测试 common.provider...")
        val lyric2 = queryLyricFromProvider()
        val commonProviderResult = if (lyric2.isEmpty()) "❌ 无数据" else "✅ 成功 (${lyric2.length} 字符)"
        sb.appendLine("common.provider: $commonProviderResult")
        Log.i(TAG, "common.provider 结果: $commonProviderResult")

        // 4. 检查 AIDL 服务连接状态
        sb.appendLine("AIDL 服务连接状态: ${if (isConnected) "✅ 已连接" else "❌ 未连接"}")
        Log.i(TAG, "AIDL 服务连接状态: $isConnected")

        // 5. 查询当前播放歌曲
        val songInfo = queryCurrentSong()
        if (songInfo != null) {
            sb.appendLine("当前播放歌曲: ${songInfo.getString("title") ?: "未知"} - ${songInfo.getString("artist") ?: "未知"}")
        } else {
            sb.appendLine("当前播放歌曲: ❌ 无法获取")
        }

        // 6. 查询播放状态
        val playState = queryPlayState()
        if (playState != null) {
            sb.appendLine("播放状态: ${playState.keySet().joinToString()}")
        } else {
            sb.appendLine("播放状态: ❌ 无法获取")
        }

        sb.appendLine("═══ 诊断完成 ═══")
        sb.appendLine("")
        sb.appendLine("说明：QQ音乐车机版不向第三方应用暴露歌词接口，")
        sb.appendLine("      这是官方限制，无法绕过。")
        sb.appendLine("      当前使用网易云音乐API作为备用方案。")

        Log.i(TAG, sb.toString())
        return sb.toString()
    }

    /**
     * 通过 PlayProcessProvider 查询播放状态
     */
    fun queryPlayState(): Bundle? {
        val uriPaths = listOf(
            "content://$PLAY_PROVIDER_AUTH/state",
            "content://$PLAY_PROVIDER_AUTH/playing",
            "content://$PLAY_PROVIDER_AUTH/current"
        )

        for (uriStr in uriPaths) {
            try {
                val uri = Uri.parse(uriStr)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val bundle = Bundle()
                        for (i in 0 until cursor.columnCount) {
                            val key = cursor.getColumnName(i)
                            val value = cursor.getString(i)
                            bundle.putString(key, value)
                        }
                        Log.d(TAG, "📦 PlayProcessProvider 状态: ${bundle.keySet().joinToString()}")
                        return bundle
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "PlayProcessProvider 状态查询失败: $uriStr - ${e.message}")
            }
        }
        return null
    }

    /**
     * 使用正确的 descriptor 发起歌词请求
     * 根据日志，真实 descriptor: com.tencent.qqmusic.third.api.contract.IQQMusicApi
     */
    private fun tryRequest() {
        val svc = musicService ?: return
        val cb  = binderCallback ?: return

        Log.d(TAG, "🔄 发起歌词请求...")

        // 尝试多种命令
        val commands = listOf(
            "getCurrentLyric",
            "getLyric",
            "getLyricContent",
            "requestLyric"
        )

        // 尝试不同的 transaction code
        val txCodes = listOf(1, 2, 3, 4, 5)

        for (txCode in txCodes) {
            for (cmd in commands) {
                val data  = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(DESCRIPTOR)
                    data.writeString(cmd)
                    // 写入请求参数 Bundle
                    val requestBundle = Bundle().apply {
                        putString("cmd", cmd)
                        putBoolean("needCallback", true)
                    }
                    data.writeInt(1)  // 标记有 Bundle
                    requestBundle.writeToParcel(data, 0)
                    data.writeStrongBinder(cb.asBinder())

                    // 使用同步调用（0）而不是 FLAG_ONEWAY，这样可以读取返回值
                    val ok = svc.transact(txCode, data, reply, 0)
                    if (ok) {
                        Log.d(TAG, "✅ transact 发送成功: txCode=$txCode, cmd=$cmd")

                        // 尝试从 reply 中读取返回值
                        try {
                            reply.readException()
                            val hasBundle = reply.readInt()
                            Log.d(TAG, "📦 reply hasBundle=$hasBundle")
                            if (hasBundle != 0) {
                                val resultBundle = Bundle.CREATOR.createFromParcel(reply)
                                Log.d(TAG, "📦 reply Bundle keys: ${resultBundle.keySet().joinToString()}")
                                handleResult(resultBundle)
                            }
                        } catch (e: Exception) {
                            // 读取 reply 失败是正常的，继续下一个
                        }
                    }
                } catch (e: RemoteException) {
                    // 失败是正常的，继续下一个
                } catch (e: Exception) {
                    // 同上
                } finally {
                    try { data.recycle(); reply.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    /** 创建回调 Binder，QQ 音乐服务会通过它把歌词数据回传给我们
     *
     * 根据日志，真实 descriptor: com.tencent.qqmusic.third.api.contract.IQQMusicApi
     */
    private fun createCallback(): IQQMusicApiCallback {
        return object : IQQMusicApiCallback {
            override fun asBinder(): IBinder {
                return object : android.os.Binder() {
                    override fun onTransact(
                        code: Int, data: Parcel, reply: Parcel?, flags: Int
                    ): Boolean {
                        Log.d(TAG, "📥 onTransact 被调用: code=$code, flags=$flags")
                        return try {
                            when (code) {
                                FIRST_CALL_TRANSACTION -> {
                                    // 读取接口描述符（必须匹配服务端）
                                    data.enforceInterface(DESCRIPTOR)

                                    // 读取返回值（根据 AIDL 规范）
                                    // 先读取是否有数据的标记
                                    val hasData = data.readInt()
                                    Log.d(TAG, "📦 回调数据标记: hasData=$hasData")

                                    val bundle = if (hasData != 0) {
                                        Bundle.CREATOR.createFromParcel(data)
                                    } else null

                                    Log.d(TAG, "📥 回调收到数据，bundle=${bundle != null}")
                                    handleResult(bundle)

                                    // 写入回复
                                    reply?.writeNoException()
                                    true
                                }
                                else -> {
                                    Log.d(TAG, "📥 调用父类 onTransact: code=$code")
                                    super.onTransact(code, data, reply, flags)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 回调 onTransact 异常: ${e.message}", e)
                            // 不要抛出异常，返回 false 让系统处理
                            false
                        }
                    }
                }
            }
            override fun onReturn(bundle: Bundle?) {}
        }
    }

    private fun handleResult(bundle: Bundle?) {
        if (bundle == null) {
            Log.d(TAG, "回调 Bundle 为空")
            return
        }

        logExecutor.execute {
            // 打印所有 key，方便调试定位字段名
            val keys = bundle.keySet()
            Log.d(TAG, "📦 收到回调 Bundle，keys = [${keys.joinToString()}]")
            for (key in keys) {
                try {
                    Log.d(TAG, "  [$key] = ${bundle.get(key)?.toString()?.take(120)}")
                } catch (_: Exception) {}
            }

            val code = bundle.getInt("code", -1)

            // 尝试所有可能的歌词字段名
            val lyric = bundle.getString("lyric")
                ?: bundle.getString("content")
                ?: bundle.getString("data")
                ?: bundle.getString("lyricContent")
                ?: bundle.getString("currentLyric")
                ?: bundle.getString("playingLyric")
                ?: bundle.getString("text")
                ?: ""

            if ((code == 0 || code == 200) && lyric.isNotEmpty()) {
                Log.i(TAG, "🎉 歌词获取成功，长度=${lyric.length}")
                mainHandler.post { onLyricReceived?.invoke(lyric) }
            } else {
                Log.w(TAG, "code=$code，未找到歌词字段，请检查 DEBUG 日志中的 key 列表")
            }
        }
    }

}

interface IQQMusicApiCallback {
    fun asBinder(): IBinder
    fun onReturn(bundle: Bundle?)
}
