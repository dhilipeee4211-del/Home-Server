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

    // Maps our local task id -> the server's own task id, needed to cancel/poll correctly.
    private val serverTaskIds = mutableMapOf<String, String>()

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

            // Use the real server-side download API. Do not simulate progress or completion.
            val endpoint = "$baseUrl/api/files/remote-download"
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            val response = ApiClient.okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                updateTask(taskId) {
                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server download request failed (HTTP ${response.code})")
                }
                return
            }

            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: root
            val serverTaskId = data.optString("task_id", data.optString("id", ""))
            if (serverTaskId.isBlank()) {
                updateTask(taskId) {
                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server did not return a download task ID")
                }
                return
            }
            serverTaskIds[taskId] = serverTaskId

            var isFinished = false
            var pollCount = 0
            while (!isFinished && pollCount < 240) {
                delay(1000)
                pollCount++

                // If the user cancelled locally, stop polling; cancelDownload() already
                // told the server to stop and marked the task CANCELLED.
                if (activeJobs[taskId]?.isCancelled == true) {
                    return
                }

                try {
                    val statusUrl = "$baseUrl/api/files/remote-download?task_id=${java.net.URLEncoder.encode(serverTaskId, "UTF-8")}"
                    val pollRequest = Request.Builder().url(statusUrl).get().build()
                    val pollResponse = ApiClient.okHttpClient.newCall(pollRequest).execute()
                    val pollBody = pollResponse.body?.string().orEmpty()
                    if (!pollResponse.isSuccessful) {
                        updateTask(taskId) { it.copy(speedText = "Waiting for server…") }
                        continue
                    }
                    val pollRoot = JSONObject(pollBody)
                    val pollJson = pollRoot.optJSONObject("data") ?: pollRoot
                    val status = pollJson.optString("status", "queued").lowercase()
                    val percent = pollJson.optInt("progress_percent", 0).coerceIn(0, 100)
                    val downloaded = pollJson.optLong("downloaded_bytes", 0L)
                    val total = pollJson.optLong("total_bytes", 0L)
                    val speed = pollJson.optString("speed", if (status == "queued") "Queued on server" else "Server downloading")

                    when (status) {
                        "completed", "finished" -> {
                            isFinished = true
                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.COMPLETED, progressPercent = 100, downloadedBytes = downloaded, totalBytes = total, speedText = "Stored on Server") }
                        }
                        "failed", "error" -> {
                            isFinished = true
                            updateTask(taskId) { it.copy(status = CloudDownloadStatus.FAILED, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, errorMessage = pollJson.optString("error", "Server download failed")) }
                        }
                        else -> updateTask(taskId) { it.copy(status = CloudDownloadStatus.DOWNLOADING, progressPercent = percent, downloadedBytes = downloaded, totalBytes = total, speedText = speed) }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Poll error: ${e.message}")
                }
            }

            if (!isFinished) {
                updateTask(taskId) {
                    it.copy(status = CloudDownloadStatus.FAILED, errorMessage = "Server download timed out; check the server task before retrying")
                }
                return
            }

            HttpServerRepository.notifyFilesChanged(destinationFolder)
            onComplete?.invoke()

        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = e.message ?: "Server download error"
                )
            }
        } finally {
            serverTaskIds.remove(taskId)
        }
    }

    /**
     * Cancels a download both locally (stops polling) and on the server
     * (tells it to abort the in-progress transfer and clean up the .part file).
     */
    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)

        val serverTaskId = serverTaskIds[taskId]
        val baseUrl = ServerConfig.baseUrl.value.trimEnd('/')

        if (!serverTaskId.isNullOrBlank() && baseUrl.isNotBlank()) {
            scope.launch {
                try {
                    val cancelUrl = "$baseUrl/api/files/remote-download?task_id=${java.net.URLEncoder.encode(serverTaskId, "UTF-8")}"
                    val request = Request.Builder().url(cancelUrl).delete().build()
                    ApiClient.okHttpClient.newCall(request).execute().close()
                } catch (e: Exception) {
                    Log.d(TAG, "Cancel request error: ${e.message}")
                }
            }
        }

        updateTask(taskId) {
            it.copy(
                status = CloudDownloadStatus.CANCELLED,
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
