package com.autofarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autofarm.core.StepResult
import com.autofarm.core.StepStatus

@Composable
fun StatusBadge(status: StepStatus) {
    val (color, label) = when (status) {
        StepStatus.PASS -> Color(0xFF6EE7B7) to "PASS"
        StepStatus.FAIL -> Color(0xFFFCA5A5) to "FAIL"
        StepStatus.RUNNING -> Color(0xFFFCD34D) to "RUNNING"
        StepStatus.SKIPPED -> Color(0xFF94A3B8) to "SKIPPED"
        StepStatus.PENDING -> Color(0xFF475569) to "PENDING"
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun StepRow(index: Int, result: StepResult) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "${index + 1}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(24.dp)
        )
        StatusBadge(result.status)
        Column(Modifier.weight(1f)) {
            Text(
                result.step.description ?: result.step.type.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            if (result.message.isNotBlank()) {
                Text(
                    result.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            "${result.durationMs}ms",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        content = { Column(Modifier.padding(16.dp), content = content) }
    )
}

@Composable
fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
