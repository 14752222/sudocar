package com.sudocar.launcher.fragment

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.sudocar.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeftFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appAdapter: AppAdapter // 改为 lateinit

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

        recyclerView = view.findViewById(R.id.rv_app_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // 【修改】初始化 Adapter 时传入点击回调
        appAdapter = AppAdapter { appInfo ->
            launchApp(appInfo.packageName)
        }

        recyclerView.adapter = appAdapter

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

        loadAllApplications()
    }

    /**
     * 【核心功能】启动应用
     */
    private fun launchApp(packageName: String) {
        try {
            val pm = requireContext().packageManager
            // 获取启动 Intent
            val intent = pm.getLaunchIntentForPackage(packageName)

            if (intent != null) {
                // 添加 NEW_TASK 标志，因为是从另一个任务栈启动
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), "无法打开该应用 (无启动界面)", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    /**
     * 【核心修改】获取并过滤应用列表
     * 只保留有 Launch Intent 的应用 (即有桌面图标的普通App)
     */
    private fun fetchInstalledApps(): List<AppInfo> {
        val pm = requireContext().packageManager
        // GET_META_DATA 获取基本信息
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

        val validApps = mutableListOf<AppInfo>()

        for (info in installedApps) {
            val packageName = info.packageName

            // 【关键过滤】检查是否有启动 Intent
            // 如果 getLaunchIntentForPackage 返回 null，说明是纯后台服务、系统组件或无界面应用，直接跳过
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                continue
            }

            val appName = info.loadLabel(pm).toString()
            // 再次确保名字不为空
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

        // 按名称排序
        return validApps.sortedBy { it.appName.lowercase() }
    }

    private fun processAppList(apps: List<AppInfo>): List<AppInfo> {
        val settingsPackageName = "com.android.settings"

        // 注意：此时 apps 列表中已经全是可启动的应用了，直接找设置即可
        val settingsApp = apps.find { it.packageName == settingsPackageName }

        return if (settingsApp != null) {
            val otherApps = apps.filter { it.packageName != settingsPackageName }
            listOf(settingsApp) + otherApps
        } else {
            // 备用方案：按名字找
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
}