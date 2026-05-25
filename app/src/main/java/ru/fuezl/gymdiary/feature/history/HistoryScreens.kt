package ru.fuezl.gymdiary.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.common.formatDate
import ru.fuezl.gymdiary.core.common.formatDuration
import ru.fuezl.gymdiary.core.common.formatKg
import ru.fuezl.gymdiary.core.common.formatMonth
import ru.fuezl.gymdiary.core.common.formatTime
import ru.fuezl.gymdiary.core.model.WorkoutDetails
import ru.fuezl.gymdiary.core.model.WorkoutSummary
import ru.fuezl.gymdiary.core.ui.EmptyState
import ru.fuezl.gymdiary.core.ui.GymDiaryTopBar
import ru.fuezl.gymdiary.core.ui.WorkoutCard
import ru.fuezl.gymdiary.data.repository.WorkoutRepository
import javax.inject.Inject

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    repository: WorkoutRepository,
) : ViewModel() {
    val history = repository.observeWorkoutHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun WorkoutHistoryRoute(
    contentPadding: PaddingValues,
    onOpen: (Long) -> Unit,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    WorkoutHistoryScreen(history, contentPadding, onOpen)
}

@Composable
fun WorkoutHistoryScreen(history: List<WorkoutSummary>, contentPadding: PaddingValues, onOpen: (Long) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { GymDiaryTopBar("История") }
        if (history.isEmpty()) {
            item { EmptyState("Нет сохранённых тренировок") }
        } else {
            history.groupBy { formatMonth(it.startedAt) }.forEach { (month, workouts) ->
                item { Text(month.replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(workouts, key = { it.id }) { workout ->
                    WorkoutCard(workout, "${formatDate(workout.startedAt)} • ${formatDuration(workout.durationSeconds)}", { onOpen(workout.id) })
                }
            }
        }
    }
}

@HiltViewModel
class WorkoutDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
) : ViewModel() {
    private val workoutId: Long = savedStateHandle["workoutId"] ?: 0L
    val details = repository.observeWorkoutDetails(workoutId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val repeatChannel = Channel<Unit>(Channel.BUFFERED)
    val repeatEvents = repeatChannel.receiveAsFlow()
    private val backChannel = Channel<Unit>(Channel.BUFFERED)
    val backEvents = backChannel.receiveAsFlow()

    fun repeat() {
        viewModelScope.launch {
            repository.repeatWorkout(workoutId)
            repeatChannel.send(Unit)
        }
    }

    fun delete() {
        viewModelScope.launch {
            repository.deleteWorkout(workoutId)
            backChannel.send(Unit)
        }
    }
}

@Composable
fun WorkoutDetailsRoute(
    onBack: () -> Unit,
    onRepeat: () -> Unit,
    viewModel: WorkoutDetailsViewModel = hiltViewModel(),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.repeatEvents.collect { onRepeat() } }
    LaunchedEffect(Unit) { viewModel.backEvents.collect { onBack() } }
    WorkoutDetailsScreen(details, onBack, viewModel::repeat, viewModel::delete)
}

@Composable
fun WorkoutDetailsScreen(details: WorkoutDetails?, onBack: () -> Unit, onRepeat: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { GymDiaryTopBar("Детали тренировки", onBack) }
        if (details == null) {
            item { EmptyState("Тренировка не найдена") }
        } else {
            item {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(details.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${formatDate(details.summary.startedAt)} в ${formatTime(details.summary.startedAt)}")
                        Text("Длительность: ${formatDuration(details.summary.durationSeconds)}")
                        Text("Общий объём: ${details.summary.totalVolume.formatKg()}")
                        if (details.summary.bodyweightReps > 0) Text("Повторения с собственным весом: ${details.summary.bodyweightReps}")
                    }
                }
            }
            items(details.exercises, key = { it.workoutExerciseId }) { exercise ->
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        exercise.sets.forEach { set ->
                            Text("${set.setNumber}. ${set.weightKg.formatKg()} × ${set.reps}${set.rpe?.let { " • RPE $it" } ?: ""}")
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onRepeat, modifier = Modifier.weight(1f)) { Text("Повторить") }
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("Удалить") }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить тренировку?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
