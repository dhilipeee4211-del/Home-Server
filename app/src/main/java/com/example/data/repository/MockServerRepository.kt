package com.example.data.repository

import com.example.data.model.FileItem
import com.example.data.model.FileType
import com.example.data.model.MediaCategory
import com.example.data.model.MediaItem
import com.example.data.model.RecentActivity
import com.example.data.model.ServerStatus
import com.example.data.model.ServiceState
import com.example.data.model.ServiceStatus
import com.example.data.model.StorageInfo
import com.example.data.model.StoragePartition
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

object MockServerRepository : ServerRepository {

    private val _serverStatus = MutableStateFlow(
        ServerStatus(
            isOnline = false,
            statusText = "Offline / Demo",
            isDemoMode = true,
            cpuPercent = 18,
            ramUsedGb = 3.2,
            ramTotalGb = 8.0,
            storageUsedTb = 1.2,
            storageTotalTb = 4.0,
            uptimeDays = 14,
            networkStatus = "Not connected",
            temperatureCelsius = 42,
            loadAverage = "0.42, 0.38, 0.31",
            serverHostname = "dhilip-server",
            osVersion = "Debian GNU/Linux 12 (bookworm)",
            kernelVersion = "Linux 6.1.0-21-amd64"
        )
    )

    private val _mediaList = MutableStateFlow(
        listOf(
            MediaItem(
                id = "m1",
                title = "Interstellar",
                category = MediaCategory.MOVIES,
                year = 2014,
                duration = "2h 49m",
                genre = "Sci-Fi / Adventure",
                rating = 8.7,
                description = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans.",
                posterGradientColor = 0xFF0284C7,
                backdropGradientColor = 0xFF0B132B,
                progress = 0.65f,
                isFavorite = true,
                isContinueWatching = true,
                isRecentlyAdded = false,
                isRecommended = true,
                resolution = "4K UHD HDR",
                audioFormat = "Dolby Atmos 7.1",
                fileSizeBytes = "16.8 GB"
            ),
            MediaItem(
                id = "m2",
                title = "Avatar: The Way of Water",
                category = MediaCategory.MOVIES,
                year = 2022,
                duration = "3h 12m",
                genre = "Sci-Fi / Action",
                rating = 7.6,
                description = "Jake Sully lives with his newfound family formed on the extrasolar moon Pandora. Once a familiar threat returns to finish what was previously started, Jake must work with Neytiri and the army of the Na'vi race to protect their home.",
                posterGradientColor = 0xFF0EA5E9,
                backdropGradientColor = 0xFF082F49,
                progress = 0.30f,
                isFavorite = false,
                isContinueWatching = true,
                isRecentlyAdded = false,
                isRecommended = true,
                resolution = "4K HDR10+",
                audioFormat = "DTS-HD MA 7.1",
                fileSizeBytes = "22.4 GB"
            ),
            MediaItem(
                id = "m3",
                title = "Severance",
                category = MediaCategory.TV_SHOWS,
                year = 2022,
                duration = "Season 1 • 9 Ep",
                genre = "Mystery / Thriller",
                rating = 8.7,
                description = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives. When a mysterious colleague appears outside of work, it begins a journey to discover the truth about their jobs.",
                posterGradientColor = 0xFF38BDF8,
                backdropGradientColor = 0xFF1E293B,
                progress = 0.85f,
                isFavorite = true,
                isContinueWatching = true,
                isRecentlyAdded = false,
                isRecommended = true,
                resolution = "1080p Web-DL",
                audioFormat = "E-AC3 5.1",
                fileSizeBytes = "8.9 GB"
            ),
            MediaItem(
                id = "m4",
                title = "Dune: Part Two",
                category = MediaCategory.MOVIES,
                year = 2024,
                duration = "2h 46m",
                genre = "Sci-Fi / Epic",
                rating = 8.6,
                description = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family. Facing a choice between the love of his life and the fate of the universe.",
                posterGradientColor = 0xFFD97706,
                backdropGradientColor = 0xFF451A03,
                progress = 0.0f,
                isFavorite = true,
                isContinueWatching = false,
                isRecentlyAdded = true,
                isRecommended = true,
                resolution = "4K Dolby Vision",
                audioFormat = "Dolby TrueHD Atmos",
                fileSizeBytes = "19.5 GB"
            ),
            MediaItem(
                id = "m5",
                title = "Blade Runner 2049",
                category = MediaCategory.MOVIES,
                year = 2017,
                duration = "2h 44m",
                genre = "Sci-Fi / Noir",
                rating = 8.0,
                description = "Young Blade Runner K's discovery of a long-buried secret leads him to track down former Blade Runner Rick Deckard, who's been missing for thirty years.",
                posterGradientColor = 0xFFF59E0B,
                backdropGradientColor = 0xFF18181B,
                progress = 0.0f,
                isFavorite = true,
                isContinueWatching = false,
                isRecentlyAdded = true,
                isRecommended = false,
                resolution = "4K HDR",
                audioFormat = "Dolby Atmos",
                fileSizeBytes = "14.8 GB"
            ),
            MediaItem(
                id = "m6",
                title = "Dark",
                category = MediaCategory.TV_SHOWS,
                year = 2017,
                duration = "Season 1-3 • 26 Ep",
                genre = "Sci-Fi / Mind-Bending",
                rating = 8.7,
                description = "A family saga with a supernatural twist, set in a German town where the disappearance of two young children exposes the relationships among four families across different timelines.",
                posterGradientColor = 0xFF475569,
                backdropGradientColor = 0xFF020617,
                progress = 0.0f,
                isFavorite = true,
                isContinueWatching = false,
                isRecentlyAdded = false,
                isRecommended = true,
                resolution = "1080p HEVC",
                audioFormat = "FLAC 5.1",
                fileSizeBytes = "32.0 GB"
            ),
            MediaItem(
                id = "m7",
                title = "Random Access Memories",
                category = MediaCategory.MUSIC,
                year = 2013,
                duration = "13 Tracks • 1h 14m",
                genre = "Electronic / Funk",
                rating = 9.1,
                description = "Daft Punk's fourth and final studio album paying tribute to late 1970s and early 1980s American music with live instrumentation.",
                posterGradientColor = 0xFF64748B,
                backdropGradientColor = 0xFF0F172A,
                progress = 0.40f,
                isFavorite = true,
                isContinueWatching = false,
                isRecentlyAdded = true,
                isRecommended = true,
                resolution = "Hi-Res FLAC 24-bit/96kHz",
                audioFormat = "Stereo Lossless",
                fileSizeBytes = "1.4 GB"
            ),
            MediaItem(
                id = "m8",
                title = "Family Vacation 2025",
                category = MediaCategory.PHOTOS,
                year = 2025,
                duration = "142 Photos • 12 Videos",
                genre = "Family Album",
                rating = 9.9,
                description = "High resolution memories from the summer trip with the family, captured in raw and 4K ProRes.",
                posterGradientColor = 0xFF10B981,
                backdropGradientColor = 0xFF064E3B,
                progress = 0.0f,
                isFavorite = false,
                isContinueWatching = false,
                isRecentlyAdded = true,
                isRecommended = false,
                resolution = "Original RAW / DNG",
                audioFormat = "N/A",
                fileSizeBytes = "8.2 GB"
            )
        )
    )

