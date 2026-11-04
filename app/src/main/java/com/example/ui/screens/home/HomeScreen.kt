package com.example.ui.screens.home

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DateCandidate
import com.example.data.model.FileItem
import com.example.ui.components.MetricCard
import com.example.ui.components.RegaliaDestination

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (RegaliaDestination) -> Unit,
    onLoadSamplesToTimestamp: (List<FileItem>) -> Unit,
    modifier: Modifier = Modifier
) {
    val recentRecords by viewModel.recentRecords.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val totalCount by viewModel.totalOperationsCount.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Hero Intro Banner
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column {
                            Text(
                                text = "Regalia Metadata Manager",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "High-precision metadata discovery & timestamp repair",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Correct volatile Android filesystem 'File Modified Date' timestamps using reliable EXIF DateTimeOriginal, QuickTime creation atoms, Document Open XML dates, or Samsung camera filenames.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onNavigate(RegaliaDestination.TIMESTAMP) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_launch_timestamp_btn")
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Timestamp Fixer")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.loadSampleSuite { samples ->
                                    onLoadSamplesToTimestamp(samples)
                                    onNavigate(RegaliaDestination.TIMESTAMP)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_load_samples_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Try Samples")
                        }
                    }
                }
            }
        }

        // Metrics Grid (2 columns)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Repaired Dates",
                    value = "$successCount",
                    subtitle = "Verified readbacks",
                    icon = Icons.Default.CheckCircle,
                    accentColor = Color(0xFF81C784),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Operations Run",
                    value = "$totalCount",
                    subtitle = "Recorded in History",
                    icon = Icons.Default.History,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Architecture & Android 15 Capability Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Device & Storage Access Framework (SAF)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Running on ${Build.MANUFACTURER} ${Build.MODEL} (${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}). Regalia operates strictly offline using Android SAF. No root or Shizuku required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Capability Policy: SAF DocumentProviders vary by manufacturer. Regalia verifies every timestamp by reading it back from storage immediately after modification.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Quick Workspaces
        item {
            Text(
                text = "Workspaces",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            WorkspaceCard(
                title = "Lightweight File Browser",
                subtitle = "Browse user-selected SAF folders, sort files, and queue items for batch processing.",
                icon = Icons.Default.Folder,
                accentColor = Color(0xFFE2B93B),
                onClick = { onNavigate(RegaliaDestination.BROWSE) },
                testTag = "home_workspace_browse"
            )
        }

        item {
            WorkspaceCard(
                title = "Deep Metadata Inspector",
                subtitle = "Examine EXIF tags, QuickTime video containers, Document core XML, GPS, and date candidates.",
                icon = Icons.Default.Info,
                accentColor = Color(0xFF42A5F5),
                onClick = { onNavigate(RegaliaDestination.METADATA) },
                testTag = "home_workspace_metadata"
            )
        }

        item {
            WorkspaceCard(
                title = "Operation History & Rollback",
                subtitle = "Review previous timestamp modifications, inspect error logs, or roll back to original dates.",
                icon = Icons.Default.History,
                accentColor = Color(0xFFAB47BC),
                onClick = { onNavigate(RegaliaDestination.HISTORY) },
                testTag = "home_workspace_history"
            )
        }

        // Recent Activity Preview
        if (recentRecords.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Operations",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "View All (${recentRecords.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate(RegaliaDestination.HISTORY) }
                    )
                }
            }

            items(recentRecords.take(3), key = { it.id }) { record ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (record.status == "SUCCESS") Color(0xFF1B3B22) else Color(0xFF332020)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (record.status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.History,
                                contentDescription = null,
                                tint = if (record.status == "SUCCESS") Color(0xFF81C784) else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val path = formatRecordPath(record.fileUriString, record.fileName)
                            if (path.isNotBlank()) {
                                Text(
                                    text = path,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${record.metadataSource} • ${DateCandidate.formatCompact(record.verifiedTimestamp ?: record.proposedTimestamp)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun WorkspaceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatRecordPath(uriString: String, fileName: String): String {
    return try {
        val uri = android.net.Uri.parse(uriString)
        if (uri.scheme == "file") {
            uri.path ?: fileName
        } else {
            val decoded = android.net.Uri.decode(uri.toString())
            when {
                decoded.contains("sample_media") -> "Demo Suite: /sample_media/$fileName"
                decoded.contains("primary:") -> "/Internal Storage/" + decoded.substringAfter("primary:")
                decoded.contains(":") -> "/" + decoded.substringAfterLast(":")
                else -> uri.lastPathSegment ?: fileName
            }
        }
    } catch (_: Exception) {
        fileName
    }
}
