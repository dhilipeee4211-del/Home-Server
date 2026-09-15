package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.network.ApiClient
import com.example.network.ServerConfig
import com.example.network.ServerConnectionManager
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
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.UUID

object CloudDownloadManager {
    private const val TAG = "CloudDownloadManager"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<CloudDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CloudDownloadTask>> = _tasks.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Extracts or derives a filename from a URL
     */
    fun deriveFilename(urlStr: String): String {
        return try {
            val path = URL(urlStr).path
            val rawName = path.substringAfterLast('/', "").trim()
            if (rawName.isNotBlank() && rawName.contains(".")) {
                java.net.URLDecoder.decode(rawName, "UTF-8")
            } else {
                "download_${System.currentTimeMillis().toString().takeLast(6)}.bin"
            }
        } catch (e: Exception) {
            "download_${System.currentTimeMillis().toString().takeLast(6)}.bin"
        }
    }

    /**
     * Dispatches a live cloud download directly to the home server.
     * The home server performs the remote download directly over its own internet
     * and writes directly to its own storage pool.
     */
    fun startDownload(
        url: String,
        destinationFolder: String = "/",
        customFilename: String? = null,
        onComplete: (() -> Unit)? = null
    ): String {
        val cleanUrl = url.trim()
        val filename = if (!customFilename.isNullOrBlank()) customFilename.trim() else deriveFilename(cleanUrl)
        val taskId = "dl_${UUID.randomUUID().toString().take(8)}"

        val initialTask = CloudDownloadTask(
            id = taskId,
            url = cleanUrl,
            filename = filename,
            destinationFolder = destinationFolder,
            status = CloudDownloadStatus.DOWNLOADING,
            speedText = "Dispatching to Server..."
        )

        _tasks.update { listOf(initialTask) + it }

        val job = scope.launch {
            executeServerLiveDownload(taskId, cleanUrl, filename, destinationFolder, onComplete)
        }

        activeJobs[taskId] = job
        return taskId
    }

    private suspend fun executeServerLiveDownload(
        taskId: String,
        url: String,
        filename: String,
        destinationFolder: String,
        onComplete: (() -> Unit)?
    ) {
        val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        val isOnline = ServerConnectionManager.isConnected.value

        if (baseUrl.isBlank() || !isOnline) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = "Server offline: Connect DhilipHome server in Settings for server-side cloud download"
                )
            }
            return
        }

        try {
            updateTask(taskId) {
                it.copy(
                    speedText = "Requesting Server to download...",
                    progressPercent = 5
                )
            }

            val payload = JSONObject().apply {
                put("url", url)
                put("filename", filename)
                put("destination", destinationFolder)
            }

            // Use the real server-side download API. Do not simulate progress or completion.\n            val endpoint = "$baseUrl/api/files/remote-download"\n            val request = Request.Builder()\n                .url(endpoint)\n                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))\n                .build()\n\n            val response = ApiClient.okHttpClient.newCall(request).execute()\n            val body = response.body?.string().orEmpty()\n            if (!response.isSuccessful) {\n                updateTask(taskId) {\n                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server download request failed (HTTP ${response.code})")\n                }\n                return\n            }\n\n            val root = JSONObject(body)\n            val data = root.optJSONObject("data") ?: root\n            val serverTaskId = data.optString("task_id", data.optString("id", ""))\n            if (serverTaskId.isBlank()) {\n                updateTask(taskId) {\n                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server did not return a download task ID")\n                }\n                return\n            }\n\n            var isFinished = false\n            var pollCount = 0\n            while (!isFinished && pollCount < 240) {\n                delay(1000)\n                pollCount++\n                try {\n                    val statusUrl = "$baseUrl/api/files/remote-download?task_id=${java.net.URLEncoder.encode(serverTaskId, "UTF-8")}"\n                    val pollRequest = Request.Builder().url(statusUrl).get().build()\n                    val pollResponse = ApiClient.okHttpClient.newCall(pollRequest).execute()\n                    val pollBody = pollResponse.body?.string().orEmpty()\n                    if (!pollResponse.isSuccessful) {\n                        updateTask(taskId) { it.copy(speedText = "Waiting for server…") }\n                        continue\n                    }\n                    val pollRoot = JSONObject(pollBody)\n                    val pollJson = pollRoot.optJSONObject("data") ?: pollRoot\n                    val status = pollJson.optString("status", "queued").lowercase()\n                    val percent = pollJson.optInt("progress_percent", 0).coerceIn(0, 100)\n                    val downloaded = pollJson.optLong("downloaded_bytes", 0L)\n                    val total = pollJson.optLong("total_bytes", 0L)\n                    val speed = pollJson.optString("speed", if (status == "queued") "Queued on server" else "Server downloading")\n\n                    when (status) {\n                        "completed", "finished" -> {\n                            isFinished = true\n                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.COMPLETED, progressPercent = 100, downloadedBytes = downloaded, totalBytes = total, speedText = "Stored on Server") }\n                        }\n                        "failed", "error" -> {\n                            isFinished = true\n                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, errorMessage = pollJson.optString("error", "Server download failed")) }\n                        }\n                        else -> updateTask(taskId) { it.copy(status = CloudDownloadStatus.DOWNLOADING, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, speedText = speed) }\n                    }\n                } catch (e: Exception) {\n                    Log.d(TAG, "Poll error: ${e.message}")\n                }\n            }\n\n            if (!isFinished) {\n                updateTask(taskId) {\n                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server download timed out; check the server task before retrying")\n                }\n                return\n            }\n\n            HttpServerRepository.notifyFilesChanged(destinationFolder)
            onComplete?.invoke()

        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = e.message ?: "Server download error"
                )
            }
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTask(taskId) {
            it.copy(
                status = CloudDownloadStatus.FAILED,
                errorMessage = "Cancelled by user"
            )
        }
    }

    fun clearCompleted() {
        _tasks.update { list ->
            list.filter { it.status == CloudDownloadStatus.DOWNLOADING }
        }
    }

    private fun updateTask(taskId: String, transform: (CloudDownloadTask) -> CloudDownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