    private val allFiles = mapOf(
        "/" to listOf(
            FileItem("f1", "Movies", "/Movies", isFolder = true, type = FileType.FOLDER, modifiedDate = "2 days ago", itemCount = 8),
            FileItem("f2", "TV Shows", "/TV Shows", isFolder = true, type = FileType.FOLDER, modifiedDate = "3 days ago", itemCount = 12),
            FileItem("f3", "Music", "/Music", isFolder = true, type = FileType.FOLDER, modifiedDate = "1 week ago", itemCount = 45),
            FileItem("f4", "Photos", "/Photos", isFolder = true, type = FileType.FOLDER, modifiedDate = "Yesterday", itemCount = 180),
            FileItem("f5", "Documents", "/Documents", isFolder = true, type = FileType.FOLDER, modifiedDate = "Just now", itemCount = 16),
            FileItem("f6", "Downloads", "/Downloads", isFolder = true, type = FileType.FOLDER, modifiedDate = "4 hours ago", itemCount = 5),
            FileItem("f7", "server_config_backup.tar.gz", "/server_config_backup.tar.gz", isFolder = false, type = FileType.ARCHIVE, sizeBytes = 48234496, formattedSize = "46.0 MB", modifiedDate = "Yesterday"),
            FileItem("f8", "DHILIP_HOME_Architecture.pdf", "/DHILIP_HOME_Architecture.pdf", isFolder = false, type = FileType.PDF, sizeBytes = 2831155, formattedSize = "2.7 MB", modifiedDate = "Today 09:15 AM")
        ),
        "/Movies" to listOf(
            FileItem("m_f1", "Interstellar.mp4", "/Movies/Interstellar.mp4", isFolder = false, type = FileType.VIDEO, sizeBytes = 18038862643, formattedSize = "16.8 GB", modifiedDate = "Aug 14, 2026"),
            FileItem("m_f2", "Avatar.mp4", "/Movies/Avatar.mp4", isFolder = false, type = FileType.VIDEO, sizeBytes = 24051816857, formattedSize = "22.4 GB", modifiedDate = "Aug 20, 2026"),
            FileItem("m_f3", "Dune_Part_Two.mkv", "/Movies/Dune_Part_Two.mkv", isFolder = false, type = FileType.VIDEO, sizeBytes = 20937965568, formattedSize = "19.5 GB", modifiedDate = "Sep 02, 2026"),
            FileItem("m_f4", "Blade_Runner_2049.mp4", "/Movies/Blade_Runner_2049.mp4", isFolder = false, type = FileType.VIDEO, sizeBytes = 15891338035, formattedSize = "14.8 GB", modifiedDate = "Jul 28, 2026")
        ),
        "/TV Shows" to listOf(
            FileItem("tv_f1", "Example Series", "/TV Shows/Example Series", isFolder = true, type = FileType.FOLDER, modifiedDate = "3 days ago", itemCount = 9),
            FileItem("tv_f2", "Dark_Complete", "/TV Shows/Dark_Complete", isFolder = true, type = FileType.FOLDER, modifiedDate = "2 weeks ago", itemCount = 26)
        ),
        "/TV Shows/Example Series" to listOf(
            FileItem("tve_f1", "Severance_S01E01.mkv", "/TV Shows/Example Series/Severance_S01E01.mkv", isFolder = false, type = FileType.VIDEO, sizeBytes = 1073741824, formattedSize = "1.0 GB", modifiedDate = "3 days ago"),
            FileItem("tve_f2", "Severance_S01E02.mkv", "/TV Shows/Example Series/Severance_S01E02.mkv", isFolder = false, type = FileType.VIDEO, sizeBytes = 1048576000, formattedSize = "998 MB", modifiedDate = "3 days ago")
        ),
        "/Music" to listOf(
            FileItem("mu_f1", "Example Song.mp3", "/Music/Example Song.mp3", isFolder = false, type = FileType.AUDIO, sizeBytes = 9437184, formattedSize = "9.0 MB", modifiedDate = "1 week ago"),
            FileItem("mu_f2", "Daft_Punk_Get_Lucky.flac", "/Music/Daft_Punk_Get_Lucky.flac", isFolder = false, type = FileType.AUDIO, sizeBytes = 44040192, formattedSize = "42.0 MB", modifiedDate = "1 week ago"),
            FileItem("mu_f3", "Synthwave_Night_Drive.flac", "/Music/Synthwave_Night_Drive.flac", isFolder = false, type = FileType.AUDIO, sizeBytes = 38797312, formattedSize = "37.0 MB", modifiedDate = "Aug 12, 2026")
        ),
        "/Photos" to listOf(
            FileItem("ph_f1", "Family.jpg", "/Photos/Family.jpg", isFolder = false, type = FileType.IMAGE, sizeBytes = 6291456, formattedSize = "6.0 MB", modifiedDate = "Yesterday"),
            FileItem("ph_f2", "Travel.jpg", "/Photos/Travel.jpg", isFolder = false, type = FileType.IMAGE, sizeBytes = 7864320, formattedSize = "7.5 MB", modifiedDate = "Yesterday"),
            FileItem("ph_f3", "Sunset_Beach.jpg", "/Photos/Sunset_Beach.jpg", isFolder = false, type = FileType.IMAGE, sizeBytes = 5242880, formattedSize = "5.0 MB", modifiedDate = "Aug 05, 2026")
        ),
        "/Documents" to listOf(
            FileItem("doc_f1", "Document.pdf", "/Documents/Document.pdf", isFolder = false, type = FileType.PDF, sizeBytes = 1887436, formattedSize = "1.8 MB", modifiedDate = "Sep 10, 2026"),
            FileItem("doc_f2", "Debian_Home_Server_Notes.txt", "/Documents/Debian_Home_Server_Notes.txt", isFolder = false, type = FileType.DOCUMENT, sizeBytes = 24576, formattedSize = "24 KB", modifiedDate = "Sep 11, 2026"),
            FileItem("doc_f3", "JioFiber_Static_Route_Config.md", "/Documents/JioFiber_Static_Route_Config.md", isFolder = false, type = FileType.DOCUMENT, sizeBytes = 8192, formattedSize = "8 KB", modifiedDate = "Sep 08, 2026")
        ),
        "/Downloads" to listOf(
            FileItem("dl_f1", "debian-12.8.0-amd64-netinst.iso", "/Downloads/debian-12.8.0-amd64-netinst.iso", isFolder = false, type = FileType.ARCHIVE, sizeBytes = 650117120, formattedSize = "620 MB", modifiedDate = "4 hours ago"),
            FileItem("dl_f2", "filebrowser-v2.30.0-linux-amd64.tar.gz", "/Downloads/filebrowser-v2.30.0-linux-amd64.tar.gz", isFolder = false, type = FileType.ARCHIVE, sizeBytes = 14680064, formattedSize = "14.0 MB", modifiedDate = "Sep 01, 2026")
        )
    )

