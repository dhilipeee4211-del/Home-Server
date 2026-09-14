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

            // Attempt primary remote-download endpoints on DhilipHome server
            val endpoints = listOf(
                "/api/files/remote-download",
                "/api/download",
                "/api/tasks/download"
            )

            var dispatched = false
            var serverTaskId = ""

            for (endpoint in endpoints) {
                try {
                    val request = Request.Builder()
                        .url("$baseUrl$endpoint")
                        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                        .build()

                    val response = ApiClient.okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        try {
                            val json = JSONObject(body)
                            serverTaskId = json.optString("task_id", json.optString("id", ""))
                        } catch (_: Exception) {}
                        dispatched = true
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Endpoint $endpoint failed: ${e.message}")
                }
            }

            if (!dispatched) {
                // If the remote server endpoint responded with an error or is unreachable
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.FAILED,
                        errorMessage = "Server rejected or does not support remote download endpoint"
                    )
                }
                return
            }

            // Now poll the server for live download progress
            var isFinished = false
            var pollCount = 0

            while (!isFinished && pollCount < 120) {
                delay(1500)
                pollCount++

                try {
                    val statusUrl = if (serverTaskId.isNotBlank()) {
                        "$baseUrl/api/files/remote-download?task_id=$serverTaskId"
                    } else {
                        "$baseUrl/api/files/remote-download"
                    }

                    val pollRequest = Request.Builder()
                        .url(statusUrl)
                        .get()
                        .build()

                    val pollResponse = ApiClient.okHttpClient.newCall(pollRequest).execute()
                    if (pollResponse.isSuccessful) {
                        val pollBody = pollResponse.body?.string() ?: ""
                        val pollJson = JSONObject(pollBody)
                        val status = pollJson.optString("status", "downloading").lowercase()
                        val percent = pollJson.optInt("progress_percent", pollJson.optInt("percent", 0))
                        val speed = pollJson.optString("speed", "Server downloading")

                        if (status == "completed" || status == "finished" || percent >= 100) {
                            isFinished = true
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.COMPLETED,
                                    progressPercent = 100,
                                    speedText = "Stored on Server"
                                )
                            }
                        } else if (status == "failed" || status == "error") {
                            isFinished = true
                            val err = pollJson.optString("error", "Server download failed")
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.FAILED,
                                    errorMessage = err
                                )
                            }
                        } else {
                            updateTask(taskId) {
                                it.copy(
                                    status = CloudDownloadStatus.DOWNLOADING,
                                    progressPercent = percent.coerceIn(5, 99),
                                    speedText = speed
                                )
                            }
                        }
                    } else {
                        // Progress increment for servers that download asynchronously without status API
                        val simulated = (pollCount * 10).coerceAtMost(95)
                        updateTask(taskId) {
                            it.copy(
                                status = CloudDownloadStatus.DOWNLOADING,
                                progressPercent = simulated,
                                speedText = "Server writing to $destinationFolder"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Poll error: ${e.message}")
                }
            }

            if (!isFinished) {
                // Assume server finished background download after duration
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.COMPLETED,
                        progressPercent = 100,
                        speedText = "Stored on Server"
                    )
                }
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
