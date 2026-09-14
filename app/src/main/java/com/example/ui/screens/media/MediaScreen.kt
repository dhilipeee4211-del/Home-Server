package com.example.ui.screens.media

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MediaCategory
import com.example.ui.components.DemoModeBadge
import com.example.ui.components.EmptyState
import com.example.ui.components.MediaCard
import com.example.ui.components.SectionHeader

@Composable
fun MediaScreen(
    viewModel: MediaViewModel,
    onMediaClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mediaItems by viewModel.mediaItems.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_screen"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Header
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
                            text = "Media",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Personal Entertainment Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.refreshMedia() },
                            modifier = Modifier.testTag("refresh_media_button")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Media from Server",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DemoModeBadge(text = "LIVE MEDIA")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Categories Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MediaCategory.entries) { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectCategory(category) },
                            label = {
                                Text(
                                    text = category.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("chip_${category.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // When "ALL" or "MOVIES"/"TV SHOWS" is selected, show curated sections
        if (selectedCategory == MediaCategory.ALL) {
            // Continue Watching section
            if (continueWatching.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionHeader(title = "Continue Watching")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.id }) { media ->
                            MediaCard(media = media, onClick = { onMediaClick(media.id) })
                        }
                    }
                }
            }

            // Recently Added section
            if (recentlyAdded.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "Recently Added")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentlyAdded, key = { it.id }) { media ->
                            MediaCard(media = media, onClick = { onMediaClick(media.id) })
                        }
                    }
                }
            }

            // Favorites section
            if (favorites.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "Favorites")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favorites, key = { it.id }) { media ->
                            MediaCard(media = media, onClick = { onMediaClick(media.id) })
                        }
                    }
                }
            }

            // Recommended section
            val recommended = mediaItems.filter { it.isRecommended }
            if (recommended.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(title = "Recommended For You")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recommended, key = { it.id }) { media ->
                            MediaCard(media = media, onClick = { onMediaClick(media.id) })
                        }
                    }
                }
            }

            if (mediaItems.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    EmptyState(
                        title = "No Media Discovered",
                        description = "Media files found on your DhilipHome server will appear here when you browse folders in the FILES tab."
                    )
                }
            }
        } else {
            // Filtered category view
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(
                    title = selectedCategory.displayName,
                    actionLabel = "${mediaItems.size} items"
                )
            }

            if (mediaItems.isEmpty()) {
                item {
                    EmptyState(
                        title = "No ${selectedCategory.displayName} Found",
                        description = "Browse your server files in the FILES tab to find and stream ${selectedCategory.displayName.lowercase()}."
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val chunked = mediaItems.chunked(2)
                        chunked.forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { media ->
                                    MediaCard(
                                        media = media,
                                        onClick = { onMediaClick(media.id) },
                                        modifier = Modifier.weight(1f),
                                        isCompact = false
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
