package com.example.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DashboardMetric
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.SectionHeader
import com.example.ui.components.ServiceStatusCard
import com.example.ui.components.StorageCard
import com.example.ui.theme.StatusDemo
import com.example.ui.theme.StatusRunning
import com.example.ui.theme.StatusStopped
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    viewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val services by viewModel.services.collectAsStateWithLifecycle()
    val storageInfo by viewModel.storageInfo.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedActionMessage by remember { mutableStateOf<String?>(null) }

    if (selectedActionMessage != null) {
        AlertDialog(
            onDismissRequest = { selectedActionMessage = null },
            title = { Text(text = "Server Action") },
            text = {
                Text(
                    text = "$selectedActionMessage\n\nAvailable after server API integration (v0.2/v0.6). Direct shell execution from mobile client is prohibited for security.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedActionMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Header: Server Admin
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Server Admin",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "DhilipHome Console (${serverStatus.serverHostname.ifBlank { "Configure in Settings" }})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DemoModeBadge(text = if (serverStatus.isOnline) "ONLINE" else "LOCAL LAN")
                    }
                }
            }

            // Quick Power & Maintenance Action Buttons (Section 7)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { selectedActionMessage = "Restart Service" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_restart_service"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Restart Svc",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    OutlinedButton(
                        onClick = { selectedActionMessage = "Restart Server" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_restart_server"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusWarning
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reboot",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusWarning
                        )
                    }

                    OutlinedButton(
                        onClick = { selectedActionMessage = "Shutdown Server" },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("action_shutdown_server"),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = StatusStopped
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shutdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusStopped
                        )
                    }
                }
            }

            // Live Telemetry Grid: CPU, RAM, Storage, Temperature, Uptime, Network
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "SERVER STATUS")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetric(
                            label = "Service Port",
                            value = "8080",
                            subValue = "HTTP Fileserver",
                            icon = Icons.Default.Dns,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetric(
                            label = "HTTP Status",
                            value = if (serverStatus.isOnline) "200 OK" else "Offline",
                            subValue = serverStatus.statusText,
                            icon = Icons.Default.Memory,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = if (serverStatus.isOnline) StatusRunning else StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DashboardMetric(
                            label = "Host Target",
                            value = serverStatus.serverHostname.ifBlank { "Unset" },
                            subValue = "DhilipHome LAN",
                            icon = Icons.Default.Storage,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.0f,
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        DashboardMetric(
                            label = "Network",
                            value = if (serverStatus.isOnline) "Connected" else "Unreachable",
                            subValue = serverStatus.networkStatus,
                            icon = Icons.Default.Thermostat,
                            progressFraction = if (serverStatus.isOnline) 1.0f else 0.1f,
                            accentColor = if (serverStatus.isOnline) StatusRunning else StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Services Status Cards
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Services",
                    actionLabel = "${services.count { it.state.name == "RUNNING" }}/${services.size} running"
                )
            }

            items(services, key = { it.name }) { service ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ServiceStatusCard(
                        service = service,
                        onServiceClick = {
                            selectedActionMessage = "Manage service: ${service.name}"
                        }
                    )
                }
            }

            // Administration Sections (Section 7)
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(title = "Administration Modules")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminModuleCard(
                        title = "SERVER",
                        subtitle = "Hardware: Intel/AMD x86_64 • Kernel: 6.1.0-21 • Debian 12",
                        icon = Icons.Default.Dns,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Server hardware: Debian 12 Bookworm on dhilip-server")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "SERVICES",
                        subtitle = "Nginx, File Browser, Database, Media Server, API, Tailscale",
                        icon = Icons.Default.Widgets,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Managed via systemd in v0.2 / v0.6")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "STORAGE",
                        subtitle = "Pool: 4.0 TB • /home/dhileepan/Media • ZFS/ext4 partitions",
                        icon = Icons.Default.Storage,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Storage pool: 1.2 TB used of 4.0 TB")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "USERS",
                        subtitle = "${users.size} Accounts • Admin: dhileepan • RBAC enforcement",
                        icon = Icons.Default.Group,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("User management will be unlocked with Admin auth in v0.3")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "BACKUPS",
                        subtitle = "Target: /var/backups • Last: Yesterday 03:00 AM • Automated nightly",
                        icon = Icons.Default.Backup,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Backup architecture: Cron automated in v0.7")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "LOGS",
                        subtitle = "Nginx access.log, systemd journal, dhilip-home audit logs",
                        icon = Icons.Default.Description,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Safe audit logs streaming in v0.6")
                            }
                        }
                    )

                    AdminModuleCard(
                        title = "SECURITY",
                        subtitle = "UFW Active • Tailscale WireGuard Mesh • No Public SSH root",
                        icon = Icons.Default.Security,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Tailscale zero-trust private network in v0.7")
                            }
                        }
                    )
                }
            }

            // Storage Details
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(title = "Storage Details")
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    StorageCard(storageInfo = storageInfo)
                }
            }
        }
    }
}

@Composable
private fun AdminModuleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("admin_module_${title.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
