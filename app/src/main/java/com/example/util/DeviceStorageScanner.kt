package com.example.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.data.model.FileItem
import com.example.data.model.FileType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DeviceStorageScanner {

    fun listFiles(context: Context, path: String): List<FileItem> {
        val cleanPath = path.trim()

        // Root directory level: provide standard storage entry points
        if (cleanPath == "/" || cleanPath.isBlank() || cleanPath == "/storage") {
            return getStorageRoots(context)
        }

        // Specific directory on device filesystem
        val targetDir = File(cleanPath)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            // Fallback to internal storage if not found
            val fallback = Environment.getExternalStorageDirectory()
            if (fallback.exists() && fallback.isDirectory) {
                return listDirectoryContents(fallback)
            }
            return emptyList()
        }

        return listDirectoryContents(targetDir)
    }

    private fun getStorageRoots(context: Context): List<FileItem> {
        val roots = mutableListOf<FileItem>()
        val extStorage = Environment.getExternalStorageDirectory()

        roots.add(
            FileItem(
                id = "root_internal",
                name = "Internal Storage",
                path = extStorage.absolutePath,
                isFolder = true,
                type = FileType.FOLDER,
                formattedSize = "Device Storage",
                modifiedDate = "System Root"
            )
        )

        val standardDirs = listOf(
            Pair("Downloads", Environment.DIRECTORY_DOWNLOADS),
            Pair("Movies", Environment.DIRECTORY_MOVIES),
            Pair("Pictures", Environment.DIRECTORY_PICTURES),
            Pair("Music", Environment.DIRECTORY_MUSIC),
            Pair("Documents", Environment.DIRECTORY_DOCUMENTS),
            Pair("DCIM", Environment.DIRECTORY_DCIM)
        )

        for ((name, dirType) in standardDirs) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(dirType)
                if (dir != null) {
                    val count = if (dir.exists()) dir.listFiles()?.size ?: 0 else 0
                    roots.add(
                        FileItem(
                            id = "root_$name",
                            name = name,
                            path = dir.absolutePath,
                            isFolder = true,
                            type = FileType.FOLDER,
                            formattedSize = "$count items",
                            modifiedDate = if (dir.exists()) formatDate(dir.lastModified()) else "Folder"
                        )
                    )
                }
            } catch (_: Exception) {}
        }

        return roots
    }

    private fun listDirectoryContents(directory: File): List<FileItem> {
        val rawFiles = directory.listFiles() ?: return emptyList()
        val items = mutableListOf<FileItem>()

        for (file in rawFiles) {
            val isFolder = file.isDirectory
            val name = file.name

            // Skip hidden dotfiles
            if (name.startsWith(".")) continue

            val type = if (isFolder) FileType.FOLDER else detectFileType(name)
            val sizeBytes = if (isFolder) null else file.length()
            val formattedSize = if (isFolder) {
                val childCount = file.listFiles()?.size ?: 0
                "$childCount items"
            } else {
                formatBytes(file.length())
            }

            val fileUri = Uri.fromFile(file).toString()

            items.add(
                FileItem(
                    id = file.absolutePath,
                    name = name,
                    path = file.absolutePath,
                    isFolder = isFolder,
                    type = type,
                    sizeBytes = sizeBytes,
                    formattedSize = formattedSize,
                    modifiedDate = formatDate(file.lastModified()),
                    downloadUrl = fileUri
                )
            )
        }

        // Sort folders first, then files alphabetically
        return items.sortedWith(
            compareBy<FileItem> { !it.isFolder }.thenBy { it.name.lowercase(Locale.getDefault()) }
        )
    }

    private fun detectFileType(fileName: String): FileType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v", "ts" -> FileType.VIDEO
            "mp3", "flac", "wav", "aac", "ogg", "m4a", "opus", "wma" -> FileType.AUDIO
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic", "heif" -> FileType.IMAGE
            "pdf", "doc", "docx", "txt", "rtf", "odt", "xls", "xlsx", "ppt", "pptx", "csv" -> FileType.DOCUMENT
            "zip", "tar", "gz", "rar", "7z", "bz2", "xz", "iso" -> FileType.ARCHIVE
            else -> FileType.OTHER
        }
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        val mb = bytes / 1_048_576.0
        val kb = bytes / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun formatDate(timeMs: Long): String {
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timeMs))
        } catch (_: Exception) {
            "Recent"
        }
    }
}
