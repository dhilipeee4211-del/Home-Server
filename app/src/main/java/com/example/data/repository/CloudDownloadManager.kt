package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.network.ApiClient
import com.example.network.ServerConfig
import com.example.network.ServerConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

object CloudDownloadManager {
    private const val TAG = "CloudDownloadManager"
    private const val POLL_INTERVAL_MS = 1000L

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()
    private val serverTaskIds = mutableMapOf<String, String>()

    private val _tasks = MutableStateFlow<List<CloudDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CloudDownloadTask>> = _tasks.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun deriveFilename(urlStr: String): String {
        return try {
            val path = URL(urlStr).path
            val rawName = path.substringAfterLast('/', "").trim()
            if (rawName.isNotBlank() && rawName.contains(".")) {
                URLDecoder.decode(rawName, "UTF-8")
            } else {
                "download_${System.currentTimeMillis().toString().takeLast(6)}.bin"
            }
        } catch (_: Exception) {
            "download_${System.currentTimeMillis().toString().takeLast(6)}.bin"
        }
    }

    fun startDownload(
        url: String,
        destinationFolder: String = "/",
        customFilename: String? = null,
        onComplete: (() -> Unit)? = null
    ): String {
        val cleanUrl = url.trim()
        val filename = if (!customFilename.isNullOrBlank()) {
            customFilename.trim()
        } else {
            deriveFilename(cleanUrl)
        }
        val taskId = "dl_${UUID.randomUUID().toString().take(8)}"

        _tasks.update {
            listOf(
                CloudDownloadTask(
                    id = taskId,
                    url = cleanUrl,
                    filename = filename,
                    destinationFolder = destinationFolder,
                    status = CloudDownloadStatus.QUEUED,
                    speedText = "Preparing server download…"
                )
            ) + it
        }

        val job = scope.launch {
            executeServerDownload(taskId, cleanUrl, filename, destinationFolder, onComplete)
        }
        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun executeServerDownload(
        taskId: String,
        url: String,
        filename: String,
        destinationFolder: String,
        onComplete: (() -> Unit)?
    ) {
        val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        if (baseUrl.isBlank()) {
            fail(taskId, "Server address is not configured")
            return
        }

        try {
            if (!ServerConnectionManager.isConnected.value) {
                fail(taskId, "Server is offline. Check the server connection.")
                return
            }

            updateTask(taskId) {
                it.copy(status = CloudDownloadStatus.QUEUED, speedText = "Connecting to server…")
            }

            val payload = JSONObject().apply {
                put("url", url)
                put("filename", filename)
                put("destination", destinationFolder.ifBlank { "/" })
            }

            // IMPORTANT: use the authenticated ApiClient. Do not create a second client
            // without the AuthInterceptor, otherwise protected server endpoints return 401.
            val client = ApiClient.okHttpClient.newBuilder()
                .callTimeout(20, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/files/remote-download")
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    fail(taskId, parseServerError(body, "Server rejected download (HTTP ${response.code})"))
                    return
                }

                val root = JSONObject(body)
                val data = root.optJSONObject("data") ?: root
                val serverTaskId = data.optString("task_id").ifBlank {
                    data.optString("id")
                }

                if (serverTaskId.isBlank()) {
                    fail(taskId, "Server accepted the request but returned no task ID")
                    return
                }

                synchronized(serverTaskIds) {
                    serverTaskIds[taskId] = serverTaskId
                }

                val total = data.optLong("total_bytes", 0L)
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.DOWNLOADING,
                        progressPercent = data.optInt("progress_percent", 0).coerceIn(0, 100),
                        downloadedBytes = data.optLong("downloaded_bytes", 0L),
                        totalBytes = total,
                        speedText = data.optString("speed", "Server downloading…")
                    )
                }
            }

