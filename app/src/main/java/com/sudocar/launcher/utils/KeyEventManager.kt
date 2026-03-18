package com.sudocar.launcher.utils

import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * 方向盘多媒体按键监听器
 * 通过 InputManager 全局监听按键事件
 */
class KeyEventManager(private val callback: KeyCallback) : InputManager.InputDeviceListener {

    private var inputManager: InputManager? = null
    private val handler = Handler(Looper.getMainLooper())

    interface KeyCallback {
        fun onMediaKey(keyCode: Int): Boolean  // 返回 true 表示已处理
    }

    /** 开始监听 */
    fun start(context: android.content.Context) {
        inputManager = context.getSystemService(android.content.Context.INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(this, handler)
        Log.i(TAG, "KeyEventManager 已启动")
    }

    /** 停止监听 */
    fun stop() {
        inputManager?.unregisterInputDeviceListener(this)
        inputManager = null
        Log.i(TAG, "KeyEventManager 已停止")
    }

    /** 分发按键事件（从 Activity 调用） */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    Log.d(TAG, "收到多媒体按键: ${event.keyCode}")
                    callback.onMediaKey(event.keyCode)
                }
                else -> false
            }
        }
        return false
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(TAG, "输入设备添加: $deviceId")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(TAG, "输入设备移除: $deviceId")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d(TAG, "输入设备变化: $deviceId")
    }

    companion object {
        private const val TAG = "KeyEventManager"
    }
}
