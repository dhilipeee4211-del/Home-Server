package com.example.util

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import java.io.File
import java.util.Locale

object DeviceMediaScanner {

    fun queryMedia(context: Context, category: MediaCategory): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        when (category) {
            MediaCategory.ALL -> {
                items.addAll(queryVideos(context))
                items.addAll(queryAudio(context))
                items.addAll(queryImages(context))
            }
            MediaCategory.MOVIES, MediaCategory.TV_SHOWS -> {
                items.addAll(queryVideos(context))
            }
            MediaCategory.MUSIC -> {
                items.addAll(queryAudio(context))
            }
            MediaCategory.PHOTOS -> {
                items.addAll(queryImages(context))
            }
        }

        // Also scan standard public media directories as direct file fallbacks
        if (items.isEmpty()) {
            items.addAll(scanDirectMediaFolders(category))
        }

        return items
    }

    private fun queryVideos(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = it.getColumnIndex(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = it.getString(nameCol) ?: "Video_$id"
                    val durationMs = if (durationCol != -1) it.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol != -1) it.getLong(sizeCol) else 0L

                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)
                    val durationStr = formatDuration(durationMs)
                    val sizeStr = formatBytes(sizeBytes)

                    list.add(
                        MediaItem(
                            id = "device_vid_$id",
                            title = name.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                            category = MediaCategory.MOVIES,
                            year = 2026,
                            duration = durationStr,
                            genre = "Device Video",
                            rating = 0.0,
                            description = "Local storage video ($name • $sizeStr)",
                            posterGradientColor = 0xFF1E3A8A,
                            backdropGradientColor = 0xFF0F172A,
                            resolution = "HD Video",
                            fileSizeBytes = sizeStr,
                            streamUrl = contentUri.toString(),
                            filePath = name,
                            isRecentlyAdded = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun queryAudio(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = (if (titleCol != -1) it.getString(titleCol) else null)
                        ?: (if (nameCol != -1) it.getString(nameCol) else null)
                        ?: "Audio_$id"
                    val artist = if (artistCol != -1) it.getString(artistCol) ?: "Unknown Artist" else "Audio"
                    val durationMs = if (durationCol != -1) it.getLong(durationCol) else 0L
                    val sizeBytes = if (sizeCol != -1) it.getLong(sizeCol) else 0L

                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        MediaItem(
                            id = "device_aud_$id",
                            title = title.substringBeforeLast('.'),
                            category = MediaCategory.MUSIC,
                            year = 2026,
                            duration = formatDuration(durationMs),
                            genre = artist,
                            rating = 0.0,
                            description = "$artist • ${formatBytes(sizeBytes)}",
                            posterGradientColor = 0xFF6B21A8,
                            backdropGradientColor = 0xFF1E1B4B,
                            audioFormat = "Audio Track",
                            fileSizeBytes = formatBytes(sizeBytes),
                            streamUrl = contentUri.toString(),
                            filePath = title,
                            isRecentlyAdded = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun queryImages(context: Context): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = it.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = if (nameCol != -1) it.getString(nameCol) ?: "Image_$id" else "Image_$id"
                    val sizeBytes = if (sizeCol != -1) it.getLong(sizeCol) else 0L
                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)

                    list.add(
                        MediaItem(
                            id = "device_img_$id",
                            title = name.substringBeforeLast('.'),
                            category = MediaCategory.PHOTOS,
                            year = 2026,
                            duration = "Photo",
                            genre = "Image Gallery",
                            rating = 0.0,
                            description = "Device photo • ${formatBytes(sizeBytes)}",
                            posterGradientColor = 0xFF065F46,
                            backdropGradientColor = 0xFF022C22,
                            resolution = "Image",
                            fileSizeBytes = formatBytes(sizeBytes),
                            streamUrl = contentUri.toString(),
                            filePath = name,
                            isRecentlyAdded = true
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        return list
    }

    private fun scanDirectMediaFolders(category: MediaCategory): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        val foldersToScan = when (category) {
            MediaCategory.ALL -> listOf(
                Pair(Environment.DIRECTORY_MOVIES, MediaCategory.MOVIES),
                Pair(Environment.DIRECTORY_MUSIC, MediaCategory.MUSIC),
                Pair(Environment.DIRECTORY_PICTURES, MediaCategory.PHOTOS),
                Pair(Environment.DIRECTORY_DOWNLOADS, MediaCategory.ALL)
            )
            MediaCategory.MOVIES, MediaCategory.TV_SHOWS -> listOf(
                Pair(Environment.DIRECTORY_MOVIES, MediaCategory.MOVIES),
                Pair(Environment.DIRECTORY_DOWNLOADS, MediaCategory.MOVIES)
            )
            MediaCategory.MUSIC -> listOf(
                Pair(Environment.DIRECTORY_MUSIC, MediaCategory.MUSIC)
            )
            MediaCategory.PHOTOS -> listOf(
                Pair(Environment.DIRECTORY_PICTURES, MediaCategory.PHOTOS),
                Pair(Environment.DIRECTORY_DCIM, MediaCategory.PHOTOS)
            )
        }

        for ((dirType, mediaCat) in foldersToScan) {
            try {
                val dir = Environment.getExternalStoragePublicDirectory(dirType)
                if (dir != null && dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        if (file.isDirectory || file.name.startsWith(".")) continue
                        val name = file.name
                        val ext = name.substringAfterLast('.', "").lowercase()
                        val detectedCat = when (ext) {
                            "mp4", "mkv", "avi", "mov", "webm" -> MediaCategory.MOVIES
                            "mp3", "flac", "wav", "aac", "ogg", "m4a" -> MediaCategory.MUSIC
                            "jpg", "jpeg", "png", "webp", "gif" -> MediaCategory.PHOTOS
                            else -> null
                        } ?: continue

                        if (category != MediaCategory.ALL && detectedCat != mediaCat && category != detectedCat) {
                            continue
                        }

                        val fileUri = Uri.fromFile(file).toString()
                        list.add(
                            MediaItem(
                                id = file.absolutePath,
                                title = name.substringBeforeLast('.').replace('_', ' ').replace('-', ' '),
                                category = detectedCat,
                                year = 2026,
                                duration = if (detectedCat == MediaCategory.MOVIES) "Video" else if (detectedCat == MediaCategory.MUSIC) "Audio" else "Photo",
                                genre = "Local Storage",
                                rating = 0.0,
                                description = "${file.parentFile?.name ?: "Storage"} • ${formatBytes(file.length())}",
                                posterGradientColor = when (detectedCat) {
                                    MediaCategory.MOVIES -> 0xFF1E3A8A
                                    MediaCategory.MUSIC -> 0xFF6B21A8
                                    MediaCategory.PHOTOS -> 0xFF065F46
                                    else -> 0xFF1E293B
                                },
                                backdropGradientColor = 0xFF0F172A,
                                fileSizeBytes = formatBytes(file.length()),
                                streamUrl = fileUri,
                                filePath = file.absolutePath,
                                isRecentlyAdded = true
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return list
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val totalSec = durationMs / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val hrs = min / 60L
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, min % 60, sec)
        } else {
            String.format(Locale.US, "%d:%02d", min, sec)
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
}
