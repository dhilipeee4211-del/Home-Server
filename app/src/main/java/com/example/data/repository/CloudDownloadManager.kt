package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.network.ApiClient
import com.example.network.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object CloudDownloadManager {
    private const val TAG = "CloudDownloadManager"
    private const val PERSISTENCE_FILE_NAME = "server_transfers_v2.json"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val activeJobs = mutableMapOf<String, Job>()
    private val activeOkHttpCalls = mutableMapOf<String, okhttp3.Call>()

    private val _tasks = MutableStateFlow<List<CloudDownloadTask>>(emptyList())
    val tasks: StateFlow<List<CloudDownloadTask>> = _tasks.asStateFlow()

    // Separate Flow for Downloads only
    val downloads: StateFlow<List<CloudDownloadTask>> = _tasks.map { list ->
        list.filter { !it.isUpload }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Separate Flow for Uploads only
    val uploads: StateFlow<List<CloudDownloadTask>> = _tasks.map { list ->
        list.filter { it.isUpload }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var appContext: Context? = null

    /**
     * Initializes the manager, restores saved transfer states after sudden app close or crash,
     * marks previously active tasks as PAUSED (so user can resume them), and purges orphaned cache files.
     */
    fun initialize(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        loadPersistedTasks(appCtx)
        cleanStaleCacheFiles(appCtx)
        startServerSyncLoop()
        // Downloads are server-owned; immediately rehydrate their current state.
        scope.launch {
            val baseUrl = getEffectiveBaseUrl()
            if (baseUrl.isNotBlank()) syncWithServerTasks(baseUrl)
        }
    }

    /**
     * Extracts or derives a clean filename from a URL, preserving extension
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
     * Starts a live cloud download directly on the server side.
     * The Android phone does NOT download file bytes to device cache.
     * All connected devices see live server progress and can pause/resume/cancel.
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
            speedText = "Injecting to Server...",
            isUpload = false
        )

        _tasks.update { listOf(initialTask) + it }
        savePersistedTasks()

        scope.launch {
            injectDownloadToServer(taskId, cleanUrl, filename, destinationFolder, onComplete)
        }

        return taskId
    }

    /**
     * Starts an upload task with real-time tracking, pause/resume, and crash resistance.
     */
    fun startUpload(
        context: Context,
        uri: Uri,
        destinationFolder: String,
        onComplete: (() -> Unit)? = null
    ): String {
        val appCtx = context.applicationContext
        val originalName = HttpServerRepository.getFileNameFromUri(appCtx, uri)
            ?: "upload_${System.currentTimeMillis()}.bin"
        val taskId = "up_${UUID.randomUUID().toString().take(8)}"

        // Probe file size
        var fileSize = 0L
        try {
            appCtx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                fileSize = pfd.statSize.coerceAtLeast(0L)
            }
        } catch (_: Exception) {}

        val task = CloudDownloadTask(
            id = taskId,
            url = uri.toString(),
            filename = originalName,
            destinationFolder = destinationFolder,
            totalBytes = fileSize,
            status = CloudDownloadStatus.DOWNLOADING,
            speedText = "Preparing upload...",
            isUpload = true
        )

        _tasks.update { listOf(task) + it }
        savePersistedTasks()

        val job = scope.launch {
            executeUploadTask(taskId, uri, originalName, destinationFolder, onComplete)
        }
        activeJobs[taskId] = job

        return taskId
    }

    /**
     * Pauses any active download or upload task.
     * For cloud downloads, dispatches a pause request to the server so all devices reflect the state.
     */
    fun pauseTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.isUpload) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            activeOkHttpCalls[taskId]?.cancel()
            activeOkHttpCalls.remove(taskId)
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.PAUSED,
                    speedText = "Paused • Tap Resume to continue"
                )
            }
            savePersistedTasks()
        } else {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.PAUSED,
                    speedText = "Paused on Server • Tap Resume"
                )
            }
            savePersistedTasks()
            scope.launch {
                sendServerControlCommand(taskId, "pause")
            }
        }
    }

    /**
     * Resumes a paused download or upload task.
     * For cloud downloads, dispatches a resume request to the server so all devices reflect the state.
     */
    fun resumeTask(taskId: String, onComplete: (() -> Unit)? = null) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        if (task.status != CloudDownloadStatus.PAUSED && task.status != CloudDownloadStatus.FAILED) return

        if (task.isUpload) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.DOWNLOADING,
                    errorMessage = null,
                    speedText = "Resuming upload..."
                )
            }
            savePersistedTasks()
            val job = scope.launch {
                val uri = Uri.parse(task.url)
                executeUploadTask(taskId, uri, task.filename, task.destinationFolder, onComplete)
            }
            activeJobs[taskId] = job
        } else {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.DOWNLOADING,
                    errorMessage = null,
                    speedText = "Resuming on Server..."
                )
            }
            savePersistedTasks()
            scope.launch {
                sendServerControlCommand(taskId, "resume")
            }
        }
    }

    /**
     * Cancels and permanently removes a task.
     */
    fun cancelDownload(taskId: String) {
        cancelTask(taskId)
    }

    fun cancelTask(taskId: String) {
        val task = _tasks.value.find { it.id == taskId }
        if (task?.isUpload == true) {
            activeJobs[taskId]?.cancel()
            activeJobs.remove(taskId)
            activeOkHttpCalls[taskId]?.cancel()
            activeOkHttpCalls.remove(taskId)
            task.tempCachePath?.let { path ->
                try { File(path).delete() } catch (_: Exception) {}
            }
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.CANCELLED,
                    speedText = "Cancelled by user"
                )
            }
            savePersistedTasks()
            appContext?.let { cleanStaleCacheFiles(it) }
        } else {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.CANCELLED,
                    speedText = "Cancelled on Server"
                )
            }
            savePersistedTasks()
            scope.launch {
                sendServerControlCommand(taskId, "cancel")
            }
        }
    }

    /**
     * Retries a failed or cancelled task.
     */
    fun retryTask(taskId: String, onComplete: (() -> Unit)? = null) {
        val task = _tasks.value.find { it.id == taskId } ?: return
        updateTask(taskId) {
            it.copy(
                status = CloudDownloadStatus.DOWNLOADING,
                errorMessage = null,
                speedText = "Retrying..."
            )
        }
        savePersistedTasks()

        if (task.isUpload) {
            val job = scope.launch {
                val uri = Uri.parse(task.url)
                executeUploadTask(taskId, uri, task.filename, task.destinationFolder, onComplete)
            }
            activeJobs[taskId] = job
        } else {
            // The task id is the persistent server job id. Retry/resume that job instead
            // of creating a second download.
            scope.launch {
                sendServerControlCommand(taskId, "resume")
                syncWithServerTasks(getEffectiveBaseUrl())
                onComplete?.invoke()
            }
        }
    }

    /**
     * Pauses all active downloads and uploads.
     */
    fun pauseAll() {
        val activeIds = _tasks.value.filter {
            it.status == CloudDownloadStatus.DOWNLOADING || it.status == CloudDownloadStatus.STORING_TO_SERVER
        }.map { it.id }

        for (id in activeIds) {
            pauseTask(id)
        }
    }

    /**
     * Resumes all paused transfers.
     */
    fun resumeAll() {
        val pausedIds = _tasks.value.filter { it.status == CloudDownloadStatus.PAUSED }.map { it.id }
        for (id in pausedIds) {
            resumeTask(id)
        }
    }

    /**
     * Clears completed or cancelled tasks, and purges any leftover cache files.
     */
    fun clearCompleted(isUploadFilter: Boolean? = null) {
        _tasks.update { list ->
            list.filter { task ->
                if (isUploadFilter != null && task.isUpload != isUploadFilter) {
                    true
                } else {
                    task.status == CloudDownloadStatus.DOWNLOADING ||
                            task.status == CloudDownloadStatus.STORING_TO_SERVER ||
                            task.status == CloudDownloadStatus.PAUSED
                }
            }
        }
        savePersistedTasks()
        appContext?.let { cleanStaleCacheFiles(it) }
    }

    /**
     * Helper to retrieve configured server base URL.
     */
    fun getEffectiveBaseUrl(): String {
        var baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        if (baseUrl.isBlank() && ServerConfig.serverHost.value.isNotBlank()) {
            val scheme = if (ServerConfig.useHttps.value) "https" else "http"
            baseUrl = "$scheme://${ServerConfig.serverHost.value}:${ServerConfig.serverPort.value}"
        }
        return baseUrl
    }

    /**
     * Injects the download URL to the server so download is performed 100% on the server side.
     * The phone APK does not download any bytes to device storage or cache.
     */
    private suspend fun injectDownloadToServer(
        taskId: String,
        url: String,
        filename: String,
        destinationFolder: String,
        onComplete: (() -> Unit)?
    ) {
        val baseUrl = getEffectiveBaseUrl()
        if (baseUrl.isBlank()) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = "Server not configured. Please set Server IP in Settings."
                )
            }
            savePersistedTasks()
            return
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // One canonical endpoint. Do not probe unrelated endpoints: a 404 from another
        // endpoint must never be mistaken for a failed server download.
        val candidateEndpoints = listOf("/api/files/remote-download")

        val payload = JSONObject().apply {
            put("url", url)
            put("filename", filename)
            put("destination", destinationFolder)
            put("destination_folder", destinationFolder)
            put("path", destinationFolder)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        var injected = false
        var serverAssignedId: String? = null
        var lastError = "Server rejected remote download"

        for (endpoint in candidateEndpoints) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl$endpoint")
                    .header("Authorization", "Bearer ${ApiClient.authInterceptor.getToken().orEmpty()}")
                    .post(payload)
                    .build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful || resp.code in 200..202) {
                    injected = true
                    if (body.isNotBlank() && body.trim().startsWith("{")) {
                        val root = JSONObject(body)
                        val data = root.optJSONObject("data") ?: root
                        serverAssignedId = data.optString("task_id", data.optString("id", null))
                    }
                    break
                } else {
                    lastError = "Server HTTP ${resp.code}: ${resp.message}"
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Connection failed"
            }
        }

        if (injected) {
            updateTask(taskId) {
                it.copy(
                    id = serverAssignedId ?: it.id,
                    status = CloudDownloadStatus.DOWNLOADING,
                    speedText = "Server download queued…",
                    progressPercent = 0
                )
            }
            savePersistedTasks()
            syncWithServerTasks(baseUrl)
        } else {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = lastError
                )
            }
            savePersistedTasks()
        }
    }

    /**
     * Sends remote control commands (pause, resume, cancel) to the server.
     */
    private suspend fun sendServerControlCommand(taskId: String, action: String) {
        val baseUrl = getEffectiveBaseUrl()
        if (baseUrl.isBlank()) return

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val endpoints = when (action) {
            "pause" -> listOf("/api/files/remote-download/pause")
            "resume" -> listOf("/api/files/remote-download/resume")
            "cancel" -> listOf("/api/files/remote-download/cancel")
            else -> emptyList()
        }

        val payload = JSONObject().apply {
            put("id", taskId)
            put("task_id", taskId)
            put("action", action)
        }.toString().toRequestBody("application/json".toMediaTypeOrNull())

        for (ep in endpoints) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl$ep")
                    .header("Authorization", "Bearer ${ApiClient.authInterceptor.getToken().orEmpty()}")
                    .post(payload)
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) break
            } catch (_: Exception) {}
        }
        syncWithServerTasks(baseUrl)
    }

    private var syncJob: Job? = null

    /**
     * Continuously synchronizes download progress from the server so all connected devices
     * receive live real-time progress, and tasks persist even across app restarts or cache clears.
     */
    fun startServerSyncLoop() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            while (true) {
                try {
                    val baseUrl = getEffectiveBaseUrl()
                    if (baseUrl.isNotBlank()) {
                        syncWithServerTasks(baseUrl)
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Server sync iteration error: ${e.message}")
                }
                val hasActive = _tasks.value.any {
                    it.status == CloudDownloadStatus.DOWNLOADING ||
                            it.status == CloudDownloadStatus.STORING_TO_SERVER ||
                            it.status == CloudDownloadStatus.QUEUED
                }
                delay(if (hasActive) 1500L else 4000L)
            }
        }
    }

    private suspend fun syncWithServerTasks(baseUrl: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        val candidatePaths = listOf("/api/files/remote-download")

        for (path in candidatePaths) {
            try {
                val req = Request.Builder()
                    .url("$baseUrl$path")
                    .header("Authorization", "Bearer ${ApiClient.authInterceptor.getToken().orEmpty()}")
                    .get()
                    .build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    if (str.isNotBlank() && (str.trim().startsWith("{") || str.trim().startsWith("["))) {
                        parseAndMergeServerTasks(str)
                        break
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun parseAndMergeServerTasks(jsonStr: String) {
        val parsedTasks = mutableListOf<CloudDownloadTask>()
        try {
            val trim = jsonStr.trim()
            val arr = if (trim.startsWith("[")) {
                JSONArray(trim)
            } else {
                val root = JSONObject(trim)
                val data = root.optJSONObject("data")
                data?.optJSONArray("tasks")
                    ?: data?.optJSONArray("downloads")
                    ?: data?.optJSONArray("items")
                    ?: root.optJSONArray("tasks")
                    ?: root.optJSONArray("downloads")
                    ?: root.optJSONArray("items")
                    ?: (if (data == null && root.optJSONArray("data") != null) root.getJSONArray("data") else null)
            } ?: return

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("task_id", obj.optString("download_id", "")))
                if (id.isBlank()) continue

                val filename = obj.optString("filename", obj.optString("name", obj.optString("file_name", "download.bin")))
                val url = obj.optString("url", "")
                val destination = obj.optString("destination", obj.optString("destination_folder", obj.optString("path", "/")))
                val progressRaw = if (obj.has("progress")) obj.optDouble("progress", 0.0)
                    else if (obj.has("progress_percent")) obj.optDouble("progress_percent", 0.0)
                    else if (obj.has("percent")) obj.optDouble("percent", 0.0)
                    else 0.0
                val progressPercent = if (progressRaw in 0.0..1.0 && progressRaw > 0.0) {
                    (progressRaw * 100).toInt()
                } else {
                    progressRaw.toInt().coerceIn(0, 100)
                }

                val downloadedBytes = obj.optLong("downloaded_bytes", obj.optLong("downloaded", 0L))
                val totalBytes = obj.optLong("total_bytes", obj.optLong("total", 0L))
                val speedStr = obj.optString("speed_text", obj.optString("speed", obj.optString("speed_human", "")))
                val statusRaw = obj.optString("status", "downloading").lowercase()
                val errorMsg = obj.optString("error", obj.optString("error_message", obj.optString("message", ""))).takeIf { it.isNotBlank() }

                val status = when {
                    statusRaw.contains("pause") -> CloudDownloadStatus.PAUSED
                    statusRaw.contains("complete") || statusRaw.contains("done") || statusRaw.contains("finish") || statusRaw.contains("success") -> CloudDownloadStatus.COMPLETED
                    statusRaw.contains("fail") || statusRaw.contains("error") -> CloudDownloadStatus.FAILED
                    statusRaw.contains("queue") || statusRaw.contains("wait") -> CloudDownloadStatus.QUEUED
                    else -> CloudDownloadStatus.DOWNLOADING
                }

                val speedText = when (status) {
                    CloudDownloadStatus.DOWNLOADING -> if (speedStr.isNotBlank()) "$speedStr • Server Download" else "Server-side download in progress..."
                    CloudDownloadStatus.PAUSED -> "Paused on Server • Tap Resume"
                    CloudDownloadStatus.COMPLETED -> "Stored on Server"
                    CloudDownloadStatus.FAILED -> errorMsg ?: "Server download failed"
                    else -> speedStr
                }

                parsedTasks.add(
                    CloudDownloadTask(
                        id = id,
                        url = url,
                        filename = filename,
                        destinationFolder = destination,
                        progressPercent = progressPercent,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes,
                        speedText = speedText,
                        status = status,
                        errorMessage = errorMsg,
                        isUpload = false
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse server tasks: ${e.message}")
            return
        }

        if (parsedTasks.isEmpty()) return

        var newlyCompletedDestination: String? = null
        _tasks.update { currentList ->
            val updated = currentList.toMutableList()
            for (st in parsedTasks) {
                val idx = updated.indexOfFirst {
                    it.id == st.id || (!it.isUpload && it.url.isNotBlank() && it.url == st.url && it.filename == st.filename)
                }
                if (idx != -1) {
                    val existing = updated[idx]
                    if (existing.status != CloudDownloadStatus.COMPLETED && st.status == CloudDownloadStatus.COMPLETED) {
                        newlyCompletedDestination = st.destinationFolder
                    }
                    updated[idx] = existing.copy(
                        id = st.id,
                        filename = st.filename,
                        destinationFolder = st.destinationFolder,
                        progressPercent = st.progressPercent,
                        downloadedBytes = st.downloadedBytes,
                        totalBytes = st.totalBytes,
                        speedText = st.speedText,
                        status = st.status,
                        errorMessage = st.errorMessage
                    )
                } else {
                    // Task discovered from server (e.g. from another connected device or persisted server-side)!
                    updated.add(0, st)
                    if (st.status == CloudDownloadStatus.COMPLETED) {
                        newlyCompletedDestination = st.destinationFolder
                    }
                }
            }
            updated
        }

        if (newlyCompletedDestination != null) {
            HttpServerRepository.notifyFilesChanged(newlyCompletedDestination)
        }
        savePersistedTasks()
    }

    /**
     * Upload execution with real-time speed/percentage tracking, pause/resume, and immediate cache purge.
     */
    private suspend fun executeUploadTask(
        taskId: String,
        uri: Uri,
        filename: String,
        destinationFolder: String,
        onComplete: (() -> Unit)?
    ) {
        val appCtx = appContext ?: return
        var baseUrl = ServerConfig.baseUrl.value.trimEnd('/')
        if (baseUrl.isBlank() && ServerConfig.serverHost.value.isNotBlank()) {
            val scheme = if (ServerConfig.useHttps.value) "https" else "http"
            baseUrl = "$scheme://${ServerConfig.serverHost.value}:${ServerConfig.serverPort.value}"
        }

        if (baseUrl.isBlank()) {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.FAILED,
                    errorMessage = "Server not configured"
                )
            }
            savePersistedTasks()
            return
        }

        val tempUploadFile = File(appCtx.cacheDir, "upload_cache_${taskId}_$filename")
        updateTask(taskId) { it.copy(tempCachePath = tempUploadFile.absolutePath) }

        try {
            updateTask(taskId) {
                it.copy(
                    status = CloudDownloadStatus.DOWNLOADING,
                    speedText = "Reading local file...",
                    progressPercent = 5
                )
            }

            // Copy from Uri to cache file if not already copied
            if (!tempUploadFile.exists() || tempUploadFile.length() == 0L) {
                appCtx.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempUploadFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val fileSize = tempUploadFile.length().coerceAtLeast(0L)
            updateTask(taskId) {
                it.copy(totalBytes = fileSize, speedText = "Uploading to server...")
            }

            // Progress tracking request body
            val fileRequestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = fileSize

                override fun writeTo(sink: BufferedSink) {
                    val fileSink = object : ForwardingSink(sink) {
                        var bytesWritten = 0L
                        var lastTime = System.currentTimeMillis()
                        var bytesSince = 0L

                        override fun write(source: Buffer, byteCount: Long) {
                            super.write(source, byteCount)
                            bytesWritten += byteCount
                            bytesSince += byteCount

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 350) {
                                val elapsedSec = (now - lastTime) / 1000.0
                                val speedMbps = if (elapsedSec > 0) (bytesSince / (1024.0 * 1024.0)) / elapsedSec else 0.0
                                val speedStr = String.format(Locale.US, "%.1f MB/s", speedMbps)
                                val pct = if (fileSize > 0) ((bytesWritten * 100) / fileSize).toInt().coerceIn(5, 98) else 50

                                updateTask(taskId) {
                                    it.copy(
                                        status = CloudDownloadStatus.DOWNLOADING,
                                        progressPercent = pct,
                                        downloadedBytes = bytesWritten,
                                        speedText = "$speedStr • Uploading"
                                    )
                                }
                                lastTime = now
                                bytesSince = 0L
                            }
                        }
                    }
                    val buffered = fileSink.buffer()
                    tempUploadFile.inputStream().use { stream ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            buffered.write(buffer, 0, read)
                        }
                    }
                    buffered.flush()
                }
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, fileRequestBody)
                .addFormDataPart("destination", destinationFolder)
                .addFormDataPart("path", destinationFolder)
                .build()

            val uploadClient = ApiClient.okHttpClient.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/files/upload")
                .post(requestBody)
                .build()

            val call = uploadClient.newCall(request)
            activeOkHttpCalls[taskId] = call
            val response = call.execute()

            // CRITICAL: Immediately delete cache file after upload
            try {
                if (tempUploadFile.exists()) {
                    tempUploadFile.delete()
                }
            } catch (_: Exception) {}
            cleanStaleCacheFiles(appCtx)

            if (response.isSuccessful) {
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.COMPLETED,
                        progressPercent = 100,
                        downloadedBytes = fileSize,
                        totalBytes = fileSize,
                        speedText = "Uploaded to Server",
                        tempCachePath = null
                    )
                }
                savePersistedTasks()
                HttpServerRepository.notifyFilesChanged(destinationFolder)
                onComplete?.invoke()
            } else {
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.FAILED,
                        errorMessage = "Upload failed: HTTP ${response.code}"
                    )
                }
                savePersistedTasks()
            }
        } catch (e: Exception) {
            val isCancelled = activeJobs[taskId]?.isCancelled == true
            if (!isCancelled) {
                updateTask(taskId) {
                    it.copy(
                        status = CloudDownloadStatus.FAILED,
                        errorMessage = e.message ?: "Upload failed"
                    )
                }
                savePersistedTasks()
            }
        } finally {
            try {
                if (tempUploadFile.exists()) tempUploadFile.delete()
            } catch (_: Exception) {}
            activeJobs.remove(taskId)
            activeOkHttpCalls.remove(taskId)
            cleanStaleCacheFiles(appCtx)
        }
    }

    /**
     * Purges all stale or orphaned temporary cache files from cache directory.
     */
    fun cleanStaleCacheFiles(context: Context) {
        try {
            val cacheDir = context.cacheDir
            val files = cacheDir.listFiles() ?: return
            val activePaths = _tasks.value.mapNotNull { it.tempCachePath }.toSet()

            for (file in files) {
                val name = file.name
                if (name.startsWith("part_") || name.startsWith("upload_cache_") || name.startsWith("cloud_dl_") || name.endsWith(".part")) {
                    if (!activePaths.contains(file.absolutePath)) {
                        try { file.delete() } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning cache: ${e.message}")
        }
    }

    /**
     * Atomically saves tasks to internal files storage so state survives sudden close or crash.
     */
    /**
     * Only Android-originated uploads may be persisted locally.
     * Server downloads are authoritative on the server and are rehydrated from its API.
     */
    private fun savePersistedTasks() {
        val appCtx = appContext ?: return
        try {
            val file = File(appCtx.filesDir, PERSISTENCE_FILE_NAME)
            val jsonArray = JSONArray()
            for (t in _tasks.value.filter { it.isUpload }) {
                val obj = JSONObject().apply {
                    put("id", t.id)
                    put("url", t.url)
                    put("filename", t.filename)
                    put("destinationFolder", t.destinationFolder)
                    put("progressPercent", t.progressPercent)
                    put("downloadedBytes", t.downloadedBytes)
                    put("totalBytes", t.totalBytes)
                    put("speedText", t.speedText)
                    put("status", t.status.name)
                    put("errorMessage", t.errorMessage ?: "")
                    put("timestamp", t.timestamp)
                    put("isUpload", true)
                    put("tempCachePath", t.tempCachePath ?: "")
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist upload tasks: ${e.message}")
        }
    }

    /**
     * Restores only uploads. Server-side downloads are never restored from Android storage.
     */
    private fun loadPersistedTasks(context: Context) {
        try {
            val file = File(context.filesDir, PERSISTENCE_FILE_NAME)
            if (!file.exists()) return
            val content = file.readText()
            if (content.isBlank()) return

            val jsonArray = JSONArray(content)
            val restoredList = mutableListOf<CloudDownloadTask>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (!obj.optBoolean("isUpload", false)) continue

                val rawStatus = obj.optString("status", CloudDownloadStatus.QUEUED.name)
                var status = try {
                    CloudDownloadStatus.valueOf(rawStatus)
                } catch (_: Exception) {
                    CloudDownloadStatus.QUEUED
                }
                var speedText = obj.optString("speedText", "")
                if (status == CloudDownloadStatus.DOWNLOADING ||
                    status == CloudDownloadStatus.STORING_TO_SERVER ||
                    status == CloudDownloadStatus.QUEUED) {
                    status = CloudDownloadStatus.PAUSED
                    speedText = "Interrupted (App Closed) • Tap Resume"
                }

                restoredList.add(
                    CloudDownloadTask(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        url = obj.optString("url", ""),
                        filename = obj.optString("filename", "file"),
                        destinationFolder = obj.optString("destinationFolder", "/"),
                        progressPercent = obj.optInt("progressPercent", 0),
                        downloadedBytes = obj.optLong("downloadedBytes", 0L),
                        totalBytes = obj.optLong("totalBytes", 0L),
                        speedText = speedText,
                        status = status,
                        errorMessage = obj.optString("errorMessage", "").takeIf { it.isNotBlank() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isUpload = true,
                        tempCachePath = obj.optString("tempCachePath", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            _tasks.update { current ->
                val serverDownloads = current.filter { !it.isUpload }
                restoredList + serverDownloads
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load persisted upload tasks: ${e.message}")
        }
    }

    private fun updateTask(taskId: String, transform: (CloudDownloadTask) -> CloudDownloadTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }
}