            pollServerTask(taskId, baseUrl, onComplete)
        } catch (cancelled: CancellationException) {
            // User cancellation is sent to the server separately in cancelDownload().
            updateTask(taskId) {
                it.copy(status = CloudDownloadStatus.CANCELLED, speedText = "Cancelled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server download failed", e)
            fail(taskId, e.localizedMessage ?: "Unable to reach the server")
        } finally {
            activeJobs.remove(taskId)
        }
    }

    private suspend fun pollServerTask(
        taskId: String,
        baseUrl: String,
        onComplete: (() -> Unit)?
    ) {
        val serverTaskId = synchronized(serverTaskIds) { serverTaskIds[taskId] } ?: run {
            fail(taskId, "Server task ID is missing")
            return
        }

        val client = ApiClient.okHttpClient.newBuilder()
            .callTimeout(12, TimeUnit.SECONDS)
            .build()

        var lastBytes = 0L
        var lastSampleAt = System.currentTimeMillis()

        while (scope.coroutineContext.isActive) {
            delay(POLL_INTERVAL_MS)

            val encodedId = URLEncoder.encode(serverTaskId, "UTF-8")
            val request = Request.Builder()
                .url("$baseUrl/api/files/remote-download?task_id=$encodedId")
                .header("Accept", "application/json")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()

                    if (response.code == 404) {
                        fail(taskId, "Server task is no longer available. The server may have restarted.")
                        return
                    }
                    if (!response.isSuccessful) {
                        updateTask(taskId) { it.copy(speedText = "Waiting for server…") }
                        return@use
                    }

                    val root = JSONObject(body)
                    val data = root.optJSONObject("data") ?: root
                    val status = data.optString("status", "queued").lowercase()
                    val percent = data.optInt("progress_percent", 0).coerceIn(0, 100)
                    val downloaded = data.optLong("downloaded_bytes", 0L)
                    val total = data.optLong("total_bytes", 0L)

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastSampleAt
                    val clientSpeed = if (elapsed >= 500) {
                        ((downloaded - lastBytes).coerceAtLeast(0L) * 1000L / elapsed)
                    } else 0L
                    if (elapsed >= 500) {
                        lastBytes = downloaded
                        lastSampleAt = now
                    }

                    val serverSpeed = data.optString("speed", "")
                    val speedText = when {
                        status == "queued" -> "Queued on server…"
                        serverSpeed.isNotBlank() && serverSpeed != "Starting…" -> serverSpeed
                        clientSpeed > 0 -> formatSpeed(clientSpeed)
                        else -> "Downloading from server…"
                    }

                    when (status) {
                        "completed", "finished" -> {
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.COMPLETED,
                                    progressPercent = 100,
                                    downloadedBytes = downloaded,
                                    totalBytes = if (total > 0) total else downloaded,
                                    speedText = "Completed • Stored on Server",
                                    errorMessage = null
                                )
                            }
                            HttpServerRepository.notifyFilesChanged(
                                _tasks.value.firstOrNull { it.id == taskId }?.destinationFolder ?: "/"
                            )
                            synchronized(serverTaskIds) { serverTaskIds.remove(taskId) }
                            onComplete?.invoke()
                            return
                        }

                        "cancelled", "canceled" -> {
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.CANCELLED,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    speedText = "Cancelled by user"
                                )
                            }
                            synchronized(serverTaskIds) { serverTaskIds.remove(taskId) }
                            return
                        }

                        "failed", "error" -> {
                            fail(taskId, data.optString("error", "Server download failed"))
                            synchronized(serverTaskIds) { serverTaskIds.remove(taskId) }
                            return
                        }

                        "cancelling" -> {
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.CANCELLED,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    speedText = "Cancelling on server…"
                                )
                            }
                        }

                        else -> {
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.DOWNLOADING,
                                    progressPercent = percent,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                    speedText = speedText
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // A single lost poll must not fail a long server-side download.
                updateTask(taskId) {
                    it.copy(speedText = "Reconnecting to server…")
                }
                Log.d(TAG, "Download status poll failed: ${e.message}")
            }
        }
    }

    fun cancelDownload(taskId: String) {
        val serverTaskId = synchronized(serverTaskIds) { serverTaskIds[taskId] }
        if (serverTaskId != null) {
            scope.launch {
                try {
                    val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
                    val encodedId = URLEncoder.encode(serverTaskId, "UTF-8")
                    val request = Request.Builder()
                        .url("$baseUrl/api/files/remote-download/$encodedId/cancel")
                        .post("{}".toRequestBody("application/json".toMediaType()))
                        .build()
                    ApiClient.okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Server cancellation returned HTTP ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to send server cancellation: ${e.message}")
                }
            }
        }

        activeJobs.remove(taskId)?.cancel()
        updateTask(taskId) {
            it.copy(
                status = CloudDownloadStatus.CANCELLED,
                errorMessage = null,
                speedText = "Cancelled"
            )
        }
    }

    fun clearCompleted() {
        _tasks.update { list ->
            list.filterNot {
                it.status == CloudDownloadStatus.COMPLETED ||
                    it.status == CloudDownloadStatus.CANCELLED ||
                    it.status == CloudDownloadStatus.FAILED
            }
        }
    }

    private fun fail(taskId: String, message: String) {
        updateTask(taskId) {
            it.copy(
                status = CloudDownloadStatus.FAILED,
                errorMessage = message,
                speedText = "Download failed"
            )
        }
    }

    private fun updateTask(
        taskId: String,
        transform: (CloudDownloadTask) -> CloudDownloadTask
    ) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    private fun parseServerError(body: String, fallback: String): String {
        return try {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            error?.optString("message").orEmpty().ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024L * 1024L ->
                String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024L ->
                String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}
