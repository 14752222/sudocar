package com.sudocar.launcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import java.util.concurrent.Executors

class QQMusicLyricController(private val context: Context) {

    private var musicService: IBinder? = null
    private var isConnected = false
    private var callback: IQQMusicApiCallback? = null

    // 创建一个单线程池，用于处理日志，避免阻塞 Binder 线程
    private val logExecutor = Executors.newSingleThreadExecutor()

    private val TARGET_PACKAGE = "com.tencent.qqmusiccar"
    private val TARGET_SERVICE = "com.tencent.qqmusiccar.third.api.QQMusicApiService"
    private val DESCRIPTOR = "com.tencent.qqmusic.third.api.contract.IQQMusicApi"
    private val TRANSACTION_executeAsync = 2

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = service
            isConnected = true
            safeLog("✅ 服务连接成功 (无 Toast)")

            try {
                callback = createCallback()
            } catch (e: Exception) {
                safeLog("❌ 创建回调失败: ${e.message}")
            }

            // ❌ 删除了自动请求逻辑，防止启动时崩溃
            // 请在 UI 上点击按钮手动触发
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isConnected = false
            callback = null
            safeLog("❌ 服务断开")
        }
    }

    fun bindService() {
        if (isConnected) return

        // 启动 QQ 音乐进程
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent?.let { context.startActivity(it) }
        } catch (e: Exception) {
            safeLog("⚠️ 启动 QQ 音乐失败: ${e.message}")
        }

        // 延迟一点再绑定，给进程启动留点时间
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val intent = Intent().setClassName(TARGET_PACKAGE, TARGET_SERVICE)
            try {
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                safeLog("🔗 正在绑定服务...")
            } catch (e: Exception) {
                safeLog("❌ 绑定服务异常: ${e.message}")
            }
        }, 500)
    }

    fun unbindService() {
        if (isConnected) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) { /* ignore */ }
            isConnected = false
        }
    }

    // 手动触发请求 (在按钮点击中调用)
    fun requestLyricManual() {
        if (!isConnected || musicService == null) {
            safeLog("⚠️ 服务未连接，请先绑定")
            return
        }
        executeRequest("getCurrentLyric")
    }

    private fun executeRequest(command: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(command)
            data.writeInt(0) // 无参数

            val cb = callback ?: run {
                safeLog("❌ 回调对象为空")
                return
            }
            data.writeStrongBinder(cb.asBinder())

            safeLog("🚀 发送请求: $command")

            // 尝试调用
            val result = musicService?.transact(TRANSACTION_executeAsync, data, reply, 0)

            if (result == true) {
                reply.readException()
                safeLog("✅ 请求发送成功，等待回调...")
            } else {
                safeLog("⚠️ Transaction 返回 false")
            }

        } catch (e: RemoteException) {
            safeLog("❌ RemoteException: ${e.message}")
        } catch (e: Exception) {
            safeLog("❌ 未知异常: ${e.message}")
        } finally {
            try {
                data.recycle()
                reply.recycle()
            } catch (e: Exception) { /* ignore */ }
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
                                val bundle = if (data.readInt() != 0) {
                                    Bundle.CREATOR.createFromParcel(data)
                                } else null

                                handleResult(bundle)
                                reply?.writeNoException()
                                true
                            } else {
                                super.onTransact(code, data, reply, flags)
                            }
                        } catch (e: Exception) {
                            safeLog("❌ 回调处理异常: ${e.message}")
                            false
                        }
                    }
                }
            }
            override fun onReturn(bundle: Bundle?) {}
        }
    }

    private fun handleResult(bundle: Bundle?) {
        safeLog("=== 📥 收到回调数据 ===")
        if (bundle == null) {
            safeLog("数据为空")
            return
        }

        // 异步打印日志，避免阻塞 Binder 线程导致死锁
        logExecutor.execute {
            for (key in bundle.keySet()) {
                try {
                    val value = bundle.get(key)
                    Log.d("QQMusic_DEBUG", "Key: [$key] -> Value: [$value]")
                } catch (e: Exception) {
                    Log.e("QQMusic_DEBUG", "读取 Key [$key] 失败")
                }
            }

            val code = bundle.getInt("code", -1)
            if (code == 0 || code == 200) {
                val lyric = bundle.getString("lyric") ?: bundle.getString("content") ?: bundle.getString("data")
                if (!lyric.isNullOrEmpty()) {
                    Log.d("QQMusic_SUCCESS", "🎉 获取歌词成功! 长度: ${lyric.length}")
                    // 这里不要直接更新 UI，建议发广播或事件总线通知 UI 线程
                } else {
                    Log.w("QQMusic_DEBUG", "成功但无歌词内容")
                }
            } else {
                Log.e("QQMusic_ERROR", "业务错误 Code: $code, Msg: ${bundle.getString("error")}")
            }
        }
    }

    // 安全的日志方法 (主线程打印简单信息，详细信息走 executor)
    private fun safeLog(msg: String) {
        Log.d("QQMusic_Controller", msg)
        // 不在这里做 UI 操作
    }
}

interface IQQMusicApiCallback {
    fun asBinder(): IBinder
    fun onReturn(bundle: Bundle?)
}