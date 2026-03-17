package com.sudocar.launcher.fragment

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    // 【删除】移除自定义的 _view 和 view 属性，避免与父类 getView() 冲突
    // private var _view: View? = null
    // private val view get() = _view!!

    private lateinit var recyclerView: RecyclerView
    private val appAdapter = AppAdapter()

    // 缓存所有数据
    private val allApps = mutableListOf<AppInfo>()
    private val pageSize = 20
    private var currentPage = 0
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 直接返回 inflate 的视图，不需要存到 _view
        return inflater.inflate(R.layout.fragment_left, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 【使用】直接使用 onViewCreated 的参数 view，或者使用 requireView()
        recyclerView = view.findViewById(R.id.rv_app_list)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = appAdapter

        // 监听滚动到底部
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // 检查是否滑到底部
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItemCount = layoutManager.itemCount
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()

                if (!isLoading && lastVisibleItem >= totalItemCount - 5) { // 提前5个加载
                    loadMoreApps()
                }
            }
        })

        // 开始加载数据
        loadAllApplications()
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
            // 合并旧列表和新列表
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

        return installedApps.mapNotNull { info ->
            val appName = info.loadLabel(pm).toString()
            val icon = info.loadIcon(pm)
            if (appName.isNotEmpty()) {
                AppInfo(
                    packageName = info.packageName,
                    appName = appName,
                    icon = icon,
                    isSystemApp = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } else {
                null
            }
        }.sortedBy { it.appName.lowercase() }
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
}