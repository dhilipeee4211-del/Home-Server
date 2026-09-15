package com.example.ui.screens.player

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.repository.HttpServerRepository
import kotlinx.coroutines.delay
import java.util.Locale

// VLC Signature Color Palette
private val VlcOrange = Color(0xFFFF8800)
private val VlcOrangeLight = Color(0xFFFFAA33)
private val VlcDarkBackground = Color(0xFF0D0E11)

enum class AspectRatioMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretch", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    FIXED_16_9("16:9", AspectRatioFrameLayout.RESIZE_MODE_FIT)
}

@OptIn(UnstableApi::class)
@Composable
fun VlcMediaPlayerScreen(
    videoUrl: String,
    videoTitle: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var bufferedPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSpeedMenuOpen by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Scrubbing state for buttery smooth seeking
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }

    // Double tap feedback state
    var doubleTapRippleType by remember { mutableStateOf<String?>(null) }

    // Configure ExoPlayer with optimized buffer parameters for ultra-smooth playback
    val exoPlayer = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Hook ExoPlayer event listeners
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    }
                    Player.STATE_ENDED -> {
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Android back handler
    BackHandler {
        onNavigateBack()
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(lastInteractionTime, areControlsVisible, isPlaying, isScrubbing) {
        if (areControlsVisible && isPlaying && !isScrubbing) {
            delay(4000)
            areControlsVisible = false
        }
    }

    // Smooth position & buffer tracking loop (only when not actively scrubbing)
    LaunchedEffect(exoPlayer, isScrubbing) {
        while (true) {
            if (!isScrubbing) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    durationMs = dur
                }
            }
            delay(200)
        }
    }

    fun triggerInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        areControlsVisible = true
    }

    fun togglePlayPause() {
        triggerInteraction()
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekBy(offsetMs: Long) {
        triggerInteraction()
        val target = (exoPlayer.currentPosition + offsetMs).coerceIn(0L, durationMs.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
        currentPositionMs = target
    }

    fun saveVideoWithOriginalName() {
        triggerInteraction()
        val savedName = HttpServerRepository.downloadUrlToDeviceAsOriginalName(context, videoUrl, videoTitle)
        if (!savedName.isNullOrBlank()) {
            Toast.makeText(context, "Saving \"$savedName\" to Downloads folder", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Failed to start download", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInVlcApp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(videoUrl), "video/*")
                setPackage("org.videolan.vlc")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val chooserIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(videoUrl), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(chooserIntent, "Open with VLC or Player"))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VlcDarkBackground)
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            togglePlayPause()
                            true
                        }
                        Key.DirectionLeft -> {
                            seekBy(-10000)
                            true
                        }
                        Key.DirectionRight -> {
                            seekBy(10000)
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            triggerInteraction()
                            true
                        }
                        Key.Back, Key.Escape -> {
                            onNavigateBack()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .testTag("vlc_player_screen")
    ) {
        // High-Performance Media3 Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = aspectRatioMode.resizeMode
                    keepScreenOn = true
                    playerViewRef = this
                }
            },
            update = { view ->
                view.resizeMode = aspectRatioMode.resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture Overlay: Double Tap Left/Right to Seek, Single Tap to Toggle Controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (areControlsVisible) {
                                areControlsVisible = false
                            } else {
                                triggerInteraction()
                            }
                        },
                        onDoubleTap = { offset ->
                            triggerInteraction()
                            val isLeft = offset.x < size.width / 2
                            if (isLeft) {
                                seekBy(-10000)
                                doubleTapRippleType = "-10s"
                            } else {
                                seekBy(10000)
                                doubleTapRippleType = "+10s"
                            }
                        }
                    )
                }
        )

        // Double Tap Animated Visual Indicator
        LaunchedEffect(doubleTapRippleType) {
            if (doubleTapRippleType != null) {
                delay(600)
                doubleTapRippleType = null
            }
        }

        if (doubleTapRippleType != null) {
            val isLeft = doubleTapRippleType == "-10s"
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isLeft) Icons.Default.Replay10 else Icons.Default.Forward10,
                            contentDescription = null,
                            tint = VlcOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = doubleTapRippleType!!,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Centered Buffering Spinner
        if (isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = VlcOrange,
                        strokeWidth = 3.5.dp
                    )
                }
            }
        }

        // Master Controls Overlay (Top Bar, Middle Controls, Bottom Bar)
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Action Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = { onNavigateBack() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .testTag("vlc_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = videoTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VlcOrange)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "DIRECT 1080P",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ExoPlayer Engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }

                        // Top Right Actions: Save Original Name & External VLC
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "Save Original Name" Button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(VlcOrange.copy(alpha = 0.25f))
                                    .border(1.dp, VlcOrange.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .clickable { saveVideoWithOriginalName() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("save_original_name_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Save Original",
                                        tint = VlcOrange,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Save Original",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Open in VLC / External Player
                            IconButton(
                                onClick = { openInVlcApp() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .testTag("vlc_open_external")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "Open in VLC app",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Center Tactile Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = { seekBy(-10000) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .testTag("vlc_replay_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Replay 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Play / Pause Master Center Button
                    IconButton(
                        onClick = { togglePlayPause() },
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(VlcOrange)
                            .testTag("vlc_play_pause")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { seekBy(10000) },
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .testTag("vlc_forward_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Bottom Control Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Time, Aspect Ratio, Speed, and Volume Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Current Position & Duration
                            val effectiveDuration = durationMs.coerceAtLeast(0L)
                            val displayPos = if (isScrubbing) (scrubRatio * effectiveDuration).toLong() else currentPositionMs
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatTime(displayPos),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = " / ${formatTime(effectiveDuration)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.65f)
                                )
                            }

                            // Control Pills
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Aspect Ratio Switcher
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .clickable {
                                            aspectRatioMode = when (aspectRatioMode) {
                                                AspectRatioMode.FIT -> AspectRatioMode.FILL
                                                AspectRatioMode.FILL -> AspectRatioMode.STRETCH
                                                AspectRatioMode.STRETCH -> AspectRatioMode.FIXED_16_9
                                                AspectRatioMode.FIXED_16_9 -> AspectRatioMode.FIT
                                            }
                                            triggerInteraction()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Aspect Ratio",
                                            tint = Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = aspectRatioMode.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Playback Speed Switcher
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .clickable { isSpeedMenuOpen = true }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Speed,
                                                contentDescription = "Speed",
                                                tint = Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${playbackSpeed}x",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isSpeedMenuOpen,
                                        onDismissRequest = { isSpeedMenuOpen = false }
                                    ) {
                                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                            DropdownMenuItem(
                                                text = { Text("${speed}x") },
                                                onClick = {
                                                    playbackSpeed = speed
                                                    exoPlayer.playbackParameters = PlaybackParameters(speed)
                                                    isSpeedMenuOpen = false
                                                    triggerInteraction()
                                                }
                                            )
                                        }
                                    }
                                }

                                // Mute / Unmute
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                        triggerInteraction()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = if (isMuted) "Unmute" else "Mute",
                                        tint = if (isMuted) VlcOrange else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Buttery-Smooth Scrubber with Buffered Position Display
                        val totalDuration = if (durationMs > 0) durationMs.toFloat() else 100f
                        val currentRatio = if (isScrubbing) {
                            scrubRatio
                        } else {
                            (currentPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Secondary buffer indicator
                            val bufferRatio = (bufferedPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
                            if (bufferRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(bufferRatio)
                                        .height(4.dp)
                                        .align(Alignment.CenterStart)
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }

                            // Interactive Seek Slider
                            Slider(
                                value = currentRatio,
                                onValueChange = { ratio ->
                                    isScrubbing = true
                                    scrubRatio = ratio
                                    triggerInteraction()
                                },
                                onValueChangeFinished = {
                                    val targetMs = (scrubRatio * totalDuration).toLong()
                                    exoPlayer.seekTo(targetMs)
                                    currentPositionMs = targetMs
                                    isScrubbing = false
                                    triggerInteraction()
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = VlcOrange,
                                    activeTrackColor = VlcOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vlc_seek_slider")
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
