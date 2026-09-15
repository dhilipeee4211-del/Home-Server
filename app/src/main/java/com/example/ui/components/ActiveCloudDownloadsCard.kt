package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CloudDownloadStatus
import com.example.data.model.CloudDownloadTask
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStopped
import com.example.ui.theme.StatusWarning

@Composable
fun ActiveCloudDownloadsCard(
    tasks: List<CloudDownloadTask>,
    onCancelTask: (String) -> Unit,
    onPauseTask: ((String) -> Unit)? = null,
    onResumeTask: ((String) -> Unit)? = null,
    onRetryTask: ((String) -> Unit)? = null,
    onClearCompleted: () -> Unit,
    onPauseAll: (() -> Unit)? = null,
    onResumeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) return

    val downloads = tasks.filter { !it.isUpload }
    val uploads = tasks.filter { it.isUpload }

    var isExpanded by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(if (downloads.isEmpty() && uploads.isNotEmpty()) 1 else 0) }

    val currentList = if (selectedTab == 0) downloads else uploads
    val activeCount = tasks.count {
        it.status == CloudDownloadStatus.DOWNLOADING || it.status == CloudDownloadStatus.STORING_TO_SERVER
    }
    val pausedCount = tasks.count { it.status == CloudDownloadStatus.PAUSED }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("active_cloud_downloads_card")
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.CloudDownload else Icons.Default.CloudUpload,
                            contentDescription = "Transfers",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "File Transfers",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (activeCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$activeCount active",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (pausedCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(StatusWarning.copy(alpha = 0.2f))
                                .border(1.dp, StatusWarning.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$pausedCount paused",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = StatusWarning,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tasks.any { it.status == CloudDownloadStatus.COMPLETED || it.status == CloudDownloadStatus.FAILED || it.status == CloudDownloadStatus.CANCELLED }) {
                        TextButton(
                            onClick = onClearCompleted,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp)
                        }
                    }

                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // Downloads & Uploads Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Downloads Tab
                        val isDlSelected = selectedTab == 0
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isDlSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Downloads",
                                    tint = if (isDlSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Downloads (${downloads.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isDlSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isDlSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Uploads Tab
                        val isUpSelected = selectedTab == 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isUpSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Uploads",
                                    tint = if (isUpSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Uploads (${uploads.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isUpSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isUpSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Global pause/resume buttons if multiple tasks exist
                    if (currentList.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (currentList.any { it.status == CloudDownloadStatus.DOWNLOADING }) {
                                TextButton(
                                    onClick = { onPauseAll?.invoke() },
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Pause All", fontSize = 10.sp)
                                }
                            }
                            if (currentList.any { it.status == CloudDownloadStatus.PAUSED }) {
                                TextButton(
                                    onClick = { onResumeAll?.invoke() },
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Resume All", fontSize = 10.sp)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (currentList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedTab == 0) "No active or saved downloads" else "No active or saved uploads",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            currentList.forEach { task ->
                                TransferItemRow(
                                    task = task,
                                    onPause = { onPauseTask?.invoke(task.id) },
                                    onResume = { onResumeTask?.invoke(task.id) },
                                    onRetry = { onRetryTask?.invoke(task.id) },
                                    onCancel = { onCancelTask(task.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferItemRow(
    task: CloudDownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    val isTransferring = task.status == CloudDownloadStatus.DOWNLOADING || task.status == CloudDownloadStatus.STORING_TO_SERVER
    val isPaused = task.status == CloudDownloadStatus.PAUSED
    val isCompleted = task.status == CloudDownloadStatus.COMPLETED
    val isFailed = task.status == CloudDownloadStatus.FAILED || task.status == CloudDownloadStatus.CANCELLED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                when {
                    isPaused -> StatusWarning.copy(alpha = 0.35f)
                    isFailed -> StatusStopped.copy(alpha = 0.35f)
                    isCompleted -> StatusRunning.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                },
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.filename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            task.isUpload -> "To: ${task.destinationFolder} • ${task.transferredFormatted} / ${task.totalFormatted}"
                            else -> "Folder: ${task.destinationFolder} • ${task.transferredFormatted} / ${task.totalFormatted}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.speedText.isNotBlank()) {
                        Text(
                            text = task.speedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = when {
                                isPaused -> StatusWarning
                                isFailed -> StatusStopped
                                isCompleted -> StatusRunning
                                else -> MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (!task.errorMessage.isNullOrBlank()) {
                        Text(
                            text = task.errorMessage,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = StatusStopped,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when {
                        isTransferring -> {
                            Text(
                                text = "${task.progressPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        isPaused -> {
                            FilledTonalButton(
                                onClick = onResume,
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        isFailed -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        isCompleted -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = StatusRunning,
                                modifier = Modifier.size(18.dp)
                            )
                            IconButton(
                                onClick = onCancel,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isTransferring || isPaused) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isPaused) StatusWarning else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
