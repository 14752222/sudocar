package com.sudocar.launcher.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.sudocar.launcher.MainActivity
import com.sudocar.launcher.databinding.FragmentBottomBinding
import com.sudocar.launcher.dialog.SettingsDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BottomFragment : Fragment() {

    private var _binding: FragmentBottomBinding? = null
    private val binding get() = _binding!!

    private val timeHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var acReceiver: BroadcastReceiver? = null

    // 快捷应用弹窗回调
    private var onQuickSwitchListener: (() -> Unit)? = null

    fun setOnQuickSwitchListener(listener: () -> Unit) {
        onQuickSwitchListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTimeUpdate()
        setupAcReceiver()
        setupButtons()
        updateNightModeIcon()
    }

    // 刷新夜间模式图标（供外部调用）
    fun refreshNightModeIcon() {
        updateNightModeIcon()
    }

    private fun updateNightModeIcon() {
        try {
            val isNightMode = SettingsDialog.isNightModeEnabled(requireContext())
            binding.btnNightMode.setImageResource(
                if (isNightMode) android.R.drawable.ic_menu_day    // 夜间模式显示太阳
                else android.R.drawable.ic_menu_today              // 日间模式显示月亮
            )
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error updating night mode icon: ${e.message}")
        }
    }

    private fun setupTimeUpdate() {
        updateTime()
        timeHandler.postDelayed(object : Runnable {
            override fun run() {
                updateTime()
                timeHandler.postDelayed(this, 60000)
            }
        }, 60000)
    }

    private fun updateTime() {
        try {
            binding.tvTime.text = timeFormat.format(Date())
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error updating time: ${e.message}")
        }
    }

    private fun setupAcReceiver() {
        try {
            acReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        ACTION_AC_STATUS -> {
                            val isOn = intent.getBooleanExtra("ac_on", false)
                            val temp = intent.getIntExtra("ac_temp", 22)
                            updateAcDisplay(isOn, temp)
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(ACTION_AC_STATUS)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(acReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                requireContext().registerReceiver(acReceiver, filter)
            }
            
            updateAcDisplay(false, 22)
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error setting up AC receiver: ${e.message}")
        }
    }

    private fun updateAcDisplay(isOn: Boolean, temp: Int) {
        try {
            binding.tvAcStatus.apply {
                text = if (isOn) "空调: 开" else "空调: 关"
                setTextColor(if (isOn) requireContext().getColor(com.sudocar.launcher.R.color.ac_on)
                            else requireContext().getColor(com.sudocar.launcher.R.color.ac_off))
            }
            binding.tvAcTemp.apply {
                text = " ${temp}°C"
                visibility = if (isOn) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error updating AC display: ${e.message}")
        }
    }

    private fun setupButtons() {
        try {
            // 设置按钮
            binding.btnSettings.setOnClickListener {
                try {
                    com.sudocar.launcher.dialog.SettingsDialog(requireContext()).show()
                } catch (e: Exception) {
                    Log.e("BottomFragment", "Error showing settings: ${e.message}")
                    Toast.makeText(requireContext(), "打开设置失败", Toast.LENGTH_SHORT).show()
                }
            }

            // 切换按钮
            binding.btnSwap.setOnClickListener {
                try {
                    (activity as? OnSwapClickListener)?.onSwapClick()
                } catch (e: Exception) {
                    Log.e("BottomFragment", "Error on swap click: ${e.message}")
                }
            }

            // 夜间模式按钮
            binding.btnNightMode.setOnClickListener {
                toggleNightMode()
            }

            // 快捷应用按钮
            binding.btnQuickApp.setOnClickListener {
                try {
                    onQuickSwitchListener?.invoke()
                } catch (e: Exception) {
                    Log.e("BottomFragment", "Error on quick app click: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error setting up buttons: ${e.message}")
        }
    }

    private fun toggleNightMode() {
        try {
            Log.d("BottomFragment", "toggleNightMode 被点击")
            
            val context = requireContext()
            val prefs = context.getSharedPreferences(
                SettingsDialog.PREFS_NAME, 
                Context.MODE_PRIVATE
            )
            val current = prefs.getBoolean(SettingsDialog.KEY_NIGHT_MODE, true)
            val newValue = !current
            
            Log.d("BottomFragment", "切换夜间模式: $current -> $newValue")
            
            // 保存设置
            prefs.edit().putBoolean(SettingsDialog.KEY_NIGHT_MODE, newValue).apply()
            Log.d("BottomFragment", "SharedPreferences 已保存")

            // 设置主题
            if (newValue) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            Log.d("BottomFragment", "主题已设置")

            // 刷新图标
            updateNightModeIcon()
            Log.d("BottomFragment", "图标已刷新")

            // 通知 MainActivity 更新 UI
            try {
                val mainActivity = activity as? MainActivity
                if (mainActivity != null) {
                    Log.d("BottomFragment", "调用 MainActivity.applyBackground()")
                    mainActivity.applyBackground()
                    Log.d("BottomFragment", "MainActivity.applyBackground() 完成")
                } else {
                    Log.w("BottomFragment", "MainActivity 为 null")
                }
            } catch (e: Exception) {
                Log.e("BottomFragment", "调用 MainActivity 失败: ${e.message}")
                e.printStackTrace()
            }

            val msg = if (newValue) "已开启夜景模式" else "已关闭夜景模式"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            Log.d("BottomFragment", "toggleNightMode 完成")
            
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error toggling night mode: ${e.message}")
            e.printStackTrace()
            try {
                Toast.makeText(requireContext(), "切换失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Log.e("BottomFragment", "Error showing toast: ${e2.message}")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            timeHandler.removeCallbacksAndMessages(null)
            acReceiver?.let { 
                try {
                    requireContext().unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e("BottomFragment", "Error unregistering receiver: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("BottomFragment", "Error in onDestroyView: ${e.message}")
        }
        _binding = null
    }

    interface OnSwapClickListener {
        fun onSwapClick()
    }

    interface OnNightModeChangeListener {
        fun onNightModeChanged()
    }

    companion object {
        private const val ACTION_AC_STATUS = "com.sudocar.action.AC_STATUS"
    }
}
