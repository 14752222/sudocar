package com.sudocar.launcher.fragment

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.sudocar.launcher.databinding.FragmentMiddleBinding

class MiddleFragment : Fragment() {

    private var _binding: FragmentMiddleBinding? = null
    private val binding get() = _binding!!

    // 地图包名列表
    private val mapPackages = listOf(
        "com.autonavi.amapauto" to "高德",
        "com.autonavi.minimap" to "高德手机",
        "com.baidu.BaiduMap" to "百度",
        "com.tencent.map" to "腾讯"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMiddleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMapSpinner()
        setupButtons()
    }

    private fun setupMapSpinner() {
        val pm = requireContext().packageManager
        val installedMaps = mapPackages.filter { (pkg, _) ->
            pm.getLaunchIntentForPackage(pkg) != null
        }

        val mapNames = listOf("地图") + installedMaps.map { it.second }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, mapNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMap.adapter = adapter

        binding.spinnerMap.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= installedMaps.size) {
                    val (pkg, name) = installedMaps[position - 1]
                    launchMap(pkg, name)
                    binding.spinnerMap.setSelection(0)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.btnSwapRight.setOnClickListener {
            (activity as? OnSwapClickListener)?.onSwapClick()
        }
    }

    // 供外部调用刷新主题
    fun refreshTheme() {
        // 刷新 Spinner 颜色
        try {
            val isNightMode = com.sudocar.launcher.dialog.SettingsDialog.isNightModeEnabled(requireContext())
            val textColor = requireContext().getColor(com.sudocar.launcher.R.color.text_primary)
            val bgColor = requireContext().getColor(com.sudocar.launcher.R.color.bg_card)
            
            binding.tvMapHint.setTextColor(
                if (isNightMode) 0xFFAAAAAA.toInt() else 0xFF666666.toInt()
            )
            
            Log.d("MiddleFragment", "主题刷新: nightMode=$isNightMode")
        } catch (e: Exception) {
            Log.e("MiddleFragment", "主题刷新失败: ${e.message}")
        }
    }

    private fun launchMap(packageName: String, label: String) {
        try {
            val intent = requireContext().packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                binding.tvMapHint.text = "已启动 $label"
                binding.layoutMapPlaceholder.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "$label 未安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    interface OnSwapClickListener {
        fun onSwapClick()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
