package ru.fuezl.gymdiary.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.fuezl.gymdiary.core.common.formatDate
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.model.WeeklyStats
import ru.fuezl.gymdiary.core.model.WorkoutSummary
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.core.ui.MetricChip
import ru.fuezl.gymdiary.core.ui.SectionTitle
import ru.fuezl.gymdiary.core.ui.StatCard
import ru.fuezl.gymdiary.core.ui.WorkoutCard
import ru.fuezl.gymdiary.data.repository.ProgressRepository
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DashboardUiState(
    val lastWorkout: WorkoutSummary? = null,
    val weeklyStats: WeeklyStats = WeeklyStats(),
    val monthlyWorkoutCount: Int = 0,
    val lastWeights: List<Pair<String, Double>> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(workoutRepository: WorkoutRepository, progressRepository: ProgressRepository) : ViewModel() {
    val uiState = combine(
        workoutRepository.observeWorkoutHistory(),
        progressRepository.observeWeeklyStats(),
        progressRepository.observeLastWeights()
    ) { history, weekly, weights ->
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DashboardUiState(
            lastWorkout = history.firstOrNull(),
            weeklyStats = weekly,
            monthlyWorkoutCount = history.count { it.startedAt >= monthStart },
            lastWeights = weights
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())
}

@Composable
fun DashboardRoute(
    contentPadding: PaddingValues,
    onStartWorkout: () -> Unit,
    onExercises: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onWorkoutDetails: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(state, contentPadding, onStartWorkout, onExercises, onHistory, onProgress, onWorkoutDetails)
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    contentPadding: PaddingValues,
    onStartWorkout: () -> Unit,
    onExercises: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onWorkoutDetails: (Long) -> Unit
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { GymDiaryTopBar("Дневник тренировок") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Текущая цель", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Начать тренировку", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Начать тренировку", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        item {
            SectionTitle("Последняя тренировка")
            if (state.lastWorkout == null) {
                EmptyState("Тренировок пока нет")
            } else {
                WorkoutCard(state.lastWorkout, formatDate(state.lastWorkout.startedAt), { onWorkoutDetails(state.lastWorkout.id) })
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("За неделю", "${state.weeklyStats.workouts} трен.", Modifier.weight(1f))
                StatCard("За месяц", "${state.monthlyWorkoutCount} трен.", Modifier.weight(1f))
            }
        }
        item { StatCard("Недельный объём", state.weeklyStats.volume.formatKg(), Modifier.fillMaxWidth()) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionTitle("Последний рабочий вес")
                    if (state.lastWeights.isEmpty()) {
                        Text("Данных пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.lastWeights.take(6).forEach { MetricChip(it.first, it.second.formatKg()) }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExercises, modifier = Modifier.weight(1f)) { Text("Упражнения") }
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("История") }
            }
        }
        item {
            OutlinedButton(onClick = onProgress, modifier = Modifier.fillMaxWidth()) {
                Text("Прогресс")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    DashboardScreen(DashboardUiState(), PaddingValues(), {}, {}, {}, {}, {})
}