    private val services = listOf(
        ServiceStatus("Nginx", "nginx.service", ServiceState.RUNNING, 80, "Reverse Proxy & Web Server", "14 days", 64),
        ServiceStatus("File Browser", "filebrowser.service", ServiceState.RUNNING, 8080, "Personal Cloud Web File Manager", "14 days", 88),
        ServiceStatus("Database", "postgresql.service", ServiceState.RUNNING, 5432, "PostgreSQL Relational Storage", "14 days", 256),
        ServiceStatus("Media Server", "mediaserver.service", ServiceState.RUNNING, 8096, "DLNA / Local Media Catalog", "14 days", 340),
        ServiceStatus("API Server", "dhilip-home-api.service", ServiceState.STOPPED, 8000, "DHILIP HOME FastAPI Backend (v0.2)", "0 days", 0),
        ServiceStatus("Tailscale", "tailscaled.service", ServiceState.RUNNING, 41641, "Secure Zero-Trust Mesh VPN", "14 days", 48)
    )

    private val users = listOf(
        User("u1", "dhileepan", "Dhileepan (Admin)", UserRole.ADMIN, "Active now", "Active"),
        User("u2", "family", "Home Family", UserRole.USER, "2 hours ago", "Active"),
        User("u3", "guest", "Guest Viewer", UserRole.USER, "5 days ago", "Restricted")
    )

