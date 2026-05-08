package com.sudocar.launcher.utils

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.widget.Toast

/**
 * 方向盘多媒体按键监听器 + 全局按键测试
 * 通过 InputManager 全局监听所有按键事件
 */
class KeyEventManager(
    private val context: Context,
    private val callback: KeyCallback
) : InputManager.InputDeviceListener {

    private var inputManager: InputManager? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // 按键测试覆盖层
    private var keyTestOverlay: KeyTestOverlay? = null
    private var isKeyTestMode = false

    interface KeyCallback {
        fun onMediaKey(keyCode: Int): Boolean  // 返回 true 表示已处理
        fun onAnyKey(keyCode: Int, action: Int, event: KeyEvent): Boolean  // 任意按键回调
    }

    /** 开始监听 */
    fun start(ctx: Context) {
        inputManager = ctx.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(this, handler)
        
        // 初始化按键测试覆盖层
        keyTestOverlay = KeyTestOverlay(ctx)
        
        Log.i(TAG, "KeyEventManager 已启动")
    }

    /** 停止监听 */
    fun stop() {
        inputManager?.unregisterInputDeviceListener(this)
        inputManager = null
        hideKeyTest()
        keyTestOverlay = null
        Log.i(TAG, "KeyEventManager 已停止")
    }

    /** 切换按键测试模式 */
    fun toggleKeyTest() {
        isKeyTestMode = !isKeyTestMode
        if (isKeyTestMode) {
            keyTestOverlay?.show()
            showToast("按键测试模式已开启，长按弹窗可关闭")
        } else {
            hideKeyTest()
            showToast("按键测试模式已关闭")
        }
        Log.d(TAG, "按键测试模式: $isKeyTestMode")
    }

    /** 显示按键测试 */
    fun showKeyTest() {
        if (!isKeyTestMode) {
            isKeyTestMode = true
            keyTestOverlay?.show()
        }
    }

    /** 隐藏按键测试 */
    fun hideKeyTest() {
        isKeyTestMode = false
        keyTestOverlay?.hide()
    }

    /** 是否在测试模式 */
    fun isKeyTestMode() = isKeyTestMode

    /**
     * 分发按键事件（从 Activity 调用）
     * 返回 true 表示已处理
     */
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val deviceId = event.deviceId

        // 记录所有按键到测试覆盖层
        if (isKeyTestMode) {
            keyTestOverlay?.logKeyEvent(
                keyCode = keyCode,
                action = action,
                deviceId = deviceId,
                repeatCount = event.repeatCount
            )
        }

        // 同时记录到 Logcat
        if (action == KeyEvent.ACTION_DOWN) {
            val deviceName = event.device?.name ?: "unknown"
            Log.d(TAG, "按键事件: ${KeyTestOverlay.getKeyName(keyCode)} ($keyCode) from [$deviceName] 重复:${event.repeatCount}")
        }

        // 回调给业务逻辑
        if (action == KeyEvent.ACTION_DOWN) {
            val handled = callback.onAnyKey(keyCode, action, event)
            if (handled) return true

            // 如果业务没处理，尝试媒体按键
            return handleMediaKey(keyCode)
        }

        return false
    }

    private fun handleMediaKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                callback.onMediaKey(keyCode)
                true
            }
            else -> false
        }
    }

    /**
     * 获取所有已连接输入设备信息
     * 用于排查车机上的方向盘/按键设备
     */
    fun getInputDevices(): List<InputDeviceInfo> {
        val devices = mutableListOf<InputDeviceInfo>()
        inputManager?.getInputDeviceIds()?.forEach { deviceId ->
            inputManager?.getInputDevice(deviceId)?.let { device ->
                val sources = mutableListOf<String>()
                if (device.supportsSource(InputDevice.SOURCE_KEYBOARD)) sources.add("键盘")
                if (device.supportsSource(InputDevice.SOURCE_DPAD)) sources.add("方向盘/十字键")
                if (device.supportsSource(InputDevice.SOURCE_GAMEPAD)) sources.add("游戏手柄")
                if (device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) sources.add("触摸屏")
                if (device.supportsSource(InputDevice.SOURCE_ROTARY_ENCODER)) sources.add("旋钮编码器")

                devices.add(InputDeviceInfo(
                    id = device.id,
                    name = device.name,
                    sources = sources.joinToString(", ")
                ))
            }
        }
        return devices
    }

    private fun showToast(message: String) {
        handler.post { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    // ============= InputDeviceListener 实现 =============

    override fun onInputDeviceAdded(deviceId: Int) {
        val device = inputManager?.getInputDevice(deviceId)
        Log.i(TAG, "输入设备添加: [$deviceId] ${device?.name ?: "unknown"}")
        showToast("发现输入设备: ${device?.name ?: "ID=$deviceId"}")
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.i(TAG, "输入设备移除: $deviceId")
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        val device = inputManager?.getInputDevice(deviceId)
        Log.i(TAG, "输入设备变化: [$deviceId] ${device?.name ?: "unknown"}")
    }

    data class InputDeviceInfo(
        val id: Int,
        val name: String,
        val sources: String
    )

    companion object {
        private const val TAG = "KeyEventManager"
    }
}
