package com.sudocar.launcher.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * 通用歌词控制器
 * 通过网络 API 获取歌词，支持任意音乐 APP
 */
class LyricController {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 当前歌曲信息，用于去重请求 */
    private var currentSong: String = ""
    private var currentArtist: String = ""

    /** 回调 */
    var onLyricReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    /**
     * 请求歌词
     * @param song 歌曲名
     * @param artist 艺术家
     */
    fun requestLyric(song: String, artist: String) {
        // 去重：如果歌曲没变，不重复请求
        val songKey = "${song}_$artist".lowercase().trim()
        if (songKey == currentSong.lowercase().trim() && currentSong.isNotEmpty()) {
            Log.d(TAG, "歌曲未变化，跳过请求: $song - $artist")
            return
        }

        currentSong = song
        currentArtist = artist

        if (song.isBlank()) {
            onLyricReceived?.invoke("")
            return
        }

        coroutineScope.launch {
            try {
                val lyric = fetchLyric(song, artist)
                mainHandler.post {
                    if (lyric != null) {
                        onLyricReceived?.invoke(lyric)
                        Log.d(TAG, "歌词获取成功: $song - $artist")
                    } else {
                        onLyricReceived?.invoke("")
                        onError?.invoke("未找到歌词")
                        Log.w(TAG, "未找到歌词: $song - $artist")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "歌词请求失败: ${e.message}")
                mainHandler.post {
                    onError?.invoke(e.message ?: "请求失败")
                }
            }
        }
    }

    /**
     * 从网络获取歌词
     * 使用网易云音乐 API
     */
    private fun fetchLyric(song: String, artist: String): String? {
        return try {
            // 方法1: 尝试网易云音乐 API
            fetchFromNetease(song, artist)
        } catch (e: Exception) {
            Log.w(TAG, "网易云 API 失败: ${e.message}")
            try {
                // 方法2: 尝试 QQ 音乐 API (若有)
                fetchFromTencent(song, artist)
            } catch (e2: Exception) {
                Log.w(TAG, "QQ API 失败: ${e2.message}")
                null
            }
        }
    }

    /**
     * 网易云音乐歌词 API
     */
    private fun fetchFromNetease(song: String, artist: String): String? {
        // 1. 先搜索歌曲获取 ID
        val searchUrl = "https://netease-cloud-music-api-five-roan-25.vercel.app/search?keywords=${URLEncoder.encode("$song $artist", "UTF-8")}&limit=1"
        
        val searchResult = httpGet(searchUrl) ?: return null
        Log.d(TAG, "搜索结果: ${searchResult.take(200)}")

        // 解析歌曲 ID (简单解析)
        val songId = extractSongId(searchResult) ?: return null
        Log.d(TAG, "找到歌曲 ID: $songId")

        // 2. 获取歌词
        val lyricUrl = "https://netease-cloud-music-api-five-roan-25.vercel.app/lyric?id=$songId"
        val lyricResult = httpGet(lyricUrl) ?: return null

        return parseNeteaseLyric(lyricResult)
    }

    /**
     * 腾讯音乐娱乐 API (QQ音乐/酷我)
     * 注意：需要代理访问
     */
    private fun fetchFromTencent(song: String, artist: String): String? {
        // 使用 代理/镜像 API
        val url = "https://api.qq.jsososo.com/song/lyric?title=${URLEncoder.encode(song, "UTF-8")}&singer=${URLEncoder.encode(artist, "UTF-8")}"
        val result = httpGet(url) ?: return null
        
        return parseTencentLyric(result)
    }

    private fun extractSongId(json: String): String? {
        // 简单 JSON 解析，提取 "id":123456
        val regex = """"id"\s*:\s*(\d+)""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseNeteaseLyric(json: String): String? {
        try {
            // 提取 lrc 部分的歌词
            val lrcRegex = """"lrc"\s*:\s*\{[^}]*"text"\s*:\s*"([^"]*)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val matches = lrcRegex.findAll(json)
            
            val lyrics = mutableListOf<String>()
            for (match in matches) {
                val line = match.groupValues.get(1).replace("\\n", "\n")
                if (line.isNotBlank()) {
                    lyrics.add(line)
                }
            }
            
            return if (lyrics.isNotEmpty()) {
                lyrics.joinToString("\n")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析网易云歌词失败: ${e.message}")
            return null
        }
    }

    private fun parseTencentLyric(json: String): String? {
        try {
            // 腾讯歌词格式可能不同，简单处理
            if (json.contains("lyric") || json.contains("content")) {
                // 返回原始JSON中的歌词部分（简化处理）
                val regex = """"(https?://[^"]+\.lrc)"""".toRegex()
                val lrcUrl = regex.find(json)?.groupValues?.get(1)
                
                if (lrcUrl != null) {
                    return httpGet(lrcUrl)
                }
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    private fun httpGet(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                Log.w(TAG, "HTTP ${connection.responseCode}: $urlString")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP 请求失败: ${e.message}")
            null
        }
    }

    fun destroy() {
        coroutineScope.cancel()
    }

    fun unbindService() {
        // 移除歌词服务绑定（当前为stub实现）
        try {
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "unbindService: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LyricController"
    }
}
