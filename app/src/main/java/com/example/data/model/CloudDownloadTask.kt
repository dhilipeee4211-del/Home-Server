package com.example.data.model

import java.util.Locale

enum class CloudDownloadStatus {
    QUEUED,
    DOWNLOADING,
    STORING_TO_SERVER,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class CloudDownloadTask(
    val id: String,
    val url: String,
    val filename: String,
    val destinationFolder: String,
    val progressPercent: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedText: String = "",
    val status: CloudDownloadStatus = CloudDownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isUpload: Boolean = false,
    val tempCachePath: String? = null
) {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    val transferredFormatted: String
        get() = formatBytes(downloadedBytes)

    val totalFormatted: String
        get() = if (totalBytes > 0) formatBytes(totalBytes) else "Unknown size"
}
