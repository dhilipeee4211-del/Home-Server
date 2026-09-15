package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.network.ApiClient
import com.example.network.ServerConfig
import com.example.network.models.RemoteDownloadCancelRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.UUID

object CloudDownloadManager {
    private const val TAG = "CloudDownloadManager"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()
    private val serverTaskIds = mutableMapOf<String, String>()
    private val _tasks = MutableStateFlow<List<CloudDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CloudDownloadTask>> = _tasks.asStateFlow()

    fun initialize(context: Context) = Unit

    fun deriveFilename(urlStr: String): String = try {
        val raw = URL(urlStr).path.substringAfterLast('/').trim()
        if (raw.isNotBlank()) URLDecoder.decode(raw, "UTF-8")
        else "download_${System.currentTimeMillis()}.bin"
    } catch (_: Exception) { "download_${System.currentTimeMillis()}.bin" }

    fun startDownload(
        url: String,
        destinationFolder: String = "/",
        customFilename: String? = null,
        onComplete: (() -> Unit)? = null
    ): String {
        val taskId = "dl_${UUID.randomUUID().toString().take(10)}"
        val filename = customFilename?.trim().takeUnless { it.isNullOrBlank() } ?: deriveFilename(url)
        _tasks.update { listOf(CloudDownloadTask(taskId, url.trim(), filename, destinationFolder, status = CloudDownloadStatus.QUEUED, speedText = "Connecting to server…")) + it }
        activeJobs[taskId] = scope.launch { execute(taskId, url.trim(), filename, destinationFolder, onComplete) }
        return taskId
    }

    private suspend fun execute(taskId: String, url: String, filename: String, destination: String, onComplete: (() -> Unit)?) {
        val api = ApiClient.getApiService()
        if (api == null || ServerConfig.baseUrl.value.isBlank()) {
            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server is not configured") }
            return
        }
        try {
            updateTask(taskId) { it.copy(status = CloudDownloadStatus.STORING_TO_SERVER, speedText = "Sending download request…") }
            val payload = JSONObject().apply {
                put("url", url)
                put("filename", filename)
                put("destination", destination)
            }
            val request = okhttp3.Request.Builder()
                .url(ServerConfig.baseUrl.value.trimEnd('/') + "/api/files/remote-download")
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            val body = response.use { it.body?.string().orEmpty() }
            if (!response.isSuccessful) throw IllegalStateException(parseError(body, "Server rejected download (${response.code})"))
            val data = JSONObject(body).optJSONObject("data") ?: JSONObject(body)
            val serverId = data.optString("task_id").ifBlank { throw IllegalStateException("Server did not return a task ID") }
            serverTaskIds[taskId] = serverId

            var done = false
            var transientFailures = 0
            while (!done) {
                delay(700)
                try {
                    val statusUrl = ServerConfig.baseUrl.value.trimEnd('/') + "/api/files/remote-download?task_id=" + URLEncoder.encode(serverId, "UTF-8")
                    val poll = ApiClient.okHttpClient.newCall(okhttp3.Request.Builder().url(statusUrl).get().build()).execute()
                    val pollBody = poll.use { it.body?.string().orEmpty() }
                    if (!poll.isSuccessful) throw IllegalStateException("HTTP ${poll.code}")
                    val obj = JSONObject(pollBody).optJSONObject("data") ?: JSONObject(pollBody)
                    val status = obj.optString("status", "queued").lowercase()
                    val downloaded = obj.optLong("downloaded_bytes", 0L)
                    val total = obj.optLong("total_bytes", 0L)
                    val percent = obj.optInt("progress_percent", if (total > 0) ((downloaded * 100) / total).toInt() else 0).coerceIn(0, 100)
                    val speedBps = obj.optLong("speed_bps", 0L)
                    val speed = if (speedBps > 0) formatRate(speedBps) else when (status) {
                        "queued" -> "Queued on server…"
                        "cancelling" -> "Cancelling…"
                        else -> "Preparing stream…"
                    }
                    transientFailures = 0
                    when (status) {
                        "completed", "finished" -> {
                            done = true
                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.COMPLETED, progressPercent = 100, downloadedBytes = downloaded, totalBytes = total, speedText = "Completed • ${formatBytes(downloaded)}") }
                            HttpServerRepository.notifyFilesChanged(destination)
                            onComplete?.invoke()
                        }
                        "cancelled" -> {
                            done = true
                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.CANCELLED, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, speedText = "Cancelled") }
                        }
                        "failed", "error" -> {
                            done = true
                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, errorMessage = obj.optString("error", "Server download failed")) }
                        }
                        else -> updateTask(taskId) { it.copy(status = CloudDownloadStatus.DOWNLOADING, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, speedText = speed) }
                    }
                } catch (e: Exception) {
                    transientFailures++
                    if (transientFailures >= 10) throw IllegalStateException("No response from server while checking download")
                    updateTask(taskId) { it.copy(speedText = "Reconnecting to server… ($transientFailures/10)") }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cloud download failed", e)
            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, errorMessage = e.message ?: "Cloud download failed") }
        } finally {
            activeJobs.remove(taskId)
            serverTaskIds.remove(taskId)
        }
    }

    fun cancelDownload(taskId: String) {
        scope.launch {
            val serverId = serverTaskIds[taskId]
            if (serverId.isNullOrBlank()) {
                activeJobs.remove(taskId)?.cancel()
                updateTask(taskId) { it.copy(status = CloudDownloadStatus.CANCELLED, speedText = "Cancelled") }
                return@launch
            }
            try {
                val api = ApiClient.getApiService()
                if (api != null) {
                    val response = api.cancelRemoteDownload(RemoteDownloadCancelRequest(serverId))
                    if (!response.isSuccessful) throw IllegalStateException("Server returned HTTP ${response.code()}")
                }
                updateTask(taskId) { it.copy(status = CloudDownloadStatus.CANCELLED, speedText = "Cancellation requested") }
            } catch (e: Exception) {
                updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Unable to cancel on server: ${e.message}") }
            }
        }
    }

    fun clearCompleted() {
        _tasks.update { list -> list.filterNot { it.status == CloudDownloadStatus.COMPLETED || it.status == CloudDownloadStatus.CANCELLED || it.status == CloudDownloadStatus.FAILED } }
    }

    private fun updateTask(id: String, transform: (CloudDownloadTask) -> CloudDownloadTask) = _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }

    private fun parseError(body: String, fallback: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message", fallback) ?: fallback
    } catch (_: Exception) { fallback }

    private fun formatRate(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / 1048576.0)
        bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
        else -> "$bytes B/s"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

}
