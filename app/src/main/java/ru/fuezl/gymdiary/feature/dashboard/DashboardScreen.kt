package ru.fuezl.gymdiary.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val lastWeights: List<Pair<String, Double>> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    progressRepository: ProgressRepository,
) : ViewModel() {
    val uiState = combine(
        workoutRepository.observeWorkoutHistory(),
        progressRepository.observeWeeklyStats(),
        progressRepository.observeLastWeights(),
    ) { history, weekly, weights ->
        val monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        DashboardUiState(
            lastWorkout = history.firstOrNull(),
            weeklyStats = weekly,
            monthlyWorkoutCount = history.count { it.startedAt >= monthStart },
            lastWeights = weights,
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
    viewModel: DashboardViewModel = hiltViewModel(),
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
    onWorkoutDetails: (Long) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("Дневник тренировок") }
        item {
            Button(onClick = onStartWorkout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Начать тренировку", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            Text("Последняя тренировка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Последний рабочий вес", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (state.lastWeights.isEmpty()) {
                        Text("Данных пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.lastWeights.forEach { Text("${it.first}: ${it.second.formatKg()}") }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onExercises, modifier = Modifier.weight(1f)) { Text("Упражнения") }
                Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("История") }
            }
        }
        item { Button(onClick = onProgress, modifier = Modifier.fillMaxWidth()) { Text("Прогресс") } }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    DashboardScreen(DashboardUiState(), PaddingValues(), {}, {}, {}, {}, {})
}
