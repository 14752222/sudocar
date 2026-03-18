package com.sudocar.launcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
 */
class QQMusicLyricController(private val context: Context) {

    private var musicService: IBinder? = null
    private var isConnected = false
    private var binderCallback: IQQMusicApiCallback? = null

    /** Fragment 设置此回调以接收歌词更新（已切换到主线程） */
    var onLyricReceived: ((String) -> Unit)? = null

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val TARGET_PACKAGE = "com.tencent.qqmusiccar"
    private val TARGET_SERVICE  = "com.tencent.qqmusiccar.third.api.QQMusicApiService"
    private val DESCRIPTOR      = "com.tencent.qqmusic.third.api.contract.IQQMusicApi"
    private val TRANSACTION_executeAsync = 2

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = service
            isConnected = true
            Log.i("QQMusicLyric", "✅ 服务连接成功")
            try {
                binderCallback = createCallback()
            } catch (e: Exception) {
                Log.e("QQMusicLyric", "❌ 创建回调失败: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isConnected = false
            binderCallback = null
            Log.w("QQMusicLyric", "❌ 服务断开")
        }
    }

    fun bindService() {
        if (isConnected) return
        mainHandler.postDelayed({
            val intent = Intent().setClassName(TARGET_PACKAGE, TARGET_SERVICE)
            try {
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Log.i("QQMusicLyric", "🔗 绑定结果: $bound")
            } catch (e: Exception) {
                Log.e("QQMusicLyric", "❌ 绑定异常: ${e.message}")
            }
        }, 500)
    }

    fun unbindService() {
        if (isConnected) {
            try { context.unbindService(connection) } catch (_: Exception) {}
            isConnected = false
            musicService = null
        }
    }

    /** 手动请求当前歌词，在 Fragment 中定时调用 */
    fun requestLyricManual() {
        if (!isConnected || musicService == null) {
            Log.w("QQMusicLyric", "⚠️ 服务未连接")
            return
        }
        executeRequest("getCurrentLyric")
    }

    private fun executeRequest(command: String) {
        val data  = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(command)
            data.writeInt(0)

            val cb = binderCallback ?: run {
                Log.w("QQMusicLyric", "❌ 回调对象为空")
                return
            }
            data.writeStrongBinder(cb.asBinder())

            val result = musicService?.transact(TRANSACTION_executeAsync, data, reply, 0)
            if (result == true) {
                reply.readException()
                Log.d("QQMusicLyric", "✅ 请求已发送，等待回调...")
            } else {
                Log.w("QQMusicLyric", "⚠️ Transaction 返回 false")
            }
        } catch (e: RemoteException) {
            Log.e("QQMusicLyric", "❌ RemoteException: ${e.message}")
        } catch (e: Exception) {
            Log.e("QQMusicLyric", "❌ 异常: ${e.message}")
        } finally {
            try { data.recycle(); reply.recycle() } catch (_: Exception) {}
        }
    }

    private fun createCallback(): IQQMusicApiCallback {
        return object : IQQMusicApiCallback {
            override fun asBinder(): IBinder {
                return object : android.os.Binder() {
                    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                        return try {
                            if (code == 1) {
                                data.enforceInterface("com.tencent.qqmusic.third.api.contract.IQQMusicApiCallback")
                                val bundle = if (data.readInt() != 0) Bundle.CREATOR.createFromParcel(data) else null
                                handleResult(bundle)
                                reply?.writeNoException()
                                true
                            } else {
                                super.onTransact(code, data, reply, flags)
                            }
                        } catch (e: Exception) {
                            Log.e("QQMusicLyric", "❌ 回调异常: ${e.message}")
                            false
                        }
                    }
                }
            }
            override fun onReturn(bundle: Bundle?) {}
        }
    }

    private fun handleResult(bundle: Bundle?) {
        if (bundle == null) { Log.d("QQMusicLyric", "Bundle 为空"); return }

        logExecutor.execute {
            // 打印所有 key，方便调试
            for (key in bundle.keySet()) {
                try { Log.d("QQMusicLyric_DEBUG", "[$key] = ${bundle.get(key)?.toString()?.take(120)}") }
                catch (_: Exception) {}
            }

            val code  = bundle.getInt("code", -1)
            val lyric = bundle.getString("lyric")
                ?: bundle.getString("content")
                ?: bundle.getString("data")
                ?: ""

            if ((code == 0 || code == 200) && lyric.isNotEmpty()) {
                Log.i("QQMusicLyric", "🎉 歌词获取成功，长度=${lyric.length}")
                mainHandler.post { onLyricReceived?.invoke(lyric) }
            } else {
                Log.w("QQMusicLyric", "code=$code, 无歌词内容")
            }
        }
    }
}

interface IQQMusicApiCallback {
    fun asBinder(): IBinder
    fun onReturn(bundle: Bundle?)
}
