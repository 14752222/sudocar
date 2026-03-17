package com.sudocar.launcher.receiver

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import android.os.Handler
import android.os.Looper

class QQMusicLyricController(private val context: Context) {

    private var musicService: IBinder? = null
    private var isConnected = false

    // 简单的 Handler，用于确保日志不在 Binder 线程打印（虽然 Log.d 本身是线程安全的，但以防万一）
    private val mainHandler = Handler(Looper.getMainLooper())

    private val TARGET_PACKAGE = "com.tencent.qqmusiccar"
    private val TARGET_SERVICE = "com.tencent.qqmusiccar.third.api.QQMusicApiService"
    private val DESCRIPTOR = "com.tencent.qqmusic.third.api.contract.IQQMusicApi"
    private val TRANSACTION_executeAsync = 2

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            musicService = service
            isConnected = true
            // ❌ 绝对不要在这里 Toast
            Log.i("QQMusic", "Service Connected")

            // 延迟绑定回调，防止时序问题
            mainHandler.postDelayed({
                if (isConnected) {
                    // 这里不自动请求，等待手动触发
                    Log.i("QQMusic", "Ready to request.")
                }
            }, 500)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isConnected = false
            Log.w("QQMusic", "Service Disconnected")
        }
    }

    fun bindService() {
        if (isConnected) return

        // 1. 启动 App
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("QQMusic", "Start app failed", e)
        }

        // 2. 绑定服务 (延迟一点，防止进程未起)
        mainHandler.postDelayed({
            try {
                val intent = Intent().setClassName(TARGET_PACKAGE, TARGET_SERVICE)
                val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                Log.i("QQMusic", "Bind result: $bound")
            } catch (e: Exception) {
                Log.e("QQMusic", "Bind exception", e)
            }
        }, 1000)
    }

    fun unbindService() {
        if (isConnected) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {}
            isConnected = false
            musicService = null
        }
    }

    fun requestLyricManual() {
        if (!isConnected || musicService == null) {
            Log.w("QQMusic", "Not connected yet.")
            return
        }

        Log.d("QQMusic", "Sending request...")
        sendTransaction("getCurrentLyric")
    }

    private fun sendTransaction(command: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(command)
            data.writeInt(0)

            // 创建一个最简单的 Callback Binder
            val callbackBinder = createSimpleCallbackBinder()
            data.writeStrongBinder(callbackBinder)

            // 执行事务
            val result = musicService?.transact(TRANSACTION_executeAsync, data, reply, 0)

            if (result == true) {
                reply.readException()
                Log.d("QQMusic", "Transaction sent successfully.")
            } else {
                Log.w("QQMusic", "Transaction returned false.")
            }
        } catch (e: RemoteException) {
            Log.e("QQMusic", "RemoteException", e)
        } catch (e: Exception) {
            Log.e("QQMusic", "General Exception", e)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // === 核心修改：将 Callback 逻辑剥离为静态/独立类，减少闭包引用 ===
    private fun createSimpleCallbackBinder(): IBinder {
        return object : android.os.Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                // 这是一个极其敏感的函数，任何异常都可能导致 Native 崩溃
                try {
                    if (code == 1) { // onReturn
                        data.enforceInterface("com.tencent.qqmusic.third.api.contract.IQQMusicApiCallback")

                        val bundle = if (data.readInt() != 0) {
                            try {
                                Bundle.CREATOR.createFromParcel(data)
                            } catch (e: Exception) {
                                Log.e("QQMusic", "Failed to read bundle", e)
                                null
                            }
                        } else null

                        // ❗️❗️ 关键：不要在 onTransact 里直接打印大量日志或操作对象
                        // 把处理逻辑抛到主线程去慢慢做，哪怕只是打印日志
                        mainHandler.post {
                            processBundleSafe(bundle)
                        }

                        reply?.writeNoException()
                        return true
                    }
                } catch (e: Exception) {
                    // 捕获所有异常，防止抛出导致 Native 层崩溃
                    Log.e("QQMusic", "Callback crash caught", e)
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
    }

    private fun processBundleSafe(bundle: Bundle?) {
        // 现在已经在主线程了，可以安全地打印日志
        Log.d("QQMusic_DEBUG", "--- Received Bundle ---")
        if (bundle == null) {
            Log.d("QQMusic_DEBUG", "Bundle is null")
            return
        }

        // 限制打印数量，防止过多 I/O 阻塞
        var count = 0
        for (key in bundle.keySet()) {
            if (count > 20) {
                Log.d("QQMusic_DEBUG", "... (too many keys, omitted)")
                break
            }
            try {
                val value = bundle.get(key)
                // 简单的字符串化，避免复杂对象 toString 崩溃
                Log.d("QQMusic_DEBUG", "Key: $key, Value: ${value?.toString()?.take(100)}")
                count++
            } catch (e: Exception) {
                Log.e("QQMusic_DEBUG", "Error reading key: $key")
            }
        }

        val code = bundle.getInt("code", -1)
        if (code == 0 || code == 200) {
            Log.i("QQMusic", "Success Code received.")
        } else {
            Log.w("QQMusic", "Error Code: $code, Msg: ${bundle.getString("error")}")
        }
    }
}