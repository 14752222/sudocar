package com.sudocar.launcher.fragment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
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
        val isNightMode = SettingsDialog.isNightModeEnabled(requireContext())
        binding.btnNightMode.setImageResource(
            if (isNightMode) android.R.drawable.ic_menu_day    // 夜间模式显示太阳
            else android.R.drawable.ic_menu_today              // 日间模式显示月亮
        )
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
        binding.tvTime.text = timeFormat.format(Date())
    }

    private fun setupAcReceiver() {
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
        requireContext().registerReceiver(acReceiver, filter, Context.RECEIVER_EXPORTED)
        updateAcDisplay(false, 22)
    }

    private fun updateAcDisplay(isOn: Boolean, temp: Int) {
        binding.tvAcStatus.apply {
            text = if (isOn) "空调: 开" else "空调: 关"
            setTextColor(if (isOn) resources.getColor(com.sudocar.launcher.R.color.ac_on, null)
                        else resources.getColor(com.sudocar.launcher.R.color.ac_off, null))
        }
        binding.tvAcTemp.apply {
            text = " ${temp}°C"
            visibility = if (isOn) View.VISIBLE else View.GONE
        }
    }

    private fun setupButtons() {
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            com.sudocar.launcher.dialog.SettingsDialog(requireContext()).show()
        }

        // 切换按钮
        binding.btnSwap.setOnClickListener {
            (activity as? OnSwapClickListener)?.onSwapClick()
        }

        // 夜间模式按钮
        binding.btnNightMode.setOnClickListener {
            toggleNightMode()
        }

        // 快捷应用按钮
        binding.btnQuickApp.setOnClickListener {
            onQuickSwitchListener?.invoke()
        }
    }

    private fun toggleNightMode() {
        Log.d("BottomFragment", "toggleNightMode 被点击")
        
        val prefs = requireContext().getSharedPreferences(
            SettingsDialog.PREFS_NAME, 
            Context.MODE_PRIVATE
        )
        val current = prefs.getBoolean(SettingsDialog.KEY_NIGHT_MODE, true)
        val newValue = !current
        prefs.edit().putBoolean(SettingsDialog.KEY_NIGHT_MODE, newValue).apply()

        Log.d("BottomFragment", "切换夜间模式: $current -> $newValue")

        // 刷新图标
        updateNightModeIcon()

        // 通知 MainActivity 刷新（会调用 recreate）
        val mainActivity = activity as? com.sudocar.launcher.MainActivity
        mainActivity?.onNightModeChanged()

        val msg = if (newValue) "已开启夜景模式" else "已关闭夜景模式"
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeHandler.removeCallbacksAndMessages(null)
        try { requireContext().unregisterReceiver(acReceiver) } catch (_: Exception) {}
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