    private val storageInfo = StorageInfo(
        totalTb = 4.0,
        usedTb = 1.2,
        freeTb = 2.8,
        usagePercent = 30,
        partitions = listOf(
            StoragePartition("/", "Debian System (NVMe)", "ext4", 250.0, 42.5, 17),
            StoragePartition("/home/dhileepan/Media", "Media Storage Pool", "ext4", 2800.0, 980.0, 35),
            StoragePartition("/var/backups", "Automated Backups", "ext4", 950.0, 177.5, 19)
        )
    )

    private val recentActivities = listOf(
        RecentActivity("ra1", "Movie.mp4", "Video Stream", "2 hours ago", FileType.VIDEO, "16.8 GB"),
        RecentActivity("ra2", "Documents", "Folder Opened", "5 hours ago", FileType.FOLDER, "16 items"),
        RecentActivity("ra3", "Music", "Audio Playback", "Yesterday", FileType.AUDIO, "3 tracks"),
        RecentActivity("ra4", "Photos", "Media Sync", "2 days ago", FileType.IMAGE, "142 photos")
    )

    override fun getServerStatus(): Flow<ServerStatus> = _serverStatus.asStateFlow()

    override fun getFiles(directoryPath: String): Flow<List<FileItem>> =
        MutableStateFlow(allFiles[directoryPath] ?: emptyList()).asStateFlow()

    override fun getMedia(category: MediaCategory): Flow<List<MediaItem>> {
        return _mediaList.map { list ->
            if (category == MediaCategory.ALL) list
            else list.filter { it.category == category }
        }
    }

    override fun getMediaById(id: String): Flow<MediaItem?> {
        return _mediaList.map { list -> list.find { it.id == id } }
    }

    override fun getContinueWatching(): Flow<List<MediaItem>> {
        return _mediaList.map { list -> list.filter { it.isContinueWatching } }
    }

    override fun getRecentlyAddedMedia(): Flow<List<MediaItem>> {
        return _mediaList.map { list -> list.filter { it.isRecentlyAdded } }
    }

    override fun getFavoriteMedia(): Flow<List<MediaItem>> {
        return _mediaList.map { list -> list.filter { it.isFavorite } }
    }

    override fun getServices(): Flow<List<ServiceStatus>> = MutableStateFlow(services).asStateFlow()

    override fun getUsers(): Flow<List<User>> = MutableStateFlow(users).asStateFlow()

    override fun getStorageInfo(): Flow<StorageInfo> = MutableStateFlow(storageInfo).asStateFlow()

    override fun getRecentActivities(): Flow<List<RecentActivity>> = MutableStateFlow(recentActivities).asStateFlow()

    override suspend fun toggleMediaFavorite(id: String): Boolean {
        var newFav = false
        _mediaList.value = _mediaList.value.map { item ->
            if (item.id == id) {
                newFav = !item.isFavorite
                item.copy(isFavorite = newFav)
            } else {
                item
            }
        }
        return newFav
    }
}
