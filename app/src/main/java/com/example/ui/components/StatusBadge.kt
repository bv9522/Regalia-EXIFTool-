package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.CandidateStatus
import com.example.data.model.OperationStatus
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusErrorDark
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun OperationStatusBadge(status: OperationStatus, modifier: Modifier = Modifier) {
    val (bgColor, contentColor, icon) = when (status) {
        OperationStatus.SUCCESS -> Triple(
            Color(0xFF1B3B22),
            Color(0xFF81C784),
            Icons.Default.CheckCircle
        )
        OperationStatus.PREVIEW_READY -> Triple(
            Color(0xFF332A10),
            Color(0xFFFFDF95),
            Icons.Default.Schedule
        )
        OperationStatus.UP_TO_DATE -> Triple(
            Color(0xFF1C2C3C),
            Color(0xFF90CAF9),
            Icons.Default.CheckCircle
        )
        OperationStatus.UNSUPPORTED_PROVIDER -> Triple(
            Color(0xFF3D2612),
            Color(0xFFFFB74D),
            Icons.Default.Warning
        )
        OperationStatus.NO_USABLE_DATE -> Triple(
            Color(0xFF2B222A),
            Color(0xFFCE93D8),
            Icons.Default.HelpOutline
        )
        OperationStatus.FAILED -> Triple(
            Color(0xFF3B1B1B),
            Color(0xFFE57373),
            Icons.Default.Error
        )
        OperationStatus.ROLLED_BACK -> Triple(
            Color(0xFF262626),
            Color(0xFFE0E0E0),
            Icons.Default.Schedule
        )
        OperationStatus.PROCESSING -> Triple(
            Color(0xFF1A3338),
            Color(0xFF80DEEA),
            Icons.Default.Schedule
        )
        OperationStatus.IDLE, OperationStatus.SCANNED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.Schedule
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, contentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = status.label,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun CandidateStatusBadge(status: CandidateStatus, modifier: Modifier = Modifier) {
    val (label, bgColor, textColor) = when (status) {
        CandidateStatus.RECOMMENDED -> Triple("Recommended", Color(0xFF1B3B22), Color(0xFF81C784))
        CandidateStatus.ALTERNATIVE -> Triple("Alternative", Color(0xFF1C2C3C), Color(0xFF90CAF9))
        CandidateStatus.REJECTED -> Triple("Rejected", Color(0xFF332020), Color(0xFFE57373))
        CandidateStatus.PARSE_ERROR -> Triple("Parse Error", Color(0xFF3B1B1B), Color(0xFFFF8A80))
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = textColor,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(0.5.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
