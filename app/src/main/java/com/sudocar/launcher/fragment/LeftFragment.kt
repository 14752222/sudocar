package com.sudocar.launcher.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sudocar.launcher.R
import com.sudocar.launcher.adapter.AppAdapter
import com.sudocar.launcher.dialog.SettingsDialog
import com.sudocar.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeftFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter

    private val allApps = mutableListOf<AppInfo>()
    private val pageSize = 20
    private var currentPage = 0
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_left, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 先初始化 RecyclerView
        recyclerView = view.findViewById(R.id.rv_app_list) ?: return

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        appAdapter = AppAdapter { appInfo ->
            launchApp(appInfo.packageName)
        }
        recyclerView.adapter = appAdapter

        // 然后应用主题颜色
        applyTheme(view)

        setupRecyclerView()
        loadAllApplications()
    }

    override fun onResume() {
        super.onResume()
    }

    // 供外部调用刷新主题（recreate 会自动处理）
    fun refreshTheme() {
    }

    private fun applyTheme(view: View) {
        try {
            val isNightMode = SettingsDialog.isNightModeEnabled(requireContext())
            
            // 从颜色资源获取
            val bgColor = requireContext().getColor(R.color.bg_dark)
            val textColor = requireContext().getColor(R.color.text_primary)
            
            // 设置背景
            view.setBackgroundColor(bgColor)

            // 设置 RecyclerView 背景
            if (::recyclerView.isInitialized) {
                recyclerView.setBackgroundColor(bgColor)
            }

            Log.d("LeftFragment", "主题应用: nightMode=$isNightMode, bgColor=${Integer.toHexString(bgColor)}")
        } catch (e: Exception) {
            Log.e("LeftFragment", "主题应用失败: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && lastVisibleItem >= totalItemCount - 5) {
                    loadMoreApps()
                }
            }
        })
    }

    private fun launchApp(packageName: String) {
        try {
            val pm = requireContext().packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "无法打开该应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun canLoadMore(): Boolean {
        return currentPage * pageSize < allApps.size
    }

    private fun loadMoreApps() {
        if (!canLoadMore()) return

        isLoading = true
        val start = currentPage * pageSize
        val end = minOf(start + pageSize, allApps.size)

        if (start < allApps.size) {
            val newItems = allApps.subList(start, end)
            val currentList = appAdapter.currentList.toMutableList()
            currentList.addAll(newItems)
            appAdapter.submitList(currentList)
            currentPage++
        }
        isLoading = false
    }

    private fun loadAllApplications() {
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                fetchInstalledApps()
            }

            allApps.clear()
            allApps.addAll(processAppList(apps))

            currentPage = 0
            appAdapter.submitList(emptyList())
            loadMoreApps()
        }
    }

    private fun fetchInstalledApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val validApps = mutableListOf<AppInfo>()

        for (info in installedApps) {
            val packageName = info.packageName
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) continue

            val appName = info.loadLabel(pm).toString()
            if (appName.isEmpty()) continue

            val icon = info.loadIcon(pm)
            validApps.add(
                AppInfo(
                    packageName = packageName,
                    appName = appName,
                    icon = icon,
                    isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            )
        }
        return validApps.sortedBy { it.appName.lowercase() }
    }

    private fun processAppList(apps: List<AppInfo>): List<AppInfo> {
        val settingsPackageName = "com.android.settings"
        val settingsApp = apps.find { it.packageName == settingsPackageName }

        return if (settingsApp != null) {
            val otherApps = apps.filter { it.packageName != settingsPackageName }
            listOf(settingsApp) + otherApps
        } else {
            val maybeSettings = apps.find {
                it.appName.contains("设置", ignoreCase = true) ||
                        it.appName.contains("Settings", ignoreCase = true)
            }
            if (maybeSettings != null) {
                val otherApps = apps.filter { it.packageName != maybeSettings.packageName }
                listOf(maybeSettings) + otherApps
            } else {
                apps
            }
        }
    }

    companion object {
        private const val TAG = "LeftFragment"
    }
}
