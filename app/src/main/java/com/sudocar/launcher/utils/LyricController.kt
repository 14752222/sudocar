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
     * 使用多个 API 源，优先尝试网易云音乐
     */
    private fun fetchLyric(song: String, artist: String): String? {
        // 方法1: 尝试网易云音乐 API
        try {
            val lyric = fetchFromNetease(song, artist)
            if (!lyric.isNullOrEmpty()) {
                return lyric
            }
        } catch (e: Exception) {
            Log.w(TAG, "网易云 API 失败: ${e.message}")
        }

        // 方法2: 尝试 QQ 音乐 API
        try {
            val lyric = fetchFromTencent(song, artist)
            if (!lyric.isNullOrEmpty()) {
                return lyric
            }
        } catch (e: Exception) {
            Log.w(TAG, "QQ API 失败: ${e.message}")
        }

        // 方法3: 尝试通用歌词搜索 API
        try {
            val lyric = fetchFromLyricsApi(song, artist)
            if (!lyric.isNullOrEmpty()) {
                return lyric
            }
        } catch (e: Exception) {
            Log.w(TAG, "通用歌词 API 失败: ${e.message}")
        }

        return null
    }

    /**
     * 通用歌词搜索 API
     * 使用 lrclib.net 或其他公开歌词库
     */
    private fun fetchFromLyricsApi(song: String, artist: String): String? {
        // lrclib.net API - 开源歌词库
        val encodedTrack = URLEncoder.encode(song, "UTF-8")
        val encodedArtist = URLEncoder.encode(artist, "UTF-8")

        // 尝试 lrclib API
        val lrclibUrl = "https://lrclib.net/api/search?q=$encodedTrack+$encodedArtist"
        val result = httpGet(lrclibUrl) ?: return null

        // 解析返回的 JSON，获取 syncedLyrics 或 plainLyrics
        val syncedRegex = """"syncedLyrics"\s*:\s*"([^"]*)"""".toRegex()
        val plainRegex = """"plainLyrics"\s*:\s*"([^"]*)"""".toRegex()

        val syncedMatch = syncedRegex.find(result)
        if (syncedMatch != null) {
            return syncedMatch.groupValues[1].replace("\\n", "\n")
        }

        val plainMatch = plainRegex.find(result)
        if (plainMatch != null) {
            return plainMatch.groupValues[1].replace("\\n", "\n")
        }

        return null
    }

    /**
     * 网易云音乐歌词 API
     * 使用网易云官方 API（无需第三方代理）
     */
    private fun fetchFromNetease(song: String, artist: String): String? {
        try {
            // 1. 搜索歌曲获取 ID
            val searchUrl = "http://music.163.com/api/search/get/web?" +
                    "s=${URLEncoder.encode(song, "UTF-8")}" +
                    "&type=1&offset=0&total=true&limit=5"

            val searchResult = httpGetWithHeaders(searchUrl, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )) ?: return null

            Log.d(TAG, "[网易云] 搜索结果: ${searchResult.take(200)}")

            // 检查是否成功
            if (!searchResult.contains("\"code\":200")) {
                Log.w(TAG, "[网易云] 搜索失败: $searchResult")
                return null
            }

            // 解析歌曲 ID
            val songId = extractSongId(searchResult) ?: return null
            Log.d(TAG, "[网易云] 找到歌曲 ID: $songId")

            // 2. 获取歌词
            val lyricUrl = "http://music.163.com/api/song/lyric?id=$songId&lv=1&kv=1&tv=-1"
            val lyricResult = httpGetWithHeaders(lyricUrl, mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )) ?: return null

            return parseNeteaseLyric(lyricResult)
        } catch (e: Exception) {
            Log.w(TAG, "[网易云] API 请求失败: ${e.message}")
            return null
        }
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
        try {
            // 查找 songs 数组中的第一个歌曲 ID
            // 格式: "songs":[{"album":{...},"id":123456,...}]
            val songsRegex = """"songs"\s*:\s*\[\s*\{[^}]*"id"\s*:\s*(\d+)""".toRegex()
            val match = songsRegex.find(json)
            if (match != null) {
                return match.groupValues[1]
            }

            // 备选：查找 "id":123456 在 "name":"歌曲名" 之后
            val idRegex = """"name"\s*:\s*"[^"]*"\s*,\s*"id"\s*:\s*(\d+)""".toRegex()
            val idMatch = idRegex.find(json)
            if (idMatch != null) {
                return idMatch.groupValues[1]
            }

            // 最后备选：直接找第一个 id
            val regex = """"id"\s*:\s*(\d+)""".toRegex()
            val matches = regex.findAll(json).toList()
            return if (matches.isNotEmpty()) {
                matches[0].groupValues[1]
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "解析歌曲 ID 失败: ${e.message}")
            return null
        }
    }

    private fun parseNeteaseLyric(json: String): String? {
        try {
            // 尝试提取 lrc.lyric 字段（标准歌词）
            val lrcRegex = """"lrc"\s*:\s*\{[^}]*"lyric"\s*:\s*"([^"]*)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val lrcMatch = lrcRegex.find(json)
            if (lrcMatch != null) {
                val lyric = lrcMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                if (lyric.isNotBlank() && lyric != "暂无歌词") {
                    return lyric
                }
            }

            // 尝试提取 tlyric.lyric 字段（翻译歌词）
            val tlyricRegex = """"tlyric"\s*:\s*\{[^}]*"lyric"\s*:\s*"([^"]*)"""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val tlyricMatch = tlyricRegex.find(json)
            if (tlyricMatch != null) {
                val lyric = tlyricMatch.groupValues[1]
                    .replace("\\n", "\n")
                    .replace("\\r", "")
                if (lyric.isNotBlank() && lyric != "暂无歌词") {
                    return lyric
                }
            }

            // 尝试提取 nolyric 标记
            if (json.contains("\"nolyric\":true") || json.contains("\"uncollected\":true")) {
                Log.w(TAG, "该歌曲暂无歌词")
                return null
            }

            return null
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
        return httpGetWithHeaders(urlString, emptyMap())
    }

    private fun httpGetWithHeaders(urlString: String, headers: Map<String, String>): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Referer", "http://music.163.com/")

            // 添加自定义 headers
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

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
