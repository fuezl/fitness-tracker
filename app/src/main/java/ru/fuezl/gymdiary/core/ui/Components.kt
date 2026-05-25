package ru.fuezl.gymdiary.core.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.model.Exercise
import ru.fuezl.gymdiary.core.model.WorkoutSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymDiaryTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
        },
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(text: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ExerciseCard(exercise: Exercise, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${exercise.muscleGroup.title} • ${exercise.equipment.title}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (exercise.isCustom) Text("Пользовательское", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun WorkoutCard(summary: WorkoutSummary, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(summary.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Упр.: ${summary.exerciseCount}")
                Text("Подх.: ${summary.setCount}")
                Text("Объём: ${summary.totalVolume.formatKg()}")
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun SimpleLineChart(values: List<Double>, modifier: Modifier = Modifier, lineColor: Color = MaterialTheme.colorScheme.primary) {
    if (values.size < 2) {
        EmptyState("Нет данных для графика", modifier)
        return
    }
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp).padding(12.dp)) {
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val stepX = size.width / (values.lastIndex)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (size.height * (value / max).toFloat())
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}
